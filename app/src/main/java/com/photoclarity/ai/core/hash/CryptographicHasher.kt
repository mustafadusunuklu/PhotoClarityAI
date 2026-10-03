package com.photoclarity.ai.core.hash

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class CryptographicHasher @Inject constructor(private val context: Context) {
    suspend fun computeMd5(uri: Uri): String? = compute(uri, "MD5")
    suspend fun computeSha256(uri: Uri): String? = compute(uri, "SHA-256")

    private suspend fun compute(uri: Uri, algorithm: String): String? = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(8192)
            var totalRead = 0L
            stream.use {
                while (true) {
                    coroutineContext.ensureActive()
                    val read = it.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    totalRead += read
                }
            }
            if (totalRead == 0L) return@withContext null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
