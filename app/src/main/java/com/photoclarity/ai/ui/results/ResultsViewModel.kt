package com.photoclarity.ai.ui.results

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import com.photoclarity.ai.core.session.RemovalCoordinator
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private fun sizeLabel(bytes: Long): String = when {
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    else -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
}
data class ResultsUiState(
    val groups: List<DuplicateGroup> = emptyList(), val selectedPhotoIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false, val error: String? = null, val message: String? = null,
    val pendingDeleteIntentSender: IntentSender? = null, val lastDeletedCount: Int = 0,
    val removalMode: PhotoRepository.RemovalMode = PhotoRepository.RemovalMode.PERMANENT_DELETE,
    val waitingForLegacyWritePermission: Boolean = false, val confirmationPhotos: List<Photo>? = null,
    val consentBatch: Int = 0, val consentBatchCount: Int = 0, val sessionId: String? = null,
    val requestId: String? = null, val pendingConsentId: String? = null,
    val restoring: Boolean = true, val recoveryRequired: Boolean = false, val scopeKey: String? = null
) {
    val selectedPhotoCount get() = selectedPhotoIds.size
    val selectedSizeLabel get() = sizeLabel(groups.flatMap { it.photos }.distinctBy { it.contentUri.toString() }.filter { it.id in selectedPhotoIds }.sumOf { it.sizeBytes })
    val confirmationSizeLabel get() = sizeLabel(confirmationPhotos.orEmpty().sumOf { it.sizeBytes })
    val selectionLocked get() = isLoading || confirmationPhotos != null || recoveryRequired
}
@HiltViewModel
class ResultsViewModel @Inject constructor(private val coordinator: RemovalCoordinator) : ViewModel() {
    val uiState = coordinator.state
    val qualityScorer get() = coordinator.qualityScorer
    fun togglePhotoSelection(id: Long, selected: Boolean) = coordinator.togglePhotoSelection(id, selected)
    fun smartSelectAll() = coordinator.smartSelectAll()
    fun requestDeleteConfirmation() = coordinator.requestDeleteConfirmation()
    fun dismissDeleteConfirmation() = coordinator.dismissDeleteConfirmation()
    fun deleteSelectedPhotos() = coordinator.deleteSelectedPhotos()
    fun onLegacyWritePermissionRequested() = coordinator.onLegacyWritePermissionRequested()
    fun onLegacyWritePermissionResult(granted: Boolean) = coordinator.onLegacyWritePermissionResult(granted)
    fun onDeletePromptLaunched() = coordinator.onDeletePromptLaunched()
    fun onDeletePromptFailed() = coordinator.onDeletePromptFailed()
    fun onDeleteResult(approved: Boolean, consentId: String? = uiState.value.pendingConsentId) = coordinator.onDeleteResult(approved, consentId)
    fun retryRecovery() = coordinator.retryRecovery()
    fun getGroupById(id: String) = uiState.value.groups.firstOrNull { it.id == id }
}
