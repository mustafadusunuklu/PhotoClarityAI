package com.photoclarity.ai.core.media

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.photoclarity.ai.domain.repository.PhotoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.photoclarity.ai.domain.model.AnalysisVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Android consent/query boundary, kept separate so removal outcomes can be tested without a device. */
@Singleton
class MediaRemovalPlatform @Inject constructor(@ApplicationContext private val context: Context) {
    val mode: PhotoRepository.RemovalMode
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            PhotoRepository.RemovalMode.SYSTEM_TRASH else PhotoRepository.RemovalMode.PERMANENT_DELETE

    fun createTrashPrompt(uris: List<Uri>): IntentSender {
        require(uris.isNotEmpty() && uris.size <= RemovalBatchPolicy.MAX_TRASH_URIS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw UnsupportedOperationException("System trash requires Android 11 or later")
        }
        return MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender
    }

    fun recoveryPrompt(error: Exception): IntentSender? =
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && error is RecoverableSecurityException)
            error.userAction.actionIntent.intentSender else null

    fun isTrashed(uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        return context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), args, null)
            ?.use { it.moveToFirst() && it.getInt(0) == 1 } ?: false
    }

    /** Query only the frozen URI set, in collection-scoped batches. Unknown rows fail closed. */
    suspend fun trashedUris(uris: List<Uri>): Set<Uri> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptySet()
        val valid = uris.distinct().filter { uri -> uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY &&
            uri.pathSegments.size == 4 && uri.pathSegments[1] == "images" && uri.pathSegments[2] == "media" &&
            uri.lastPathSegment?.toLongOrNull()?.takeIf { it > 0 } != null }
        val removed = HashSet<Uri>()
        for ((collection, members) in valid.groupBy { it.toString().substringBeforeLast('/') }) {
            for (batch in members.chunked(AnalysisVersion.CACHE_BATCH)) {
                currentCoroutineContext().ensureActive()
                val expected = batch.associateBy { checkNotNull(it.lastPathSegment).toLong() }
                val confirmed = HashSet<Uri>()
                try {
                    val args = Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                        putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                            "${MediaStore.MediaColumns._ID} IN (${batch.joinToString(",") { "?" }})")
                        putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                            batch.map { it.lastPathSegment!! }.toTypedArray())
                    }
                    context.contentResolver.query(Uri.parse(collection), arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_TRASHED), args, null)?.use { row ->
                        while (row.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val uri = expected[row.getLong(0)]
                            if (uri != null && !row.isNull(1) && row.getInt(1) == 1) confirmed.add(uri)
                        }
                    }
                    removed.addAll(confirmed)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { /* A failed query does not confirm any row in this batch. */ }
            }
        }
        return removed
    }
}
