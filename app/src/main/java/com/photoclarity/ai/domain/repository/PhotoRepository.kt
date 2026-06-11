package com.photoclarity.ai.domain.repository

import android.net.Uri
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.domain.model.StorageInfo
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    /**
     * Load all photos from device via MediaStore.
     * @param selectedFolders If non-empty, only photos from these bucket names are returned.
     */
    suspend fun loadAllPhotos(selectedFolders: Set<String> = emptySet()): List<Photo>

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

    /**
     * Delete photos by URI list.
     * On API 30+ returns an IntentSender for user confirmation.
     * On older APIs deletes directly.
     */
    suspend fun deletePhotos(uris: List<Uri>): DeleteResult

    /**
     * Get total count of photos on device.
     */
    suspend fun getPhotoCount(): Int

    sealed class DeleteResult {
        data class Success(val deletedCount: Int) : DeleteResult()
        data class RequiresPermission(val intentSender: android.content.IntentSender) : DeleteResult()
        data class Error(val message: String) : DeleteResult()
    }
}
