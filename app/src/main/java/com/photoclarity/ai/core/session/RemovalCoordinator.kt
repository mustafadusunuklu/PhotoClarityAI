package com.photoclarity.ai.core.session

import android.net.Uri
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.core.media.PhotoAccessManager
import com.photoclarity.ai.core.media.RemovalBatchPolicy
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.*
import com.photoclarity.ai.ui.results.ResultsUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Application ownership; navigation and ViewModel destruction do not cancel a removal. */
@Singleton
class RemovalCoordinator @Inject constructor(
    private val photos: PhotoRepository,
    private val settings: SettingsRepository,
    val qualityScorer: QualityScorer,
    private val sessions: ScanSessionRepository,
    private val access: PhotoAccessManager?,
    private val gate: OperationGate,
    private val clock: SessionClock,
    private val runtime: SessionScope
) {
    private val scope get() = runtime.scope
    private val _state = MutableStateFlow(ResultsUiState(removalMode = photos.removalMode))
    val state = _state.asStateFlow()
    private var request: RemovalJournal? = null
    private var pending: PhotoRepository.DeleteResult.RequiresPermission? = null
    private var token: String? = null
    private val retries = mutableSetOf<Uri>()
    private val selectionLock = Mutex()
    private var selectionWrites = 0
    private var accessInvalidated = false
    init {
        publish(sessions.state.value)
        scope.launch { sessions.state.collect { publish(it) } }
        access?.let { manager ->
            var revision = manager.state.value.revision
            scope.launch { manager.state.collect { a ->
                if (a.revision != revision || a.error != null || a.access == com.photoclarity.ai.core.media.PhotoAccess.DENIED) {
                    revision = a.revision; accessChanged()
                    if (!_state.value.isLoading && sessions.state.value.ready) {
                        try { sessions.invalidate(SessionError.ACCESS_CHANGED) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            // Room already publishes untrusted/storageError; keep this observer alive.
                            _state.value = _state.value.copy(error = "Erişim değişikliği kaydedilemedi. İşlem yapılmadı; yeniden tarayın.", confirmationPhotos = null)
                        }
                    }
                }
            } }
        }
    }
    private fun publish(s: SessionSnapshot) {
        val groups = s.visibleGroups
        val keys = if (selectionWrites > 0) _state.value.selectedPhotoIds.let { ids -> groups.flatMap { it.photos }.filter { it.id in ids }.map { it.mediaKey }.toSet() } else s.selectedKeys
        val safe = selectableKeys(groups)
        _state.value = _state.value.copy(groups = groups, sessionId = s.session?.id, scopeKey = s.session?.scopeKey,
            restoring = (!s.ready || (s.session?.status == SessionStatus.COMPLETED && !s.trusted)) && s.storageError == null,
            selectedPhotoIds = groups.flatMap { it.photos }.filter { it.mediaKey in keys && it.mediaKey in safe }.map { it.id }.toSet(),
            error = if (_state.value.isLoading) _state.value.error else s.storageError ?: s.errorMessage ?: _state.value.error.takeIf { _state.value.sessionId == s.session?.id })
    }
    fun accessChanged() {
        accessInvalidated = true
        _state.value = _state.value.copy(confirmationPhotos = null, waitingForLegacyWritePermission = false)
    }
    private fun safeIds(): Set<Long> = selectableKeys(_state.value.groups).let { keys -> _state.value.groups.flatMap { it.photos }.filter { it.mediaKey in keys }.map { it.id }.toSet() }
    private fun saveSelection(ids: Set<Long>) {
        val sessionId = sessions.state.value.session?.id ?: return
        val keys = _state.value.groups.flatMap { it.photos }.filter { it.id in ids }.map { it.mediaKey }.toSet()
        _state.value = _state.value.copy(selectedPhotoIds = ids); ++selectionWrites
        scope.launch {
            try { selectionLock.withLock { sessions.select(sessionId, keys) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(error = "Seçim kaydedilemedi; işlem yapılmadı.", confirmationPhotos = null) }
            finally { --selectionWrites; if (selectionWrites == 0) publish(sessions.state.value) }
        }
    }
    fun togglePhotoSelection(id: Long, selected: Boolean) {
        if (_state.value.selectionLocked || _state.value.restoring) return
        if (selected && id !in safeIds()) { _state.value = _state.value.copy(error = "Korunacak fotoğraf ve tekil kalite önerileri silme için seçilemez."); return }
        saveSelection(if (selected) _state.value.selectedPhotoIds + id else _state.value.selectedPhotoIds - id)
    }
    fun smartSelectAll() { if (!_state.value.selectionLocked && !_state.value.restoring) saveSelection(safeIds()) }
    fun requestDeleteConfirmation() {
        if (_state.value.selectionLocked || _state.value.restoring || gate.busy() || !sessions.state.value.trusted) return
        val ids = _state.value.selectedPhotoIds.intersect(safeIds())
        val frozen = _state.value.groups.flatMap { it.photos }.distinctBy { it.mediaKey }.filter { it.id in ids }
        if (frozen.isNotEmpty()) _state.value = _state.value.copy(confirmationPhotos = frozen.toList(), error = null, message = null)
    }
    fun dismissDeleteConfirmation() { if (!_state.value.isLoading) _state.value = _state.value.copy(confirmationPhotos = null) }
    fun onLegacyWritePermissionRequested() { if (_state.value.confirmationPhotos != null && !_state.value.isLoading) _state.value = _state.value.copy(waitingForLegacyWritePermission = true) }
    fun onLegacyWritePermissionResult(granted: Boolean) {
        if (!_state.value.waitingForLegacyWritePermission) return
        _state.value = _state.value.copy(waitingForLegacyWritePermission = false)
        if (granted) deleteSelectedPhotos() else _state.value = _state.value.copy(confirmationPhotos = null, error = "Silme izni verilmedi; fotoğraflar silinmedi.")
    }
    fun deleteSelectedPhotos() {
        if (_state.value.isLoading) return
        val frozen = _state.value.confirmationPhotos ?: return
        val session = sessions.state.value.session ?: return
        val id = clock.newId(); if (!gate.acquire(id)) return
        accessInvalidated = false; retries.clear()
        _state.value = _state.value.copy(isLoading = true, confirmationPhotos = null, error = null, message = null,
            lastDeletedCount = 0, consentBatch = 0, requestId = id,
            consentBatchCount = if (photos.removalMode == PhotoRepository.RemovalMode.SYSTEM_TRASH) RemovalBatchPolicy.count(frozen.size) else 0)
        scope.launch {
            try {
                selectionLock.withLock { /* Flush earlier queued selection writes. */ }
                val current = sessions.state.value; val keys = frozen.map { it.mediaKey }.toSet()
                check(current.trusted && current.session?.id == session.id && keys.all { it in selectableKeys(current.groups) })
                val keeperPhotos = current.groups.filter { g -> g.photos.any { it.mediaKey in keys } }.flatMap { it.photos }
                if (!photos.arePhotosCurrent(keeperPhotos)) {
                    sessions.invalidate(SessionError.MEDIA_CHANGED)
                    _state.value = _state.value.copy(isLoading = false, error = "Fotoğraflar değişti. İşlem yapılmadı; yeniden tarayın.")
                    gate.release(id); return@launch
                }
                request = RemovalJournal(id, session.id, clock.now(), photos.removalMode, frozen.toList())
                sessions.journal(checkNotNull(request)) // Durable before any destructive side effect.
                runRemoval(frozen.map { it.contentUri })
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failClosed(id) }
        }
    }
    private suspend fun checkpoint(status: RemovalStatus, removed: Set<Uri> = emptySet(), failed: Set<Uri> = emptySet(), issued: List<Uri>? = null) {
        val r = checkNotNull(request); val allowed = r.photos.map { it.mediaKey }.toSet()
        request = r.copy(status = status, removedKeys = r.removedKeys + removed.map { it.toString() }.filter { it in allowed },
            failedKeys = r.failedKeys + failed.map { it.toString() }.filter { it in allowed },
            issuedKeys = issued?.map { it.toString() }?.filter { it in allowed }?.toSet() ?: r.issuedKeys)
        sessions.journal(checkNotNull(request))
    }
    private suspend fun runRemoval(uris: List<Uri>) {
        try {
            checkpoint(RemovalStatus.APPLYING)
            when (val result = photos.deletePhotos(uris.toList())) {
                is PhotoRepository.DeleteResult.Success -> { checkpoint(RemovalStatus.RECONCILING, result.removedUris, result.failedUris); finish() }
                is PhotoRepository.DeleteResult.Error -> finish(result.message)
                is PhotoRepository.DeleteResult.RequiresPermission -> {
                    checkpoint(RemovalStatus.WAITING_SYSTEM, result.removedUris, result.failedUris,
                        result.trashUris.ifEmpty { if (result.retryUris.isEmpty()) uris else emptyList() })
                    if (result.retryUris.firstOrNull() in retries) finish("Sistem izni sonrasında silme tamamlanamadı.")
                    else {
                        pending = result.copy(retryUris = result.retryUris.toList(), trashUris = result.trashUris.toList(), remainingTrashUris = result.remainingTrashUris.toList())
                        token = clock.newId()
                        _state.value = _state.value.copy(pendingDeleteIntentSender = result.intentSender, pendingConsentId = token,
                            consentBatch = _state.value.consentBatch + if (result.retryUris.isEmpty()) 1 else 0)
                    }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { request?.let { failClosed(it.id) } }
    }
    fun onDeletePromptLaunched() { _state.value = _state.value.copy(pendingDeleteIntentSender = null) }
    fun onDeletePromptFailed() {
        if (pending == null) return
        pending = null; token = null
        scope.launch { try { finish("Sistem onay ekranı açılamadı.") } catch (e: CancellationException) { throw e }
            catch (e: Exception) { request?.let { failClosed(it.id) } } }
    }
    fun onDeleteResult(approved: Boolean, consentId: String? = token) {
        if (consentId == null || consentId != token) return
        val prompt = pending ?: return
        pending = null; token = null
        _state.value = _state.value.copy(pendingDeleteIntentSender = null, pendingConsentId = null)
        scope.launch {
            try {
                if (!approved) finish("İşlem iptal edildi. Tamamlanmayan fotoğraflar sonuçlarda korundu.")
                else if (accessInvalidated && prompt.retryUris.isNotEmpty()) finish("Fotoğraf erişimi değişti; kalıcı silme tekrar denenmedi.")
                else if (prompt.retryUris.isNotEmpty()) { retries.add(prompt.retryUris.first()); runRemoval(prompt.retryUris) }
                else {
                    val r = checkNotNull(request)
                    val batch = r.photos.filter { it.mediaKey in r.issuedKeys }.map { it.contentUri }
                    val verified = photos.verifyTrashedPhotos(batch)
                    checkpoint(RemovalStatus.RECONCILING, verified.removedUris, verified.failedUris)
                    if (accessInvalidated) finish("Fotoğraf erişimi değişti. Kalan bölümler işlenmedi; yeniden tarayın.")
                    else if (verified.failedUris.isNotEmpty()) finish("Bu bölümde bazı fotoğraflar doğrulanamadı. Kalan bölümler işlenmedi; yeniden tarayın.")
                    else if (prompt.remainingTrashUris.isNotEmpty()) runRemoval(prompt.remainingTrashUris)
                    else finish()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { request?.let { failClosed(it.id) } }
        }
    }
    private fun failClosed(id: String) {
        pending = null; token = null
        _state.value = _state.value.copy(isLoading = false, pendingDeleteIntentSender = null, pendingConsentId = null,
            error = "İşlem kaydı doğrulanamadı. Fotoğrafları yeniden tarayarak kontrol edin.", recoveryRequired = true)
        if (sessions.state.value.removal?.id != id) {
            request = null; gate.release(id)
            _state.value = _state.value.copy(recoveryRequired = false)
        }
    }
    private suspend fun finish(error: String? = null) {
        val r = request ?: return
        val removed = r.photos.filter { it.mediaKey in r.removedKeys }; val incomplete = r.photos.size - removed.size
        val message = if (removed.isEmpty()) null else if (r.mode == PhotoRepository.RemovalMode.SYSTEM_TRASH)
            "${removed.size} fotoğraf cihazın sistem çöp kutusuna taşındı. Geri yüklemeyi sistem veya galeri uygulaması yönetir."
        else "${removed.size} fotoğraf kalıcı olarak silindi."
        val finalError = error ?: if (incomplete > 0) "$incomplete fotoğraf için işlem doğrulanamadı; bu fotoğraflar silinmiş kabul edilmedi." else null
        request = r.copy(status = RemovalStatus.RECONCILING, error = finalError)
        sessions.reconcile(checkNotNull(request))
        if (r.mode == PhotoRepository.RemovalMode.PERMANENT_DELETE && removed.isNotEmpty())
            settings.addCleanedBytesOnce(r.id, removed.sumOf { it.sizeBytes }, clock.monthKey(r.createdAt))
        sessions.journal(checkNotNull(request).copy(status = RemovalStatus.FINISHED))
        request = null; pending = null; token = null
        _state.value = _state.value.copy(isLoading = false, pendingDeleteIntentSender = null, pendingConsentId = null,
            lastDeletedCount = removed.size, message = message, error = finalError, recoveryRequired = false)
        gate.release(r.id)
        if (accessInvalidated) sessions.invalidate(SessionError.ACCESS_CHANGED)
        publish(sessions.state.value)
    }
    /** Never re-launch consent, retry permanent deletion or advance remaining batches on reopen. */
    suspend fun recover() {
        val r = sessions.state.value.removal?.takeIf { it.status != RemovalStatus.FINISHED } ?: return
        if (request != null || !gate.acquire(r.id)) return
        request = r; pending = null; token = null
        _state.value = _state.value.copy(isLoading = true, requestId = r.id)
        try {
            if (r.mode == PhotoRepository.RemovalMode.SYSTEM_TRASH && r.status in setOf(RemovalStatus.WAITING_SYSTEM, RemovalStatus.APPLYING)) {
                val batch = r.photos.filter { it.mediaKey in r.issuedKeys }.map { it.contentUri }
                if (batch.isNotEmpty()) { val result = photos.verifyTrashedPhotos(batch); checkpoint(RemovalStatus.RECONCILING, result.removedUris, result.failedUris) }
            }
            val incomplete = checkNotNull(request).photos.any { it.mediaKey !in checkNotNull(request).removedKeys }
            finish("Önceki işlem kesildi. Yalnız doğrulanmış sonuçlar kaydedildi; kalan fotoğraflar için yeni inceleme ve onay gerekir.")
            if (incomplete) sessions.invalidate(SessionError.MEDIA_CHANGED)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failClosed(r.id) }
    }
    fun retryRecovery() {
        val r = request
        if (_state.value.isLoading) return
        if (r != null) { request = null; gate.release(r.id) }
        scope.launch { recover() }
    }
}
