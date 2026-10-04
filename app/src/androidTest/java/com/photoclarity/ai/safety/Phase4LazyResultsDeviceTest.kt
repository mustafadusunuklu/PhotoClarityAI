package com.photoclarity.ai.safety

import android.net.Uri
import android.view.FrameMetrics
import android.view.Window
import android.view.MotionEvent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photoclarity.ai.MainActivity
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.core.session.*
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.*
import com.photoclarity.ai.testing.MemorySessionRepository
import com.photoclarity.ai.ui.results.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Controlled 20k-member results; no MediaStore bytes, deletion, production session writes or Espresso. */
@RunWith(AndroidJUnit4::class)
class Phase4LazyResultsDeviceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) { if (System.currentTimeMillis() >= deadline) fail("Lazy screen condition timed out"); Thread.sleep(100) }
    }
    @Test fun hugeGroupComposesVisibleMembersAndScrollsWithoutEagerImageTree() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val frames = CopyOnWriteArrayList<Long>()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)) }
        val photos = (1L..20_000L).map { id ->
            val uri = Uri.parse("content://phase4.invalid/generated/$id")
            Photo(id,uri,uri,"phase4-virtual-$id", "image/jpeg",100_000,100,101,null,1440,1440,"virtual",1,null,null)
        }
        val store = MemorySessionRepository(listOf(DuplicateGroup("huge",photos,DuplicateGroup.GroupType.VISUAL_SIMILAR,.9f,1,1_999_900_000)))
        val repository = object : PhotoRepository {
            override val removalMode = PhotoRepository.RemovalMode.SYSTEM_TRASH
            override suspend fun deletePhotos(uris: List<Uri>): PhotoRepository.DeleteResult = error("No deletion allowed")
            override suspend fun verifyTrashedPhotos(uris: List<Uri>): PhotoRepository.DeleteResult.Success = error("No verification allowed")
            override suspend fun loadAllPhotos(selectedFolders: Set<String>) = emptyList<Photo>()
            override suspend fun loadPhotosFromBucket(bucketId: Long) = emptyList<Photo>()
            override suspend fun getAllBuckets() = emptyMap<Long,String>()
            override suspend fun getStorageInfo() = StorageInfo(1,0,0,0)
            override suspend fun getPhotoCount() = 0
        }
        val settings = object : SettingsRepository {
            override fun getScanSettings() = flowOf(ScanSettings())
            override suspend fun saveScanSettings(settings: ScanSettings) = error("No preference writes")
            override suspend fun getCleanedBytesThisMonth() = 0L
            override suspend fun addCleanedBytes(bytes: Long) = error("No stats writes")
            override suspend fun resetMonthlyStats() = error("No stats writes")
        }
        try {
            activityRule.scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
                val vm = ResultsViewModel(RemovalCoordinator(repository,settings,QualityScorer(),store,null,OperationGate(),SessionClock(),SessionScope(scope)))
                activity.setContent { ResultsScreen(onGroupClick = {},onBack = {},viewModel = vm) }
            }
            await { nodes(instrument.uiAutomation.rootInActiveWindow).any { it.contentDescription?.toString() == "phase4-virtual-1" } }
            fun visibleImages() = nodes(instrument.uiAutomation.rootInActiveWindow).count { it.contentDescription?.toString()?.startsWith("phase4-virtual-") == true }
            assertTrue("Offscreen images composed", visibleImages() in 1..20)
            repeat(5) {
                val scroll = nodes(instrument.uiAutomation.rootInActiveWindow).firstOrNull { it.isScrollable }
                assertNotNull(scroll)
                val bounds = Rect().also { checkNotNull(scroll).getBoundsInScreen(it) }
                val downTime = SystemClock.uptimeMillis()
                fun touch(action: Int, fraction: Float) {
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                        bounds.exactCenterX(), bounds.top + bounds.height() * fraction, 0)
                    try { assertTrue(instrument.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
                }
                touch(MotionEvent.ACTION_DOWN, .85f)
                for (step in 1..12) { Thread.sleep(15); touch(MotionEvent.ACTION_MOVE, .85f - step * .05f) }
                touch(MotionEvent.ACTION_UP, .25f); Thread.sleep(500)
            }
            assertTrue(visibleImages() in 1..20)
            assertFalse(nodes(instrument.uiAutomation.rootInActiveWindow).any { it.contentDescription?.toString() == "phase4-virtual-1" })
            val sorted = frames.sorted()
            println("PHASE4 lazy20k visibleImages=${visibleImages()} frames=${sorted.size} frameP95Ms=${if (sorted.isEmpty()) -1.0 else sorted[((sorted.size - 1) * .95).toInt()] / 1_000_000.0}")
            // Separate detail screen must also avoid eagerly composing all member metadata.
            activityRule.scenario.onActivity { activity ->
                val vm = ResultsViewModel(RemovalCoordinator(repository,settings,QualityScorer(),store,null,OperationGate(),SessionClock(),SessionScope(scope)))
                activity.setContent { GroupDetailScreen("huge", {}, vm) }
            }
            await { nodes(instrument.uiAutomation.rootInActiveWindow).any { it.text?.toString()?.startsWith("Grup Detayı") == true } }
            await { visibleImages() > 0 }; assertTrue(visibleImages() <= 20)
        } finally {
            activityRule.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            scope.cancel()
        }
    }
}
