package com.photoclarity.ai.data.repository

import kotlinx.coroutines.CancellationException
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.photoclarity.ai.core.media.MediaRemovalPlatform
import com.photoclarity.ai.core.media.RemovalBatchPolicy
import com.photoclarity.ai.core.media.MediaStoreScanner
import com.photoclarity.ai.core.util.StorageUtils
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.domain.model.StorageInfo
import com.photoclarity.ai.domain.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepositoryImpl @Inject constructor(
    private val context: Context,
    private val scanner: MediaStoreScanner,
    private val storageUtils: StorageUtils,
    private val removalPlatform: MediaRemovalPlatform
) : PhotoRepository {

    override suspend fun arePhotosCurrent(photos: List<Photo>): Boolean = withContext(Dispatchers.IO) {
        com.photoclarity.ai.core.media.PhotoAccessManager.requireAccess(context)
        val versions = mutableMapOf<String, String>()
        photos.distinctBy { it.contentUri }.all { photo ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val uri = photo.contentUri
            if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY || uri.lastPathSegment?.toLongOrNull() != photo.id) false
            else if (android.os.Build.VERSION.SDK_INT >= 30 && photo.mediaStoreVersion != null &&
                versions.getOrPut(uri.pathSegments.first()) { MediaStore.getVersion(context, uri.pathSegments.first()) } != photo.mediaStoreVersion) false
            else context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT) +
                (if (android.os.Build.VERSION.SDK_INT >= 30 && photo.generationModified != null) arrayOf(MediaStore.MediaColumns.GENERATION_MODIFIED) else emptyArray()), null, null, null)?.use { row ->
                row.moveToFirst() && row.getLong(0) == photo.id && row.getLong(1) == photo.sizeBytes && row.getLong(2) == photo.dateModified &&
                    row.getLong(3) == photo.dateAdded && row.getString(4) == photo.mimeType && row.getInt(5) == photo.width && row.getInt(6) == photo.height &&
                    (photo.generationModified == null || row.getLong(7) == photo.generationModified)
            } ?: false
        }
    }

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

    override val removalMode: PhotoRepository.RemovalMode
        get() = removalPlatform.mode

    override suspend fun deletePhotos(uris: List<Uri>): PhotoRepository.DeleteResult =
        withContext(Dispatchers.IO) {
            val snapshot = uris.distinct().toList()
            // Never allow a collection URI to delete multiple unselected rows.
            if (snapshot.any { it.scheme != "content" || it.authority != MediaStore.AUTHORITY ||
                    it.lastPathSegment?.toLongOrNull()?.takeIf { id -> id > 0 } == null }) {
                return@withContext PhotoRepository.DeleteResult.Error("Geçersiz fotoğraf adresi; işlem yapılmadı.")
            }
            if (snapshot.isEmpty()) return@withContext PhotoRepository.DeleteResult.Success(emptySet())
            if (removalMode == PhotoRepository.RemovalMode.SYSTEM_TRASH) {
                try {
                    val batch = snapshot.take(RemovalBatchPolicy.MAX_TRASH_URIS)
                    PhotoRepository.DeleteResult.RequiresPermission(
                        removalPlatform.createTrashPrompt(batch),
                        trashUris = batch,
                        remainingTrashUris = snapshot.drop(batch.size)
                    )
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    // No permanent-delete fallback when system trash is unavailable.
                    PhotoRepository.DeleteResult.Error("Sistem çöp kutusu isteği oluşturulamadı. Fotoğraflar silinmedi.")
                }
            } else {
                val removed = mutableSetOf<Uri>()
                val failed = mutableSetOf<Uri>()
                for ((index, uri) in snapshot.withIndex()) {
                    try {
                        if (context.contentResolver.delete(uri, null, null) > 0) removed.add(uri)
                        else failed.add(uri)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        val prompt = removalPlatform.recoveryPrompt(e)
                        if (prompt != null) {
                            return@withContext PhotoRepository.DeleteResult.RequiresPermission(
                                prompt, removed.toSet(), failed.toSet(),
                                snapshot.drop(index)
                            )
                        }
                        failed.add(uri)
                    }
                }
                PhotoRepository.DeleteResult.Success(removed.toSet(), failed.toSet())
            }
        }

    override suspend fun verifyTrashedPhotos(uris: List<Uri>): PhotoRepository.DeleteResult.Success =
        withContext(Dispatchers.IO) {
            val removed = mutableSetOf<Uri>()
            if (removalMode == PhotoRepository.RemovalMode.SYSTEM_TRASH) {
                for (uri in uris.distinct()) {
                    try {
                        if (removalPlatform.isTrashed(uri)) removed.add(uri)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { /* Unknown state is not success. */ }
                }
            }
            PhotoRepository.DeleteResult.Success(removed, uris.toSet() - removed)
        }

    override suspend fun getPhotoCount(): Int = scanner.getPhotoCount()
}
