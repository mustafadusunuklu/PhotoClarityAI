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

/** Android consent/query boundary, kept separate so removal outcomes can be tested without a device. */
@Singleton
class MediaRemovalPlatform @Inject constructor(@ApplicationContext private val context: Context) {
    val mode: PhotoRepository.RemovalMode
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            PhotoRepository.RemovalMode.SYSTEM_TRASH else PhotoRepository.RemovalMode.PERMANENT_DELETE

    fun createTrashPrompt(uris: List<Uri>): IntentSender {
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
}
