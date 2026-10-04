package com.photoclarity.ai.core.session

import com.photoclarity.ai.core.analysis.AnalysisOutcome
import com.photoclarity.ai.core.media.PhotoAccess
import com.photoclarity.ai.core.media.PhotoAccessManager
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.*
import com.photoclarity.ai.ui.scan.ScanUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

fun interface ScanAnalysisEngine {
    suspend fun analyze(photos: List<Photo>, settings: ScanSettings, progress: MutableSharedFlow<ScanProgress>): AnalysisOutcome
}

@Singleton
class ScanCoordinator @Inject constructor(
    private val sessions: ScanSessionRepository,
    private val photos: PhotoRepository,
    private val settings: SettingsRepository,
    private val analysis: ScanAnalysisEngine,
    private val access: PhotoAccessManager,
    private val removal: RemovalCoordinator,
    private val gate: OperationGate,
    private val clock: SessionClock,
    private val runtime: SessionScope
) {
    private val scope get() = runtime.scope
    private val _state = MutableStateFlow(ScanUiState())
    val state = _state.asStateFlow()
    private var scanJob: Job? = null
    private var validationJob: Job? = null
    private var initialized = false
    private var initializing = false
    private var visible = false
    private var cancelReason = SessionError.USER_CANCELLED
    private var session: ScanSession? = null
    private var revision: Long? = null
    private var startedElapsed = 0L

    fun initialize() {
        if (initialized || initializing) return
        initializing = true
        scope.launch {
            try {
                sessions.initialize()
                removal.recover()
                initialized = true
                restoreScanState()
                access.state.collect { a ->
                    if (scanJob?.isActive == true && (revision != a.revision || a.error != null || a.access == PhotoAccess.DENIED)) cancel(SessionError.ACCESS_CHANGED)
                    val current = sessions.state.value.session
                    if (a.ready && current != null && (a.error != null || a.access == PhotoAccess.DENIED || current.scopeKey != a.scopeKey)) {
                        removal.accessChanged()
                        if (!removal.state.value.isLoading) sessions.invalidate(SessionError.ACCESS_CHANGED)
                    } else if (a.ready && current?.status == SessionStatus.COMPLETED && !gate.busy()) validateRestored()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { initialized = false; _state.value = _state.value.copy(error = "Tarama kayıtları açılamadı. İşlem başlatılmadı.") }
            finally { initializing = false }
        }
    }
    private fun restoreScanState() {
        val s = sessions.state.value
        _state.value = ScanUiState(totalPhotos = s.session?.discovered ?: 0,
            currentProgress = s.session?.attempted ?: 0,
            scanFinished = s.session?.status == SessionStatus.COMPLETED,
            error = if (s.session?.status == SessionStatus.COMPLETED) null else s.errorMessage,
            sessionId = s.session?.id, failedPhotos = s.session?.failed ?: 0)
    }
    private fun validateRestored() {
        validationJob?.cancel()
        val a = access.state.value; val s = sessions.state.value
        validationJob = scope.launch {
            try {
                sessions.setTrusted(false)
                val current = photos.arePhotosCurrent(s.groups.flatMap { it.photos })
                if (access.state.value.scopeKey != a.scopeKey || access.state.value.revision != a.revision || sessions.state.value.session?.id != s.session?.id) return@launch
                if (current) sessions.setTrusted(true) else invalidateRestored()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { invalidateRestored() }
        }
    }
    private suspend fun invalidateRestored() {
        try { sessions.invalidate(SessionError.MEDIA_CHANGED) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _state.value = _state.value.copy(error = "Kayıtlı sonuçlar doğrulanamadı. İşlem yapılmadı; yeniden tarayın.")
        }
    }
    fun setVisible(value: Boolean) { visible = value; if (!value) cancel(SessionError.BACKGROUND) }
    fun start(): Boolean {
        val a = access.state.value
        if (!initialized || !visible || !a.ready || a.access == PhotoAccess.DENIED || a.error != null || scanJob?.isActive == true || sessions.state.value.storageError != null) return false
        val id = clock.newId(); if (!gate.acquire(id)) return false
        validationJob?.cancel(); cancelReason = SessionError.USER_CANCELLED; revision = a.revision
        startedElapsed = clock.elapsed()
        _state.value = ScanUiState(isScanning = true, sessionId = id,
            steps = ScanStep.values().map { ScanStepStatus(it, ScanStepStatus.StepStatus.PENDING) })
        scanJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val config = settings.getScanSettingsSnapshot().let { it.copy(selectedFolders = it.selectedFolders.toSet()) }
                val current = ScanSession(id, clock.now(), scopeKey = checkNotNull(a.scopeKey), settings = config)
                session = current; sessions.begin(current)
                step(ScanStep.SCAN_FOLDERS, ScanStepStatus.StepStatus.IN_PROGRESS)
                val catalog = photos.loadAllPhotos(config.selectedFolders)
                currentCoroutineContext().ensureActive()
                session = current.copy(discovered = catalog.size)
                sessions.checkpoint(checkNotNull(session))
                _state.value = _state.value.copy(totalPhotos = catalog.size)
                step(ScanStep.SCAN_FOLDERS, ScanStepStatus.StepStatus.DONE)
                step(ScanStep.EXTRACT_METADATA, ScanStepStatus.StepStatus.DONE)
                step(ScanStep.COMPUTE_SIMILARITY, ScanStepStatus.StepStatus.IN_PROGRESS)
                step(ScanStep.MATCH_HASHES, ScanStepStatus.StepStatus.IN_PROGRESS)
                val outcome = coroutineScope {
                    val progress = MutableSharedFlow<ScanProgress>(extraBufferCapacity = 64)
                    var lastCheckpoint = clock.elapsed()
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                        progress.collect { p ->
                            if (p is ScanProgress.Hashing) {
                                val attempted = maxOf(_state.value.currentProgress, p.current)
                                _state.value = _state.value.copy(currentProgress = attempted, currentPhotoName = p.currentPhotoName)
                                session = checkNotNull(session).copy(attempted = attempted, durationMillis = (clock.elapsed() - startedElapsed).coerceAtLeast(0))
                                if (clock.elapsed() - lastCheckpoint >= 1000) { sessions.checkpoint(checkNotNull(session)); lastCheckpoint = clock.elapsed() }
                            }
                            // Comparing total is candidate count; never replace discovered/attempted.
                        }
                    }
                    try { analysis.analyze(catalog, config, progress) } finally { collector.cancelAndJoin() }
                }
                currentCoroutineContext().ensureActive()
                check(access.state.value.revision == revision && access.state.value.error == null)
                val terminal = checkNotNull(session).copy(status = SessionStatus.COMPLETED, endedAt = clock.now(),
                    attempted = outcome.attempted, failed = outcome.failed, failedKnown = true, matched = outcome.groups.flatMap { it.photos }.map { it.mediaKey }.distinct().size,
                    durationMillis = (clock.elapsed() - startedElapsed).coerceAtLeast(0), error = if (outcome.failed > 0) SessionError.MEDIA_UNREADABLE else null)
                sessions.complete(terminal, outcome.groups)
                _state.value = _state.value.copy(isScanning = false, scanFinished = true, currentProgress = outcome.attempted,
                    failedPhotos = outcome.failed, results = outcome.groups,
                    steps = ScanStep.values().map { ScanStepStatus(it, ScanStepStatus.StepStatus.DONE) })
            } catch (e: CancellationException) {
                withContext(NonCancellable) { terminal(id, if (cancelReason == SessionError.USER_CANCELLED) SessionStatus.CANCELLED else SessionStatus.INTERRUPTED, cancelReason) }
            } catch (e: Exception) {
                withContext(NonCancellable) { terminal(id, SessionStatus.FAILED, if (session == null) SessionError.STORAGE else if (e is SecurityException) SessionError.ACCESS_CHANGED else SessionError.ANALYSIS) }
            } finally { gate.release(id); session = null }
        }
        return true
    }
    private suspend fun terminal(id: String, status: SessionStatus, error: SessionError) {
        try {
            session?.takeIf { it.id == id }?.let { sessions.complete(it.copy(status = status, endedAt = clock.now(),
                durationMillis = (clock.elapsed() - startedElapsed).coerceAtLeast(0), error = error), emptyList()) }
        } catch (e: Exception) { /* Repository exposes a fail-closed storage error. */ }
        _state.value = _state.value.copy(isScanning = false, isCancelled = error == SessionError.USER_CANCELLED,
            error = if (error == SessionError.USER_CANCELLED) null else if (session == null && error == SessionError.STORAGE)
                "Tarama ayarları okunamadı. Tarama başlatılmadı." else SessionSnapshot(session = session?.copy(error = error)).errorMessage ?: "Tarama kesildi.")
    }
    fun cancel(reason: SessionError = SessionError.USER_CANCELLED) { if (scanJob?.isActive == true) { cancelReason = reason; scanJob?.cancel() } }
    private fun step(step: ScanStep, status: ScanStepStatus.StepStatus) { _state.value = _state.value.copy(steps = _state.value.steps.map { if (it.step == step) it.copy(status = status) else it }) }
}
