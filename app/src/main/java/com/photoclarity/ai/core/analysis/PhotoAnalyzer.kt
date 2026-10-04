package com.photoclarity.ai.core.analysis

import com.photoclarity.ai.core.hash.*
import com.photoclarity.ai.core.session.SessionClock
import com.photoclarity.ai.core.session.SessionDispatchers
import com.photoclarity.ai.core.util.BitmapUtils
import com.photoclarity.ai.data.local.db.HashCacheDao
import com.photoclarity.ai.data.local.db.entity.HashCacheEntity
import com.photoclarity.ai.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class AnalysisMetrics(
    val hashingMillis: Long = 0, val exactMillis: Long = 0, val visualMillis: Long = 0, val burstMillis: Long = 0,
    val cacheReadBatches: Int = 0, val cacheRowsWritten: Int = 0, val fullFileHashRequests: Int = 0,
    val decodeRequests: Int = 0, val peakWorkers: Int = 0, val uniqueIndexNodes: Int = 0,
    val visualComparisons: Long = 0, val bucketLookups: Long = 0
)
data class AnalysisOutcome(val groups: List<DuplicateGroup>, val attempted: Int, val failed: Int, val metrics: AnalysisMetrics = AnalysisMetrics())

@Singleton
class PhotoAnalyzer @Inject constructor(
    private val cryptoHasher: CryptographicHasher, private val pHasher: PerceptualHasher,
    private val aHasher: AverageHasher, private val dHasher: DifferenceHasher,
    private val qualityScorer: QualityScorer,
    private val burstDetector: BurstDetector, private val bitmapUtils: BitmapUtils,
    private val hashCacheDao: HashCacheDao, private val runtime: SessionDispatchers = SessionDispatchers(),
    private val clock: SessionClock = SessionClock()
) {
    private data class Computed(val photo: Photo, val entry: HashCacheEntity?, val failed: Boolean)
    private class Work {
        val fullReads = AtomicInteger(); val decodes = AtomicInteger(); val active = AtomicInteger(); val peak = AtomicInteger()
        var batches = 0; var writes = 0
    }
    suspend fun analyze(photos: List<Photo>, settings: ScanSettings, progress: MutableSharedFlow<ScanProgress>) =
        analyzeDetailed(photos, settings, progress).groups

    suspend fun analyzeDetailed(photos: List<Photo>, settings: ScanSettings, progress: MutableSharedFlow<ScanProgress>): AnalysisOutcome = withContext(runtime.compute) {
        require(settings.similarityThreshold.isFinite() && settings.similarityThreshold in 0f..1f)
        val catalogue = photos.distinctBy { it.mediaKey }
        if (catalogue.isEmpty()) return@withContext AnalysisOutcome(emptyList(), 0, 0)
        val job = currentCoroutineContext()
        withContext(runtime.io) { hashCacheDao.deleteExpired(clock.now() - 30L * 24 * 60 * 60 * 1000) }
        val sizes = catalogue.groupingBy { it.sizeBytes }.eachCount()
        val burstCandidates = if (settings.detectBurstShots) burstDetector.detectBursts(catalogue) { job.ensureActive() }.flatten().map { it.mediaKey }.toSet() else emptySet()
        val work = Work()
        progress.emit(ScanProgress.Stage(ScanPhase.HASHING))
        progress.emit(ScanProgress.Hashing(0, catalogue.size, ""))
        val start = clock.elapsed()
        val analyzed = computeBatches(catalogue, settings, sizes, burstCandidates, progress, work)
        val hashMillis = clock.elapsed() - start
        val available = analyzed.filter { it.photo.sharpnessScore >= 0f }.map { it.photo }
        val groups = ArrayList<DuplicateGroup>()
        val used = HashSet<String>()
        progress.emit(ScanProgress.Stage(ScanPhase.EXACT))
        val exactStart = clock.elapsed()
        if (settings.exactMatchEnabled) {
            available.filter { it.sizeBytes > 0 }.groupBy { it.sizeBytes to cryptoHash(it, settings) }
                .filterKeys { it.second != null }.values.forEach { members ->
                    job.ensureActive()
                    if (members.size >= 2) groups.add(group(members, DuplicateGroup.GroupType.EXACT_DUPLICATE, 1f))
                }
            groups.forEach { it.photos.forEach { photo -> used.add(photo.mediaKey) } }
        }
        val exactMillis = clock.elapsed() - exactStart
        progress.emit(ScanProgress.Stage(ScanPhase.VISUAL))
        progress.emit(ScanProgress.Comparing(0, available.size))
        val visualStart = clock.elapsed()
        var nodes = 0; var comparisons = 0L; var lookups = 0L
        fun visualGroups(input: List<Photo>, type: DuplicateGroup.GroupType, minimum: Int = 2) {
            val candidates = input.filter { informative(visualHash(it, settings), bits(settings)) }
            val records = candidates.map { VisualRecord(it.mediaKey, checkNotNull(visualHash(it, settings)), it.qualityScore) }
            val matched = KeeperMatcher.match(records, bits(settings), settings.similarityThreshold) { job.ensureActive() }
            nodes += matched.metrics.uniqueHashes; comparisons += matched.metrics.comparisons; lookups += matched.metrics.bucketLookups
            matched.groups.filter { it.size >= minimum }.forEach { indices ->
                job.ensureActive()
                val members = indices.map { candidates[it] }
                val score = HammingIndex.meanSimilarity(members.map { checkNotNull(visualHash(it, settings)) }.toLongArray(), bits(settings)) { job.ensureActive() }
                groups.add(group(members, type, score)); members.forEach { used.add(it.mediaKey) }
            }
        }
        if (settings.visualSimilarityEnabled) visualGroups(available.filter { it.mediaKey !in used }, DuplicateGroup.GroupType.VISUAL_SIMILAR)
        val visualMillis = clock.elapsed() - visualStart
        progress.emit(ScanProgress.Comparing(available.size, available.size))
        progress.emit(ScanProgress.Stage(ScanPhase.BURST))
        val burstStart = clock.elapsed()
        if (settings.detectBurstShots) burstDetector.detectBursts(available.filter { it.mediaKey !in used }) { job.ensureActive() }.forEach { burst ->
            job.ensureActive(); visualGroups(burst, DuplicateGroup.GroupType.BURST_SHOT, 3)
        }
        val burstMillis = clock.elapsed() - burstStart
        if (settings.detectLowQuality) available.filter { it.mediaKey !in used && it.sharpnessScore / 1000f < .10f }.forEach { photo ->
            job.ensureActive(); groups.add(group(listOf(photo), DuplicateGroup.GroupType.LOW_QUALITY, 0f))
        }
        progress.emit(ScanProgress.Stage(ScanPhase.GROUPING))
        progress.emit(ScanProgress.Grouping(groups.size))
        job.ensureActive()
        progress.emit(ScanProgress.Completed)
        AnalysisOutcome(groups.sortedByDescending { it.totalWasteBytes }, catalogue.size, analyzed.count { it.failed },
            AnalysisMetrics(hashMillis, exactMillis, visualMillis, burstMillis, work.batches, work.writes, work.fullReads.get(), work.decodes.get(), work.peak.get(), nodes, comparisons, lookups))
    }

    private suspend fun computeBatches(photos: List<Photo>, settings: ScanSettings, sizes: Map<Long, Int>, bursts: Set<String>, progress: MutableSharedFlow<ScanProgress>, work: Work): List<Computed> {
        val output = ArrayList<Computed>(photos.size)
        var done = 0
        val progressLock = Mutex()
        for (batch in photos.chunked(AnalysisVersion.CACHE_BATCH)) {
            currentCoroutineContext().ensureActive()
            val cache = withContext(runtime.io) { hashCacheDao.getForUris(batch.map { it.mediaKey }) }.associateBy { it.photoUri }
            work.batches++
            val next = AtomicInteger()
            val computed = arrayOfNulls<Computed>(batch.size)
            coroutineScope {
                List(minOf(AnalysisVersion.WORKERS, batch.size)) {
                    async(runtime.io) {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val index = next.getAndIncrement()
                            if (index >= batch.size) break
                            val active = work.active.incrementAndGet(); work.peak.updateAndGet { maxOf(it, active) }
                            try {
                                val photo = batch[index]
                                computed[index] = compute(photo, settings, cache[photo.mediaKey], photo.sizeBytes > 0 && (sizes[photo.sizeBytes] ?: 0) > 1, photo.mediaKey in bursts, work)
                                progressLock.withLock { done++; progress.emit(ScanProgress.Hashing(done, photos.size, photo.displayName)) }
                            } finally { work.active.decrementAndGet() }
                        }
                    }
                }.awaitAll()
            }
            val ready = computed.map { checkNotNull(it) }
            val writes = ready.mapNotNull { it.entry }
            if (writes.isNotEmpty()) { withContext(runtime.io) { hashCacheDao.insertAllCache(writes) }; work.writes += writes.size }
            output.addAll(ready)
        }
        return output
    }

    private suspend fun compute(photo: Photo, settings: ScanSettings, cachedRow: HashCacheEntity?, sizeCandidate: Boolean, burstCandidate: Boolean, work: Work): Computed {
        val exact = settings.exactMatchEnabled && sizeCandidate
        val visual = settings.visualSimilarityEnabled || (settings.detectBurstShots && burstCandidate)
        if (!exact && !visual && !settings.detectLowQuality) return Computed(photo, null, false)
        val cached = cachedRow?.takeIf { HashCachePolicy.valid(it, photo) }
        var result = if (cached == null) photo.copy(md5Hash = null, sha256Hash = null, pHash = null, aHash = null, dHash = null, sharpnessScore = -1f)
        else photo.copy(md5Hash = cached.md5Hash, sha256Hash = cached.sha256Hash, pHash = cached.pHash, aHash = cached.aHash, dHash = cached.dHash, qualityScore = cached.qualityScore, sharpnessScore = cached.sharpnessScore)
        result = result.copy(md5Hash = result.md5Hash?.takeUnless { it == "d41d8cd98f00b204e9800998ecf8427e" }, sha256Hash = result.sha256Hash?.takeUnless { it == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" })
        if (exact && cryptoHash(result, settings) == null) {
            work.fullReads.incrementAndGet()
            result = if (settings.hashAlgorithm == HashAlgorithm.SHA256) result.copy(sha256Hash = cryptoHasher.computeSha256(photo.contentUri))
            else result.copy(md5Hash = cryptoHasher.computeMd5(photo.contentUri))
        }
        val missingVisual = visual && visualHash(result, settings) == null
        if (missingVisual || result.sharpnessScore < 0f) {
            work.decodes.incrementAndGet()
            val bitmap = bitmapUtils.decodeSampledBitmap(photo.contentUri, AnalysisVersion.DECODE_EDGE, AnalysisVersion.DECODE_EDGE)
            if (bitmap != null) try {
                result = withContext(runtime.compute) {
                    var ready = result
                    if (ready.sharpnessScore < 0f) {
                        val sharpness = try { bitmapUtils.computeSharpness(bitmap) } catch (e: CancellationException) { throw e } catch (e: Exception) { -1f }
                        ready = ready.copy(sharpnessScore = sharpness)
                        ready = ready.copy(qualityScore = if (sharpness < 0f) 0f else qualityScorer.score(ready))
                    }
                    if (missingVisual) ready = when (settings.visualHashAlgorithm) {
                        HashAlgorithm.PHASH -> ready.copy(pHash = pHasher.computeFromBitmap(bitmap))
                        HashAlgorithm.AHASH -> ready.copy(aHash = aHasher.computeFromBitmap(bitmap))
                        HashAlgorithm.DHASH -> ready.copy(dHash = dHasher.computeFromBitmap(bitmap))
                        else -> ready
                    }
                    ready
                }
            } finally { bitmap.recycle() }
        }
        currentCoroutineContext().ensureActive()
        val failed = result.sharpnessScore < 0f || (exact && cryptoHash(result, settings) == null) || (visual && visualHash(result, settings) == null)
        val entry = HashCachePolicy.entry(result, clock.now())
        // Compare payload, not the timestamp: a valid complete warm row incurs no write.
        val changed = cached == null || entry.copy(cachedAt = cached.cachedAt) != cached
        return Computed(result, entry.takeIf { changed }, failed)
    }

    private fun cryptoHash(photo: Photo, settings: ScanSettings) = if (settings.hashAlgorithm == HashAlgorithm.SHA256) photo.sha256Hash else photo.md5Hash
    private fun visualHash(photo: Photo, settings: ScanSettings) = when (settings.visualHashAlgorithm) {
        HashAlgorithm.PHASH -> photo.pHash?.takeIf { it >= 0 }
        HashAlgorithm.AHASH -> photo.aHash
        HashAlgorithm.DHASH -> photo.dHash
        else -> null
    }
    private fun bits(settings: ScanSettings) = if (settings.visualHashAlgorithm == HashAlgorithm.PHASH) PerceptualDct.BITS else 64
    private fun informative(hash: Long?, bits: Int): Boolean = hash != null && hash != 0L && hash != (if (bits == 64) -1L else Long.MAX_VALUE)
    private fun group(photos: List<Photo>, type: DuplicateGroup.GroupType, score: Float): DuplicateGroup {
        val sorted = photos.sortedWith(compareByDescending<Photo> { it.qualityScore }.thenBy { it.mediaKey })
        val keeper = sorted.first()
        return DuplicateGroup(UUID.randomUUID().toString(), sorted, type, score, keeper.id, sorted.drop(1).sumOf { it.sizeBytes })
    }
}
