package com.photoclarity.ai.core.hash

import android.graphics.Color
import android.net.Uri
import com.photoclarity.ai.core.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Difference Hash (dHash) implementation.
 *
 * Algorithm:
 * 1. Resize to 9×8 grayscale
 * 2. Compare adjacent horizontal pixel pairs
 * 3. Each bit = 1 if left pixel > right pixel
 * 4. Return as 64-bit Long
 *
 * Very robust to slight brightness changes, good for near-duplicates.
 */
@Singleton
class DifferenceHasher @Inject constructor(
    private val context: android.content.Context,
    private val bitmapUtils: BitmapUtils
) {
    companion object {
        private const val WIDTH = 9
        private const val HEIGHT = 8
    }

    suspend fun computeDHash(uri: Uri): Long? = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = bitmapUtils.decodeSampledBitmap(uri, WIDTH, HEIGHT) ?: return@runCatching null
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, WIDTH, HEIGHT, true)
            bitmap.recycle()

            var hash = 0L
            var bit = 0
            for (y in 0 until HEIGHT) {
                for (x in 0 until WIDTH - 1) {
                    val left = scaled.getPixel(x, y)
                    val right = scaled.getPixel(x + 1, y)
                    val leftGray = Color.red(left) * 0.299 + Color.green(left) * 0.587 + Color.blue(left) * 0.114
                    val rightGray = Color.red(right) * 0.299 + Color.green(right) * 0.587 + Color.blue(right) * 0.114
                    if (leftGray > rightGray) hash = hash or (1L shl bit)
                    bit++
                }
            }
            scaled.recycle()
            hash
        }.getOrNull()
    }
}
