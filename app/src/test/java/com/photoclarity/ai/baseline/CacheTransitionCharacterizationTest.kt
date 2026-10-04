package com.photoclarity.ai.baseline

import android.content.Context
import android.graphics.Bitmap
import com.photoclarity.ai.core.analysis.*
import com.photoclarity.ai.core.hash.*
import com.photoclarity.ai.core.util.BitmapUtils
import com.photoclarity.ai.data.local.db.entity.HashCacheEntity
import com.photoclarity.ai.domain.model.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/** Real analyzer with deterministic hash/decoder boundaries; no real media access. */
class CacheTransitionCharacterizationTest {
    private lateinit var analyzer: PhotoAnalyzer
    private lateinit var dao: FakeHashCacheDao
    private lateinit var crypto: CryptographicHasher
    private lateinit var aHash: AverageHasher
    private lateinit var dHash: DifferenceHasher
    private lateinit var pHash: PerceptualHasher
    private lateinit var bitmap: BitmapUtils
    private val photos = listOf(photo(1, 100), photo(2, 100))
    private val sources = photos.associateWith { mock(Bitmap::class.java) }
    private val settings = ScanSettings(exactMatchEnabled = false, detectBurstShots = false)

    @Before fun setup(): Unit = runBlocking {
        crypto = mock(CryptographicHasher::class.java)
        aHash = mock(AverageHasher::class.java); dHash = mock(DifferenceHasher::class.java)
        pHash = mock(PerceptualHasher::class.java); bitmap = mock(BitmapUtils::class.java)
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), null, null, 42L, null, null,
                .8f, 800f, it.dateModified, it.sizeBytes).currentFor(it)
        })
        for (photo in photos) {
            val source = sources.getValue(photo)
            `when`(bitmap.decodeSampledBitmap(photo.contentUri, 256, 256)).thenReturn(source)
            `when`(bitmap.computeSharpness(source)).thenReturn(800f)
            `when`(aHash.computeFromBitmap(source)).thenReturn(41L)
            `when`(dHash.computeFromBitmap(source)).thenReturn(43L)
            `when`(pHash.computeFromBitmap(source)).thenReturn(42L)
            `when`(aHash.computeAHash(photo.contentUri)).thenReturn(41L)
            `when`(dHash.computeDHash(photo.contentUri)).thenReturn(43L)
            `when`(crypto.computeSha256(photo.contentUri)).thenReturn("sha-same")
            `when`(crypto.computeMd5(photo.contentUri)).thenReturn("md5-same")
        }
        rebuild()
        clearInvocations(crypto, aHash, dHash)
    }
    private fun rebuild() {
        analyzer = PhotoAnalyzer(crypto, pHash, aHash, dHash,
            QualityScorer(), BurstDetector(), bitmap, dao)
    }
    private suspend fun scan(s: ScanSettings = settings) = analyzer.analyze(photos, s, MutableSharedFlow())

    @Test fun unchangedPhashCacheProducesGroupWithoutIo(): Unit = runBlocking {
        assertEquals(1, scan().size)
        verifyNoInteractions(crypto, aHash, dHash, pHash, bitmap)
        assertEquals(0, dao.writes)
    }
    @Test fun phashToAhashCompletesCacheAndPreservesPhash(): Unit = runBlocking {
        val s = settings.copy(hashAlgorithm = HashAlgorithm.AHASH)
        assertEquals(1, scan(s).size)
        assertEquals(2, dao.writes)
        assertEquals(42L, dao.getValidCache(photos[0].contentUri.toString(), 100, 100)?.pHash)
        scan(s); assertEquals(2, dao.writes)
        photos.forEach { verify(aHash, times(1)).computeFromBitmap(sources.getValue(it)) }
    }
    @Test fun phashToDhashCompletesCache(): Unit = runBlocking {
        assertEquals(1, scan(settings.copy(hashAlgorithm = HashAlgorithm.DHASH)).size)
        assertEquals(2, dao.writes)
    }
    @Test fun enablingSha256CompletesCache(): Unit = runBlocking {
        val g = scan(settings.copy(hashAlgorithm = HashAlgorithm.SHA256, exactMatchEnabled = true))
        assertEquals(DuplicateGroup.GroupType.EXACT_DUPLICATE, g.single().groupType)
        photos.forEach { verify(crypto).computeSha256(it.contentUri) }
    }
    @Test fun enablingMd5CompletesCache(): Unit = runBlocking {
        assertEquals(1, scan(settings.copy(exactMatchEnabled = true)).size)
        assertEquals(2, dao.writes)
    }
    @Test fun cryptoChoiceStillRunsVisualStage(): Unit = runBlocking {
        assertEquals(DuplicateGroup.GroupType.VISUAL_SIMILAR,
            scan(settings.copy(hashAlgorithm = HashAlgorithm.SHA256)).single().groupType)
    }
    @Test fun failedMissingHashIsRetriedNextScan(): Unit = runBlocking {
        `when`(aHash.computeFromBitmap(sources.getValue(photos[0]))).thenReturn(null).thenReturn(41L)
        val s = settings.copy(hashAlgorithm = HashAlgorithm.AHASH)
        assertTrue(scan(s).isEmpty())
        assertEquals(1, scan(s).size)
        verify(aHash, times(2)).computeFromBitmap(sources.getValue(photos[0]))
    }
    @Test fun unrelatedLowQualityPhotosAreIndividualSuggestionsWithNoWaste(): Unit = runBlocking {
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), null, null, null, null, null,
                .1f, 10f, it.dateModified, it.sizeBytes).currentFor(it)
        }); rebuild()
        val groups = scan(settings.copy(visualSimilarityEnabled = false, detectLowQuality = true))
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.photos.size == 1 && it.totalWasteBytes == 0L && it.similarityScore == 0f })
    }
    @Test fun decoderFailuresAreNotExactOrLowQualityCandidates(): Unit = runBlocking {
        dao = FakeHashCacheDao(emptyList()); rebuild()
        photos.forEach { `when`(bitmap.decodeSampledBitmap(it.contentUri, 256, 256)).thenReturn(null) }
        val groups = scan(settings.copy(exactMatchEnabled = true, detectLowQuality = true))
        assertTrue(groups.isEmpty())
        val cached = dao.getValidCache(photos[0].contentUri.toString(), 100, 100)
        assertEquals(-1f, cached!!.sharpnessScore)
    }
    @Test fun legacyZeroSharpnessIsRetriedInsteadOfClassifiedAsBlurred(): Unit = runBlocking {
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), "same", null, null, null, null,
                .1f, 0f, it.dateModified, it.sizeBytes).currentFor(it).copy(algorithmVersion = 0)
        }); rebuild()
        photos.forEach { `when`(bitmap.decodeSampledBitmap(it.contentUri, 256, 256)).thenReturn(null) }
        assertTrue(scan(settings.copy(exactMatchEnabled = true, detectLowQuality = true)).isEmpty())
        photos.forEach { verify(bitmap).decodeSampledBitmap(it.contentUri, 256, 256) }
    }
    @Test fun legacyEmptyDigestIsInvalidatedAndRetried(): Unit = runBlocking {
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), "d41d8cd98f00b204e9800998ecf8427e", null, 42L, null, null,
                .8f, 800f, it.dateModified, it.sizeBytes).currentFor(it)
        }); rebuild()
        scan(settings.copy(exactMatchEnabled = true))
        photos.forEach { verify(crypto).computeMd5(it.contentUri) }
    }
    @Test fun offToOnFlagsCompleteOnlyRequestedHashes(): Unit = runBlocking {
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), null, null, null, null, null,
                .8f, 800f, it.dateModified, it.sizeBytes).currentFor(it)
        }); rebuild()
        `when`(pHash.computePHash(photos[0].contentUri)).thenReturn(42L)
        `when`(pHash.computePHash(photos[1].contentUri)).thenReturn(42L)
        assertTrue(scan(settings.copy(visualSimilarityEnabled = false)).isEmpty())
        verifyNoInteractions(crypto, aHash, dHash)
        assertEquals(1,scan().size)
        assertEquals(1,scan(settings.copy(exactMatchEnabled = true)).size)
        assertEquals(4,dao.writes)
    }
    @Test fun warmCacheUsesBatchesWithoutWritesFullReadsOrDecode(): Unit = runBlocking {
        val large = (1L..600L).map { photo(it, 100) }
        dao = FakeHashCacheDao(large.map { HashCachePolicy.entry(it.copy(pHash = 42, md5Hash = "same", qualityScore = .8f, sharpnessScore = 800f), System.currentTimeMillis()) }); rebuild()
        val result = analyzer.analyzeDetailed(large, settings.copy(exactMatchEnabled = true), MutableSharedFlow())
        assertEquals(600, result.attempted); assertEquals(0, result.failed)
        assertEquals(3, result.metrics.cacheReadBatches); assertEquals(0, result.metrics.cacheRowsWritten)
        assertEquals(0, result.metrics.fullFileHashRequests); assertEquals(0, result.metrics.decodeRequests)
        assertTrue(result.metrics.peakWorkers <= 4); assertEquals(600, result.groups.single().photoCount)
        verifyNoInteractions(crypto, pHash, aHash, dHash, bitmap)
    }
    @Test fun differentSizesSkipFullFileHashing(): Unit = runBlocking {
        val different = listOf(photo(1, 100), photo(2, 200))
        val result = analyzer.analyzeDetailed(different, settings.copy(exactMatchEnabled = true, visualSimilarityEnabled = false), MutableSharedFlow())
        assertEquals(0, result.metrics.fullFileHashRequests); assertTrue(result.groups.isEmpty())
        verifyNoInteractions(crypto)
    }
    @Test fun coldFeaturesBorrowOneBitmapAndRecycleOncePerPhoto(): Unit = runBlocking {
        dao = FakeHashCacheDao(emptyList()); rebuild()
        val result = analyzer.analyzeDetailed(photos, settings.copy(exactMatchEnabled = true, detectLowQuality = true), MutableSharedFlow())
        assertEquals(2, result.metrics.decodeRequests); assertEquals(2, result.metrics.fullFileHashRequests)
        assertEquals(2, result.metrics.cacheRowsWritten); assertEquals(1, dao.writeBatches)
        sources.values.forEach { verify(it, times(1)).recycle() }
        photos.forEach { verify(bitmap, times(1)).decodeSampledBitmap(it.contentUri, 256, 256) }
    }
    @Test fun currentZeroSharpnessIsAValidWarmFlatImage(): Unit = runBlocking {
        dao = FakeHashCacheDao(photos.map { HashCachePolicy.entry(it.copy(pHash = 0, sharpnessScore = 0f), System.currentTimeMillis()) }); rebuild()
        val result = analyzer.analyzeDetailed(photos, settings.copy(detectLowQuality = true), MutableSharedFlow())
        assertEquals(0, result.metrics.decodeRequests); assertEquals(0, result.failed)
        assertEquals(2, result.groups.size); assertTrue(result.groups.all { it.groupType == DuplicateGroup.GroupType.LOW_QUALITY })
    }
    @Test fun cachePolicyRejectsVersionGenerationAndMetadataChanges() {
        val p = photos.first().copy(generationModified = 2, mediaStoreVersion = "v")
        val entry = HashCachePolicy.entry(p, 100)
        assertTrue(HashCachePolicy.valid(entry, p))
        assertFalse(HashCachePolicy.valid(entry.copy(algorithmVersion = 0), p))
        assertFalse(HashCachePolicy.valid(entry, p.copy(generationModified = 3)))
        assertFalse(HashCachePolicy.valid(entry, p.copy(mediaStoreVersion = "other")))
        assertFalse(HashCachePolicy.valid(entry, p.copy(dateAdded = 999)))
        assertFalse(HashCachePolicy.valid(entry, p.copy(width = 65)))
        assertFalse(HashCachePolicy.valid(entry, p.copy(mimeType = "image/jpeg")))
    }
    @Test fun repeatedLongIdOnDifferentVolumesKeepsUriIdentity(): Unit = runBlocking {
        val alternate = mock(android.net.Uri::class.java)
        `when`(alternate.toString()).thenReturn("content://media/second/images/media/1")
        val sameId = listOf(photos.first(), photos.first().copy(uri = alternate, contentUri = alternate, qualityScore = .9f))
        dao = FakeHashCacheDao(sameId.map { HashCachePolicy.entry(it.copy(pHash = 42, sharpnessScore = 800f), System.currentTimeMillis()) }); rebuild()
        val result = analyzer.analyzeDetailed(sameId, settings, MutableSharedFlow())
        assertEquals(2, result.attempted); assertEquals(2, result.groups.single().photos.size)
        assertEquals(100L, result.groups.single().totalWasteBytes)
        assertEquals(alternate.toString(), result.groups.single().recommendedPhoto?.mediaKey)
    }
    @Test fun timeOnlyBurstDoesNotClaimVisualSimilarity(): Unit = runBlocking {
        val timed = (1L..3L).map { photo(it, 100).copy(dateTaken = 1000 + it * 100) }
        dao = FakeHashCacheDao(timed.map { HashCachePolicy.entry(it.copy(pHash = 0, sharpnessScore = 800f), System.currentTimeMillis()) }); rebuild()
        assertTrue(analyzer.analyze(timed, settings.copy(visualSimilarityEnabled = false, detectBurstShots = true), MutableSharedFlow()).isEmpty())
    }
    @Test fun burstWindowIsBoundedAndVolumesAreSeparate() {
        val timed = (0L..4L).map { photo(it + 1).copy(dateTaken = 1000 + it * 1500) }
        assertTrue(BurstDetector().detectBursts(timed).isEmpty())
    }
    @Test fun burstWasteExcludesActualKeeperRatherThanFirstChronologicalPhoto(): Unit = runBlocking {
        val burstPhotos = listOf(photo(1, 100), photo(2, 200), photo(3, 300))
        val timed = burstPhotos.mapIndexed { index, photo -> photo.copy(dateTaken = 1000L + index * 1000L) }
        dao = FakeHashCacheDao(timed.map {
            HashCacheEntity(it.contentUri.toString(), null, null, 42L, null, null,
                if (it.id == 3L) .9f else .1f,800f,it.dateModified,it.sizeBytes).currentFor(it)
        }); rebuild()
        val groups = analyzer.analyze(timed,settings.copy(visualSimilarityEnabled = false,detectBurstShots = true),MutableSharedFlow())
        assertEquals(3L,groups.single().recommendedKeepId)
        assertEquals(300L,groups.single().totalWasteBytes)
        assertEquals(1f,groups.single().similarityScore)
    }

}
