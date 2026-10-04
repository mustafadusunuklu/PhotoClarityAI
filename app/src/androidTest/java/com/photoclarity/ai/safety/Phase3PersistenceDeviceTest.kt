package com.photoclarity.ai.safety

import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.photoclarity.ai.core.session.SessionClock
import com.photoclarity.ai.data.local.db.PhotoClarityDatabase
import com.photoclarity.ai.data.local.db.SessionCodec
import com.photoclarity.ai.data.local.preferences.SettingsDataStore
import com.photoclarity.ai.data.repository.RoomScanSessionRepository
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.PhotoRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Isolated DB/DataStore only: never open, clear or replace the user's production database. */
@RunWith(AndroidJUnit4::class)
class Phase3PersistenceDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "phase3-${UUID.randomUUID()}.db"
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        PhotoClarityDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())
    private var db: PhotoClarityDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferenceFile get() = File(context.cacheDir, "$dbName.preferences_pb")
    private fun open(): PhotoClarityDatabase = Room.databaseBuilder(context, PhotoClarityDatabase::class.java, dbName)
        .addMigrations(PhotoClarityDatabase.MIGRATION_1_2).build().also { db = it }
    private fun group(): DuplicateGroup {
        val photos = (9001L..9003L).map { id ->
            val uri = Uri.parse("content://media/external/images/media/$id")
            Photo(id, uri, uri, "isolated-$id.png", "image/png", 12420, 100, 101, null, 64, 64, "isolated", 1,
                1.0, 2.0, qualityScore = .8f, sharpnessScore = 12f)
        }
        return DuplicateGroup("isolated-group", photos, DuplicateGroup.GroupType.EXACT_DUPLICATE, 1f, 9001, 24840)
    }
    @After fun cleanup() { db?.close(); scope.cancel(); context.deleteDatabase(dbName); preferenceFile.delete() }

    @Test fun migrationPreservesVersionOneHashCacheAndExistingPreferences(): Unit = runBlocking {
        val clock = SessionClock()
        val preferenceStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferenceFile })
        val settings = SettingsDataStore(context, clock, preferenceStore)
        val oldSettings = ScanSettings(hashAlgorithm = HashAlgorithm.SHA256, similarityThreshold = .91f, selectedFolders = setOf("isolated-test-folder"))
        settings.saveScanSettings(oldSettings); settings.addCleanedBytes(1234)
        migration.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO hash_cache VALUES ('content://media/external/images/media/9001', 'old-md5', 'old-sha', 12, 13, 14, 0.8, 12.0, 101, 12420, 123)")
            close()
        }
        migration.runMigrationsAndValidate(dbName, 2, true, PhotoClarityDatabase.MIGRATION_1_2).close()
        val database = open()
        assertEquals(1, database.hashCacheDao().count())
        assertEquals("old-sha", database.hashCacheDao().getValidCache("content://media/external/images/media/9001", 101, 12420)?.sha256Hash)
        assertEquals(oldSettings, settings.getScanSettings().firstValue())
        assertEquals(1234L, settings.getCleanedBytesThisMonth())
    }
    @Test fun reopenRestoresStableGroupsSelectionAndFrozenRequestButNotTrust(): Unit = runBlocking {
        val clock = SessionClock(); val store = RoomScanSessionRepository(open(), clock)
        store.initialize()
        val s = ScanSession("persisted-session", clock.now(), scopeKey = "FULL", settings = ScanSettings())
        store.begin(s); store.complete(s.copy(status = SessionStatus.COMPLETED, attempted = 10, matched = 3, failed = 2, endedAt = clock.now()), listOf(group()))
        store.select(s.id, group().photos.map { it.mediaKey }.toSet())
        val request = RemovalJournal("persisted-request", s.id, clock.now(), PhotoRepository.RemovalMode.SYSTEM_TRASH,
            group().photos.drop(1), RemovalStatus.WAITING_SYSTEM, issuedKeys = setOf(group().photos[1].mediaKey))
        store.journal(request)
        db!!.close()
        val restored = RoomScanSessionRepository(open(), clock); restored.initialize()
        assertTrue(restored.state.value.ready); assertFalse(restored.state.value.trusted)
        assertTrue(restored.state.value.visibleGroups.isEmpty())
        assertEquals("isolated-group", restored.state.value.groups.single().id)
        assertEquals(setOf(group().photos[1].mediaKey, group().photos[2].mediaKey), restored.state.value.selectedKeys)
        assertEquals(request.copy(photos = request.photos.map { it.copy(latitude = null, longitude = null) }), restored.state.value.removal)
        assertEquals(10, restored.state.value.session?.attempted); assertEquals(3, restored.state.value.session?.matched)
        assertNull(restored.state.value.groups.single().photos.first().latitude)
    }
    @Test fun incompleteCheckpointBecomesInterruptedOnReopen(): Unit = runBlocking {
        val clock = SessionClock(); val store = RoomScanSessionRepository(open(), clock); store.initialize()
        store.begin(ScanSession("running-session", clock.now(), scopeKey = "FULL", settings = ScanSettings(), discovered = 20, attempted = 7, durationMillis = 400))
        db!!.close()
        val restored = RoomScanSessionRepository(open(), clock); restored.initialize()
        assertEquals(SessionStatus.INTERRUPTED, restored.state.value.session?.status)
        assertEquals(SessionError.PROCESS_INTERRUPTED, restored.state.value.session?.error)
        assertEquals(7, restored.state.value.session?.attempted)
        assertEquals(400L, restored.state.value.session?.durationMillis)
    }
    @Test fun atomicRequestCreditIsIdempotentAndPreservesPreferencesAcrossMonth(): Unit = runBlocking {
        val fixed = object : SessionClock() { var month = 202609; override fun monthKey(at: Long) = month }
        val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferenceFile })
        val settings = SettingsDataStore(context, fixed, preferences)
        settings.saveScanSettings(ScanSettings(similarityThreshold = .93f)); settings.addCleanedBytes(1200)
        settings.addCleanedBytesOnce("request-a", 300, 202609); settings.addCleanedBytesOnce("request-a", 300, 202609)
        assertEquals(1500L, settings.getCleanedBytesThisMonth())
        settings.resetMonthlyStats(); settings.addCleanedBytesOnce("request-a", 300, 202609)
        assertEquals(0L, settings.getCleanedBytesThisMonth())
        fixed.month = 202610
        settings.addCleanedBytesOnce("request-a", 300, 202609)
        assertEquals(0L, settings.getCleanedBytesThisMonth())
        settings.addCleanedBytesOnce("request-b", 400, 202610)
        assertEquals(400L, settings.getCleanedBytesThisMonth())
        settings.addCleanedBytesOnce("request-a", 300, 202610) // Replay even if timezone changes the derived operation month.
        assertEquals(400L, settings.getCleanedBytesThisMonth())
        assertEquals(.93f, settings.getScanSettings().firstValue().similarityThreshold)
    }
    @Test fun codecRetainsSettingsSnapshotWithoutPhotoBytesOrGps() {
        val session = ScanSession("codec-session", 100, scopeKey = "LIMITED:test-fingerprint", settings = ScanSettings(selectedFolders = setOf("A", "B"), minFileSizeBytes = 999))
        assertEquals(session, SessionCodec.session(SessionCodec.session(session)))
        val photo = group().photos.first()
        val restored = SessionCodec.photo(SessionCodec.photo(photo))
        assertEquals(photo.mediaKey, restored.mediaKey); assertEquals(photo.dateModified, restored.dateModified)
        assertNull(restored.latitude); assertNull(restored.longitude)
    }
    @Test fun clockRollbackStillKeepsNewSessionAsCurrent(): Unit = runBlocking {
        val clock = SessionClock(); val store = RoomScanSessionRepository(open(),clock); store.initialize()
        val first = ScanSession("before-clock-rollback",200,scopeKey = "FULL",settings = ScanSettings())
        store.begin(first); store.complete(first.copy(status = SessionStatus.COMPLETED),listOf(group()))
        val next = ScanSession("after-clock-rollback",100,scopeKey = "FULL",settings = ScanSettings())
        store.begin(next)
        assertEquals(next.id,store.state.value.session?.id); assertTrue(store.state.value.groups.isEmpty())
        store.complete(next.copy(status = SessionStatus.COMPLETED),emptyList())
        db!!.close()
        val reopened = RoomScanSessionRepository(open(),clock); reopened.initialize()
        assertEquals(next.id,reopened.state.value.session?.id)
        assertEquals(listOf(next.id,first.id),reopened.state.value.history.map { it.id })
    }
    @Test fun executionSnapshotNeverUsesLegacyDisplayFallbackOnReadFailure(): Unit = runBlocking {
        val failed = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data: kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> =
                kotlinx.coroutines.flow.flow { throw java.io.IOException("Isolated unreadable preferences") }
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences =
                error("No writes allowed")
        }
        val settings = SettingsDataStore(context,SessionClock(),failed)
        assertEquals(ScanSettings(),settings.getScanSettings().first()) // Existing display behavior only.
        try { settings.getScanSettingsSnapshot(); fail("Execution must propagate read failure") }
        catch (expected: java.io.IOException) { assertEquals("Isolated unreadable preferences",expected.message) }
    }
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()
}
