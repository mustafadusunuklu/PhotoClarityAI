package com.photoclarity.ai.core.hash

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** 32x32 orthonormal DCT, computing only the 8x8 frequencies used by pHash.
 * The DC coefficient is excluded: there are 63 active bits, not 64.
 */
object PerceptualDct {
    const val BITS = 63
    private val basis = Array(8) { frequency ->
        DoubleArray(32) { position ->
            val scale = if (frequency == 0) 1.0 / sqrt(32.0) else sqrt(2.0 / 32.0)
            scale * cos((2 * position + 1) * frequency * Math.PI / 64.0)
        }
    }

    fun coefficients(gray: DoubleArray, checkCancelled: () -> Unit = {}): DoubleArray {
        require(gray.size == 1024)
        val first = DoubleArray(8 * 32)
        for (u in 0 until 8) {
            checkCancelled()
            for (y in 0 until 32) {
                var sum = 0.0
                for (x in 0 until 32) sum += gray[x * 32 + y] * basis[u][x]
                first[u * 32 + y] = sum
            }
        }
        return DoubleArray(64).also { output ->
            for (u in 0 until 8) {
                checkCancelled()
                for (v in 0 until 8) {
                    var sum = 0.0
                    for (y in 0 until 32) sum += first[u * 32 + y] * basis[v][y]
                    // Remove numerical noise in constant images; covered by the cache version.
                    output[u * 8 + v] = if (abs(sum) < 1e-7) 0.0 else sum
                }
            }
        }
    }

    fun hash(gray: DoubleArray, checkCancelled: () -> Unit = {}): Long {
        val ac = coefficients(gray, checkCancelled).copyOfRange(1, 64)
        val median = ac.sortedArray()[31]
        var hash = 0L
        ac.forEachIndexed { index, value -> if (value > median) hash = hash or (1L shl index) }
        return hash
    }
}
