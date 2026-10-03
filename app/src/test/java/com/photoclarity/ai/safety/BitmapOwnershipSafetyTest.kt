package com.photoclarity.ai.safety

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.photoclarity.ai.core.hash.AverageHasher
import com.photoclarity.ai.core.hash.DifferenceHasher
import com.photoclarity.ai.core.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class BitmapOwnershipSafetyTest {
    // Static mocks stay on this dispatcher thread; decode is mocked, no Android/real file I/O.
    @Test fun ahashCanReadSourceWhenScalingReturnsSameBitmap(): Unit = runBlocking(Dispatchers.Default) {
        val source = mock(Bitmap::class.java); val utils = mock(BitmapUtils::class.java)
        val uri = mock(Uri::class.java)
        `when`(utils.decodeSampledBitmap(uri,8,8)).thenReturn(source)
        mockStatic(Bitmap::class.java).use { scaling ->
            scaling.`when`<Bitmap> { Bitmap.createScaledBitmap(source,8,8,true) }.thenReturn(source)
            mockStatic(Color::class.java).use {
                assertEquals(0L,AverageHasher(mock(Context::class.java),utils).computeAHash(uri))
            }
        }
        verify(source,times(1)).recycle()
        val order = inOrder(source)
        order.verify(source).getPixels(any(),eq(0),eq(8),eq(0),eq(0),eq(8),eq(8))
        order.verify(source).recycle()
    }
    @Test fun dhashCanReadSourceWhenScalingReturnsSameBitmap(): Unit = runBlocking(Dispatchers.Default) {
        val source = mock(Bitmap::class.java); val utils = mock(BitmapUtils::class.java)
        val uri = mock(Uri::class.java)
        `when`(utils.decodeSampledBitmap(uri,9,8)).thenReturn(source)
        mockStatic(Bitmap::class.java).use { scaling ->
            scaling.`when`<Bitmap> { Bitmap.createScaledBitmap(source,9,8,true) }.thenReturn(source)
            mockStatic(Color::class.java).use {
                assertEquals(0L,DifferenceHasher(mock(Context::class.java),utils).computeDHash(uri))
            }
        }
        verify(source,times(1)).recycle()
        val order = inOrder(source); order.verify(source).getPixel(0,0); order.verify(source).recycle()
    }
    @Test fun failedPixelReadReleasesSourceAndDistinctScaledBitmap(): Unit = runBlocking(Dispatchers.Default) {
        val source = mock(Bitmap::class.java); val scaled = mock(Bitmap::class.java)
        val utils = mock(BitmapUtils::class.java); val uri = mock(Uri::class.java)
        `when`(utils.decodeSampledBitmap(uri,9,8)).thenReturn(source)
        `when`(scaled.getPixel(0,0)).thenThrow(IllegalStateException("decode failure"))
        mockStatic(Bitmap::class.java).use { scaling ->
            scaling.`when`<Bitmap> { Bitmap.createScaledBitmap(source,9,8,true) }.thenReturn(scaled)
            assertNull(DifferenceHasher(mock(Context::class.java),utils).computeDHash(uri))
        }
        verify(source).recycle(); verify(scaled).recycle()
    }
}
