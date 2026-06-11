package com.photoclarity.ai.core.analysis

import com.photoclarity.ai.domain.model.Photo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scores photo quality on a composite scale (0.0 – 1.0).
 *
 * Criteria weights:
 * - Resolution (megapixels): 40%
 * - File size:               20%
 * - Sharpness:               30%
 * - Metadata completeness:   10%
 */
@Singleton
class QualityScorer @Inject constructor() {

    companion object {
        private const val W_RESOLUTION = 0.40f
        private const val W_SIZE = 0.20f
        private const val W_SHARPNESS = 0.30f
        private const val W_METADATA = 0.10f

        // Normalization constants
        private const val MAX_MP = 50f           // 50 megapixels reference
        private const val MAX_SIZE_MB = 20f      // 20 MB reference
        private const val MAX_SHARPNESS = 1000f  // Laplacian variance reference
    }

    /**
     * Score a single photo.
     */
    fun score(photo: Photo): Float {
        val resolutionScore = (photo.megapixels / MAX_MP).coerceIn(0f, 1f)
        val sizeScore = (photo.sizeMb / MAX_SIZE_MB).coerceIn(0f, 1f)
        val sharpnessScore = (photo.sharpnessScore / MAX_SHARPNESS).coerceIn(0f, 1f)
        val metadataScore = computeMetadataScore(photo)

        return (resolutionScore * W_RESOLUTION +
                sizeScore * W_SIZE +
                sharpnessScore * W_SHARPNESS +
                metadataScore * W_METADATA)
    }

    /**
     * From a group of photos, select the best one to keep.
     * Returns the photo with the highest quality score.
     */
    fun selectBest(photos: List<Photo>): Photo {
        return photos.maxByOrNull { it.qualityScore } ?: photos.first()
    }

    /**
     * Determine quality label for display (e.g., "Net Odak", "Bulanık").
     */
    fun qualityLabel(photo: Photo): String {
        val sharpNorm = (photo.sharpnessScore / MAX_SHARPNESS).coerceIn(0f, 1f)
        return when {
            sharpNorm >= 0.6f -> "Net Odak"
            sharpNorm >= 0.3f -> "Orta Netlik"
            else -> "Bulanık"
        }
    }

    fun megapixelLabel(photo: Photo): String {
        return "%.0f MP".format(photo.megapixels)
    }

    private fun computeMetadataScore(photo: Photo): Float {
        var score = 0f
        if (photo.dateTaken != null) score += 0.5f
        if (photo.latitude != null && photo.longitude != null) score += 0.3f
        if (photo.displayName.isNotBlank()) score += 0.2f
        return score
    }
}
