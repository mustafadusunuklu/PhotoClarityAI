package com.photoclarity.ai.domain.repository

import com.photoclarity.ai.domain.model.ScanSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface SettingsRepository {
    fun getScanSettings(): Flow<ScanSettings>
    /** Critical execution snapshot: read errors must propagate instead of supplying UI defaults. */
    suspend fun getScanSettingsSnapshot(): ScanSettings = getScanSettings().first()
    suspend fun saveScanSettings(settings: ScanSettings)
    suspend fun getCleanedBytesThisMonth(): Long
    suspend fun addCleanedBytes(bytes: Long)
    suspend fun resetMonthlyStats()
    fun observeCleanedBytesThisMonth(): Flow<Long> = kotlinx.coroutines.flow.flow { emit(getCleanedBytesThisMonth()) }
    suspend fun addCleanedBytesOnce(requestId: String, bytes: Long, operationMonthKey: Int) {
        throw UnsupportedOperationException("Idempotent statistics implementation required")
    }
}
