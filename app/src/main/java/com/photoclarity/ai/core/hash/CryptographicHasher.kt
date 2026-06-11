package com.photoclarity.ai.core.hash

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptographicHasher @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Compute MD5 hash of a file identified by URI.
     * Reads the file in buffered chunks to avoid OOM on large files.
     */
    suspend fun computeMd5(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(BUFFER_SIZE)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().toHexString()
        }.getOrNull()
    }

    /**
     * Compute SHA-256 hash of a file identified by URI.
     */
    suspend fun computeSha256(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().toHexString()
        }.getOrNull()
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}
