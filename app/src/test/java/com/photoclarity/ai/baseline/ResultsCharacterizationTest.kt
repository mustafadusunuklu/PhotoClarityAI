package com.photoclarity.ai.baseline

import android.content.IntentSender
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.ui.results.ResultsViewModel
import com.photoclarity.ai.ui.scan.ScanResultHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsCharacterizationTest {
    @get:Rule val main = MainDispatcherRule()
    private lateinit var repo: FakePhotoRepository
    private lateinit var stats: FakeSettingsRepository
    private lateinit var vm: ResultsViewModel
    private lateinit var original: DuplicateGroup
    @Before fun setup() {
        original = group(); ScanResultHolder.groups = listOf(original)
        repo = FakePhotoRepository(); stats = FakeSettingsRepository()
        vm = ResultsViewModel(repo, stats, QualityScorer())
    }
    @After fun clear() { ScanResultHolder.groups = emptyList() }
    private fun select(vararg ids: Long) = ids.forEach { vm.togglePhotoSelection(it, true) }
    private fun start() { vm.requestDeleteConfirmation(); vm.deleteSelectedPhotos() }
    private fun uri(id: Long) = original.photos.first { it.id == id }.contentUri
    private fun permission() = PhotoRepository.DeleteResult.RequiresPermission(mock(IntentSender::class.java))

    @Test fun smartSelectionProtectsKeeper() { vm.smartSelectAll(); assertEquals(setOf(2L,3L,4L), vm.uiState.value.selectedPhotoIds) }
    @Test fun keeperCannotBeSelectedManually() { select(1); assertTrue(vm.uiState.value.selectedPhotoIds.isEmpty()) }
    @Test fun unknownPhotoCannotBeSelected() { select(999); assertTrue(vm.uiState.value.selectedPhotoIds.isEmpty()) }
    @Test fun lowQualitySuggestionsAreNeverBulkDeletionCandidates() {
        ScanResultHolder.groups = listOf(group(DuplicateGroup.GroupType.LOW_QUALITY))
        vm = ResultsViewModel(repo, stats, QualityScorer()); vm.smartSelectAll(); select(2)
        assertTrue(vm.uiState.value.selectedPhotoIds.isEmpty())
    }
    @Test fun missingKeeperDisablesDeletionForGroup() {
        ScanResultHolder.groups = listOf(original.copy(recommendedKeepId = 999))
        vm = ResultsViewModel(repo, stats, QualityScorer()); vm.smartSelectAll()
        assertTrue(vm.uiState.value.selectedPhotoIds.isEmpty())
    }
    @Test fun selectionCanBeCleared() { select(2); vm.togglePhotoSelection(2,false); assertTrue(vm.uiState.value.selectedPhotoIds.isEmpty()) }
    @Test fun noDeletionWithoutAppConfirmation() = runTest { select(2); vm.deleteSelectedPhotos(); advanceUntilIdle(); assertTrue(repo.requests.isEmpty()) }
    @Test fun zeroRowDeletePreservesPhotosAndDoesNotCreditBytes() = runTest {
        select(2); start(); advanceUntilIdle()
        assertEquals(original, vm.uiState.value.groups.single()); assertTrue(stats.additions.isEmpty())
        assertNotNull(vm.uiState.value.error)
    }
    @Test fun partialDeleteRemovesOnlySuccessfulUriAndRecomputesWaste() = runTest {
        repo.result = PhotoRepository.DeleteResult.Success(setOf(uri(2)), setOf(uri(3)))
        select(2,3); start(); advanceUntilIdle()
        assertEquals(listOf(1L,3L,4L), vm.uiState.value.groups.single().photos.map { it.id })
        assertEquals(700L, vm.uiState.value.groups.single().totalWasteBytes)
        assertEquals(listOf(200L),stats.additions); assertEquals(setOf(3L),vm.uiState.value.selectedPhotoIds)
    }
    @Test fun errorPreservesPhotosAndSelection() = runTest {
        repo.result = PhotoRepository.DeleteResult.Error("failure"); select(2); start(); advanceUntilIdle()
        assertEquals(original,vm.uiState.value.groups.single()); assertTrue(stats.additions.isEmpty())
        assertFalse(vm.uiState.value.isLoading); assertEquals("failure",vm.uiState.value.error)
    }
    @Test fun dialogSnapshotIsFrozenAndReentryLocked() = runTest {
        repo.result = permission(); select(2); start(); advanceUntilIdle()
        select(3); vm.togglePhotoSelection(2,false); start(); advanceUntilIdle()
        assertEquals(listOf(listOf(uri(2))),repo.requests)
        assertEquals(setOf(2L),vm.uiState.value.selectedPhotoIds)
    }
    @Test fun cancelledSystemPromptClearsPendingAndPreservesPhotos() = runTest {
        repo.result = permission(); select(2); start(); advanceUntilIdle(); vm.onDeleteResult(false); advanceUntilIdle()
        assertNull(vm.uiState.value.pendingDeleteIntentSender); assertFalse(vm.uiState.value.isLoading)
        assertEquals(original,vm.uiState.value.groups.single()); assertTrue(stats.additions.isEmpty())
    }
    @Test fun trashApprovalRequiresUriVerificationAndDoesNotCreditFreeSpace() = runTest {
        repo.removalMode = PhotoRepository.RemovalMode.SYSTEM_TRASH
        vm = ResultsViewModel(repo,stats,QualityScorer())
        repo.result = permission(); repo.verified = PhotoRepository.DeleteResult.Success(setOf(uri(2)),setOf(uri(3)))
        select(2,3); start(); advanceUntilIdle(); vm.onDeleteResult(true); advanceUntilIdle()
        assertEquals(listOf(1L,3L,4L),vm.uiState.value.groups.single().photos.map { it.id })
        assertTrue(stats.additions.isEmpty()); assertEquals(1,vm.uiState.value.lastDeletedCount)
    }
    @Test fun repeatedCallbackCannotRemoveDifferentSelection() = runTest {
        repo.result = PhotoRepository.DeleteResult.RequiresPermission(mock(IntentSender::class.java), retryUris = listOf(uri(2)))
        select(2); start(); advanceUntilIdle()
        repo.result = PhotoRepository.DeleteResult.Success(setOf(uri(2)))
        vm.onDeleteResult(true); advanceUntilIdle()
        select(3); vm.onDeleteResult(true); advanceUntilIdle()
        assertTrue(vm.uiState.value.groups.single().photos.any { it.id == 3L })
        assertEquals(listOf(200L),stats.additions)
    }
    @Test fun androidTenPermissionRetriesOnlyFrozenRemainingUris() = runTest {
        repo.result = PhotoRepository.DeleteResult.RequiresPermission(mock(IntentSender::class.java),
            setOf(uri(2)), emptySet(), listOf(uri(3)))
        select(2,3); start(); advanceUntilIdle()
        repo.result = PhotoRepository.DeleteResult.Success(setOf(uri(3)))
        vm.onDeleteResult(true); advanceUntilIdle()
        assertEquals(listOf(listOf(uri(2),uri(3)),listOf(uri(3))),repo.requests)
        assertEquals(listOf(500L),stats.additions)
    }
    @Test fun permissionCancelledAfterPartialLegacyDeleteReconcilesCompletedOnly() = runTest {
        repo.result = PhotoRepository.DeleteResult.RequiresPermission(mock(IntentSender::class.java),
            setOf(uri(2)),emptySet(),listOf(uri(3)))
        select(2,3); start(); advanceUntilIdle(); vm.onDeleteResult(false); advanceUntilIdle()
        assertEquals(listOf(1L,3L,4L),vm.uiState.value.groups.single().photos.map { it.id })
        assertEquals(listOf(200L),stats.additions)
    }
    @Test fun dismissingAppDialogMakesNoRepositoryRequest() = runTest {
        select(2); vm.requestDeleteConfirmation(); vm.dismissDeleteConfirmation(); vm.deleteSelectedPhotos(); advanceUntilIdle()
        assertTrue(repo.requests.isEmpty())
    }
    @Test fun systemLaunchFailureReleasesLock() = runTest {
        repo.result = permission(); select(2); start(); advanceUntilIdle(); vm.onDeletePromptFailed(); advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading); assertNotNull(vm.uiState.value.error)
    }
    @Test fun deniedLegacyWritePermissionCannotStartDeletion() = runTest {
        select(2); vm.requestDeleteConfirmation(); vm.onLegacyWritePermissionRequested()
        vm.onLegacyWritePermissionResult(false); advanceUntilIdle()
        assertTrue(repo.requests.isEmpty()); assertNull(vm.uiState.value.confirmationPhotos)
        assertNotNull(vm.uiState.value.error); assertFalse(vm.uiState.value.selectionLocked)
    }
    @Test fun grantedLegacyWritePermissionUsesFrozenAppConfirmedSelection() = runTest {
        select(2); vm.requestDeleteConfirmation(); vm.onLegacyWritePermissionRequested(); select(3)
        vm.onLegacyWritePermissionResult(true); advanceUntilIdle()
        assertEquals(listOf(listOf(uri(2))),repo.requests)
    }
    @Test fun repeatedAndroidTenPermissionRequestStopsRetryLoop() = runTest {
        repo.result = PhotoRepository.DeleteResult.RequiresPermission(mock(IntentSender::class.java), retryUris = listOf(uri(2)))
        select(2); start(); advanceUntilIdle(); vm.onDeleteResult(true); advanceUntilIdle()
        assertEquals(2,repo.requests.size); assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.pendingDeleteIntentSender); assertNotNull(vm.uiState.value.error)
    }

}
