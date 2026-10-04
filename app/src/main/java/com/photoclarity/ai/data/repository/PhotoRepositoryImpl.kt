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
        val catalogue = photos.distinctBy { it.contentUri }
        if (catalogue.any { photo ->
                val uri = photo.contentUri
                uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY || uri.pathSegments.size != 4 ||
                    uri.pathSegments[1] != "images" || uri.pathSegments[2] != "media" ||
                    uri.lastPathSegment?.toLongOrNull()?.takeIf { it > 0 } != photo.id
            }) return@withContext false
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT) +
            (if (android.os.Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.MediaColumns.GENERATION_MODIFIED) else emptyArray())
        for ((collection, members) in catalogue.groupBy { it.contentUri.toString().substringBeforeLast('/') }) {
            val volume = members.first().contentUri.pathSegments.first()
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val version = versions.getOrPut(volume) { MediaStore.getVersion(context, volume) }
                if (members.any { it.mediaStoreVersion != null && it.mediaStoreVersion != version }) return@withContext false
            }
            for (batch in members.chunked(com.photoclarity.ai.domain.model.AnalysisVersion.CACHE_BATCH)) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val expected = batch.associateBy { it.id }
                val seen = HashSet<Long>()
                val selection = "${MediaStore.MediaColumns._ID} IN (${batch.joinToString(",") { "?" }})"
                val cursor = context.contentResolver.query(Uri.parse(collection), projection, selection,
                    batch.map { it.id.toString() }.toTypedArray(), null) ?: return@withContext false
                cursor.use { row ->
                    while (row.moveToNext()) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val id = row.getLong(0)
                        val photo = expected[id] ?: return@withContext false
                        if (!seen.add(id) || row.getLong(1) != photo.sizeBytes || row.getLong(2) != photo.dateModified ||
                            row.getLong(3) != photo.dateAdded || row.getString(4) != photo.mimeType || row.getInt(5) != photo.width ||
                            row.getInt(6) != photo.height || (photo.generationModified != null && (row.isNull(7) || row.getLong(7) != photo.generationModified)))
                            return@withContext false
                    }
                }
                if (seen.size != expected.size) return@withContext false
            }
        }
        true
    }

    override suspend fun loadAllPhotos(selectedFolders: Set<String>): List<Photo> =
        scanner.scanAllPhotos(selectedFolders = selectedFolders)

    override fun loadPhotoPages(settings: com.photoclarity.ai.domain.model.ScanSettings) =
        scanner.scanPhotoPages(settings.minFileSizeBytes, settings.selectedFolders)

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
                try { removed.addAll(removalPlatform.trashedUris(uris.distinct()).intersect(uris.toSet())) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { /* Unknown state is not success. */ }
            }
            PhotoRepository.DeleteResult.Success(removed, uris.toSet() - removed)
        }

    override suspend fun getPhotoCount(): Int = scanner.getPhotoCount()
}
