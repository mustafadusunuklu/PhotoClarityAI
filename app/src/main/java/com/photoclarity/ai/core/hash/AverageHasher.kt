package com.photoclarity.ai.core.hash

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.photoclarity.ai.core.util.BitmapUtils
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AverageHasher @Inject constructor(private val context: android.content.Context, private val bitmapUtils: BitmapUtils) {
    suspend fun computeAHash(uri: Uri): Long? = withContext(Dispatchers.Default) {
        val bitmap = bitmapUtils.decodeSampledBitmap(uri, 8, 8) ?: return@withContext null
        try { computeFromBitmap(bitmap) } finally { bitmap.recycle() }
    }
    suspend fun computeFromBitmap(bitmap: Bitmap): Long? = withContext(Dispatchers.Default) {
        var scaled: Bitmap? = null
        try {
            currentCoroutineContext().ensureActive()
            scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
            val pixels = IntArray(64)
            scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)
            val gray = DoubleArray(64) { i -> val p = pixels[i]; 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p) }
            val mean = gray.average()
            var hash = 0L
            gray.forEachIndexed { i, value -> if (value > mean) hash = hash or (1L shl i) }
            hash
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
        finally { if (scaled !== bitmap) scaled?.recycle() }
    }
}
