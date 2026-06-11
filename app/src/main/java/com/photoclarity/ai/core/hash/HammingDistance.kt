package com.photoclarity.ai.core.hash

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hamming Distance calculator for perceptual hash comparison.
 *
 * Hamming distance between two 64-bit hashes = number of differing bits.
 * Similarity = 1 - (distance / 64)
 */
@Singleton
class HammingDistance @Inject constructor() {

    /**
     * Returns the number of differing bits (0–64).
     */
    fun distance(hash1: Long, hash2: Long): Int {
        return (hash1 xor hash2).countOneBits()
    }

    /**
     * Returns similarity as 0.0 – 1.0 (1.0 = identical).
     */
    fun similarity(hash1: Long, hash2: Long): Float {
        val dist = distance(hash1, hash2)
        return 1f - (dist.toFloat() / 64f)
    }

    /**
     * Returns true if two hashes are similar above the given threshold.
     * @param threshold 0.0 – 1.0 (e.g., 0.85 = 85% similar)
     */
    fun isSimilar(hash1: Long, hash2: Long, threshold: Float): Boolean {
        return similarity(hash1, hash2) >= threshold
    }
}
