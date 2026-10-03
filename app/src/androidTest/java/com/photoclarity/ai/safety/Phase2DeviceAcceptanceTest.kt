package com.photoclarity.ai.safety

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.preferencesDataStoreFile
import com.photoclarity.ai.MainActivity
import com.photoclarity.ai.core.media.MediaStoreScanner
import com.photoclarity.ai.core.media.PhotoAccess
import com.photoclarity.ai.core.media.PhotoAccessManager
import com.photoclarity.ai.core.util.StorageUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Preset grants outside instrumentation. No photo bytes are read/written; normal ID/count queries run. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 34)
@RunWith(AndroidJUnit4::class)
class Phase2DeviceAcceptanceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = buildList {
        if (root != null) { add(root); for (i in 0 until root.childCount) addAll(nodes(root.getChild(i))) }
    }
    private fun awaitText(text: String): AccessibilityNodeInfo {
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
                it.packageName?.toString() == context.packageName && it.text?.toString() == text
            }?.let { return it }
            Thread.sleep(100)
        }
        throw AssertionError("Controlled app text not found: $text")
    }

    @Test fun fullAccessAndInsetsSurviveActivityRecreation() {
        assertEquals(36, context.applicationInfo.targetSdkVersion)
        assertTrue(context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().contains(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        assertEquals(context.filesDir.resolve("datastore/photoclarity_settings.preferences_pb").canonicalPath,
            context.preferencesDataStoreFile("photoclarity_settings").canonicalPath)
        assertEquals(PhotoAccess.FULL, PhotoAccessManager.current(context))
        val text = "Fotoğraf erişimi: tüm fotoğraflar"
        val node = awaitText(text)
        activityRule.scenario.onActivity { activity ->
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            val insets = activity.window.decorView.rootWindowInsets.systemWindowInsetTop
            assertTrue("Permission text overlaps status bar", bounds.top >= insets)
            assertEquals(PhotoAccess.FULL, activity.photoAccess.state.value.access)
        }
        activityRule.scenario.recreate()
        awaitText(text)
        assertNotNull(awaitText("Fotoğraf izin ayarlarını aç"))
    }

    @Test fun deniedAccessFailsClosedInsteadOfReturningEmptyGallery(): Unit = runBlocking {
        assertEquals(PhotoAccess.DENIED, PhotoAccessManager.current(context))
        awaitText("Fotoğraf erişimi yok. Tarama için izin gereklidir.")
        val scanner = MediaStoreScanner(context, StorageUtils(context))
        try { scanner.scanAllPhotos(); fail("Denied access was treated as empty success") }
        catch (expected: SecurityException) { /* Required: no query and no clean-gallery claim. */ }
        try { scanner.getPhotoCount(); fail("Denied access was treated as zero photos") }
        catch (expected: SecurityException) { }
        activityRule.scenario.recreate()
        awaitText("Fotoğraf erişimi yok. Tarama için izin gereklidir.")
    }

    @Test fun selectedPhotoGrantIsLimitedAndLabelledAfterResume() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES))
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        assertEquals(PhotoAccess.LIMITED, PhotoAccessManager.current(context))
        awaitText("Sınırlı erişim: yalnız seçtiğiniz fotoğraflar taranır. Tüm galeri taranmaz.")
        activityRule.scenario.recreate()
        awaitText("Sınırlı erişim: yalnız seçtiğiniz fotoğraflar taranır. Tüm galeri taranmaz.")
    }

    @Test fun batchFixtureCleanupIncludesTrashedRows() {
        // Do not mistake the normal MediaStore query's trash exclusion for successful cleanup.
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?")
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf("PhotoClarityAI_Phase2_Batch_%", context.packageName))
        }
        val cursor = context.contentResolver.query(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.MediaColumns._ID), args, null)
        assertNotNull("Cleanup state is unknown if provider returns null", cursor)
        checkNotNull(cursor).use { assertEquals("Synthetic media left behind, including trash", 0, it.count) }
    }
}
