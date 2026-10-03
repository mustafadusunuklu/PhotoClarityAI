package com.photoclarity.ai.safety

import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.photoclarity.ai.core.media.MediaRemovalPlatform
import com.photoclarity.ai.core.media.MediaStoreScanner
import com.photoclarity.ai.core.util.StorageUtils
import com.photoclarity.ai.data.repository.PhotoRepositoryImpl
import com.photoclarity.ai.domain.repository.PhotoRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class MediaRemovalSafetyTest {
    private lateinit var resolver: ContentResolver
    private lateinit var platform: MediaRemovalPlatform
    private lateinit var repo: PhotoRepositoryImpl
    private fun uri(id: String): Uri = mock(Uri::class.java).also {
        `when`(it.scheme).thenReturn("content"); `when`(it.authority).thenReturn("media")
        `when`(it.lastPathSegment).thenReturn(id)
    }
    @Before fun setup() {
        val context = mock(Context::class.java); resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        platform = mock(MediaRemovalPlatform::class.java)
        `when`(platform.mode).thenReturn(PhotoRepository.RemovalMode.PERMANENT_DELETE)
        repo = PhotoRepositoryImpl(context, mock(MediaStoreScanner::class.java), mock(StorageUtils::class.java), platform)
    }
    @Test fun zeroRowsAndExceptionsAreNotSuccessfulDeletions(): Unit = runBlocking {
        val a = uri("1"); val b = uri("2"); val c = uri("3")
        `when`(resolver.delete(a,null,null)).thenReturn(1)
        `when`(resolver.delete(b,null,null)).thenReturn(0)
        `when`(resolver.delete(c,null,null)).thenThrow(SecurityException("denied"))
        val result = repo.deletePhotos(listOf(a,b,c)) as PhotoRepository.DeleteResult.Success
        assertEquals(setOf(a),result.removedUris); assertEquals(setOf(b,c),result.failedUris)
    }
    @Test fun collectionUriIsRejectedWithoutAnyMutation(): Unit = runBlocking {
        assertTrue(repo.deletePhotos(listOf(uri("images"))) is PhotoRepository.DeleteResult.Error)
        verifyNoInteractions(resolver)
    }
    @Test fun systemTrashFailureNeverFallsBackToPermanentDelete(): Unit = runBlocking {
        `when`(platform.mode).thenReturn(PhotoRepository.RemovalMode.SYSTEM_TRASH)
        val a = uri("1"); `when`(platform.createTrashPrompt(listOf(a))).thenThrow(IllegalArgumentException("unavailable"))
        assertTrue(repo.deletePhotos(listOf(a)) is PhotoRepository.DeleteResult.Error)
        verifyNoInteractions(resolver)
    }
    @Test fun systemTrashCreatesOnlyAConsentRequest(): Unit = runBlocking {
        `when`(platform.mode).thenReturn(PhotoRepository.RemovalMode.SYSTEM_TRASH)
        val a = uri("1"); val sender = mock(IntentSender::class.java)
        `when`(platform.createTrashPrompt(listOf(a))).thenReturn(sender)
        val result = repo.deletePhotos(listOf(a)) as PhotoRepository.DeleteResult.RequiresPermission
        assertSame(sender,result.intentSender); verifyNoInteractions(resolver)
    }
    @Test fun trashVerificationFailsClosedForUnknownAndUnchangedRows(): Unit = runBlocking {
        `when`(platform.mode).thenReturn(PhotoRepository.RemovalMode.SYSTEM_TRASH)
        val a = uri("1"); val b = uri("2"); val c = uri("3")
        `when`(platform.isTrashed(a)).thenReturn(true)
        `when`(platform.isTrashed(b)).thenReturn(false)
        `when`(platform.isTrashed(c)).thenThrow(SecurityException("denied"))
        val result = repo.verifyTrashedPhotos(listOf(a,b,c))
        assertEquals(setOf(a),result.removedUris); assertEquals(setOf(b,c),result.failedUris)
    }
    @Test fun perItemConsentReturnsCompletedAndFrozenRemainingUris(): Unit = runBlocking {
        val a = uri("1"); val b = uri("2"); val c = uri("3")
        val error = SecurityException("permission"); val sender = mock(IntentSender::class.java)
        `when`(resolver.delete(a,null,null)).thenReturn(1)
        `when`(resolver.delete(b,null,null)).thenThrow(error)
        `when`(platform.recoveryPrompt(error)).thenReturn(sender)
        val result = repo.deletePhotos(listOf(a,b,c)) as PhotoRepository.DeleteResult.RequiresPermission
        assertEquals(setOf(a),result.removedUris); assertEquals(listOf(b,c),result.retryUris)
        verify(resolver,never()).delete(c,null,null)
    }
}
