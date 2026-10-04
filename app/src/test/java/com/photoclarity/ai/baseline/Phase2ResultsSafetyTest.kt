package com.photoclarity.ai.baseline

import android.content.IntentSender
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.core.media.PhotoAccess
import com.photoclarity.ai.core.media.PhotoAccessManager
import com.photoclarity.ai.core.media.PhotoAccessSnapshot
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.ui.results.ResultsViewModel
import com.photoclarity.ai.testing.MemorySessionRepository
import com.photoclarity.ai.core.session.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class Phase2ResultsSafetyTest {
    @get:Rule val main = MainDispatcherRule()
    private val photos = group()
    private lateinit var repo: FakePhotoRepository
    private lateinit var stats: FakeSettingsRepository
    private lateinit var vm: ResultsViewModel
    private lateinit var access: MutableStateFlow<PhotoAccessSnapshot>
    @Before fun setup() {
        val sessions = MemorySessionRepository(listOf(photos))
        repo = FakePhotoRepository().also { it.removalMode = PhotoRepository.RemovalMode.SYSTEM_TRASH }
        stats = FakeSettingsRepository()
        access = MutableStateFlow(PhotoAccessSnapshot(PhotoAccess.FULL))
        val manager = mock(PhotoAccessManager::class.java)
        `when`(manager.state).thenReturn(access)
        vm = ResultsViewModel(RemovalCoordinator(repo, stats, QualityScorer(), sessions, manager, OperationGate(), SessionClock(), main.runtime()))
    }
    private fun uri(id: Long) = photos.photos.first { it.id == id }.contentUri
    private fun firstBatch() = PhotoRepository.DeleteResult.RequiresPermission(mock(IntentSender::class.java),
        trashUris = listOf(uri(2)), remainingTrashUris = listOf(uri(3), uri(4)))
    private fun start() { vm.smartSelectAll(); vm.requestDeleteConfirmation(); vm.deleteSelectedPhotos() }

    @Test fun eachBatchNeedsConsentAndOnlyApprovedBatchIsVerified() = runTest {
        repo.result = firstBatch(); start(); advanceUntilIdle()
        repo.verified = PhotoRepository.DeleteResult.Success(setOf(uri(2)))
        repo.result = PhotoRepository.DeleteResult.RequiresPermission(mock(IntentSender::class.java), trashUris = listOf(uri(3), uri(4)))
        vm.onDeleteResult(true); advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(2, vm.uiState.value.consentBatch)
        assertEquals(listOf(listOf(uri(2))), repo.verificationRequests)
        assertEquals(listOf(listOf(uri(2), uri(3), uri(4)), listOf(uri(3), uri(4))), repo.requests)
        repo.verified = PhotoRepository.DeleteResult.Success(setOf(uri(3), uri(4)))
        vm.onDeleteResult(true); advanceUntilIdle()
        assertEquals(3, vm.uiState.value.lastDeletedCount)
        assertEquals(listOf(listOf(uri(2)), listOf(uri(3), uri(4))), repo.verificationRequests)
        assertTrue(vm.uiState.value.groups.isEmpty()); assertTrue(stats.additions.isEmpty())
    }
    @Test fun cancellingLaterBatchRetainsOnlyUnprocessedSelection() = runTest {
        repo.result = firstBatch(); start(); advanceUntilIdle()
        repo.verified = PhotoRepository.DeleteResult.Success(setOf(uri(2)))
        repo.result = PhotoRepository.DeleteResult.RequiresPermission(mock(IntentSender::class.java), trashUris = listOf(uri(3), uri(4)))
        vm.onDeleteResult(true); advanceUntilIdle(); vm.onDeleteResult(false); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.lastDeletedCount)
        assertEquals(setOf(3L, 4L), vm.uiState.value.selectedPhotoIds)
        assertEquals(listOf(1L, 3L, 4L), vm.uiState.value.groups.single().photos.map { it.id })
        assertTrue(stats.additions.isEmpty())
    }
    @Test fun unverifiedBatchStopsQueueWithoutClaimingSuccess() = runTest {
        repo.result = firstBatch(); start(); advanceUntilIdle()
        repo.verified = PhotoRepository.DeleteResult.Success(emptySet(), setOf(uri(2)))
        vm.onDeleteResult(true); advanceUntilIdle()
        assertEquals(1, repo.requests.size); assertEquals(0, vm.uiState.value.lastDeletedCount)
        assertEquals(setOf(2L, 3L, 4L), vm.uiState.value.selectedPhotoIds)
    }
    @Test fun permissionChangeDismissesFrozenAppConfirmation() = runTest {
        vm.smartSelectAll(); vm.requestDeleteConfirmation(); advanceUntilIdle()
        access.value = PhotoAccessSnapshot(PhotoAccess.LIMITED, revision = 1); advanceUntilIdle()
        vm.deleteSelectedPhotos(); advanceUntilIdle()
        assertTrue(repo.requests.isEmpty()); assertTrue(vm.uiState.value.groups.isEmpty())
        assertNull(vm.uiState.value.confirmationPhotos)
    }
    @Test fun permissionChangeDuringSystemConsentDefersReconciliationAndStopsNextBatch() = runTest {
        repo.result = firstBatch(); start(); advanceUntilIdle()
        access.value = PhotoAccessSnapshot(PhotoAccess.DENIED, revision = 1); advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoading); assertEquals(photos, vm.uiState.value.groups.single())
        repo.verified = PhotoRepository.DeleteResult.Success(setOf(uri(2)))
        vm.onDeleteResult(true); advanceUntilIdle()
        assertEquals(1, repo.requests.size); assertEquals(1, vm.uiState.value.lastDeletedCount)
        assertTrue(vm.uiState.value.groups.isEmpty()); assertNotNull(vm.uiState.value.error)
    }
    @Test fun unchangedPermissionOnResumePreservesReviewSelection() = runTest {
        vm.togglePhotoSelection(2, true); advanceUntilIdle()
        access.value = PhotoAccessSnapshot(PhotoAccess.FULL, foreground = 1); advanceUntilIdle()
        assertEquals(setOf(2L), vm.uiState.value.selectedPhotoIds)
    }
}
