package com.photoclarity.ai.core.hash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.photoclarity.ai.core.util.BitmapUtils
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerceptualHasher @Inject constructor(private val context: Context, private val bitmapUtils: BitmapUtils) {
    suspend fun computePHash(uri: Uri): Long? = withContext(Dispatchers.Default) {
        val bitmap = bitmapUtils.decodeSampledBitmap(uri, 32, 32) ?: return@withContext null
        try { computeFromBitmap(bitmap) } finally { bitmap.recycle() }
    }
    /** Borrows the source; only owns a distinct scaled bitmap. */
    suspend fun computeFromBitmap(bitmap: Bitmap): Long? = withContext(Dispatchers.Default) {
        val job = currentCoroutineContext()
        var scaled: Bitmap? = null
        try {
            job.ensureActive()
            scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
            val pixels = IntArray(1024)
            scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
            val gray = DoubleArray(1024) { i -> val p = pixels[i]; 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p) }
            PerceptualDct.hash(gray) { job.ensureActive() }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
        finally { if (scaled !== bitmap) scaled?.recycle() }
    }
}
