package com.photoclarity.ai.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.exifinterface.media.ExifInterface
import com.photoclarity.ai.domain.model.AnalysisVersion
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
            val job = currentCoroutineContext()
            var owned: Bitmap? = null
            try {
                require(reqWidth > 0 && reqHeight > 0)
                job.ensureActive()
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val bounds = context.contentResolver.openInputStream(uri) ?: return@withContext null
                bounds.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
                options.inSampleSize = DecodeBudget.sampleSize(options.outWidth, options.outHeight, maxOf(reqWidth, reqHeight))
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565
                options.inScaled = false
                job.ensureActive()
                val orientation = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        ExifInterface(stream).let { it.isFlipped to it.rotationDegrees }
                    } ?: return@withContext null
                } catch (e: CancellationException) { throw e }
                catch (e: java.io.IOException) { false to 0 } // Formats without readable EXIF.
                job.ensureActive()
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                } ?: return@withContext null
                owned = decoded
                if (decoded.width > AnalysisVersion.DECODE_EDGE || decoded.height > AnalysisVersion.DECODE_EDGE ||
                    decoded.width.toLong() * decoded.height > AnalysisVersion.DECODE_PIXELS) return@withContext null
                job.ensureActive()
                if (orientation.first || orientation.second != 0) {
                    val source = decoded
                    val matrix = Matrix().apply {
                        if (orientation.first) postScale(-1f, 1f)
                        postRotate(orientation.second.toFloat())
                    }
                    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                    if (rotated !== source) source.recycle()
                    owned = rotated
                }
                job.ensureActive()
                val result = owned
                owned = null
                result
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { null }
            finally { owned?.recycle() }
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

        val grays = FloatArray(pixels.size) { index -> val p = pixels[index]
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

}
