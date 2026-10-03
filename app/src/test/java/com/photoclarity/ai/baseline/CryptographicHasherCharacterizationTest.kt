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

    /** KNOWN BUG R03: these are characterization expectations, not safety requirements. */
    @Test fun nullStreamCurrentlyReturnsEmptyMd5() = runBlocking {
        `when`(resolver.openInputStream(uri)).thenReturn(null)
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hasher.computeMd5(uri))
    }

    @Test fun nullStreamCurrentlyReturnsEmptySha256() = runBlocking {
        `when`(resolver.openInputStream(uri)).thenReturn(null)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hasher.computeSha256(uri))
    }

    @Test fun distinctUnreadableUrisCurrentlyCollide() = runBlocking {
        val other = mock(Uri::class.java)
        assertEquals(hasher.computeMd5(uri), hasher.computeMd5(other))
    }

    @Test fun openingExceptionReturnsNullForBothAlgorithms() = runBlocking {
        `when`(resolver.openInputStream(uri)).thenThrow(FileNotFoundException("synthetic"))
        assertNull(hasher.computeMd5(uri))
        assertNull(hasher.computeSha256(uri))
    }

    @Test fun readExceptionReturnsNullAndClosesStream() = runBlocking {
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int = throw IOException("synthetic")
            override fun close() { closed = true }
        }
        `when`(resolver.openInputStream(uri)).thenReturn(stream)
        assertNull(hasher.computeSha256(uri))
        assertTrue(closed)
    }

    @Test fun fixtureBytesAreHashedAcrossBufferBoundariesAndClosed() = runBlocking {
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
}
