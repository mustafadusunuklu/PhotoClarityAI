package com.photoclarity.ai.core.hash

import android.graphics.Color
import android.net.Uri
import com.photoclarity.ai.core.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Average Hash (aHash) implementation.
 *
 * Algorithm:
 * 1. Resize to 8×8 grayscale
 * 2. Compute mean pixel value
 * 3. Each bit = 1 if pixel > mean, 0 otherwise
 * 4. Return as 64-bit Long
 *
 * Faster than pHash, less robust but still effective.
 */
@Singleton
class AverageHasher @Inject constructor(
    private val context: android.content.Context,
    private val bitmapUtils: BitmapUtils
) {
    companion object {
        private const val SIZE = 8
    }

    suspend fun computeAHash(uri: Uri): Long? = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = bitmapUtils.decodeSampledBitmap(uri, SIZE, SIZE) ?: return@runCatching null
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
            bitmap.recycle()

            val pixels = IntArray(SIZE * SIZE)
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            scaled.recycle()

            val grays = pixels.map { p ->
                0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
            }
            val mean = grays.average()

            var hash = 0L
            grays.forEachIndexed { i, v ->
                if (v > mean) hash = hash or (1L shl i)
            }
            hash
        }.getOrNull()
    }
}
