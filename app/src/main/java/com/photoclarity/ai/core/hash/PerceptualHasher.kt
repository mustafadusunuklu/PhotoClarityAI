package com.photoclarity.ai.core.hash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.photoclarity.ai.core.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Perceptual Hash (pHash) implementation.
 *
 * Algorithm:
 * 1. Resize image to 32×32 grayscale
 * 2. Apply Discrete Cosine Transform (DCT)
 * 3. Extract top-left 8×8 block (low-frequency coefficients)
 * 4. Compute median of those 64 values
 * 5. Set each bit: 1 if coefficient > median, 0 otherwise
 * 6. Return as 64-bit Long
 */
@Singleton
class PerceptualHasher @Inject constructor(
    private val context: Context,
    private val bitmapUtils: BitmapUtils
) {
    companion object {
        private const val RESIZE_DIM = 32
        private const val HASH_DIM = 8
        private const val HASH_BITS = HASH_DIM * HASH_DIM  // 64 bits
    }

    // Pre-compute DCT coefficients for performance
    private val dctCoefficients: Array<DoubleArray> by lazy {
        Array(RESIZE_DIM) { i ->
            DoubleArray(RESIZE_DIM) { j ->
                val alpha = if (i == 0) 1.0 / sqrt(RESIZE_DIM.toDouble()) else sqrt(2.0 / RESIZE_DIM)
                alpha * cos((2 * j + 1) * i * Math.PI / (2 * RESIZE_DIM))
            }
        }
    }

    suspend fun computePHash(uri: Uri): Long? = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = bitmapUtils.decodeSampledBitmap(uri, RESIZE_DIM, RESIZE_DIM) ?: return@runCatching null
            val grayscale = toGrayscaleMatrix(bitmap)
            bitmap.recycle()

            val dct = applyDct(grayscale)
            val lowFreq = extractLowFrequency(dct)
            val median = computeMedian(lowFreq)

            buildHash(lowFreq, median)
        }.getOrNull()
    }

    private fun toGrayscaleMatrix(bitmap: Bitmap): Array<DoubleArray> {
        val resized = Bitmap.createScaledBitmap(bitmap, RESIZE_DIM, RESIZE_DIM, true)
        val matrix = Array(RESIZE_DIM) { DoubleArray(RESIZE_DIM) }
        for (y in 0 until RESIZE_DIM) {
            for (x in 0 until RESIZE_DIM) {
                val pixel = resized.getPixel(x, y)
                // Luminance: ITU-R BT.601
                matrix[y][x] = 0.299 * Color.red(pixel) +
                        0.587 * Color.green(pixel) +
                        0.114 * Color.blue(pixel)
            }
        }
        if (resized != bitmap) resized.recycle()
        return matrix
    }

    private fun applyDct(matrix: Array<DoubleArray>): Array<DoubleArray> {
        val result = Array(RESIZE_DIM) { DoubleArray(RESIZE_DIM) }
        for (u in 0 until RESIZE_DIM) {
            for (v in 0 until RESIZE_DIM) {
                var sum = 0.0
                for (x in 0 until RESIZE_DIM) {
                    for (y in 0 until RESIZE_DIM) {
                        sum += matrix[x][y] * dctCoefficients[u][x] * dctCoefficients[v][y]
                    }
                }
                result[u][v] = sum
            }
        }
        return result
    }

    private fun extractLowFrequency(dct: Array<DoubleArray>): DoubleArray {
        val lowFreq = DoubleArray(HASH_BITS)
        var idx = 0
        for (u in 0 until HASH_DIM) {
            for (v in 0 until HASH_DIM) {
                lowFreq[idx++] = dct[u][v]
            }
        }
        // Skip DC component (index 0) – it dominates
        return lowFreq.copyOfRange(1, HASH_BITS)
    }

    private fun computeMedian(values: DoubleArray): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun buildHash(values: DoubleArray, median: Double): Long {
        var hash = 0L
        for (i in values.indices) {
            if (values[i] > median) {
                hash = hash or (1L shl i)
            }
        }
        return hash
    }
}
