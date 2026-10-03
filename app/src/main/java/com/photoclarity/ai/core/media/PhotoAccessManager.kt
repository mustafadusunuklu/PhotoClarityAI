package com.photoclarity.ai.core.media

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PhotoAccessSnapshot(
    val access: PhotoAccess,
    val revision: Long = 0,
    val foreground: Long = 0,
    val needsSettings: Boolean = false,
    val error: String? = null
)

@Singleton
class PhotoAccessManager @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        fun permissions(sdk: Int = Build.VERSION.SDK_INT): Array<String> = when {
            sdk >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            sdk >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        fun current(context: Context): PhotoAccess {
            fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            return PhotoAccessPolicy.resolve(Build.VERSION.SDK_INT,
                granted(Manifest.permission.READ_EXTERNAL_STORAGE),
                granted(Manifest.permission.READ_MEDIA_IMAGES),
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        }

        fun requireAccess(context: Context) {
            if (current(context) == PhotoAccess.DENIED) throw SecurityException("Fotoğraf erişimi yok. İzin verin veya cihaz ayarlarını açın.")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val history = context.getSharedPreferences("photo_permission_history", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(PhotoAccessSnapshot(current(context)))
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var limitedIds: Set<Long>? = null

    fun markRequested() { history.edit().putBoolean("requested", true).apply() }

    /** Refresh on resume and request callback; never persist a grant or a selected URI. */
    fun refresh(activity: Activity, selectionMayHaveChanged: Boolean = false) {
        refreshJob?.cancel()
        val access = current(context)
        val previous = _state.value
        val changed = access != previous.access || (selectionMayHaveChanged && access == PhotoAccess.LIMITED)
        if (access != PhotoAccess.LIMITED) limitedIds = null
        val blocked = access == PhotoAccess.DENIED && history.getBoolean("requested", false) &&
            permissions().none { activity.shouldShowRequestPermissionRationale(it) }
        _state.value = previous.copy(access = access, revision = previous.revision + if (changed) 1 else 0,
            foreground = previous.foreground + 1, needsSettings = blocked, error = null)
        if (access != PhotoAccess.LIMITED || Build.VERSION.SDK_INT < 34) return
        refreshJob = scope.launch {
            try {
                val ids = withContext(Dispatchers.IO) {
                    requireAccess(context)
                    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    // Keep trashed rows in the fingerprint so our own trash request is not
                    // mistaken for the user changing the selected-photo permission.
                    val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
                    val cursor = context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), args, null)
                        ?: throw IllegalStateException("Fotoğraf erişimi doğrulanamadı.")
                    cursor.use { buildSet { while (it.moveToNext()) add(it.getLong(0)) } }
                }
                val selectionChanged = limitedIds != null && limitedIds != ids
                limitedIds = ids
                if (selectionChanged) _state.value = _state.value.copy(revision = _state.value.revision + 1)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(revision = _state.value.revision + 1,
                    error = "Seçilen fotoğraflara erişim doğrulanamadı. İzni kontrol ederek yeniden tarayın.")
            }
        }
    }
}
