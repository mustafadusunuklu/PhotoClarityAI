package com.photoclarity.ai.safety

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photoclarity.ai.core.analysis.*
import com.photoclarity.ai.core.hash.*
import com.photoclarity.ai.core.session.SessionClock
import com.photoclarity.ai.core.media.MediaStoreScanner
import com.photoclarity.ai.core.media.MediaRemovalPlatform
import com.photoclarity.ai.core.util.StorageUtils
import com.photoclarity.ai.data.repository.PhotoRepositoryImpl
import com.photoclarity.ai.core.util.BitmapUtils
import com.photoclarity.ai.data.local.db.PhotoClarityDatabase
import com.photoclarity.ai.data.repository.RoomScanSessionRepository
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.PhotoRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Own generated app-private files and isolated DB. Never scans or deletes the user's gallery. */
@RunWith(AndroidJUnit4::class)
class Phase4ScaleDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = "phase4-${UUID.randomUUID()}"
    private val directory by lazy { File(context.cacheDir, token).also { check(it.mkdir()) } }
    private val db by lazy { Room.databaseBuilder(context, PhotoClarityDatabase::class.java, "$token.db").build() }
    private val bitmaps by lazy { BitmapUtils(context) }
    private val crypto by lazy { CryptographicHasher(context) }
    private val pHash by lazy { PerceptualHasher(context, bitmaps) }
    private val aHash by lazy { AverageHasher(context, bitmaps) }
    private val dHash by lazy { DifferenceHasher(context, bitmaps) }
    @After fun cleanup() {
        db.close(); context.deleteDatabase("$token.db")
        val testLock = File(context.cacheDir, "$token.db.lck")
        check(!testLock.exists() || testLock.delete())
        check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile && directory.name == token)
        directory.deleteRecursively()
    }
    private fun pattern(seed: Int, size: Int = 512): Bitmap {
        val random = Random(seed)
        val pixels = IntArray(32 * 32) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        val tiny = Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(tiny, size, size, true).also { if (it !== tiny) tiny.recycle() }
    }
    private fun jpeg(bitmap: Bitmap, name: String, quality: Int = 95): File = File(directory, name).also { file ->
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) }
    }
    private fun photo(file: File, id: Long, width: Int = 512, height: Int = width): Photo {
        val uri = Uri.fromFile(file)
        return Photo(id, uri, uri, file.name, "image/jpeg", file.length(), 100, 101, null, width, height, "generated", 1, null, null)
    }
    @Test fun decoderBoundsPanoramaAndRejectsTruncatedInput(): Unit = runBlocking {
        for ((w, h) in listOf(20_000 to 16, 16 to 20_000, 4096 to 4096)) {
            val source = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            val file = try { jpeg(source, "$w-$h.jpg") } finally { source.recycle() }
            val decoded = checkNotNull(bitmaps.decodeSampledBitmap(Uri.fromFile(file), 256, 256))
            try { assertTrue(decoded.width <= 256 && decoded.height <= 256); assertTrue(decoded.width.toLong() * decoded.height <= 65536) }
            finally { decoded.recycle() }
        }
        val bad = File(directory, "truncated.jpg").also { it.writeBytes(byteArrayOf(-1, -40, 1, 2, 3)) }
        assertNull(bitmaps.decodeSampledBitmap(Uri.fromFile(bad), 256, 256))
    }
    @Test fun decoderNormalizesEveryExifOrientationBeforeHashing(): Unit = runBlocking {
        val source = pattern(444, 128)
        val base = jpeg(source, "base.jpg"); source.recycle()
        val decoded = checkNotNull(bitmaps.decodeSampledBitmap(Uri.fromFile(base), 256, 256))
        try {
            for (orientation in 1..8) {
                val file = File(directory, "orientation-$orientation.jpg"); base.copyTo(file)
                ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
                val exif = ExifInterface(file)
                val matrix = Matrix().apply { if (exif.isFlipped) postScale(-1f, 1f); postRotate(exif.rotationDegrees.toFloat()) }
                val expected = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                val actual = checkNotNull(bitmaps.decodeSampledBitmap(Uri.fromFile(file), 256, 256))
                try {
                    assertTrue("orientation=$orientation", expected.sameAs(actual))
                    assertEquals(pHash.computeFromBitmap(expected), pHash.computeFromBitmap(actual))
                    assertFalse(actual.isRecycled)
                } finally { actual.recycle(); if (expected !== decoded) expected.recycle() }
            }
        } finally { decoded.recycle() }
    }
    @Test fun syntheticLabelledCalibrationReportsEachAlgorithmAndThreshold(): Unit = runBlocking {
        val originals = (0 until 40).map { seed ->
            val bitmap = pattern(seed)
            try { jpeg(bitmap, "base-$seed.jpg", 95) to jpeg(bitmap, "near-$seed.jpg", 65) } finally { bitmap.recycle() }
        }
        suspend fun features(file: File): LongArray {
            val bitmap = checkNotNull(bitmaps.decodeSampledBitmap(Uri.fromFile(file), 256, 256))
            try { return longArrayOf(checkNotNull(pHash.computeFromBitmap(bitmap)), checkNotNull(aHash.computeFromBitmap(bitmap)), checkNotNull(dHash.computeFromBitmap(bitmap))) }
            finally { bitmap.recycle() }
        }
        val base = originals.map { features(it.first) }; val near = originals.map { features(it.second) }
        val positive = base.indices.map { it to it }; val negative = base.indices.flatMap { a -> (a + 1 until base.size).map { b -> a to b } }
        for (algorithm in 0..2) for (threshold in listOf(.70f, .85f, .90f, .95f, .99f)) {
            val bits = if (algorithm == 0) 63 else 64
            fun similar(a: Long, b: Long) = 1f - (a xor b).countOneBits() / bits.toFloat() >= threshold
            val tp = positive.count { (a, b) -> similar(base[a][algorithm], near[b][algorithm]) }
            val fp = negative.count { (a, b) -> similar(base[a][algorithm], base[b][algorithm]) }
            println("PHASE4 calibration algorithm=${listOf("pHash", "aHash", "dHash")[algorithm]} threshold=$threshold tp=$tp fn=${40 - tp} fp=$fp tn=${780 - fp}")
        }
        // Golden exact pair is byte-identical; lossy re-encoding is deliberately not an exact match.
        assertEquals(crypto.computeSha256(Uri.fromFile(originals[0].first)), crypto.computeSha256(Uri.fromFile(originals[0].first)))
        assertNotEquals(crypto.computeSha256(Uri.fromFile(originals[0].first)), crypto.computeSha256(Uri.fromFile(originals[0].second)))
    }
    @Test fun journalCheckpointsUpdateFlagsWithoutReplacingFrozenItems(): Unit = runBlocking {
        val store = RoomScanSessionRepository(db, SessionClock()); store.initialize()
        val photos = (1L..2001L).map { id -> photo(File(directory, "not-read-$id.jpg"), id) }
        val request = RemovalJournal("r", "s", 100, PhotoRepository.RemovalMode.SYSTEM_TRASH, photos, RemovalStatus.WAITING_SYSTEM,
            issuedKeys = photos.take(2000).map { it.mediaKey }.toSet())
        store.journal(request)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE isolated_item_audit (writes INTEGER NOT NULL)")
        sql.execSQL("INSERT INTO isolated_item_audit VALUES (0)")
        sql.execSQL("CREATE TRIGGER isolated_item_insert AFTER INSERT ON removal_items BEGIN UPDATE isolated_item_audit SET writes = writes + 1; END")
        sql.execSQL("CREATE TRIGGER isolated_item_delete AFTER DELETE ON removal_items BEGIN UPDATE isolated_item_audit SET writes = writes + 1; END")
        val second = request.copy(issuedKeys = setOf(photos.last().mediaKey), removedKeys = request.issuedKeys)
        store.journal(second)
        sql.query("SELECT writes FROM isolated_item_audit").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        val rows = db.scanSessionDao().items(request.id)
        assertEquals(2001, rows.size); assertEquals(2000, rows.count { it.removed }); assertEquals(1, rows.count { it.issued })
        assertEquals(photos.map { it.mediaKey }, rows.map { it.mediaKey })
    }
    @Test fun metadataPagesRespectSizeAndFrozenQueriesDetectMissingOrChangedRows(): Unit = runBlocking {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 30)
        val resolver = context.contentResolver
        val created = ArrayList<Uri>()
        check(com.photoclarity.ai.core.media.PhotoAccessManager.current(context) == com.photoclarity.ai.core.media.PhotoAccess.FULL) {
            "Preset FULL read access outside instrumentation; record and restore the original grants."
        }
        try {
            val bytes = Phase1SyntheticMedia.png()
            repeat(600) { index ->
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$token-$index.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$token/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
                created.add(uri)
                checkNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            val storage = StorageUtils(context); val scanner = MediaStoreScanner(context, storage)
            val repo = PhotoRepositoryImpl(context, scanner, storage, MediaRemovalPlatform(context))
            val pages = ArrayList<List<Photo>>()
            repo.loadPhotoPages(ScanSettings(selectedFolders = setOf(token), minFileSizeBytes = 10_000)).collect { pages.add(it) }
            assertEquals(listOf(256, 256, 88), pages.map { it.size })
            val catalog = pages.flatten()
            assertEquals(600, catalog.size); assertTrue(repo.arePhotosCurrent(catalog))
            val empty = ArrayList<Photo>()
            repo.loadPhotoPages(ScanSettings(selectedFolders = setOf(token), minFileSizeBytes = bytes.size.toLong())).collect { empty.addAll(it) }
            assertTrue(empty.isEmpty())
            // Only our owned fixtures are modified here. Production still requires app + OS consent.
            val trashed = catalog.take(270).map { it.contentUri }.toSet()
            trashed.forEach { resolver.update(it, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null) }
            assertEquals(trashed, repo.verifyTrashedPhotos(catalog.map { it.contentUri }).removedUris)
            assertFalse(repo.arePhotosCurrent(catalog))
            trashed.forEach { resolver.update(it, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }, null, null) }
            assertTrue(repo.verifyTrashedPhotos(catalog.map { it.contentUri }).removedUris.isEmpty())
            assertFalse(repo.arePhotosCurrent(catalog)) // Generation changed after restore.
            val fresh = scanner.scanAllPhotos(selectedFolders = setOf(token))
            assertTrue(repo.arePhotosCurrent(fresh))
            resolver.delete(fresh.last().contentUri, null, null)
            assertFalse(repo.arePhotosCurrent(fresh))
        } finally {
            // No broad directory/collection deletion. Every URI was inserted by this test invocation.
            created.forEach { uri ->
                val args = android.os.Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.OWNER_PACKAGE_NAME), args, null)?.use { row ->
                    if (row.moveToFirst()) {
                        check(row.getString(0).startsWith("$token-") && row.getString(1) == context.packageName)
                        resolver.delete(uri, null, null)
                    }
                }
            }
        }
    }
    @Test fun runningWideRadiusCpuMatchCancelsWithinTwoSeconds(): Unit = runBlocking {
        val random = Random(414)
        val records = List(30_000) { VisualRecord("uri:$it", random.nextLong() and Long.MAX_VALUE, 1f) }
        val started = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            val owner = currentCoroutineContext()
            started.complete(Unit)
            KeeperMatcher.match(records, 63, .70f) { owner.ensureActive() }
        }
        started.await(); delay(50)
        val start = SystemClock.elapsedRealtime()
        job.cancelAndJoin()
        val elapsed = SystemClock.elapsedRealtime() - start
        assertTrue("CPU cancellation took $elapsed ms", elapsed <= 2000)
        println("PHASE4 cpuCancellationMs=$elapsed")
    }
    @Test fun realTwentyThousandFilePipelineColdAndWarm(): Unit = runBlocking {
        // Duplicate-heavy, generated JPEG stress; not a representative real-photo or physical-device budget claim.
        val count = InstrumentationRegistry.getArguments().getString("phase4Count")?.toInt() ?: 20_000
        require(count >= 40 && count % 40 == 0)
        val sources = (0 until 40).map { seed ->
            val bitmap = pattern(seed, 1440)
            try { jpeg(bitmap, "source-$seed.jpg", 65) } finally { bitmap.recycle() }
        }
        val requiredBytes = sources.sumOf { it.length() } * (count / 40)
        check(requiredBytes < directory.usableSpace - 512L * 1024 * 1024) { "Insufficient disk space for scoped generated copies" }
        val photos = List(count) { index ->
            val file = File(directory, "item-$index.jpg")
            sources[index % sources.size].copyTo(file)
            photo(file, index.toLong() + 1, 1440)
        }
        val analyzer = PhotoAnalyzer(crypto, pHash, aHash, dHash, QualityScorer(), BurstDetector(), bitmaps, db.hashCacheDao())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val peak = AtomicInteger()
        val sampler = scope.launch { while (isActive) { val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info); peak.updateAndGet { maxOf(it, info.totalPss) }; delay(100) } }
        try {
            val settings = ScanSettings(hashAlgorithm = HashAlgorithm.SHA256, detectBurstShots = false)
            val start = SystemClock.elapsedRealtime()
            val cold = withTimeout(15 * 60 * 1000L) { analyzer.analyzeDetailed(photos, settings, MutableSharedFlow()) }
            val coldMillis = SystemClock.elapsedRealtime() - start
            assertEquals(0, cold.failed); assertEquals(count, cold.attempted)
            assertEquals(40, cold.groups.size); assertTrue(cold.groups.all { it.groupType == DuplicateGroup.GroupType.EXACT_DUPLICATE && it.photoCount == count / 40 })
            assertEquals(count, cold.metrics.decodeRequests); assertEquals(count, cold.metrics.fullFileHashRequests)
            assertTrue(cold.metrics.peakWorkers <= 4)
            val store = RoomScanSessionRepository(db, SessionClock()); store.initialize()
            val session = ScanSession("$token-session", System.currentTimeMillis(), scopeKey = "GENERATED_TEST_ONLY", settings = settings, discovered = count)
            store.begin(session)
            val persistStart = SystemClock.elapsedRealtime()
            store.complete(session.copy(status = SessionStatus.COMPLETED, attempted = count, matched = count), cold.groups)
            val persistMillis = SystemClock.elapsedRealtime() - persistStart
            assertEquals(count, store.state.value.groups.sumOf { it.photoCount })
            val warmStart = SystemClock.elapsedRealtime()
            val warm = analyzer.analyzeDetailed(photos, settings, MutableSharedFlow())
            assertEquals(0, warm.metrics.decodeRequests); assertEquals(0, warm.metrics.fullFileHashRequests); assertEquals(0, warm.metrics.cacheRowsWritten)
            assertEquals((count + 255) / 256, warm.metrics.cacheReadBatches)
            assertEquals(cold.groups.map { it.photos.map { p -> p.mediaKey }.toSet() }.toSet(), warm.groups.map { it.photos.map { p -> p.mediaKey }.toSet() }.toSet())
            println("PHASE4 realPipeline count=$count coldMs=$coldMillis persistMs=$persistMillis warmMs=${SystemClock.elapsedRealtime() - warmStart} peakPssKiB=${peak.get()} logicalBytes=${photos.sumOf { it.sizeBytes }} coldMetrics=${cold.metrics} warmMetrics=${warm.metrics}")
        } finally { sampler.cancelAndJoin(); scope.cancel() }
    }
}
