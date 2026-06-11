package com.photoclarity.ai.domain.model

import android.net.Uri

/**
 * Core photo domain model.
 */
data class Photo(
    val id: Long,
    val uri: Uri,
    val contentUri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAdded: Long,          // epoch seconds
    val dateModified: Long,       // epoch seconds
    val dateTaken: Long?,         // epoch millis from EXIF
    val width: Int,
    val height: Int,
    val bucketName: String,       // folder/album name
    val bucketId: Long,
    val latitude: Double?,
    val longitude: Double?,
    // Analysis results (filled after scan)
    val md5Hash: String? = null,
    val sha256Hash: String? = null,
    val pHash: Long? = null,
    val aHash: Long? = null,
    val dHash: Long? = null,
    val qualityScore: Float = 0f,
    val sharpnessScore: Float = 0f,
    val isBursted: Boolean = false
) {
    val megapixels: Float get() = (width * height) / 1_000_000f
    val sizeKb: Long get() = sizeBytes / 1024
    val sizeMb: Float get() = sizeBytes / (1024f * 1024f)
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height else 1f
    val resolution: String get() = "${width}×${height}"
}
