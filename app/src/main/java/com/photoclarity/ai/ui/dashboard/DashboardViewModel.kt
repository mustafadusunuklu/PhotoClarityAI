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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Depolama bilgisi alınamadı: ${e.message}"
                )
            }
        }
    }
}
