package com.photoclarity.ai.domain.repository

import com.photoclarity.ai.domain.model.ScanSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getScanSettings(): Flow<ScanSettings>
    suspend fun saveScanSettings(settings: ScanSettings)
    suspend fun getCleanedBytesThisMonth(): Long
    suspend fun addCleanedBytes(bytes: Long)
    suspend fun resetMonthlyStats()
}
