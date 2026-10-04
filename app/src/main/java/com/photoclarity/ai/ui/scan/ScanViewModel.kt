package com.photoclarity.ai.ui.scan

import androidx.lifecycle.ViewModel
import com.photoclarity.ai.core.session.ScanCoordinator
import com.photoclarity.ai.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class ScanUiState(
    val isScanning: Boolean = false, val scanFinished: Boolean = false,
    val currentProgress: Int = 0, val totalPhotos: Int = 0, val currentPhotoName: String = "",
    val steps: List<ScanStepStatus> = ScanStep.values().map { ScanStepStatus(it) },
    val isCancelled: Boolean = false, val error: String? = null,
    val results: List<DuplicateGroup> = emptyList(), val sessionId: String? = null, val failedPhotos: Int = 0
) {
    val progressFraction get() = if (totalPhotos > 0) (currentProgress.toFloat() / totalPhotos).coerceIn(0f, 1f) else -1f
    val isCompleted get() = scanFinished || isCancelled || error != null
    val hasResults get() = results.isNotEmpty()
}

@HiltViewModel
class ScanViewModel @Inject constructor(val coordinator: ScanCoordinator) : ViewModel() {
    val uiState = coordinator.state
    fun startScan() = coordinator.start()
    fun cancelScan() = coordinator.cancel()
}
