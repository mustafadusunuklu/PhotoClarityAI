package com.photoclarity.ai.core.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.core.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(
    private val context: Context,
    private val storageUtils: StorageUtils
) {
    companion object {
        private val PHOTO_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
    }

    private val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    // Deprecated DATA column omitted for API 29+; LATITUDE/LONGITUDE removed (deprecated API 29+)
    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_ID
    )

    /**
     * Load all photos from the device via MediaStore.
     *
     * @param minSizeBytes  Skip files smaller than this (default 10 KB – avoids thumbnails).
     * @param selectedFolders  If non-empty, only photos whose BUCKET_DISPLAY_NAME is in this set
     *                         are returned, enabling per-folder scanning.
     */
    suspend fun scanAllPhotos(
        minSizeBytes: Long = 10 * 1024,
        selectedFolders: Set<String> = emptySet()
    ): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()

        // Build the WHERE clause dynamically
        val (selection, selectionArgs) = buildSelectionClause(minSizeBytes, selectedFolders)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateAddedCol= cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateModCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dateTakenCol= cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val widthCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)

                photos.add(
                    Photo(
                        id          = id,
                        uri         = contentUri,
                        contentUri  = contentUri,
                        displayName = cursor.getString(nameCol) ?: "",
                        mimeType    = cursor.getString(mimeCol) ?: "image/jpeg",
                        sizeBytes   = cursor.getLong(sizeCol),
                        dateAdded   = cursor.getLong(dateAddedCol),
                        dateModified= cursor.getLong(dateModCol),
                        dateTaken   = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol).takeIf { it > 0 } else null,
                        width       = cursor.getInt(widthCol),
                        height      = cursor.getInt(heightCol),
                        bucketName  = cursor.getString(bucketNameCol) ?: "Unknown",
                        bucketId    = cursor.getLong(bucketIdCol),
                        // GPS coordinates are no longer read from MediaStore (deprecated API 29+).
                        // They can be retrieved on-demand from ExifInterface if needed.
                        latitude    = null,
                        longitude   = null
                    )
                )
            }
        }

        photos
    }

    /**
     * Builds a parameterised SQL selection clause.
     *
     * Resulting filter:
     *   SIZE > minSizeBytes [AND BUCKET_DISPLAY_NAME IN (?,?,…)]
     */
    private fun buildSelectionClause(
        minSizeBytes: Long,
        selectedFolders: Set<String>
    ): Pair<String, Array<String>> {
        val args = mutableListOf(minSizeBytes.toString())
        var selection = "${MediaStore.Images.Media.SIZE} > ?"

        if (selectedFolders.isNotEmpty()) {
            val placeholders = selectedFolders.joinToString(",") { "?" }
            selection += " AND ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} IN ($placeholders)"
            args.addAll(selectedFolders)
        }

        return selection to args.toTypedArray()
    }

    /**
     * Get all distinct buckets (folders) on the device.
     */
    suspend fun getAllBuckets(): Map<Long, String> = withContext(Dispatchers.IO) {
        val buckets = mutableMapOf<Long, String>()
        val bucketProjection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        context.contentResolver.query(
            collection,
            bucketProjection,
            null, null,
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id   = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                buckets[id] = name
            }
        }

        buckets
    }

    /**
     * Get total photo count (no size filter so the number matches the gallery).
     */
    suspend fun getPhotoCount(): Int = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null, null, null
        )?.use { it.count } ?: 0
    }
}
