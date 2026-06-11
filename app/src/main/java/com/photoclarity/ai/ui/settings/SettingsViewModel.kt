package com.photoclarity.ai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoclarity.ai.domain.model.HashAlgorithm
import com.photoclarity.ai.domain.model.ScanSettings
import com.photoclarity.ai.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(ScanSettings())
    val settings: StateFlow<ScanSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getScanSettings().collect {
                _settings.value = it
            }
        }
    }

    fun updateHashAlgorithm(algo: HashAlgorithm) = save(_settings.value.copy(hashAlgorithm = algo))
    fun updateSimilarityThreshold(value: Float) = save(_settings.value.copy(similarityThreshold = value))
    fun updateExactMatch(enabled: Boolean) = save(_settings.value.copy(exactMatchEnabled = enabled))
    fun updateVisualSimilarity(enabled: Boolean) = save(_settings.value.copy(visualSimilarityEnabled = enabled))
    fun updateSameFolder(enabled: Boolean) = save(_settings.value.copy(includeSameFolderPhotos = enabled))
    fun updateMetadata(enabled: Boolean) = save(_settings.value.copy(useMetadata = enabled))
    fun updateGps(enabled: Boolean) = save(_settings.value.copy(useGpsMetadata = enabled))
    fun updateSmartSelection(enabled: Boolean) = save(_settings.value.copy(smartSelectionEnabled = enabled))
    fun updateBurstDetection(enabled: Boolean) = save(_settings.value.copy(detectBurstShots = enabled))
    fun updateLowQuality(enabled: Boolean) = save(_settings.value.copy(detectLowQuality = enabled))

    private fun save(settings: ScanSettings) {
        _settings.value = settings
        viewModelScope.launch { settingsRepository.saveScanSettings(settings) }
    }
}
