package com.photoclarity.ai.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoclarity.ai.core.analysis.PhotoAnalyzer
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.model.ScanProgress
import com.photoclarity.ai.domain.model.ScanStepStatus
import com.photoclarity.ai.domain.model.ScanStepStatus.StepStatus
import com.photoclarity.ai.domain.model.ScanStep
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val isScanning: Boolean = false,
    val scanFinished: Boolean = false,  // true once scan reaches any terminal state
    val currentProgress: Int = 0,
    val totalPhotos: Int = 0,
    val currentPhotoName: String = "",
    val steps: List<ScanStepStatus> = ScanStep.values().map { ScanStepStatus(it) },
    val isCancelled: Boolean = false,
    val error: String? = null,
    val results: List<DuplicateGroup> = emptyList()
) {
    val progressFraction: Float
        get() = if (totalPhotos > 0) currentProgress.toFloat() / totalPhotos else -1f
    /** True as soon as the scan is no longer running (success, empty, cancelled or error). */
    val isCompleted: Boolean
        get() = scanFinished || isCancelled || error != null
    val hasResults: Boolean
        get() = results.isNotEmpty()
}

// Global state to pass results between screens
object ScanResultHolder {
    var groups: List<DuplicateGroup> = emptyList()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val photoAnalyzer: PhotoAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val progressFlow = MutableSharedFlow<ScanProgress>(extraBufferCapacity = 64)
    private var scanJob: Job? = null

    init {
        startScan()
    }

    fun startScan() {
        scanJob?.cancel()
        _uiState.value = ScanUiState(
            isScanning = true,
            steps = ScanStep.values().map { ScanStepStatus(it, StepStatus.PENDING) }
        )

        scanJob = viewModelScope.launch {
            // Collect progress updates
            launch {
                progressFlow.collect { progress ->
                    handleProgress(progress)
                }
            }

            try {
                // Step 1: Scan folders / load photos
                updateStep(ScanStep.SCAN_FOLDERS, StepStatus.IN_PROGRESS)
                val settings = settingsRepository.getScanSettings().first()
                val photos = photoRepository.loadAllPhotos(settings.selectedFolders)
                updateStep(ScanStep.SCAN_FOLDERS, StepStatus.DONE)

                _uiState.value = _uiState.value.copy(totalPhotos = photos.size)

                // Fast-path: nothing to analyse
                if (photos.isEmpty()) {
                    ScanStep.values().forEach { updateStep(it, StepStatus.DONE) }
                    ScanResultHolder.groups = emptyList()
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        scanFinished = true,
                        results = emptyList()
                    )
                    return@launch
                }

                // Step 2: Extract metadata (already present in MediaStore row)
                updateStep(ScanStep.EXTRACT_METADATA, StepStatus.IN_PROGRESS)
                kotlinx.coroutines.delay(300)
                updateStep(ScanStep.EXTRACT_METADATA, StepStatus.DONE)

                // Step 3: Compute hashes + similarity grouping
                updateStep(ScanStep.COMPUTE_SIMILARITY, StepStatus.IN_PROGRESS)
                updateStep(ScanStep.MATCH_HASHES, StepStatus.IN_PROGRESS)

                val groups = photoAnalyzer.analyze(photos, settings, progressFlow)

                updateStep(ScanStep.COMPUTE_SIMILARITY, StepStatus.DONE)
                updateStep(ScanStep.MATCH_HASHES, StepStatus.DONE)

                ScanResultHolder.groups = groups
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanFinished = true,
                    results = groups
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = _uiState.value.copy(isScanning = false, isCancelled = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = e.message ?: "Tarama başarısız"
                )
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(isScanning = false, isCancelled = true)
    }

    private fun handleProgress(progress: ScanProgress) {
        _uiState.value = when (progress) {
            is ScanProgress.Counting -> _uiState.value.copy(totalPhotos = progress.total)
            is ScanProgress.Hashing -> _uiState.value.copy(
                currentProgress = progress.current,
                totalPhotos = progress.total,
                currentPhotoName = progress.currentPhotoName
            )
            is ScanProgress.Comparing -> _uiState.value.copy(
                currentProgress = progress.current,
                totalPhotos = progress.total
            )
            is ScanProgress.Grouping -> _uiState.value
            is ScanProgress.Completed -> _uiState.value
            is ScanProgress.Error -> _uiState.value.copy(error = progress.message)
            is ScanProgress.Cancelled -> _uiState.value.copy(isCancelled = true)
        }
    }

    private fun updateStep(step: ScanStep, status: StepStatus) {
        val updated = _uiState.value.steps.map {
            if (it.step == step) it.copy(status = status) else it
        }
        _uiState.value = _uiState.value.copy(steps = updated)
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
