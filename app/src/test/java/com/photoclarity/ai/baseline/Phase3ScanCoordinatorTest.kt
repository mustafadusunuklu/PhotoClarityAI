package com.photoclarity.ai.baseline

import com.photoclarity.ai.core.analysis.AnalysisOutcome
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.core.media.*
import com.photoclarity.ai.core.session.*
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.ScanSessionRepository
import com.photoclarity.ai.testing.MemorySessionRepository
import com.photoclarity.ai.ui.scan.ScanViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class Phase3ScanCoordinatorTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakePhotoRepository()
    private val settings = FakeSettingsRepository()
    private val sessions = MemorySessionRepository(initial = SessionSnapshot(ready = true, trusted = true))
    private val access = MutableStateFlow(PhotoAccessSnapshot(PhotoAccess.FULL, ready = true, scopeKey = "FULL"))
    private val manager = mock(PhotoAccessManager::class.java).also { `when`(it.state).thenReturn(access) }
    private val gate = OperationGate()
    private val runtime get() = main.runtime()
    private var engine = ScanAnalysisEngine { photos, _, _ -> AnalysisOutcome(emptyList(), photos.size, 0) }
    private fun coordinator(): ScanCoordinator {
        val scope = runtime
        return ScanCoordinator(sessions, repo, settings, engine, manager,
            RemovalCoordinator(repo, settings, QualityScorer(), sessions, manager, gate, SessionClock(), scope), gate, SessionClock(), scope)
            .also { it.setVisible(true); it.initialize() }
    }
    @Test fun restoredValidationStorageFailureIsShownWithoutRetryLoopOrAnalysis() = runTest {
        val s = ScanSession("restore", 1, scopeKey = "FULL", settings = ScanSettings())
        sessions.begin(s); sessions.complete(s.copy(status = SessionStatus.COMPLETED), listOf(group()))
        repo.currentPhotos = false
        var invalidations = 0; var analyses = 0
        val failing = object : ScanSessionRepository by sessions {
            override suspend fun invalidate(error: SessionError) {
                ++invalidations
                throw java.io.IOException("isolated storage failure")
            }
        }
        val owned = runtime
        val removal = RemovalCoordinator(repo, settings, QualityScorer(), failing, manager, gate, SessionClock(), owned)
        val c = ScanCoordinator(failing, repo, settings, ScanAnalysisEngine { photos, _, _ ->
            ++analyses; AnalysisOutcome(emptyList(), photos.size, 0)
        }, manager, removal, gate, SessionClock(), owned)
        c.setVisible(true); c.initialize(); advanceUntilIdle()
        assertEquals(1, invalidations)
        assertFalse(sessions.state.value.trusted)
        assertNotNull(c.state.value.error)
        assertEquals(0, analyses); assertFalse(gate.busy())
    }
    @Test fun oldAnalysisVersionCannotRestoreTrustedResults() = runTest {
        val legacy = ScanSession("old-analysis", 1, scopeKey = "FULL", settings = ScanSettings(), analysisVersion = 0)
        sessions.begin(legacy); sessions.complete(legacy.copy(status = SessionStatus.COMPLETED), listOf(group()))
        coordinator(); advanceUntilIdle()
        assertEquals(SessionStatus.STALE, sessions.state.value.session?.status)
        assertEquals(SessionError.ANALYSIS_VERSION_CHANGED, sessions.state.value.session?.error)
        assertFalse(sessions.state.value.trusted); assertTrue(sessions.state.value.visibleGroups.isEmpty())
        assertEquals(0, repo.loadCount)
    }
    @Test fun progressPhaseDoesNotTurnAttemptedCountIntoComparisonCount() = runTest {
        repo.catalog = listOf(photo(1))
        engine = ScanAnalysisEngine { _, _, p -> p.emit(ScanProgress.Hashing(1, 1, "fixture")); p.emit(ScanProgress.Stage(ScanPhase.VISUAL)); awaitCancellation() }
        val c = coordinator(); advanceUntilIdle(); c.start(); runCurrent()
        assertEquals(ScanPhase.VISUAL, c.state.value.phase)
        assertEquals(1, c.state.value.currentProgress)
        c.cancel(); advanceUntilIdle()
    }
    @Test fun oneScanSurvivesDifferentViewModelsAndRejectsDuplicateStart() = runTest {
        repo.catalog = group().photos; engine = ScanAnalysisEngine { _, _, _ -> awaitCancellation() }
        val c = coordinator(); advanceUntilIdle()
        assertTrue(c.start()); runCurrent(); assertFalse(c.start())
        assertSame(ScanViewModel(c).uiState, ScanViewModel(c).uiState)
        assertEquals(1, repo.loadCount)
        c.cancel(); advanceUntilIdle(); assertEquals(SessionStatus.CANCELLED, sessions.state.value.session?.status)
        assertFalse(gate.busy())
    }
    @Test fun countsAreAttemptedPhotosRatherThanGroupMembersOrComparisonCandidates() = runTest {
        repo.catalog = (1L..6).map { photo(it) }
        engine = ScanAnalysisEngine { _, _, progress ->
            progress.emit(ScanProgress.Hashing(6, 6, "synthetic")); progress.emit(ScanProgress.Comparing(999, 999))
            AnalysisOutcome(listOf(group()), 6, 2)
        }
        val c = coordinator(); advanceUntilIdle(); assertTrue(c.start()); advanceUntilIdle()
        val s = sessions.state.value.session!!
        assertEquals(6, s.discovered); assertEquals(6, s.attempted); assertEquals(2, s.failed); assertEquals(4, s.matched)
        assertEquals(6, c.state.value.totalPhotos); assertEquals(6, c.state.value.currentProgress)
        assertEquals(SessionError.MEDIA_UNREADABLE, s.error)
    }
    @Test fun emptyCatalogIsCompletedSessionNotNoScanOrFailed() = runTest {
        val c = coordinator(); advanceUntilIdle(); c.start(); advanceUntilIdle()
        assertEquals(SessionStatus.COMPLETED, sessions.state.value.session?.status)
        assertTrue(sessions.state.value.groups.isEmpty()); assertTrue(c.state.value.scanFinished)
    }
    @Test fun backgroundCancelsAndPersistsInterruptedStateWithoutRestart() = runTest {
        engine = ScanAnalysisEngine { _, _, _ -> awaitCancellation() }
        val c = coordinator(); advanceUntilIdle(); c.start(); runCurrent(); c.setVisible(false); advanceUntilIdle()
        assertEquals(SessionStatus.INTERRUPTED, sessions.state.value.session?.status)
        assertEquals(SessionError.BACKGROUND, sessions.state.value.session?.error)
        c.setVisible(true); advanceUntilIdle(); assertFalse(c.state.value.isScanning); assertEquals(1, repo.loadCount)
    }
    @Test fun errorIsPersistedSeparatelyFromCleanResult() = runTest {
        engine = ScanAnalysisEngine { _, _, _ -> error("Analysis failed") }
        val c = coordinator(); advanceUntilIdle(); c.start(); advanceUntilIdle()
        assertEquals(SessionStatus.FAILED, sessions.state.value.session?.status)
        assertNotNull(c.state.value.error); assertFalse(c.state.value.scanFinished)
    }
    @Test fun permissionChangeStopsScanAndNeverPublishesCompletedFindings() = runTest {
        engine = ScanAnalysisEngine { _, _, _ -> awaitCancellation() }
        val c = coordinator(); advanceUntilIdle(); c.start(); runCurrent()
        access.value = PhotoAccessSnapshot(PhotoAccess.DENIED, revision = 1, ready = true, scopeKey = "DENIED"); advanceUntilIdle()
        assertEquals(SessionError.ACCESS_CHANGED, sessions.state.value.session?.error)
        assertNotEquals(SessionStatus.COMPLETED, sessions.state.value.session?.status); assertFalse(c.state.value.isScanning)
    }
    @Test fun settingsSnapshotDoesNotChangeDuringRunningScan() = runTest {
        val folders = mutableSetOf("test-folder")
        settings.config = ScanSettings(selectedFolders = folders)
        engine = ScanAnalysisEngine { _, _, _ -> awaitCancellation() }
        val c = coordinator(); advanceUntilIdle(); c.start(); runCurrent(); folders.add("other-folder")
        assertEquals(setOf("test-folder"), sessions.state.value.session?.settings?.selectedFolders)
        c.cancel(); advanceUntilIdle()
    }
    @Test fun runningCheckpointReopensAsInterruptedWithoutAutomaticAnalysis() = runTest {
        sessions.begin(ScanSession("dead-process", 1, scopeKey = "FULL", settings = ScanSettings(), attempted = 3))
        val c = coordinator(); advanceUntilIdle()
        assertEquals(SessionStatus.INTERRUPTED, sessions.state.value.session?.status)
        assertEquals(SessionError.PROCESS_INTERRUPTED, sessions.state.value.session?.error)
        assertEquals(0, repo.loadCount); assertFalse(c.state.value.isScanning)
    }
    @Test fun removalAndScanCannotAcquireSameOperationGate() = runTest {
        val c = coordinator(); advanceUntilIdle(); assertTrue(gate.acquire("removal-in-progress"))
        assertFalse(c.start()); assertEquals(0, repo.loadCount)
    }
    @Test fun immediateCancellationReleasesOwnerBeforeQueuedWorkRuns() = runTest {
        engine = ScanAnalysisEngine { _, _, _ -> awaitCancellation() }
        val c = coordinator(); advanceUntilIdle()
        assertTrue(c.start()); c.cancel(); assertTrue(c.state.value.isCancelling); advanceUntilIdle()
        assertFalse(c.state.value.isCancelling)
        assertFalse(gate.busy()); assertFalse(c.state.value.isScanning)
        assertEquals(SessionStatus.CANCELLED, sessions.state.value.session?.status)
    }
    @Test fun unreadableSettingsNeverStartCatalogOrFabricateSessionSettings() = runTest {
        settings.settingsReadError = java.io.IOException("Unreadable settings")
        val c = coordinator(); advanceUntilIdle(); assertTrue(c.start()); advanceUntilIdle()
        assertEquals(0, repo.loadCount); assertNull(sessions.state.value.session)
        assertFalse(c.state.value.scanFinished); assertFalse(c.state.value.isScanning)
        assertEquals("Tarama ayarları okunamadı. Tarama başlatılmadı.", c.state.value.error)
        assertFalse(gate.busy())
    }
}
