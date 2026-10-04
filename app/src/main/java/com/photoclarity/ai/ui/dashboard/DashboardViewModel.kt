package com.photoclarity.ai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoclarity.ai.domain.model.StorageInfo
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.domain.repository.SettingsRepository
import com.photoclarity.ai.domain.repository.ScanSessionRepository
import com.photoclarity.ai.domain.model.SessionStatus
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
    private val photoAccess: PhotoAccessManager? = null,
    private val sessions: ScanSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    init {
        viewModelScope.launch {
            var lastFinished: String? = null
            sessions.state.collect { s ->
                _uiState.value = _uiState.value.copy(hasPreviousScanResults = s.trusted && s.session?.status == SessionStatus.COMPLETED,
                    lastScanGroupCount = s.visibleGroups.size)
                val finished = s.removal?.takeIf { it.status == com.photoclarity.ai.domain.model.RemovalStatus.FINISHED }?.id
                if (finished != null && finished != lastFinished) { lastFinished = finished; loadDashboardData() }
            }
        }
        viewModelScope.launch {
            try { settingsRepository.observeCleanedBytesThisMonth().collect { bytes ->
                _uiState.value = _uiState.value.copy(storageInfo = _uiState.value.storageInfo?.copy(cleanedThisMonthBytes = bytes))
            } } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(error = "Alan istatistiği okunamadı.") }
        }
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
                val sessionState = sessions.state.value
                _uiState.value = DashboardUiState(
                    storageInfo = storageInfo.copy(cleanedThisMonthBytes = cleanedBytes),
                    isLoading = false,
                    hasPreviousScanResults = sessionState.trusted && sessionState.session?.status == SessionStatus.COMPLETED,
                    lastScanGroupCount = sessionState.visibleGroups.size
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
