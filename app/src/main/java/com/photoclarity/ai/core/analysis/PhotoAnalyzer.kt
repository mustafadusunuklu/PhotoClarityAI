package com.photoclarity.ai.core.analysis

import com.photoclarity.ai.core.hash.AverageHasher
import com.photoclarity.ai.core.hash.CryptographicHasher
import com.photoclarity.ai.core.hash.DifferenceHasher
import com.photoclarity.ai.core.hash.HammingDistance
import com.photoclarity.ai.core.hash.PerceptualHasher
import com.photoclarity.ai.core.util.BitmapUtils
import com.photoclarity.ai.data.local.db.HashCacheDao
import com.photoclarity.ai.data.local.db.entity.HashCacheEntity
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.model.HashAlgorithm
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.domain.model.ScanProgress
import com.photoclarity.ai.domain.model.ScanSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class PhotoAnalyzer @Inject constructor(
    private val cryptoHasher: CryptographicHasher,
    private val pHasher: PerceptualHasher,
    private val aHasher: AverageHasher,
    private val dHasher: DifferenceHasher,
    private val hammingDistance: HammingDistance,
    private val qualityScorer: QualityScorer,
    private val burstDetector: BurstDetector,
    private val bitmapUtils: BitmapUtils,
    private val hashCacheDao: HashCacheDao
) {
    companion object {
        /** Parallel coroutines used for hash computation. */
        private const val PARALLEL_JOBS = 4
        /** Sharpness score (normalised 0–1) below which a photo is flagged as low quality. */
        private const val LOW_QUALITY_SHARPNESS_THRESHOLD = 0.10f
        /** Hash cache max age: 30 days in milliseconds. */
        private const val CACHE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }

    /**
     * Full analysis pipeline:
     * 1. Compute hashes (cryptographic + perceptual), using the local Room cache when possible
     * 2. Find exact duplicates (same hash)
     * 3. Find near-duplicates (Hamming distance within threshold)
     * 4. Detect burst shots
     * 5. Detect low-quality photos (when enabled)
     * 6. Sort groups by descending reclaim size
     */
    suspend fun analyze(
        photos: List<Photo>,
        settings: ScanSettings,
        progress: MutableSharedFlow<ScanProgress>
    ): List<DuplicateGroup> = withContext(Dispatchers.Default) {

        if (photos.isEmpty()) return@withContext emptyList()

        // Evict stale cache entries once per run
        withContext(Dispatchers.IO) {
            hashCacheDao.deleteExpired(System.currentTimeMillis() - CACHE_MAX_AGE_MS)
        }

        progress.emit(ScanProgress.Hashing(0, photos.size, ""))

        // ── Stage 1: Compute / retrieve hashes ───────────────────────────────
        val hashedPhotos = computeHashesInParallel(photos, settings, progress)

        if (!coroutineContext.isActive) {
            progress.emit(ScanProgress.Cancelled)
            return@withContext emptyList()
        }

        progress.emit(ScanProgress.Comparing(0, hashedPhotos.size))

        val groups = mutableListOf<DuplicateGroup>()
        val processedIds = mutableSetOf<Long>()

        // ── Stage 2: Exact duplicates ────────────────────────────────────────
        if (settings.exactMatchEnabled) {
            val exactGroups = findExactDuplicates(hashedPhotos, settings)
            groups.addAll(exactGroups)
            exactGroups.forEach { g -> g.photos.forEach { processedIds.add(it.id) } }
        }

        // ── Stage 3: Near-duplicates (visual similarity) ─────────────────────
        if (settings.visualSimilarityEnabled) {
            val remaining = hashedPhotos.filter { it.id !in processedIds }
            val nearGroups = findNearDuplicates(remaining, settings)
            groups.addAll(nearGroups)
            nearGroups.forEach { g -> g.photos.forEach { processedIds.add(it.id) } }
        }

        // ── Stage 4: Burst detection ─────────────────────────────────────────
        if (settings.detectBurstShots) {
            val remaining = hashedPhotos.filter { it.id !in processedIds }
            val burstGroups = burstDetector.detectBursts(remaining).map { burst ->
                val bestId = qualityScorer.selectBest(burst).id
                DuplicateGroup(
                    id = UUID.randomUUID().toString(),
                    photos = burst,
                    groupType = DuplicateGroup.GroupType.BURST_SHOT,
                    similarityScore = 0.95f,
                    recommendedKeepId = bestId,
                    totalWasteBytes = burst.drop(1).sumOf { it.sizeBytes }
                )
            }
            groups.addAll(burstGroups)
            burstGroups.forEach { g -> g.photos.forEach { processedIds.add(it.id) } }
        }

        // ── Stage 5: Low-quality detection ───────────────────────────────────
        if (settings.detectLowQuality) {
            val lowQuality = hashedPhotos
                .filter { it.id !in processedIds }
                .filter { (it.sharpnessScore / 1000f).coerceIn(0f, 1f) < LOW_QUALITY_SHARPNESS_THRESHOLD }

            if (lowQuality.size >= 2) {
                // Group all low-quality photos together as a single group
                val sorted = lowQuality.sortedByDescending { it.qualityScore }
                groups.add(
                    DuplicateGroup(
                        id = UUID.randomUUID().toString(),
                        photos = sorted,
                        groupType = DuplicateGroup.GroupType.LOW_QUALITY,
                        similarityScore = 0f,
                        recommendedKeepId = sorted.first().id,
                        totalWasteBytes = sorted.drop(1).sumOf { it.sizeBytes }
                    )
                )
            }
        }

        progress.emit(ScanProgress.Grouping(groups.size))
        progress.emit(ScanProgress.Completed)

        groups.sortedByDescending { it.totalWasteBytes }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hash computation
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun computeHashesInParallel(
        photos: List<Photo>,
        settings: ScanSettings,
        progress: MutableSharedFlow<ScanProgress>
    ): List<Photo> = coroutineScope {
        val chunks = photos.chunked((photos.size / PARALLEL_JOBS).coerceAtLeast(1))
        // AtomicInteger makes the counter safe across the 4 parallel coroutines
        val done = AtomicInteger(0)

        chunks.map { chunk ->
            async(Dispatchers.IO) {
                chunk.map { photo ->
                    val hashed = computeHashesForPhoto(photo, settings)
                    val current = done.incrementAndGet()
                    progress.emit(ScanProgress.Hashing(current, photos.size, photo.displayName))
                    hashed
                }
            }
        }.awaitAll().flatten()
    }

    /**
     * Compute hashes for a single photo, consulting the Room cache first.
     * On a cache hit all hash/quality values are read from DB instead of
     * re-scanning the file — critical for large libraries.
     */
    private suspend fun computeHashesForPhoto(photo: Photo, settings: ScanSettings): Photo =
        withContext(Dispatchers.IO) {
            val uriString = photo.contentUri.toString()

            // ── Cache lookup ──────────────────────────────────────────────────
            val cached = hashCacheDao.getValidCache(uriString, photo.dateModified, photo.sizeBytes)
            if (cached != null) {
                return@withContext photo.copy(
                    md5Hash      = cached.md5Hash,
                    sha256Hash   = cached.sha256Hash,
                    pHash        = cached.pHash,
                    aHash        = cached.aHash,
                    dHash        = cached.dHash,
                    qualityScore = cached.qualityScore,
                    sharpnessScore = cached.sharpnessScore
                )
            }

            // ── Compute from scratch ──────────────────────────────────────────
            var result = photo

            if (settings.exactMatchEnabled) {
                result = when (settings.hashAlgorithm) {
                    HashAlgorithm.SHA256 -> result.copy(sha256Hash = cryptoHasher.computeSha256(photo.contentUri))
                    else                 -> result.copy(md5Hash    = cryptoHasher.computeMd5(photo.contentUri))
                }
            }

            if (settings.visualSimilarityEnabled) {
                result = when (settings.hashAlgorithm) {
                    HashAlgorithm.PHASH -> result.copy(pHash = pHasher.computePHash(photo.contentUri))
                    HashAlgorithm.AHASH -> result.copy(aHash = aHasher.computeAHash(photo.contentUri))
                    HashAlgorithm.DHASH -> result.copy(dHash = dHasher.computeDHash(photo.contentUri))
                    else                -> result.copy(pHash = pHasher.computePHash(photo.contentUri))
                }
            }

            // ── Sharpness / quality score ─────────────────────────────────────
            val sharpness = runCatching {
                val bitmap = bitmapUtils.decodeSampledBitmap(photo.contentUri, 256, 256)
                bitmap?.let {
                    val score = bitmapUtils.computeSharpness(it)
                    it.recycle()
                    score
                } ?: 0f
            }.getOrDefault(0f)

            result = result.copy(
                sharpnessScore = sharpness,
                qualityScore   = qualityScorer.score(result.copy(sharpnessScore = sharpness))
            )

            // ── Persist to cache ──────────────────────────────────────────────
            hashCacheDao.insertCache(
                HashCacheEntity(
                    photoUri       = uriString,
                    md5Hash        = result.md5Hash,
                    sha256Hash     = result.sha256Hash,
                    pHash          = result.pHash,
                    aHash          = result.aHash,
                    dHash          = result.dHash,
                    qualityScore   = result.qualityScore,
                    sharpnessScore = result.sharpnessScore,
                    lastModified   = photo.dateModified,
                    fileSize       = photo.sizeBytes
                )
            )

            result
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Grouping logic
    // ─────────────────────────────────────────────────────────────────────────

    private fun findExactDuplicates(photos: List<Photo>, settings: ScanSettings): List<DuplicateGroup> {
        val hashToPhotos = mutableMapOf<String, MutableList<Photo>>()

        photos.forEach { photo ->
            val hash = when (settings.hashAlgorithm) {
                HashAlgorithm.SHA256 -> photo.sha256Hash
                else                 -> photo.md5Hash
            } ?: return@forEach

            hashToPhotos.getOrPut(hash) { mutableListOf() }.add(photo)
        }

        return hashToPhotos.values
            .filter { it.size >= 2 }
            .map { group ->
                val sorted = group.sortedByDescending { it.qualityScore }
                DuplicateGroup(
                    id               = UUID.randomUUID().toString(),
                    photos           = sorted,
                    groupType        = DuplicateGroup.GroupType.EXACT_DUPLICATE,
                    similarityScore  = 1.0f,
                    recommendedKeepId= sorted.first().id,
                    totalWasteBytes  = sorted.drop(1).sumOf { it.sizeBytes }
                )
            }
    }

    private fun findNearDuplicates(photos: List<Photo>, settings: ScanSettings): List<DuplicateGroup> {
        // Union-Find for transitive grouping
        val parent = IntArray(photos.size) { it }

        fun find(x: Int): Int {
            if (parent[x] != x) parent[x] = find(parent[x])
            return parent[x]
        }

        fun union(x: Int, y: Int) {
            parent[find(x)] = find(y)
        }

        for (i in photos.indices) {
            for (j in i + 1 until photos.size) {
                val a = photos[i]; val b = photos[j]
                val similar = when (settings.hashAlgorithm) {
                    HashAlgorithm.PHASH -> {
                        val ha = a.pHash; val hb = b.pHash
                        if (ha != null && hb != null) hammingDistance.isSimilar(ha, hb, settings.similarityThreshold) else false
                    }
                    HashAlgorithm.AHASH -> {
                        val ha = a.aHash; val hb = b.aHash
                        if (ha != null && hb != null) hammingDistance.isSimilar(ha, hb, settings.similarityThreshold) else false
                    }
                    HashAlgorithm.DHASH -> {
                        val ha = a.dHash; val hb = b.dHash
                        if (ha != null && hb != null) hammingDistance.isSimilar(ha, hb, settings.similarityThreshold) else false
                    }
                    else -> false
                }
                if (similar) union(i, j)
            }
        }

        val groupMap = mutableMapOf<Int, MutableList<Photo>>()
        photos.forEachIndexed { i, photo ->
            groupMap.getOrPut(find(i)) { mutableListOf() }.add(photo)
        }

        return groupMap.values
            .filter { it.size >= 2 }
            .map { group ->
                val sorted = group.sortedByDescending { it.qualityScore }
                DuplicateGroup(
                    id               = UUID.randomUUID().toString(),
                    photos           = sorted,
                    groupType        = DuplicateGroup.GroupType.VISUAL_SIMILAR,
                    similarityScore  = computeGroupSimilarity(group, settings),
                    recommendedKeepId= sorted.first().id,
                    totalWasteBytes  = sorted.drop(1).sumOf { it.sizeBytes }
                )
            }
    }

    private fun computeGroupSimilarity(photos: List<Photo>, settings: ScanSettings): Float {
        if (photos.size < 2) return 1f
        var totalSim = 0f
        var count = 0
        for (i in photos.indices) {
            for (j in i + 1 until photos.size) {
                val sim = when (settings.hashAlgorithm) {
                    HashAlgorithm.PHASH -> {
                        val ha = photos[i].pHash; val hb = photos[j].pHash
                        if (ha != null && hb != null) hammingDistance.similarity(ha, hb) else 0f
                    }
                    else -> settings.similarityThreshold
                }
                totalSim += sim
                count++
            }
        }
        return if (count > 0) totalSim / count else settings.similarityThreshold
    }
}
