package com.photoclarity.ai.baseline

import android.content.IntentSender
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.core.media.*
import com.photoclarity.ai.core.session.*
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.domain.repository.ScanSessionRepository
import com.photoclarity.ai.testing.MemorySessionRepository
import com.photoclarity.ai.ui.results.ResultsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class Phase3RemovalStateTest {
    @get:Rule val main = MainDispatcherRule()
    private val original = group()
    private val sessions = MemorySessionRepository(listOf(original))
    private val repo = FakePhotoRepository()
    private val stats = FakeSettingsRepository()
    private fun coordinator() = RemovalCoordinator(repo, stats, QualityScorer(), sessions, null, OperationGate(), SessionClock(), main.runtime())
    private fun uri(id: Long) = original.photos.first { it.id == id }.contentUri
    private fun begin(vm: ResultsViewModel) { vm.togglePhotoSelection(2, true); vm.requestDeleteConfirmation(); vm.deleteSelectedPhotos() }

    @Test fun frozenJournalExistsBeforeRepositorySideEffect() = runTest {
        val co = coordinator(); val vm = ResultsViewModel(co)
        repo.beforeDelete = {
            val r = checkNotNull(sessions.state.value.removal)
            assertEquals(RemovalStatus.APPLYING, r.status)
            assertEquals(listOf(uri(2)), r.photos.map { it.contentUri })
            assertFalse(r.photos.any { it.id == original.recommendedKeepId })
        }
        begin(vm); advanceUntilIdle(); assertEquals(1, repo.requests.size)
    }
    @Test fun navigationViewModelsShareSelectionAndDeletionState() = runTest {
        val co = coordinator(); val list = ResultsViewModel(co); val detail = ResultsViewModel(co)
        list.togglePhotoSelection(2, true); advanceUntilIdle()
        assertEquals(setOf(2L), detail.uiState.value.selectedPhotoIds)
        repo.result = PhotoRepository.DeleteResult.Success(setOf(uri(2)))
        list.requestDeleteConfirmation(); list.deleteSelectedPhotos(); advanceUntilIdle()
        assertEquals(list.uiState.value, detail.uiState.value)
        assertEquals(listOf(1L,3L,4L), detail.uiState.value.groups.single().photos.map { it.id })
    }
    @Test fun restoredSelectionRequiresTrustAndKeeperIsFiltered() = runTest {
        sessions.setTrusted(false)
        val vm = ResultsViewModel(coordinator()); vm.smartSelectAll(); vm.requestDeleteConfirmation(); vm.deleteSelectedPhotos(); advanceUntilIdle()
        assertTrue(repo.requests.isEmpty()); assertTrue(vm.uiState.value.restoring)
        sessions.setTrusted(true); sessions.select("test-session", original.photos.map { it.mediaKey }.toSet()); advanceUntilIdle()
        assertEquals(setOf(2L,3L,4L), vm.uiState.value.selectedPhotoIds)
    }
    @Test fun changedMediaInvalidatesInsteadOfDeletingOldSnapshot() = runTest {
        repo.currentPhotos = false
        val vm = ResultsViewModel(coordinator()); begin(vm); advanceUntilIdle()
        assertTrue(repo.requests.isEmpty()); assertEquals(SessionError.MEDIA_CHANGED, sessions.state.value.session?.error)
        assertTrue(vm.uiState.value.groups.isEmpty()); assertEquals(0L, stats.additions.sum())
    }
    @Test fun journalFailurePreventsDestructiveCall() = runTest {
        val vm = ResultsViewModel(coordinator()); vm.togglePhotoSelection(2, true); advanceUntilIdle()
        sessions.rejectWrites = true
        vm.requestDeleteConfirmation(); vm.deleteSelectedPhotos(); advanceUntilIdle()
        assertTrue(repo.requests.isEmpty()); assertFalse(vm.uiState.value.isLoading)
    }
    @Test fun staleConsentTokenCannotApproveNewRequest() = runTest {
        repo.removalMode = PhotoRepository.RemovalMode.SYSTEM_TRASH
        repo.result = PhotoRepository.DeleteResult.RequiresPermission(mock(IntentSender::class.java), trashUris = listOf(uri(2)))
        val vm = ResultsViewModel(coordinator()); begin(vm); advanceUntilIdle()
        val old = vm.uiState.value.pendingConsentId
        vm.onDeleteResult(false, old); advanceUntilIdle()
        vm.requestDeleteConfirmation(); vm.deleteSelectedPhotos(); advanceUntilIdle()
        assertNotEquals(old, vm.uiState.value.pendingConsentId)
        vm.onDeleteResult(true, old); advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoading); assertTrue(repo.verificationRequests.isEmpty())
    }
    @Test fun processRecoveryOnlyVerifiesIssuedTrashBatchAndNeverRepeatsDelete() = runTest {
        repo.removalMode = PhotoRepository.RemovalMode.SYSTEM_TRASH
        sessions.journal(RemovalJournal("killed", "test-session", SessionClock().now(), repo.removalMode, original.photos.drop(1),
            RemovalStatus.WAITING_SYSTEM, issuedKeys = setOf(original.photos[1].mediaKey)))
        repo.verified = PhotoRepository.DeleteResult.Success(setOf(uri(2)))
        coordinator().recover(); advanceUntilIdle()
        assertTrue(repo.requests.isEmpty())
        assertEquals(listOf(listOf(uri(2))), repo.verificationRequests)
        assertEquals(setOf(original.photos[1].mediaKey), sessions.state.value.removal?.removedKeys)
        assertEquals(RemovalStatus.FINISHED, sessions.state.value.removal?.status)
        assertTrue(stats.additions.isEmpty()); assertEquals(SessionStatus.STALE, sessions.state.value.session?.status)
    }
    @Test fun interruptedPermanentDeleteNeverTreatsMissingOrUnknownAsSuccess() = runTest {
        sessions.journal(RemovalJournal("legacy-killed", "test-session", SessionClock().now(), repo.removalMode, original.photos.drop(1), RemovalStatus.APPLYING))
        coordinator().recover(); advanceUntilIdle()
        assertTrue(repo.requests.isEmpty()); assertTrue(repo.verificationRequests.isEmpty()); assertTrue(stats.additions.isEmpty())
        assertTrue(sessions.state.value.removal!!.removedKeys.isEmpty())
    }
    @Test fun crashAfterAtomicCreditReplaysSameRequestWithoutDoubleCount() = runTest {
        repo.result = PhotoRepository.DeleteResult.Success(setOf(uri(2)))
        stats.failAfterCredit = true
        val vm = ResultsViewModel(coordinator()); begin(vm); advanceUntilIdle()
        assertEquals(listOf(200L), stats.additions)
        assertEquals(RemovalStatus.RECONCILING, sessions.state.value.removal?.status)
        coordinator().recover(); advanceUntilIdle()
        assertEquals(listOf(200L), stats.additions); assertEquals(1, repo.requests.size)
        assertEquals(RemovalStatus.FINISHED, sessions.state.value.removal?.status)
    }
    @Test fun newSessionRejectsOldSessionSelection() = runTest {
        sessions.begin(ScanSession("new-session", 2, scopeKey = "FULL", settings = ScanSettings()))
        sessions.select("test-session", setOf(original.photos[1].mediaKey))
        assertTrue(sessions.state.value.selectedKeys.isEmpty())
    }
    @Test fun accessInvalidationStorageFailureDoesNotCrashObserverOrDelete() = runTest {
        val access = MutableStateFlow(PhotoAccessSnapshot(PhotoAccess.FULL, ready = true, scopeKey = "FULL"))
        val manager = mock(PhotoAccessManager::class.java).also { `when`(it.state).thenReturn(access) }
        var invalidations = 0
        val failing = object : ScanSessionRepository by sessions {
            override suspend fun invalidate(error: SessionError) {
                ++invalidations
                sessions.setTrusted(false)
                throw java.io.IOException("isolated storage failure")
            }
        }
        val vm = ResultsViewModel(RemovalCoordinator(repo, stats, QualityScorer(), failing, manager, OperationGate(), SessionClock(), main.runtime()))
        advanceUntilIdle()
        access.value = PhotoAccessSnapshot(PhotoAccess.DENIED, revision = 1, ready = true, scopeKey = "DENIED")
        advanceUntilIdle()
        assertEquals(1, invalidations)
        assertNotNull(vm.uiState.value.error)
        vm.smartSelectAll(); vm.requestDeleteConfirmation(); vm.deleteSelectedPhotos(); advanceUntilIdle()
        assertTrue(repo.requests.isEmpty())
        access.value = PhotoAccessSnapshot(PhotoAccess.FULL, revision = 2, ready = true, scopeKey = "FULL")
        advanceUntilIdle()
        assertEquals(2, invalidations) // Observer survives; no automatic delete or retry.
    }
}
