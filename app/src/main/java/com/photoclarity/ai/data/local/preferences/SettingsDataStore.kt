package com.photoclarity.ai.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.photoclarity.ai.domain.model.HashAlgorithm
import com.photoclarity.ai.domain.model.ScanSettings
import com.photoclarity.ai.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import com.photoclarity.ai.core.session.SessionClock

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "photoclarity_settings")

@Singleton
class SettingsDataStore(
    private val context: Context,
    private val clock: SessionClock = SessionClock(),
    suppliedStore: DataStore<Preferences>? = null
) : SettingsRepository {
    private val store = suppliedStore ?: context.dataStore

    private object Keys {
        val HASH_ALGORITHM = stringPreferencesKey("hash_algorithm")
        val SIMILARITY_THRESHOLD = floatPreferencesKey("similarity_threshold")
        val EXACT_MATCH_ENABLED = booleanPreferencesKey("exact_match_enabled")
        val VISUAL_SIMILARITY_ENABLED = booleanPreferencesKey("visual_similarity_enabled")
        val SAME_FOLDER = booleanPreferencesKey("same_folder")
        val USE_METADATA = booleanPreferencesKey("use_metadata")
        val USE_GPS = booleanPreferencesKey("use_gps")
        val SMART_SELECTION = booleanPreferencesKey("smart_selection")
        val DETECT_BURST = booleanPreferencesKey("detect_burst")
        val DETECT_LOW_QUALITY = booleanPreferencesKey("detect_low_quality")
        val CLEANED_THIS_MONTH = longPreferencesKey("cleaned_this_month")
        val CLEANED_MONTH_KEY = intPreferencesKey("cleaned_month_key")
        val CREDITED_REQUEST_IDS = stringSetPreferencesKey("credited_request_ids")
        val SELECTED_FOLDERS = stringSetPreferencesKey("selected_folders")
        val MIN_FILE_SIZE = longPreferencesKey("min_file_size")
    }

    override fun getScanSettings(): Flow<ScanSettings> =
        store.data
            // Preserve the existing Settings screen's display fallback. Execution must use
            // the strict snapshot below, so an unreadable file never starts a default scan.
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { parseSettings(it) }

    override suspend fun getScanSettingsSnapshot(): ScanSettings = parseSettings(store.data.first())

    private fun parseSettings(prefs: Preferences): ScanSettings = ScanSettings(
        hashAlgorithm = HashAlgorithm.valueOf(prefs[Keys.HASH_ALGORITHM] ?: HashAlgorithm.PHASH.name),
        similarityThreshold = prefs[Keys.SIMILARITY_THRESHOLD] ?: 0.85f,
        exactMatchEnabled = prefs[Keys.EXACT_MATCH_ENABLED] ?: true,
        visualSimilarityEnabled = prefs[Keys.VISUAL_SIMILARITY_ENABLED] ?: true,
        includeSameFolderPhotos = prefs[Keys.SAME_FOLDER] ?: true,
        useMetadata = prefs[Keys.USE_METADATA] ?: true,
        useGpsMetadata = prefs[Keys.USE_GPS] ?: false,
        smartSelectionEnabled = prefs[Keys.SMART_SELECTION] ?: true,
        detectBurstShots = prefs[Keys.DETECT_BURST] ?: true,
        detectLowQuality = prefs[Keys.DETECT_LOW_QUALITY] ?: false,
        selectedFolders = prefs[Keys.SELECTED_FOLDERS] ?: emptySet(),
        minFileSizeBytes = prefs[Keys.MIN_FILE_SIZE] ?: 10 * 1024
    )

    override suspend fun saveScanSettings(settings: ScanSettings) {
        store.edit { prefs ->
            prefs[Keys.HASH_ALGORITHM] = settings.hashAlgorithm.name
            prefs[Keys.SIMILARITY_THRESHOLD] = settings.similarityThreshold
            prefs[Keys.EXACT_MATCH_ENABLED] = settings.exactMatchEnabled
            prefs[Keys.VISUAL_SIMILARITY_ENABLED] = settings.visualSimilarityEnabled
            prefs[Keys.SAME_FOLDER] = settings.includeSameFolderPhotos
            prefs[Keys.USE_METADATA] = settings.useMetadata
            prefs[Keys.USE_GPS] = settings.useGpsMetadata
            prefs[Keys.SMART_SELECTION] = settings.smartSelectionEnabled
            prefs[Keys.DETECT_BURST] = settings.detectBurstShots
            prefs[Keys.DETECT_LOW_QUALITY] = settings.detectLowQuality
            prefs[Keys.SELECTED_FOLDERS] = settings.selectedFolders
            prefs[Keys.MIN_FILE_SIZE] = settings.minFileSizeBytes
        }
    }

    override suspend fun getCleanedBytesThisMonth(): Long {
        val currentMonthKey = getCurrentMonthKey()
        val prefs = store.data.first()
        val savedMonthKey = prefs[Keys.CLEANED_MONTH_KEY] ?: 0
        return if (savedMonthKey == currentMonthKey) prefs[Keys.CLEANED_THIS_MONTH] ?: 0L else 0L
    }

    override suspend fun addCleanedBytes(bytes: Long) {
        val currentMonthKey = getCurrentMonthKey()
        store.edit { prefs ->
            val savedMonthKey = prefs[Keys.CLEANED_MONTH_KEY] ?: 0
            val currentTotal = if (savedMonthKey == currentMonthKey) prefs[Keys.CLEANED_THIS_MONTH] ?: 0L else 0L
            prefs[Keys.CLEANED_THIS_MONTH] = currentTotal + bytes
            prefs[Keys.CLEANED_MONTH_KEY] = currentMonthKey
        }
    }

    override suspend fun resetMonthlyStats() {
        store.edit { prefs ->
            prefs[Keys.CLEANED_THIS_MONTH] = 0L
        }
    }

    override fun observeCleanedBytesThisMonth(): Flow<Long> = store.data.map { prefs ->
        if (prefs[Keys.CLEANED_MONTH_KEY] == getCurrentMonthKey()) prefs[Keys.CLEANED_THIS_MONTH] ?: 0L else 0L
    }

    override suspend fun addCleanedBytesOnce(requestId: String, bytes: Long, operationMonthKey: Int) {
        require(requestId.isNotBlank() && bytes >= 0)
        val currentMonth = getCurrentMonthKey()
        // A delayed recovery of an old-month operation never credits the new month.
        if (operationMonthKey != currentMonth || bytes == 0L) return
        store.edit { prefs ->
            val sameMonth = prefs[Keys.CLEANED_MONTH_KEY] == currentMonth
            // Keep request identity across month/time-zone changes as well as stats resets.
            // A crash replay must never credit the same request again in a different month.
            val ids = prefs[Keys.CREDITED_REQUEST_IDS].orEmpty()
            if (requestId !in ids) {
                val total = if (sameMonth) prefs[Keys.CLEANED_THIS_MONTH] ?: 0L else 0L
                prefs[Keys.CLEANED_THIS_MONTH] = Math.addExact(total, bytes)
                prefs[Keys.CLEANED_MONTH_KEY] = currentMonth
                prefs[Keys.CREDITED_REQUEST_IDS] = ids + requestId
            }
        }
    }

    private fun getCurrentMonthKey(): Int {
        return clock.monthKey(clock.now())
    }
}
