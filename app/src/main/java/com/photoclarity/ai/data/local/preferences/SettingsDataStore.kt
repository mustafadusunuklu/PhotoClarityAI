package com.photoclarity.ai.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.photoclarity.ai.domain.model.HashAlgorithm
import com.photoclarity.ai.domain.model.ScanSettings
import com.photoclarity.ai.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "photoclarity_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    private val context: Context
) : SettingsRepository {

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
    }

    override fun getScanSettings(): Flow<ScanSettings> =
        context.dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences())
                else throw e
            }
            .map { prefs ->
                ScanSettings(
                    hashAlgorithm = HashAlgorithm.valueOf(
                        prefs[Keys.HASH_ALGORITHM] ?: HashAlgorithm.PHASH.name
                    ),
                    similarityThreshold = prefs[Keys.SIMILARITY_THRESHOLD] ?: 0.85f,
                    exactMatchEnabled = prefs[Keys.EXACT_MATCH_ENABLED] ?: true,
                    visualSimilarityEnabled = prefs[Keys.VISUAL_SIMILARITY_ENABLED] ?: true,
                    includeSameFolderPhotos = prefs[Keys.SAME_FOLDER] ?: true,
                    useMetadata = prefs[Keys.USE_METADATA] ?: true,
                    useGpsMetadata = prefs[Keys.USE_GPS] ?: false,
                    smartSelectionEnabled = prefs[Keys.SMART_SELECTION] ?: true,
                    detectBurstShots = prefs[Keys.DETECT_BURST] ?: true,
                    detectLowQuality = prefs[Keys.DETECT_LOW_QUALITY] ?: false
                )
            }

    override suspend fun saveScanSettings(settings: ScanSettings) {
        context.dataStore.edit { prefs ->
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
        }
    }

    override suspend fun getCleanedBytesThisMonth(): Long {
        val currentMonthKey = getCurrentMonthKey()
        val prefs = context.dataStore.data.first()
        val savedMonthKey = prefs[Keys.CLEANED_MONTH_KEY] ?: 0
        return if (savedMonthKey == currentMonthKey) prefs[Keys.CLEANED_THIS_MONTH] ?: 0L else 0L
    }

    override suspend fun addCleanedBytes(bytes: Long) {
        val currentMonthKey = getCurrentMonthKey()
        context.dataStore.edit { prefs ->
            val savedMonthKey = prefs[Keys.CLEANED_MONTH_KEY] ?: 0
            val currentTotal = if (savedMonthKey == currentMonthKey) prefs[Keys.CLEANED_THIS_MONTH] ?: 0L else 0L
            prefs[Keys.CLEANED_THIS_MONTH] = currentTotal + bytes
            prefs[Keys.CLEANED_MONTH_KEY] = currentMonthKey
        }
    }

    override suspend fun resetMonthlyStats() {
        context.dataStore.edit { prefs ->
            prefs[Keys.CLEANED_THIS_MONTH] = 0L
        }
    }

    private fun getCurrentMonthKey(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 100 + cal.get(java.util.Calendar.MONTH)
    }
}
