package com.photoclarity.ai.safety

import android.app.Activity
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.core.media.MediaRemovalPlatform
import com.photoclarity.ai.core.media.MediaStoreScanner
import com.photoclarity.ai.core.media.PhotoAccess
import com.photoclarity.ai.core.media.PhotoAccessManager
import com.photoclarity.ai.core.util.StorageUtils
import com.photoclarity.ai.data.repository.PhotoRepositoryImpl
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.model.ScanSettings
import com.photoclarity.ai.domain.repository.SettingsRepository
import com.photoclarity.ai.ui.results.ResultsViewModel
import com.photoclarity.ai.ui.scan.ScanResultHolder
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real 2000+1 consent/reconciliation; a minimal host avoids testing Phase 5's large-group layout. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
@android.annotation.TargetApi(30)
@RunWith(AndroidJUnit4::class)
class Phase2BatchDeviceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val resolver get() = context.contentResolver
    private val folder = "PhotoClarityAI_Phase2_Batch_${UUID.randomUUID()}"
    private val created = mutableListOf<Uri>()
    private val originalGroups = ScanResultHolder.groups

    private fun waitUntil(timeout: Long = 30000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition()) { if (System.currentTimeMillis() >= deadline) fail("Batch condition timed out"); Thread.sleep(100) }
    }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun systemConsent(approve: Boolean) {
        var button: AccessibilityNodeInfo? = null
        waitUntil(60000) {
            button = nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
                it.packageName?.toString()?.contains("providers.media") == true &&
                    it.viewIdResourceName?.endsWith(if (approve) "/button1" else "/button2") == true
            }
            button != null
        }
        assertTrue(checkNotNull(button).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    @Test fun approve2000ThenCancelRemainingOneProtectsKeeperAndDoesNotCreditSpace() {
        assertEquals(PhotoAccess.FULL, PhotoAccessManager.current(context))
        val bytes = Phase1SyntheticMedia.png()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        repeat(2002) { index ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$folder-$index.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$folder/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(collection, values)); created.add(uri)
            checkNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            if (index % 500 == 0) println("BATCH_FIXTURES_CREATED=$index")
        }
        val repo = PhotoRepositoryImpl(context, MediaStoreScanner(context, StorageUtils(context)), StorageUtils(context), MediaRemovalPlatform(context))
        val photos = runBlocking { repo.loadAllPhotos(setOf(folder)) }.sortedBy { it.id }
        assertEquals(2002, photos.size)
        var credited = 0L
        val settings = object : SettingsRepository {
            override fun getScanSettings() = flowOf(ScanSettings())
            override suspend fun saveScanSettings(settings: ScanSettings) = Unit
            override suspend fun getCleanedBytesThisMonth() = credited
            override suspend fun addCleanedBytes(bytes: Long) { credited += bytes }
            override suspend fun resetMonthlyStats() = Unit
        }
        lateinit var vm: ResultsViewModel
        activityRule.scenario.onActivity { activity ->
            ScanResultHolder.groups = listOf(DuplicateGroup("batch-$folder", photos,
                DuplicateGroup.GroupType.EXACT_DUPLICATE, 1f, photos.first().id, photos.drop(1).sumOf { it.sizeBytes }))
            ScanResultHolder.error = null
            vm = ResultsViewModel(repo, settings, QualityScorer())
            activity.setContent {
                val state by vm.uiState.collectAsStateWithLifecycle()
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    vm.onDeleteResult(it.resultCode == Activity.RESULT_OK)
                }
                LaunchedEffect(state.pendingDeleteIntentSender) {
                    state.pendingDeleteIntentSender?.let {
                        vm.onDeletePromptLaunched()
                        launcher.launch(IntentSenderRequest.Builder(it).build())
                    }
                }
                Text("Controlled batch ${state.consentBatch}/${state.consentBatchCount}")
            }
            vm.smartSelectAll()
            assertEquals(2001, vm.uiState.value.selectedPhotoCount)
            assertFalse(vm.uiState.value.selectedPhotoIds.contains(photos.first().id))
            vm.requestDeleteConfirmation(); vm.deleteSelectedPhotos()
        }
        systemConsent(true)
        waitUntil(90000) { vm.uiState.value.consentBatch == 2 }
        systemConsent(false)
        waitUntil(30000) { !vm.uiState.value.isLoading }
        assertEquals(2000, vm.uiState.value.lastDeletedCount)
        assertEquals(setOf(photos.last().id), vm.uiState.value.selectedPhotoIds)
        assertEquals(listOf(photos.first().id, photos.last().id), vm.uiState.value.groups.single().photos.map { it.id })
        assertEquals(0L, credited)
        val verified = runBlocking { repo.verifyTrashedPhotos(photos.map { it.contentUri }) }
        assertEquals(photos.drop(1).take(2000).map { it.contentUri }.toSet(), verified.removedUris)
        assertEquals(setOf(photos.first().contentUri, photos.last().contentUri), verified.failedUris)
        assertTrue(vm.uiState.value.error?.contains("iptal") == true)
    }

    @After fun cleanup() {
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
        for (uri in created) {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.OWNER_PACKAGE_NAME), args, null)?.use {
                if (it.moveToFirst()) {
                    check(it.getString(0).startsWith(folder) && it.getString(1) == context.packageName)
                    resolver.delete(uri, null, null)
                }
            }
        }
        instrumentation.runOnMainSync { ScanResultHolder.groups = originalGroups; ScanResultHolder.error = null }
        println("BATCH_FIXTURES_CLEANED=${created.size}")
    }
}
