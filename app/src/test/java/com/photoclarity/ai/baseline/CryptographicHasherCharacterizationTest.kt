package com.photoclarity.ai.baseline

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.photoclarity.ai.core.hash.CryptographicHasher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class CryptographicHasherCharacterizationTest {
    private lateinit var resolver: ContentResolver
    private lateinit var uri: Uri
    private lateinit var hasher: CryptographicHasher

    @Before fun setup() {
        val context = mock(Context::class.java)
        resolver = mock(ContentResolver::class.java)
        uri = mock(Uri::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        hasher = CryptographicHasher(context)
    }

    /** R03 regression: inaccessible files have no usable digest. */
    @Test fun nullStreamHasNoMd5(): Unit = runBlocking {
        `when`(resolver.openInputStream(uri)).thenReturn(null)
        assertNull(hasher.computeMd5(uri))
    }

    @Test fun nullStreamHasNoSha256(): Unit = runBlocking {
        `when`(resolver.openInputStream(uri)).thenReturn(null)
        assertNull(hasher.computeSha256(uri))
    }

    @Test fun distinctUnreadableUrisHaveNoUsableHashes(): Unit = runBlocking {
        val other = mock(Uri::class.java)
        assertNull(hasher.computeMd5(uri))
        assertNull(hasher.computeMd5(other))
    }

    @Test fun openingExceptionReturnsNullForBothAlgorithms(): Unit = runBlocking {
        `when`(resolver.openInputStream(uri)).thenThrow(FileNotFoundException("synthetic"))
        assertNull(hasher.computeMd5(uri))
        assertNull(hasher.computeSha256(uri))
    }

    @Test fun readExceptionReturnsNullAndClosesStream(): Unit = runBlocking {
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int = throw IOException("synthetic")
            override fun close() { closed = true }
        }
        `when`(resolver.openInputStream(uri)).thenReturn(stream)
        assertNull(hasher.computeSha256(uri))
        assertTrue(closed)
    }

    @Test fun fixtureBytesAreHashedAcrossBufferBoundariesAndClosed(): Unit = runBlocking {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/fixtures/base.png")).use { it.readBytes() }
        assertTrue(bytes.size > 8192)
        var closed = false
        val stream = object : ByteArrayInputStream(bytes) {
            override fun close() { closed = true; super.close() }
        }
        `when`(resolver.openInputStream(uri)).thenReturn(stream)
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, hasher.computeSha256(uri))
        assertTrue(closed)
    }
    @Test fun emptyInputIsNotAUsablePhotoDigest(): Unit = runBlocking {
        `when`(resolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(byteArrayOf()))
        assertNull(hasher.computeMd5(uri))
    }
    @Test fun cancellationIsRethrownAndStreamClosed(): Unit = runBlocking {
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int = throw kotlinx.coroutines.CancellationException("cancel")
            override fun close() { closed = true }
        }
        `when`(resolver.openInputStream(uri)).thenReturn(stream)
        try { hasher.computeSha256(uri); fail("Cancellation must propagate") }
        catch (e: kotlinx.coroutines.CancellationException) { assertTrue(closed) }
    }

}
