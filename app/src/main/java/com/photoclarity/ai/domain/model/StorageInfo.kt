package com.photoclarity.ai.domain.model

/**
 * Device storage information.
 */
data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val totalPhotoCount: Int,
    val totalPhotoBytesEstimate: Long,
    val reclaimableBytes: Long = 0L,  // filled after scan
    val cleanedThisMonthBytes: Long = 0L
) {
    val freeBytes: Long get() = totalBytes - usedBytes
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val totalGb: Float get() = totalBytes / (1024f * 1024f * 1024f)
    val usedGb: Float get() = usedBytes / (1024f * 1024f * 1024f)
    val freeGb: Float get() = freeBytes / (1024f * 1024f * 1024f)
    val reclaimableMb: Float get() = reclaimableBytes / (1024f * 1024f)
    val cleanedGb: Float get() = cleanedThisMonthBytes / (1024f * 1024f * 1024f)
    val isHealthy: Boolean get() = usedFraction < 0.85f
}

/**
 * Scan summary shown after analysis completes.
 */
data class ScanResult(
    val groups: List<DuplicateGroup>,
    val totalPhotosScanned: Int,
    val reclaimableBytes: Long,
    val scanDurationMs: Long
) {
    val totalGroupCount: Int get() = groups.size
    val totalDuplicateCount: Int get() = groups.sumOf { it.photos.size - 1 }
    val reclaimableMb: Float get() = reclaimableBytes / (1024f * 1024f)
}
