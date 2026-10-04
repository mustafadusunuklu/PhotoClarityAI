package com.photoclarity.ai.core.hash

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.photoclarity.ai.core.util.BitmapUtils
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DifferenceHasher @Inject constructor(private val context: android.content.Context, private val bitmapUtils: BitmapUtils) {
    suspend fun computeDHash(uri: Uri): Long? = withContext(Dispatchers.Default) {
        val bitmap = bitmapUtils.decodeSampledBitmap(uri, 9, 8) ?: return@withContext null
        try { computeFromBitmap(bitmap) } finally { bitmap.recycle() }
    }
    suspend fun computeFromBitmap(bitmap: Bitmap): Long? = withContext(Dispatchers.Default) {
        var scaled: Bitmap? = null
        try {
            currentCoroutineContext().ensureActive()
            scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            var hash = 0L
            var bit = 0
            for (y in 0 until 8) {
                currentCoroutineContext().ensureActive()
                for (x in 0 until 8) {
                    val left = scaled.getPixel(x, y); val right = scaled.getPixel(x + 1, y)
                    val a = 0.299 * Color.red(left) + 0.587 * Color.green(left) + 0.114 * Color.blue(left)
                    val b = 0.299 * Color.red(right) + 0.587 * Color.green(right) + 0.114 * Color.blue(right)
                    if (a > b) hash = hash or (1L shl bit)
                    bit++
                }
            }
            hash
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
        finally { if (scaled !== bitmap) scaled?.recycle() }
    }
}
