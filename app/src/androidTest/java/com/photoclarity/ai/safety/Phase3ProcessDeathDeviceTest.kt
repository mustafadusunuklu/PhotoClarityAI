package com.photoclarity.ai.safety

import android.app.Activity
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photoclarity.ai.MainActivity
import com.photoclarity.ai.core.media.*
import com.photoclarity.ai.core.util.StorageUtils
import com.photoclarity.ai.data.repository.PhotoRepositoryImpl
import com.photoclarity.ai.domain.model.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Seed → external shell force-stop/start → verify → cleanup; separate invocations, real process. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
@RunWith(AndroidJUnit4::class)
class Phase3ProcessDeathDeviceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val resolver get() = context.contentResolver
    private val entry get() = EntryPointAccessors.fromApplication(context.applicationContext, Phase3TestEntryPoint::class.java)
    private val recordFile get() = context.cacheDir.resolve("phase3-process-test-record.json")
    private fun waitUntil(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 20000
        while (!condition()) { if (System.currentTimeMillis() > until) fail("Process-death condition timed out"); Thread.sleep(100) }
    }
    private fun repo(): PhotoRepositoryImpl { val s = StorageUtils(context); return PhotoRepositoryImpl(context, MediaStoreScanner(context,s),s,MediaRemovalPlatform(context)) }
    private fun record() = JSONObject(recordFile.readText()).also {
        require(it.getString("folder").startsWith("PhotoClarityAI_Phase3_")); require(it.getString("owner") == context.packageName)
    }
    private fun seed(): List<Photo> {
        check(!recordFile.exists()) { "Previous test record requires scoped cleanup" }
        waitUntil { entry.sessions().state.value.ready }
        check(entry.sessions().state.value.session == null) { "Refuse to replace user session" }
        check(PhotoAccessManager.current(context) == PhotoAccess.FULL)
        val folder = "PhotoClarityAI_Phase3_${UUID.randomUUID()}"; val uris = mutableListOf<Uri>()
        val beforeBytes = runBlocking { entry.settings().getCleanedBytesThisMonth() }
        recordFile.writeText(JSONObject().put("folder",folder).put("owner",context.packageName).put("uris",JSONArray()).put("beforeBytes",beforeBytes).toString())
        repeat(3) { i ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,"$folder-$i.png"); put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/$folder/"); put(MediaStore.MediaColumns.IS_PENDING,1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values)); uris += uri
            recordFile.writeText(record().put("uris",JSONArray(uris.map { it.toString() })).toString())
            checkNotNull(resolver.openOutputStream(uri)).use { it.write(Phase1SyntheticMedia.png()) }
            resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
        }
        val photos = runBlocking { repo().loadAllPhotos(setOf(folder)) }.sortedBy { it.id }; assertEquals(3,photos.size)
        runBlocking { ProductionTestSessions.seed(context,listOf(DuplicateGroup("device-group-$folder",photos,DuplicateGroup.GroupType.EXACT_DUPLICATE,1f,photos.first().id,photos.drop(1).sumOf { it.sizeBytes })),folder) }
        return photos
    }
    @Test fun seedCompletedSelectionForShellKill(): Unit = runBlocking {
        val photos = seed(); val s = entry.sessions().state.value.session!!
        entry.sessions().select(s.id,setOf(photos[1].mediaKey)); assertEquals(setOf(photos[1].mediaKey),entry.sessions().state.value.selectedKeys)
    }
    @Test fun verifyCompletedSelectionAfterShellKill() {
        val folder = record().getString("folder")
        waitUntil { entry.sessions().state.value.trusted && !entry.removals().state.value.restoring }
        val s = entry.sessions().state.value
        assertEquals("device-session-$folder",s.session?.id); assertEquals(SessionStatus.COMPLETED,s.session?.status)
        assertEquals(3,s.visibleGroups.single().photos.size); assertEquals(1,s.selectedKeys.size)
        assertFalse(s.selectedKeys.contains(s.visibleGroups.single().recommendedPhoto!!.mediaKey))
        assertEquals(1,entry.removals().state.value.selectedPhotoCount); assertFalse(entry.scans().state.value.isScanning)
        // Recreate actual ResultsViewModels through production navigation; persisted selection survives Back.
        repeat(2) {
            var shortcut: AccessibilityNodeInfo? = null
            repeat(8) {
                if (shortcut == null) {
                    val all = nodes(instrument.uiAutomation.rootInActiveWindow)
                    shortcut = all.firstOrNull { it.text?.toString() == "Kopyalar" }
                    if (shortcut == null) {
                        all.firstOrNull { it.isScrollable && it.packageName?.toString() == context.packageName }
                            ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        Thread.sleep(400)
                    }
                }
            }
            assertNotNull("Production Dashboard results shortcut",shortcut)
            var click = shortcut
            while (click != null && !click!!.isClickable) click = click!!.parent
            assertTrue(checkNotNull(click).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil { nodes(instrument.uiAutomation.rootInActiveWindow).any { it.text?.toString() == "Çöp Kutusu (1)" } }
            assertEquals(1,entry.removals().state.value.selectedPhotoCount)
            assertEquals(s.session?.id,entry.sessions().state.value.session?.id)
            assertTrue(instrument.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            Thread.sleep(500)
        }
    }
    @Test fun seedRunningCheckpointForShellKill(): Unit = runBlocking {
        seed(); val s = entry.sessions().state.value.session!!
        entry.sessions().begin(s.copy(status = SessionStatus.RUNNING,attempted = 1,discovered = 3,durationMillis = 500))
    }
    @Test fun verifyInterruptedCheckpointAfterShellKill() {
        waitUntil { entry.sessions().state.value.session?.status == SessionStatus.INTERRUPTED }
        val s = entry.sessions().state.value
        assertEquals(SessionError.PROCESS_INTERRUPTED,s.session?.error); assertEquals(1,s.session?.attempted)
        assertTrue(s.visibleGroups.isEmpty()); assertFalse(entry.scans().state.value.isScanning); assertNotNull(entry.scans().state.value.error)
    }
    @Test fun seedRealApprovedTrashWithoutDeliveringCallback() {
        val photos = seed(); val co = entry.removals(); val received = AtomicBoolean(false)
        instrument.uiAutomation.serviceInfo = instrument.uiAutomation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        waitUntil { co.state.value.groups.size == 1 }
        activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val state by co.state.collectAsStateWithLifecycle()
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    assertEquals(Activity.RESULT_OK,it.resultCode); received.set(true) // Deliberately withhold coordinator callback.
                }
                LaunchedEffect(state.pendingDeleteIntentSender) { state.pendingDeleteIntentSender?.let {
                    co.onDeletePromptLaunched(); launcher.launch(IntentSenderRequest.Builder(it).build())
                } }
                Text("Controlled crash before callback reconciliation")
            }
            co.smartSelectAll(); co.requestDeleteConfirmation(); co.deleteSelectedPhotos()
        }
        var button: AccessibilityNodeInfo? = null
        waitUntil { button = nodes(instrument.uiAutomation.rootInActiveWindow).firstOrNull {
            it.packageName?.toString()?.contains("providers.media") == true && it.viewIdResourceName?.endsWith("/button1") == true }; button != null }
        assertTrue(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)); waitUntil { received.get() }
        assertTrue(co.state.value.isLoading); assertEquals(RemovalStatus.WAITING_SYSTEM,entry.sessions().state.value.removal?.status)
        assertFalse(MediaRemovalPlatform(context).isTrashed(photos.first().contentUri))
        photos.drop(1).forEach { assertTrue(MediaRemovalPlatform(context).isTrashed(it.contentUri)) }
    }
    @Test fun verifyApprovedTrashRecoveredAfterShellKill() {
        val data = record()
        waitUntil { entry.sessions().state.value.removal?.status == RemovalStatus.FINISHED && !entry.removals().state.value.isLoading }
        val s = entry.sessions().state.value
        assertEquals(2,s.removal?.removedKeys?.size); assertTrue(s.groups.isEmpty()); assertTrue(s.selectedKeys.isEmpty())
        val photos = runBlocking { repo().loadAllPhotos(s.session!!.settings.selectedFolders) }
        assertEquals(1,photos.size); assertFalse(MediaRemovalPlatform(context).isTrashed(photos.single().contentUri))
        assertNull(entry.removals().state.value.pendingDeleteIntentSender)
        assertEquals(data.getLong("beforeBytes"),runBlocking { entry.settings().getCleanedBytesThisMonth() })
    }
    @Test fun cleanupOnlyRecordedProcessFixtures(): Unit = runBlocking {
        if (!recordFile.exists()) return@runBlocking
        val data = record(); val folder = data.getString("folder"); val uris = data.getJSONArray("uris")
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_INCLUDE) }
        for (i in 0 until uris.length()) {
            val uri = Uri.parse(uris.getString(i))
            resolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.OWNER_PACKAGE_NAME),args,null)?.use {
                if (it.moveToFirst()) { check(it.getString(0).startsWith(folder) && it.getString(1) == context.packageName); resolver.delete(uri,null,null) }
            }
        }
        ProductionTestSessions.cleanup(context,folder); check(recordFile.delete())
    }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
}
