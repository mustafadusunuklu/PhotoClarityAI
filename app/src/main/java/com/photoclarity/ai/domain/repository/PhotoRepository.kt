package com.photoclarity.ai.domain.repository

import android.net.Uri
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.domain.model.StorageInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.photoclarity.ai.domain.model.ScanSettings
import com.photoclarity.ai.domain.model.AnalysisVersion

interface PhotoRepository {
    suspend fun arePhotosCurrent(photos: List<com.photoclarity.ai.domain.model.Photo>): Boolean {
        throw UnsupportedOperationException("Media freshness validation required")
    }
    /**
     * Load all photos from device via MediaStore.
     * @param selectedFolders If non-empty, only photos from these bucket names are returned.
     */
    suspend fun loadAllPhotos(selectedFolders: Set<String> = emptySet()): List<Photo>

    fun loadPhotoPages(settings: ScanSettings): Flow<List<Photo>> = flow {
        loadAllPhotos(settings.selectedFolders)
            .chunked(AnalysisVersion.CACHE_BATCH).forEach { emit(it) }
    }

    /**
     * Load photos from a specific folder (bucketId).
     */
    suspend fun loadPhotosFromBucket(bucketId: Long): List<Photo>

    /**
     * Get all unique buckets/folders on the device.
     */
    suspend fun getAllBuckets(): Map<Long, String>

    /**
     * Get device storage information.
     */
    suspend fun getStorageInfo(): StorageInfo

    enum class RemovalMode { SYSTEM_TRASH, PERMANENT_DELETE }
    val removalMode: RemovalMode

    /** API 30+: system trash only. Older versions: permanent deletion after app confirmation. */
    suspend fun deletePhotos(uris: List<Uri>): DeleteResult

    /** Verify IS_TRASHED after system approval; inaccessible/unknown rows fail closed. */
    suspend fun verifyTrashedPhotos(uris: List<Uri>): DeleteResult.Success

    /**
     * Get total count of photos on device.
     */
    suspend fun getPhotoCount(): Int

    sealed class DeleteResult {
        data class Success(val removedUris: Set<Uri>, val failedUris: Set<Uri> = emptySet()) : DeleteResult()
        data class RequiresPermission(
            val intentSender: android.content.IntentSender,
            val removedUris: Set<Uri> = emptySet(),
            val failedUris: Set<Uri> = emptySet(),
            // Android 10 grants permission; the frozen remaining request must then be retried.
            val retryUris: List<Uri> = emptyList(),
            // API 30+: verify only this approved batch, then request fresh consent for the rest.
            val trashUris: List<Uri> = emptyList(),
            val remainingTrashUris: List<Uri> = emptyList()
        ) : DeleteResult()
        data class Error(val message: String) : DeleteResult()
    }
}
