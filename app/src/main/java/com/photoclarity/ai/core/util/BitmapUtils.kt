package com.photoclarity.ai.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitmapUtils @Inject constructor(
    private val context: Context
) {
    /**
     * Decode a bitmap from URI with inSampleSize to avoid OOM.
     * The result is scaled as close as possible to [reqWidth]×[reqHeight].
     */
    suspend fun decodeSampledBitmap(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565  // Reduced memory footprint

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }.getOrNull()
        }

    /**
     * Compute Laplacian variance as a sharpness score.
     * Higher value = sharper image.
     */
    fun computeSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return 0f

        // Sample a center region for speed
        val sampleSize = minOf(128, width, height)
        val startX = (width - sampleSize) / 2
        val startY = (height - sampleSize) / 2

        val pixels = IntArray(sampleSize * sampleSize)
        bitmap.getPixels(pixels, 0, sampleSize, startX, startY, sampleSize, sampleSize)

        val grays = pixels.map { p ->
            (0.299 * android.graphics.Color.red(p) +
                    0.587 * android.graphics.Color.green(p) +
                    0.114 * android.graphics.Color.blue(p)).toFloat()
        }

        // Laplacian approximation: variance of second derivative
        var sum = 0f
        var sumSq = 0f
        var count = 0
        for (y in 1 until sampleSize - 1) {
            for (x in 1 until sampleSize - 1) {
                val laplacian = (
                        grays[(y - 1) * sampleSize + x] +
                        grays[(y + 1) * sampleSize + x] +
                        grays[y * sampleSize + (x - 1)] +
                        grays[y * sampleSize + (x + 1)] -
                        4 * grays[y * sampleSize + x]
                        )
                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        return (sumSq / count - mean * mean).coerceAtLeast(0f)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
