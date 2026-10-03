package com.photoclarity.ai.baseline

import android.content.IntentSender
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.repository.PhotoRepository.DeleteResult
import com.photoclarity.ai.ui.results.ResultsViewModel
import com.photoclarity.ai.ui.scan.ScanResultHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.mock

/** Expected unsafe behaviors are deliberately named and linked to roadmap risks. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultsCharacterizationTest {
    @get:Rule val main = MainDispatcherRule()
    private lateinit var repository: FakePhotoRepository
    private lateinit var stats: FakeSettingsRepository
    private lateinit var vm: ResultsViewModel

    @Before fun setup() {
        repository = FakePhotoRepository()
        stats = FakeSettingsRepository()
        ScanResultHolder.groups = listOf(group())
        vm = ResultsViewModel(repository, stats, QualityScorer())
    }
    @After fun cleanup() { ScanResultHolder.groups = emptyList() }
    private fun remaining() = vm.uiState.value.groups.flatMap { it.photos }.map { it.id }.toSet()

    @Test fun smartSelectionExcludesRecommendedPhoto() {
        vm.smartSelectAll()
        assertEquals(setOf(2L, 3L, 4L), vm.uiState.value.selectedPhotoIds)
        assertTrue(repository.requests.isEmpty())
    }

    @Test fun lowQualityGroupCurrentlySelectsAllExceptOneDespiteUnrelatedContent_R02() {
        ScanResultHolder.groups = listOf(group(DuplicateGroup.GroupType.LOW_QUALITY))
        vm = ResultsViewModel(repository, stats, QualityScorer())
        vm.smartSelectAll()
        assertEquals(setOf(2L, 3L, 4L), vm.uiState.value.selectedPhotoIds)
    }

    @Test fun manualSelectionCurrentlyAllowsRecommendedKeeper_R12() {
        vm.togglePhotoSelection(1, true)
        assertEquals(setOf(1L), vm.uiState.value.selectedPhotoIds)
    }

    @Test fun deselectionRemovesOnlySpecifiedId() {
        vm.togglePhotoSelection(2, true)
        vm.togglePhotoSelection(3, true)
        vm.togglePhotoSelection(2, false)
        assertEquals(setOf(3L), vm.uiState.value.selectedPhotoIds)
    }

    @Test fun successZeroCurrentlyRemovesSelectionAndCreditsBytes_R07_R14() = runTest {
        repository.result = DeleteResult.Success(0)
        vm.togglePhotoSelection(2, true)
        vm.deleteSelectedPhotos()
        advanceUntilIdle()
        assertEquals(listOf("content://phase0.synthetic/images/2"), repository.requests.single().map { it.toString() })
        assertEquals(setOf(1L, 3L, 4L), remaining())
        assertEquals(listOf(200L), stats.additions)
        assertEquals(0, vm.uiState.value.lastDeletedCount)
        assertTrue(vm.uiState.value.selectedPhotoIds.isEmpty())
    }

    @Test fun partialSuccessCurrentlyRemovesEverySelectedPhoto_R07() = runTest {
        repository.result = DeleteResult.Success(1)
        vm.togglePhotoSelection(2, true)
        vm.togglePhotoSelection(3, true)
        vm.deleteSelectedPhotos()
        advanceUntilIdle()
        assertEquals(2, repository.requests.single().size)
        assertEquals(setOf(1L, 4L), remaining())
        assertEquals(listOf(500L), stats.additions)
        assertEquals(1, vm.uiState.value.lastDeletedCount)
    }

    @Test fun errorPreservesSelectionGroupsAndDoesNotCreditBytes() = runTest {
        repository.result = DeleteResult.Error("synthetic denial")
        vm.togglePhotoSelection(2, true)
        vm.deleteSelectedPhotos()
        advanceUntilIdle()
        assertEquals(setOf(1L, 2L, 3L, 4L), remaining())
        assertEquals(setOf(2L), vm.uiState.value.selectedPhotoIds)
        assertEquals("synthetic denial", vm.uiState.value.error)
        assertTrue(stats.additions.isEmpty())
    }

    @Test fun permissionRequestPreservesGroupsUntilConfirmation() = runTest {
        val sender = mock(IntentSender::class.java)
        repository.result = DeleteResult.RequiresPermission(sender)
        vm.togglePhotoSelection(2, true)
        vm.deleteSelectedPhotos()
        advanceUntilIdle()
        assertSame(sender, vm.uiState.value.pendingDeleteIntentSender)
        assertEquals(setOf(1L, 2L, 3L, 4L), remaining())
        assertTrue(stats.additions.isEmpty())
    }

    @Test fun confirmationCurrentlyUsesChangedSelectionInsteadOfRequestSnapshot_R13() = runTest {
        repository.result = DeleteResult.RequiresPermission(mock(IntentSender::class.java))
        val requested = vm.uiState.value.groups.single().photos[1].contentUri
        vm.togglePhotoSelection(2, true)
        vm.deleteSelectedPhotos()
        advanceUntilIdle()
        vm.togglePhotoSelection(2, false)
        vm.togglePhotoSelection(3, true)
        vm.onDeleteConfirmed()
        assertEquals(listOf(requested), repository.requests.single())
        assertEquals(setOf(1L, 2L, 4L), remaining())
        assertNull(vm.uiState.value.pendingDeleteIntentSender)
        assertTrue(stats.additions.isEmpty()) // R14: API30+ confirmation omits statistics.
    }

    @Test fun deletionCurrentlyLeavesWasteAndKeeperStale_R14() = runTest {
        repository.result = DeleteResult.Success(1)
        vm.togglePhotoSelection(1, true)
        vm.deleteSelectedPhotos()
        advanceUntilIdle()
        val updated = vm.uiState.value.groups.single()
        assertEquals(1L, updated.recommendedKeepId)
        assertNull(updated.recommendedPhoto)
        assertEquals(900L, updated.totalWasteBytes)
        assertEquals(updated, ScanResultHolder.groups.single())
    }

    @Test fun singletonGroupCurrentlyDisappearsAfterDeletion() = runTest {
        repository.result = DeleteResult.Success(3)
        vm.smartSelectAll()
        vm.deleteSelectedPhotos()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.groups.isEmpty())
        assertTrue(ScanResultHolder.groups.isEmpty())
        assertEquals(3, vm.uiState.value.lastDeletedCount)
    }
}
