package com.photoclarity.ai.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.photoclarity.ai.core.media.MediaStoreScanner
import com.photoclarity.ai.core.util.StorageUtils
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.domain.model.StorageInfo
import com.photoclarity.ai.domain.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepositoryImpl @Inject constructor(
    private val context: Context,
    private val scanner: MediaStoreScanner,
    private val storageUtils: StorageUtils
) : PhotoRepository {

    override suspend fun loadAllPhotos(selectedFolders: Set<String>): List<Photo> =
        scanner.scanAllPhotos(selectedFolders = selectedFolders)

    override suspend fun loadPhotosFromBucket(bucketId: Long): List<Photo> =
        scanner.scanAllPhotos().filter { it.bucketId == bucketId }

    override suspend fun getAllBuckets(): Map<Long, String> = scanner.getAllBuckets()

    override suspend fun getStorageInfo(): StorageInfo {
        val count = scanner.getPhotoCount()
        return StorageInfo(
            totalBytes = storageUtils.getInternalStorageTotal(),
            usedBytes = storageUtils.getInternalStorageUsed(),
            totalPhotoCount = count,
            totalPhotoBytesEstimate = 0L
        )
    }

    override suspend fun deletePhotos(uris: List<Uri>): PhotoRepository.DeleteResult =
        withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext PhotoRepository.DeleteResult.Success(0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: use createDeleteRequest which shows system dialog
                try {
                    val intentSender = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        uris
                    ).intentSender
                    PhotoRepository.DeleteResult.RequiresPermission(intentSender)
                } catch (e: Exception) {
                    PhotoRepository.DeleteResult.Error(e.message ?: "Delete failed")
                }
            } else {
                // Android 8-10: direct deletion via ContentResolver
                var deleted = 0
                uris.forEach { uri ->
                    try {
                        context.contentResolver.delete(uri, null, null)
                        deleted++
                    } catch (e: Exception) {
                        // Skip failed deletion
                    }
                }
                PhotoRepository.DeleteResult.Success(deleted)
            }
        }

    override suspend fun getPhotoCount(): Int = scanner.getPhotoCount()
}
