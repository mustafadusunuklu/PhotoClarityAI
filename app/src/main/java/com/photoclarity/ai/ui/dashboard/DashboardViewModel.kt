package com.photoclarity.ai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoclarity.ai.domain.model.StorageInfo
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.domain.repository.SettingsRepository
import com.photoclarity.ai.ui.scan.ScanResultHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.photoclarity.ai.core.media.PhotoAccessManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

data class DashboardUiState(
    val storageInfo: StorageInfo? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val hasPreviousScanResults: Boolean = false,
    val lastScanGroupCount: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val photoAccess: PhotoAccessManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    init {
        if (photoAccess == null) loadDashboardData()
        else viewModelScope.launch { photoAccess.state.collect { loadDashboardData() } }
    }

    fun loadDashboardData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val storageInfo = photoRepository.getStorageInfo()
                val cleanedBytes = settingsRepository.getCleanedBytesThisMonth()
                val previousGroups = ScanResultHolder.groups
                _uiState.value = DashboardUiState(
                    storageInfo = storageInfo.copy(cleanedThisMonthBytes = cleanedBytes),
                    isLoading = false,
                    hasPreviousScanResults = previousGroups.isNotEmpty(),
                    lastScanGroupCount = previousGroups.size
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    storageInfo = null, hasPreviousScanResults = false, lastScanGroupCount = 0,
                    error = "Depolama bilgisi alınamadı: ${e.message}"
                )
            }
        }
    }
}
