package com.photoclarity.ai.baseline

import android.content.ContentResolver
import android.content.Context
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

/** R04: real analyzer + real hashers, warm synthetic DAO; no Android image decoder. */
class CacheTransitionCharacterizationTest {
    private lateinit var resolver: ContentResolver
    private lateinit var analyzer: PhotoAnalyzer
    private lateinit var dao: FakeHashCacheDao
    private val photos = listOf(photo(1), photo(2))
    private val settings = ScanSettings(exactMatchEnabled = false, detectBurstShots = false)

    @Before fun setup() {
        val context = mock(Context::class.java)
        resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        val bitmap = BitmapUtils(context)
        dao = FakeHashCacheDao(photos.map {
            HashCacheEntity(it.contentUri.toString(), null, null, 42L, null, null,
                .8f, 800f, it.dateModified, it.sizeBytes)
        })
        analyzer = PhotoAnalyzer(CryptographicHasher(context), PerceptualHasher(context, bitmap),
            AverageHasher(context, bitmap), DifferenceHasher(context, bitmap), HammingDistance(),
            QualityScorer(), BurstDetector(), bitmap, dao)
    }

    @Test fun unchangedPhashCacheStillProducesVisualGroupWithoutIo() = runBlocking {
        val groups = analyzer.analyze(photos, settings, MutableSharedFlow())
        assertEquals(1, groups.size)
        assertEquals(DuplicateGroup.GroupType.VISUAL_SIMILAR, groups.single().groupType)
        assertEquals(setOf(1L, 2L), groups.single().photos.map { it.id }.toSet())
        verifyNoInteractions(resolver)
        assertEquals(0, dao.writes)
    }

    @Test fun phashToAhashCurrentlyLosesResultsWithoutCompletingCache() = runBlocking {
        assertEquals(1, analyzer.analyze(photos, settings, MutableSharedFlow()).size)
        assertTrue(analyzer.analyze(photos, settings.copy(hashAlgorithm = HashAlgorithm.AHASH), MutableSharedFlow()).isEmpty())
        verifyNoInteractions(resolver)
        assertEquals(0, dao.writes)
    }

    @Test fun phashToDhashCurrentlyLosesResultsWithoutCompletingCache() = runBlocking {
        assertEquals(1, analyzer.analyze(photos, settings, MutableSharedFlow()).size)
        assertTrue(analyzer.analyze(photos, settings.copy(hashAlgorithm = HashAlgorithm.DHASH), MutableSharedFlow()).isEmpty())
        verifyNoInteractions(resolver)
        assertEquals(0, dao.writes)
    }

    @Test fun enablingSha256ExactMatchingCurrentlyLeavesMissingHashes() = runBlocking {
        assertEquals(1, analyzer.analyze(photos, settings, MutableSharedFlow()).size)
        val exact = settings.copy(hashAlgorithm = HashAlgorithm.SHA256, exactMatchEnabled = true, visualSimilarityEnabled = false)
        assertTrue(analyzer.analyze(photos, exact, MutableSharedFlow()).isEmpty())
        verifyNoInteractions(resolver)
        assertEquals(0, dao.writes)
    }

    @Test fun enablingMd5ExactMatchingCurrentlyLeavesMissingHashes() = runBlocking {
        val exact = settings.copy(exactMatchEnabled = true, visualSimilarityEnabled = false)
        assertTrue(analyzer.analyze(photos, exact, MutableSharedFlow()).isEmpty())
        verifyNoInteractions(resolver)
        assertEquals(0, dao.writes)
    }
}
