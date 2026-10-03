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
    private val photos = listOf(photo(1), photo(2))
    private val settings = ScanSettings(exactMatchEnabled = false, detectBurstShots = false)

    @Before fun setup(): Unit = runBlocking {
        crypto = mock(CryptographicHasher::class.java)
        aHash = mock(AverageHasher::class.java); dHash = mock(DifferenceHasher::class.java)
        pHash = mock(PerceptualHasher::class.java); bitmap = mock(BitmapUtils::class.java)
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), null, null, 42L, null, null,
                .8f, 800f, it.dateModified, it.sizeBytes)
        })
        for (photo in photos) {
            `when`(aHash.computeAHash(photo.contentUri)).thenReturn(41L)
            `when`(dHash.computeDHash(photo.contentUri)).thenReturn(43L)
            `when`(crypto.computeSha256(photo.contentUri)).thenReturn("sha-same")
            `when`(crypto.computeMd5(photo.contentUri)).thenReturn("md5-same")
        }
        rebuild()
        clearInvocations(crypto, aHash, dHash)
    }
    private fun rebuild() {
        analyzer = PhotoAnalyzer(crypto, pHash, aHash, dHash, HammingDistance(),
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
        photos.forEach { verify(aHash, times(1)).computeAHash(it.contentUri) }
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
        `when`(aHash.computeAHash(photos[0].contentUri)).thenReturn(null).thenReturn(41L)
        val s = settings.copy(hashAlgorithm = HashAlgorithm.AHASH)
        assertTrue(scan(s).isEmpty())
        assertEquals(1, scan(s).size)
        verify(aHash, times(2)).computeAHash(photos[0].contentUri)
    }
    @Test fun unrelatedLowQualityPhotosAreIndividualSuggestionsWithNoWaste(): Unit = runBlocking {
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), null, null, null, null, null,
                .1f, 10f, it.dateModified, it.sizeBytes)
        }); rebuild()
        val groups = scan(settings.copy(visualSimilarityEnabled = false, detectLowQuality = true))
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.photos.size == 1 && it.totalWasteBytes == 0L && it.similarityScore == 0f })
    }
    @Test fun decoderFailuresAreNotExactOrLowQualityCandidates(): Unit = runBlocking {
        dao = FakeHashCacheDao(emptyList()); rebuild()
        val groups = scan(settings.copy(exactMatchEnabled = true, detectLowQuality = true))
        assertTrue(groups.isEmpty())
        val cached = dao.getValidCache(photos[0].contentUri.toString(), 100, 100)
        assertEquals(-1f, cached!!.sharpnessScore)
    }
    @Test fun legacyZeroSharpnessIsRetriedInsteadOfClassifiedAsBlurred(): Unit = runBlocking {
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), "same", null, null, null, null,
                .1f, 0f, it.dateModified, it.sizeBytes)
        }); rebuild()
        assertTrue(scan(settings.copy(exactMatchEnabled = true, detectLowQuality = true)).isEmpty())
        photos.forEach { verify(bitmap).decodeSampledBitmap(it.contentUri, 256, 256) }
    }
    @Test fun legacyEmptyDigestIsInvalidatedAndRetried(): Unit = runBlocking {
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), "d41d8cd98f00b204e9800998ecf8427e", null, 42L, null, null,
                .8f, 800f, it.dateModified, it.sizeBytes)
        }); rebuild()
        scan(settings.copy(exactMatchEnabled = true))
        photos.forEach { verify(crypto).computeMd5(it.contentUri) }
    }
    @Test fun offToOnFlagsCompleteOnlyRequestedHashes(): Unit = runBlocking {
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), null, null, null, null, null,
                .8f, 800f, it.dateModified, it.sizeBytes)
        }); rebuild()
        `when`(pHash.computePHash(photos[0].contentUri)).thenReturn(42L)
        `when`(pHash.computePHash(photos[1].contentUri)).thenReturn(42L)
        assertTrue(scan(settings.copy(visualSimilarityEnabled = false)).isEmpty())
        verifyNoInteractions(crypto, aHash, dHash)
        assertEquals(1,scan().size)
        assertEquals(1,scan(settings.copy(exactMatchEnabled = true)).size)
        assertEquals(4,dao.writes)
    }
    @Test fun burstWasteExcludesActualKeeperRatherThanFirstChronologicalPhoto(): Unit = runBlocking {
        val burstPhotos = photos + photo(3)
        val timed = burstPhotos.mapIndexed { index, photo -> photo.copy(dateTaken = 1000L + index * 1000L) }
        dao = FakeHashCacheDao(timed.map {
            HashCacheEntity(it.contentUri.toString(), null, null, null, null, null,
                if (it.id == 3L) .9f else .1f,800f,it.dateModified,it.sizeBytes)
        }); rebuild()
        val groups = analyzer.analyze(timed,settings.copy(visualSimilarityEnabled = false,detectBurstShots = true),MutableSharedFlow())
        assertEquals(3L,groups.single().recommendedKeepId)
        assertEquals(300L,groups.single().totalWasteBytes)
    }

}
