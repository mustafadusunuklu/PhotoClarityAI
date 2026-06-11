package com.photoclarity.ai.ui.results

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.domain.repository.SettingsRepository
import com.photoclarity.ai.ui.scan.ScanResultHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultsUiState(
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedPhotoIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val pendingDeleteIntentSender: IntentSender? = null,
    val lastDeletedCount: Int = 0
) {
    val selectedPhotoCount: Int get() = selectedPhotoIds.size
    val selectedSizeLabel: String get() {
        val bytes = groups
            .flatMap { it.photos }
            .filter { it.id in selectedPhotoIds }
            .sumOf { it.sizeBytes }
        return when {
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
            else -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
        }
    }
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    val qualityScorer: QualityScorer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        loadResults()
    }

    private fun loadResults() {
        val groups = ScanResultHolder.groups
        _uiState.value = _uiState.value.copy(groups = groups)
    }

    fun togglePhotoSelection(photoId: Long, selected: Boolean) {
        val current = _uiState.value.selectedPhotoIds.toMutableSet()
        if (selected) current.add(photoId) else current.remove(photoId)
        _uiState.value = _uiState.value.copy(selectedPhotoIds = current)
    }

    fun smartSelectAll() {
        // For each group, select all photos EXCEPT the recommended one
        val toSelect = _uiState.value.groups.flatMap { group ->
            group.photos
                .filter { it.id != group.recommendedKeepId }
                .map { it.id }
        }.toSet()
        _uiState.value = _uiState.value.copy(selectedPhotoIds = toSelect)
    }

    fun deleteSelectedPhotos() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedPhotoIds
            val urisToDelete = _uiState.value.groups
                .flatMap { it.photos }
                .filter { it.id in selectedIds }
                .map { it.contentUri }

            val totalBytes = _uiState.value.groups
                .flatMap { it.photos }
                .filter { it.id in selectedIds }
                .sumOf { it.sizeBytes }

            when (val result = photoRepository.deletePhotos(urisToDelete)) {
                is PhotoRepository.DeleteResult.Success -> {
                    settingsRepository.addCleanedBytes(totalBytes)
                    val deletedCount = result.deletedCount
                    // Remove deleted photos from groups
                    val updatedGroups = _uiState.value.groups
                        .map { group ->
                            group.copy(photos = group.photos.filter { it.id !in selectedIds })
                        }
                        .filter { it.photos.size >= 2 }
                    _uiState.value = _uiState.value.copy(
                        groups = updatedGroups,
                        selectedPhotoIds = emptySet(),
                        lastDeletedCount = deletedCount
                    )
                    ScanResultHolder.groups = updatedGroups
                }
                is PhotoRepository.DeleteResult.RequiresPermission -> {
                    _uiState.value = _uiState.value.copy(
                        pendingDeleteIntentSender = result.intentSender
                    )
                }
                is PhotoRepository.DeleteResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
            }
        }
    }

    fun onDeleteConfirmed() {
        // After system dialog confirmed on Android 11+
        val selectedIds = _uiState.value.selectedPhotoIds
        val updatedGroups = _uiState.value.groups
            .map { group ->
                group.copy(photos = group.photos.filter { it.id !in selectedIds })
            }
            .filter { it.photos.size >= 2 }
        _uiState.value = _uiState.value.copy(
            groups = updatedGroups,
            selectedPhotoIds = emptySet(),
            pendingDeleteIntentSender = null,
            lastDeletedCount = selectedIds.size
        )
        ScanResultHolder.groups = updatedGroups
    }

    fun getGroupById(groupId: String): DuplicateGroup? =
        _uiState.value.groups.firstOrNull { it.id == groupId }
}
