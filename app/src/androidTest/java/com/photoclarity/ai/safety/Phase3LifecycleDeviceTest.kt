package com.photoclarity.ai.safety

import android.accessibilityservice.AccessibilityService
import android.net.Uri
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photoclarity.ai.MainActivity
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.core.media.PhotoAccessManager
import com.photoclarity.ai.core.session.*
import com.photoclarity.ai.data.local.db.PhotoClarityDatabase
import com.photoclarity.ai.data.repository.RoomScanSessionRepository
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.*
import com.photoclarity.ai.ui.scan.ScanViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Real ProcessLifecycleOwner + Activity rotation/Home; controlled analysis, isolated Room. */
@RunWith(AndroidJUnit4::class)
class Phase3LifecycleDeviceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private fun waitUntil(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 15000
        while (!condition()) { if (System.currentTimeMillis() > until) fail("Lifecycle condition timed out"); Thread.sleep(100) }
    }
    @Test fun rotationKeepsOneOwnerAndHomePersistsInterruption() {
        val name = "phase3-lifecycle-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context,PhotoClarityDatabase::class.java,name).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val clock = SessionClock(); val store = RoomScanSessionRepository(db,clock)
        val uri = Uri.parse("content://media/external_primary/images/media/999999")
        val catalog = listOf(Photo(999999,uri,uri,"isolated.png","image/png",12420,1,1,null,64,64,"isolated",1,null,null))
        val repository = object : PhotoRepository {
            override val removalMode = PhotoRepository.RemovalMode.SYSTEM_TRASH
            override suspend fun verifyTrashedPhotos(uris: List<Uri>): PhotoRepository.DeleteResult.Success = error("No verification in lifecycle test")
            override suspend fun arePhotosCurrent(photos: List<Photo>) = true
            override suspend fun loadAllPhotos(selectedFolders: Set<String>) = catalog
            override suspend fun loadPhotosFromBucket(bucketId: Long) = catalog
            override suspend fun getAllBuckets() = emptyMap<Long,String>()
            override suspend fun getStorageInfo() = StorageInfo(1,0,1,12420)
            override suspend fun getPhotoCount() = 1
            override suspend fun deletePhotos(uris: List<Uri>): PhotoRepository.DeleteResult = error("No deletion in lifecycle test")
        }
        val settings = object : SettingsRepository {
            override fun getScanSettings() = flowOf(ScanSettings())
            override suspend fun saveScanSettings(settings: ScanSettings) = Unit
            override suspend fun getCleanedBytesThisMonth() = 0L
            override suspend fun addCleanedBytes(bytes: Long) = error("No credit in lifecycle test")
            override suspend fun resetMonthlyStats() = Unit
        }
        lateinit var coordinator: ScanCoordinator
        var observer: DefaultLifecycleObserver? = null
        val calls = AtomicInteger(); var progress: kotlinx.coroutines.flow.MutableSharedFlow<ScanProgress>? = null
        try {
            activityRule.scenario.onActivity { activity ->
                val gate = OperationGate(); val runtime = SessionScope(scope)
                val engine = ScanAnalysisEngine { _, _, p -> calls.incrementAndGet(); progress = p; awaitCancellation() }
                coordinator = ScanCoordinator(store,repository,settings,engine,activity.photoAccess,
                    RemovalCoordinator(repository,settings,QualityScorer(),store,activity.photoAccess,gate,clock,runtime),gate,clock,runtime)
                observer = object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = coordinator.setVisible(true)
                    override fun onStop(owner: LifecycleOwner) = coordinator.setVisible(false)
                }
                ProcessLifecycleOwner.get().lifecycle.addObserver(checkNotNull(observer)); coordinator.initialize()
            }
            waitUntil { store.state.value.ready }
            var accepted = false
            waitUntil { instrument.runOnMainSync { accepted = coordinator.start() }; accepted }
            waitUntil { calls.get() == 1 }
            instrument.runOnMainSync {
                assertFalse(coordinator.start())
                assertSame(ScanViewModel(coordinator).uiState,ScanViewModel(coordinator).uiState)
            }
            activityRule.scenario.recreate(); Thread.sleep(1000)
            assertEquals(1,calls.get()); assertTrue(coordinator.state.value.isScanning)
            assertEquals(SessionStatus.RUNNING,store.state.value.session?.status)
            assertTrue(instrument.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            waitUntil { store.state.value.session?.status == SessionStatus.INTERRUPTED }
            assertEquals(SessionError.BACKGROUND,store.state.value.session?.error)
            assertFalse(coordinator.state.value.isScanning)
            waitUntil { progress?.subscriptionCount?.value == 0 }
            instrument.runOnMainSync { coordinator.setVisible(true) }
            assertFalse(coordinator.state.value.isScanning); assertEquals(1,calls.get())
        } finally {
            instrument.runOnMainSync { observer?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }; scope.cancel() }
            db.close(); context.deleteDatabase(name)
        }
    }
}
