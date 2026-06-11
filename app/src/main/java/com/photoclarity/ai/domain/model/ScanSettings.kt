package com.photoclarity.ai.domain.model

/**
 * User-configurable scan settings.
 */
data class ScanSettings(
    val hashAlgorithm: HashAlgorithm = HashAlgorithm.PHASH,
    val exactMatchEnabled: Boolean = true,
    val visualSimilarityEnabled: Boolean = true,
    val similarityThreshold: Float = 0.85f,  // 0.70 – 0.99
    val includeSameFolderPhotos: Boolean = true,
    val useMetadata: Boolean = true,
    val useGpsMetadata: Boolean = false,
    val smartSelectionEnabled: Boolean = true,
    val detectBurstShots: Boolean = true,
    val detectLowQuality: Boolean = false,
    val selectedFolders: Set<String> = emptySet(),  // empty = all folders
    val minFileSizeBytes: Long = 10 * 1024,          // skip thumbnails < 10KB
) {
    val similarityPercent: Int get() = (similarityThreshold * 100).toInt()
}
