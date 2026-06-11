package com.photoclarity.ai.core.analysis

import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.model.Photo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Detects burst (rapid sequential) photos.
 *
 * Criteria:
 * - Photos taken within [BURST_WINDOW_MS] of each other (default: 2 seconds)
 * - Same bucket (folder)
 * - At least [MIN_BURST_SIZE] photos in sequence
 */
@Singleton
class BurstDetector @Inject constructor() {

    companion object {
        private const val BURST_WINDOW_MS = 2_000L   // 2 seconds
        private const val MIN_BURST_SIZE = 3
    }

    fun detectBursts(photos: List<Photo>): List<List<Photo>> {
        if (photos.isEmpty()) return emptyList()

        // Group by bucket, then sort by date taken within each bucket
        val byBucket = photos
            .filter { it.dateTaken != null }
            .groupBy { it.bucketId }

        val bursts = mutableListOf<List<Photo>>()

        for ((_, bucketPhotos) in byBucket) {
            val sorted = bucketPhotos.sortedBy { it.dateTaken }
            val currentBurst = mutableListOf<Photo>()

            for (photo in sorted) {
                if (currentBurst.isEmpty()) {
                    currentBurst.add(photo)
                } else {
                    val lastTaken = currentBurst.last().dateTaken ?: continue
                    val thisTaken = photo.dateTaken ?: continue
                    if (abs(thisTaken - lastTaken) <= BURST_WINDOW_MS) {
                        currentBurst.add(photo)
                    } else {
                        if (currentBurst.size >= MIN_BURST_SIZE) {
                            bursts.add(currentBurst.toList())
                        }
                        currentBurst.clear()
                        currentBurst.add(photo)
                    }
                }
            }
            if (currentBurst.size >= MIN_BURST_SIZE) {
                bursts.add(currentBurst.toList())
            }
        }

        return bursts
    }
}
