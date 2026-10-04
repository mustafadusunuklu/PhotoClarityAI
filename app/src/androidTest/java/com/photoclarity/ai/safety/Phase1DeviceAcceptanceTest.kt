package com.photoclarity.ai.safety

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photoclarity.ai.MainActivity
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.core.analysis.PhotoAnalyzer
import com.photoclarity.ai.core.analysis.BurstDetector
import com.photoclarity.ai.data.local.db.PhotoClarityDatabase
import androidx.room.Room
import com.photoclarity.ai.core.hash.*
import com.photoclarity.ai.core.media.*
import com.photoclarity.ai.core.util.*
import com.photoclarity.ai.data.repository.PhotoRepositoryImpl
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.*
import com.photoclarity.ai.ui.results.*
import com.photoclarity.ai.testing.MemorySessionRepository
import com.photoclarity.ai.core.session.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import com.photoclarity.ai.ui.theme.PhotoClarityTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Compose, ActivityResult, MediaStore and Android codecs. Only test-created media is mutated. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
@android.annotation.TargetApi(30)
@RunWith(AndroidJUnit4::class)
class Phase1DeviceAcceptanceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private lateinit var activity: MainActivity
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val resolver get() = context.contentResolver
    private val folder = "PhotoClarityAI_Phase1_${UUID.randomUUID()}"
    private val created = mutableListOf<Uri>()
    private lateinit var photos: List<Photo>
    private lateinit var realRepo: PhotoRepositoryImpl
    private lateinit var vm: ResultsViewModel
    private val stats = MemoryStats()
    private var sessions = MemorySessionRepository()
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before fun setup() {
        require(android.os.Build.VERSION.SDK_INT >= 30)
        activityRule.scenario.onActivity { activity = it }
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        val storage = StorageUtils(context)
        realRepo = PhotoRepositoryImpl(context,MediaStoreScanner(context,storage),storage,MediaRemovalPlatform(context))
        val fixture = Phase1SyntheticMedia.png()
        assertEquals(12420,fixture.size)
        assertEquals("4771c5d32bf6255bc53cff931045cab5247ec67944d243cc60fab0b1e00b4f55",
            java.security.MessageDigest.getInstance("SHA-256").digest(fixture).joinToString("") { "%02x".format(it) })
        repeat(4) { index ->
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"$folder-$index.png")
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/$folder/")
                put(MediaStore.Images.Media.IS_PENDING,1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values))
            created.add(uri)
            checkNotNull(resolver.openOutputStream(uri)).use { out ->
                out.write(fixture)
            }
            resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
        }
        photos = runBlocking { realRepo.loadAllPhotos(setOf(folder)) }.sortedBy { it.id }
        assertEquals(4,photos.size)
        assertTrue(photos.all { it.displayName.startsWith(folder) && it.sizeBytes > 10*1024 })
        bind(realRepo)
    }

    private fun bind(repo: PhotoRepository, type: DuplicateGroup.GroupType = DuplicateGroup.GroupType.EXACT_DUPLICATE, keeperId: Long = photos.first().id) {
        instrumentation.runOnMainSync {
            sessions = MemorySessionRepository(listOf(DuplicateGroup("device-$folder",photos,type,1f,keeperId,photos.drop(1).sumOf { it.sizeBytes })))
            vm = ResultsViewModel(RemovalCoordinator(repo,stats,QualityScorer(),sessions,null,OperationGate(),SessionClock(),SessionScope(testScope)))
            activity.setContent { PhotoClarityTheme { ResultsScreen({}, {}, vm) } }
        }
        instrumentation.waitForIdleSync()
    }

    @After fun cleanup() {
        // Specific URI, specific run prefix, own test-created media only. No gallery-wide cleanup.
        for (uri in created) {
            val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_INCLUDE) }
            resolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),args,null)?.use {
                if (it.moveToFirst()) {
                    check(it.getString(0).startsWith(folder))
                    resolver.delete(uri,null,null)
                }
            }
        }
        testScope.cancel()
        runBlocking { ProductionTestSessions.cleanup(context, folder) }
    }

    // No Espresso hidden input API: Android 17 removed InputManager.getInstance().
    private fun waitUntil(timeout: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) fail("Condition timed out")
            Thread.sleep(100)
        }
    }
    private fun awaitText(text: String): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        try { waitUntil(10000) {
            found = nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
                it.packageName?.toString() == context.packageName &&
                    (it.text?.toString() == text || it.contentDescription?.toString() == text)
            }
            found != null
        } } catch (failure: AssertionError) {
            evidence("failed-await-text")
            val tree = nodes(instrumentation.uiAutomation.rootInActiveWindow)
                .filter { it.packageName?.toString() == context.packageName || it.packageName?.toString()?.contains("providers.media") == true }
                .joinToString("\n") { "${it.text} | ${it.contentDescription} | ${it.viewIdResourceName}" }
            fail("Missing text '$text'; active controlled tree:\n$tree")
        }
        return checkNotNull(found)
    }
    private fun clickText(text: String) {
        var node = awaitText(text)
        while (!node.isClickable && node.parent != null) node = node.parent
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        instrumentation.waitForIdleSync()
    }

    private fun bulkSelect() {
        clickText("Korunacaklar Dışındakileri Seç")
        waitUntil(5000) { vm.uiState.value.selectedPhotoCount == photos.size - 1 }
        assertFalse(vm.uiState.value.selectedPhotoIds.contains(photos.first().id))
    }
    private fun appConfirm() {
        clickText("Çöp Kutusu (${photos.size - 1})")
        awaitText("Sistem Çöp Kutusuna Taşı")
        clickText("Sistem Onayına Geç")
    }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (node == null) return emptyList()
        return listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    }
    private fun systemPrompt(approve: Boolean) {
        val automation = instrumentation.uiAutomation
        val deadline = System.currentTimeMillis()+15000
        while (System.currentTimeMillis() < deadline) {
            val all = nodes(automation.rootInActiveWindow)
            val system = all.filter { it.packageName?.toString()?.let { p -> p.contains("providers.media") || p.contains("permissioncontroller") } == true }
            if (system.isNotEmpty()) {
                val snapshot = system.joinToString("\n") { "${it.viewIdResourceName} | ${it.text} | clickable=${it.isClickable}" }
                val id = if (approve) "button1" else "button2"
                val alternative = if (approve) "permission_allow_button" else "permission_deny_button"
                val target = system.firstOrNull { it.viewIdResourceName?.let { res -> res.endsWith("/$id") || res.endsWith("/$alternative") } == true }
                    ?: system.firstOrNull { it.isClickable && it.text?.toString()?.let { text ->
                        if (approve) text.equals("Move to trash",true) || text.equals("Allow",true)
                        else text.equals("Cancel",true) || text.equals("Don't allow",true)
                    } == true }
                if (target != null) {
                    println("SYSTEM_PROMPT approve=$approve\n$snapshot")
                    evidence(if (approve) "system-approve" else "system-cancel")
                    assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    return
                }
                if (System.currentTimeMillis()+300 >= deadline) fail("Unrecognized system prompt:\n$snapshot")
            }
            Thread.sleep(150)
        }
        fail("System confirmation was not observed: "+nodes(automation.rootInActiveWindow).joinToString { "${it.packageName}:${it.text}" })
    }
    private fun evidence(name: String) {
        val dir = File(context.getExternalFilesDir(null),"phase1-evidence").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(dir,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    private fun awaitFinished() {
        waitUntil(15000) { !vm.uiState.value.isLoading }
        instrumentation.waitForIdleSync()
    }

    @Test fun keeperAndAppCancelNeverMutateMedia() {
        // UI click on protected photo and direct state guard both preserve the keeper.
        clickText("Korunacak")
        instrumentation.runOnMainSync { vm.togglePhotoSelection(photos.first().id,true) }
        assertTrue(vm.uiState.value.selectedPhotoIds.isEmpty())
        bulkSelect()
        clickText("Çöp Kutusu (${photos.size - 1})")
        clickText("İptal")
        assertFalse(vm.uiState.value.selectionLocked)
        assertEquals(4,runBlocking { realRepo.loadAllPhotos(setOf(folder)) }.size)
        created.forEach { assertFalse(MediaRemovalPlatform(context).isTrashed(it)) }
        evidence("keeper-app-cancel")
    }

    @Test fun systemCancelPreservesSelectionAndPhotos() {
        bulkSelect(); appConfirm(); systemPrompt(false); awaitFinished()
        assertEquals(3,vm.uiState.value.selectedPhotoCount)
        assertEquals(4,vm.uiState.value.groups.single().photos.size)
        assertNull(vm.uiState.value.pendingDeleteIntentSender)
        assertNotNull(vm.uiState.value.error)
        created.forEach { assertFalse(MediaRemovalPlatform(context).isTrashed(it)) }
        assertTrue(stats.bytes.isEmpty())
        evidence("system-cancel-result")
    }

    @Test fun manualSelectionCanBeReversedWithoutADeleteRequest() {
        clickText(photos[1].displayName)
        waitUntil(5000) { vm.uiState.value.selectedPhotoIds == setOf(photos[1].id) }
        clickText(photos[1].displayName)
        waitUntil(5000) { vm.uiState.value.selectedPhotoIds.isEmpty() }
        assertNull(vm.uiState.value.pendingDeleteIntentSender)
        created.forEach { assertFalse(MediaRemovalPlatform(context).isTrashed(it)) }
    }

    @Test fun systemApprovalTrashesOnlyFrozenNonKeepersAndCanRestoreThroughSystem() {
        bulkSelect(); appConfirm()
        instrumentation.runOnMainSync {
            vm.togglePhotoSelection(photos.first().id,true)
            vm.togglePhotoSelection(photos.last().id,false)
            vm.deleteSelectedPhotos()
        }
        systemPrompt(true); awaitFinished()
        assertNull(vm.uiState.value.error)
        assertEquals(3,vm.uiState.value.lastDeletedCount)
        assertTrue(vm.uiState.value.groups.isEmpty())
        assertFalse(MediaRemovalPlatform(context).isTrashed(photos.first().contentUri))
        photos.drop(1).forEach { assertTrue(MediaRemovalPlatform(context).isTrashed(it.contentUri)) }
        assertEquals(1,runBlocking { realRepo.loadAllPhotos(setOf(folder)) }.size)
        assertTrue(stats.bytes.isEmpty())
        evidence("system-trash-result")
        // System provider restoration only, using app ownership in test cleanup; not a product restore feature.
        photos.drop(1).forEach {
            assertEquals(1,resolver.update(it.contentUri,ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED,0) },null,null))
            assertFalse(MediaRemovalPlatform(context).isTrashed(it.contentUri))
        }
        assertEquals(4,runBlocking { realRepo.loadAllPhotos(setOf(folder)) }.size)
    }

    @Test fun missingUriVerificationAndInvalidCollectionFailClosed() = runBlocking {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val invalid = realRepo.deletePhotos(listOf(collection))
        assertTrue(invalid is PhotoRepository.DeleteResult.Error)
        val absent = ContentUris.withAppendedId(collection,Long.MAX_VALUE)
        val verified = realRepo.verifyTrashedPhotos(listOf(photos.first().contentUri,absent))
        assertTrue(verified.removedUris.isEmpty())
        assertEquals(setOf(photos.first().contentUri,absent),verified.failedUris)
        assertEquals(4,realRepo.loadAllPhotos(setOf(folder)).size)
    }

    @Test fun disappearingCandidateDuringSystemConsentIsNeverReportedAsTrashed() {
        bulkSelect(); appConfirm()
        waitUntil(15000) {
            nodes(instrumentation.uiAutomation.rootInActiveWindow).any {
                it.packageName?.toString()?.contains("providers.media") == true
            }
        }
        // Simulate an outside gallery change while consent is open; this is our own test URI only.
        val vanished = photos.last()
        assertEquals(1,resolver.delete(vanished.contentUri,null,null))
        systemPrompt(true); awaitFinished()
        val actuallyTrashed = photos.drop(1).filter { MediaRemovalPlatform(context).isTrashed(it.contentUri) }
        assertFalse(actuallyTrashed.any { it.id == vanished.id })
        assertEquals(actuallyTrashed.size,vm.uiState.value.lastDeletedCount)
        assertNotNull(vm.uiState.value.error)
        assertFalse(MediaRemovalPlatform(context).isTrashed(photos.first().contentUri))
        assertTrue(vm.uiState.value.selectedPhotoIds.contains(vanished.id))
        assertEquals(photos.map { it.id }.toSet() - actuallyTrashed.map { it.id }.toSet(),
            vm.uiState.value.groups.single().photos.map { it.id }.toSet())
        assertTrue(stats.bytes.isEmpty())
        println("DISAPPEARING_CANDIDATE actuallyTrashed=${actuallyTrashed.size}, reported=${vm.uiState.value.lastDeletedCount}, error=${vm.uiState.value.error}")
        evidence("disappearing-during-consent")
    }

    @Test fun mixedTrashedAndUnchangedRowsAreReconciledByUri() {
        val wrapper = object : PhotoRepository by realRepo {
            override suspend fun deletePhotos(uris: List<Uri>): PhotoRepository.DeleteResult {
                // Apply the controlled provider outcome after preflight/journal, like a real
                // partial side effect. Changing media before preflight must invalidate findings.
                assertEquals(setOf(photos[1].contentUri,photos[2].contentUri),uris.toSet())
                assertEquals(1,resolver.update(photos[1].contentUri,ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED,1) },null,null))
                val result = realRepo.verifyTrashedPhotos(uris)
                assertEquals(setOf(photos[1].contentUri),result.removedUris)
                assertEquals(setOf(photos[2].contentUri),result.failedUris)
                return result
            }
        }
        bind(wrapper)
        instrumentation.runOnMainSync {
            vm.togglePhotoSelection(photos[1].id,true); vm.togglePhotoSelection(photos[2].id,true)
            vm.requestDeleteConfirmation(); vm.deleteSelectedPhotos()
        }
        awaitFinished()
        assertEquals(setOf(photos[0].id,photos[2].id,photos[3].id),vm.uiState.value.groups.single().photos.map { it.id }.toSet())
        assertEquals(setOf(photos[2].id),vm.uiState.value.selectedPhotoIds)
        assertEquals(1,vm.uiState.value.lastDeletedCount)
        assertNotNull(vm.uiState.value.error)
        assertTrue(stats.bytes.isEmpty())
    }

    @Test fun lowQualityAndMissingKeeperHaveNoDeletionCandidates() {
        bind(realRepo,DuplicateGroup.GroupType.LOW_QUALITY)
        instrumentation.runOnMainSync { vm.smartSelectAll(); vm.togglePhotoSelection(photos[1].id,true) }
        assertTrue(vm.uiState.value.selectedPhotoIds.isEmpty())
        bind(realRepo, keeperId = -1)
        instrumentation.runOnMainSync { vm.smartSelectAll(); vm.togglePhotoSelection(photos[1].id,true) }
        assertTrue(vm.uiState.value.selectedPhotoIds.isEmpty())
        assertEquals(4,runBlocking { realRepo.loadAllPhotos(setOf(folder)) }.size)
    }

    @Test fun realAndroidDecoderAndAllHashersCanReadSyntheticMedia() = runBlocking {
        val utils = BitmapUtils(context)
        val bitmap = checkNotNull(utils.decodeSampledBitmap(photos.first().contentUri,256,256))
        try { assertTrue(utils.computeSharpness(bitmap) >= 0f) } finally { bitmap.recycle() }
        for (p in photos) {
            assertNotNull(AverageHasher(context,utils).computeAHash(p.contentUri))
            assertNotNull(DifferenceHasher(context,utils).computeDHash(p.contentUri))
        }
        val crypto = CryptographicHasher(context)
        assertEquals(crypto.computeSha256(photos[0].contentUri),crypto.computeSha256(photos[1].contentUri))
        assertNotNull(PerceptualHasher(context,utils).computePHash(photos.first().contentUri))
    }

    @Test fun nonOwnedPhotoRequiresSystemConsentAndIsVerifiedAfterTrash() {
        val args = InstrumentationRegistry.getArguments()
        val externalUriString = args.getString("externalUri")
        val externalFolder = args.getString("externalFolder")
        Assume.assumeTrue("External fixture arguments required",externalUriString != null && externalFolder != null)
        val external = runBlocking { realRepo.loadAllPhotos(setOf(externalFolder!!)) }.single()
        val expectedUri = Uri.parse(externalUriString)
        assertEquals(ContentUris.parseId(expectedUri),external.id)
        resolver.query(expectedUri,arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME),null,null,null)!!.use {
            assertTrue(it.moveToFirst())
            assertNotEquals(context.packageName,it.getString(0))
            println("EXTERNAL_FIXTURE owner="+it.getString(0))
        }
        photos = listOf(photos.first(),external)
        bind(realRepo)
        bulkSelect(); appConfirm(); systemPrompt(true); awaitFinished()
        println("EXTERNAL_VERIFICATION removed=${vm.uiState.value.lastDeletedCount}, error=${vm.uiState.value.error}")
        assertNull(vm.uiState.value.error)
        assertEquals(1,vm.uiState.value.lastDeletedCount)
        assertTrue(MediaRemovalPlatform(context).isTrashed(external.contentUri))
        assertFalse(MediaRemovalPlatform(context).isTrashed(photos.first().contentUri))
        assertTrue(stats.bytes.isEmpty())
        evidence("nonowned-trash-result")
    }

    @Test fun realAnalyzerAndNavigationPreserveSelectionAfterActivityRecreation() {
        // Real codecs + analyzer + Room, isolated cache; never scan the user's whole archive.
        val db = Room.inMemoryDatabaseBuilder(context,PhotoClarityDatabase::class.java).build()
        val bitmap = BitmapUtils(context)
        val analyzer = PhotoAnalyzer(CryptographicHasher(context),PerceptualHasher(context,bitmap),
            AverageHasher(context,bitmap),DifferenceHasher(context,bitmap),
            QualityScorer(),BurstDetector(),bitmap,db.hashCacheDao())
        try {
            val groups = runBlocking { analyzer.analyze(photos,
                ScanSettings(visualSimilarityEnabled=false,detectBurstShots=false),MutableSharedFlow()) }
            assertEquals(1,groups.size)
            assertEquals(DuplicateGroup.GroupType.EXACT_DUPLICATE,groups.single().groupType)
            assertEquals(4,groups.single().photos.size)
            assertTrue(groups.single().recommendedPhoto != null)
            openRealResults(groups)
            clickText("Korunacaklar Dışındakileri Seç")
            awaitText("Çöp Kutusu (3)")
            activityRule.scenario.recreate()
            activityRule.scenario.onActivity { activity = it }
            awaitText("Çöp Kutusu (3)")
            clickText("Çöp Kutusu (3)")
            awaitText("Sistem Çöp Kutusuna Taşı")
            clickText("İptal")
            assertEquals(4,runBlocking { realRepo.loadAllPhotos(setOf(folder)) }.size)
            created.forEach { assertFalse(MediaRemovalPlatform(context).isTrashed(it)) }
            evidence("real-navigation-recreated")
        } finally { db.close() }
    }

    @Test fun systemConsentSurvivesLandscapeActivityRecreation() {
        openRealResults(sessions.state.value.groups)
        clickText("Korunacaklar Dışındakileri Seç")
        appConfirm()
        val beforeRotation = activity
        // Configuration change during real system consent, using production NavHost/Hilt state.
        instrumentation.runOnMainSync {
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        waitUntil(15000) {
            context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        systemPrompt(true)
        waitUntil(15000) { runBlocking { realRepo.loadAllPhotos(setOf(folder)) }.size == 1 }
        assertFalse(MediaRemovalPlatform(context).isTrashed(photos.first().contentUri))
        photos.drop(1).forEach { assertTrue(MediaRemovalPlatform(context).isTrashed(it.contentUri)) }
        // Landscape snackbar covers the subtitle: assert the visible empty title and actual outcome.
        awaitText("Bu taramada grup kalmadı")
        assertTrue(ProductionTestSessions.state(context).visibleGroups.isEmpty())
        evidence("system-consent-landscape-result")
        activityRule.scenario.onActivity {
            assertNotSame(beforeRotation,it)
            activity = it
            it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun openRealResults(groups: List<DuplicateGroup>) {
        runBlocking { ProductionTestSessions.seed(context, groups, folder) }
        activityRule.scenario.recreate() // Actual MainActivity / NavHost / Hilt ViewModels.
        activityRule.scenario.onActivity { activity = it }
        var shortcut: AccessibilityNodeInfo? = null
        repeat(8) {
            if (shortcut == null) {
                val all = nodes(instrumentation.uiAutomation.rootInActiveWindow)
                shortcut = all.firstOrNull { it.text?.toString() == "Kopyalar" }
                if (shortcut == null) {
                all.firstOrNull { it.isScrollable && it.packageName?.toString() == context.packageName }
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(400)
                }
            }
        }
        clickText("Kopyalar")
    }

    private class MemoryStats : SettingsRepository {
        val bytes = mutableListOf<Long>()
        override fun getScanSettings() = flowOf(ScanSettings())
        override suspend fun saveScanSettings(settings: ScanSettings) = Unit
        override suspend fun getCleanedBytesThisMonth() = bytes.sum()
        override suspend fun addCleanedBytes(bytes: Long) { this.bytes.add(bytes) }
        override suspend fun resetMonthlyStats() { bytes.clear() }
    }
}
