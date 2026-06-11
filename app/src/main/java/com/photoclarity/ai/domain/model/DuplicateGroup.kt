package com.photoclarity.ai.domain.model

/**
 * A group of photos identified as duplicates or near-duplicates.
 */
data class DuplicateGroup(
    val id: String,
    val photos: List<Photo>,
    val groupType: GroupType,
    val similarityScore: Float,   // 0.0 – 1.0
    val recommendedKeepId: Long,  // ID of the best photo to keep
    val totalWasteBytes: Long     // bytes that can be freed
) {
    val photoCount: Int get() = photos.size
    val wastedMb: Float get() = totalWasteBytes / (1024f * 1024f)
    val recommendedPhoto: Photo? get() = photos.firstOrNull { it.id == recommendedKeepId }

    enum class GroupType {
        EXACT_DUPLICATE,   // MD5/SHA-256 match
        VISUAL_SIMILAR,    // pHash/aHash/dHash within threshold
        BURST_SHOT,        // Time-proximity burst
        LOW_QUALITY        // Blurry / low resolution
    }
}
