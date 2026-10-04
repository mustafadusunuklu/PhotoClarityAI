package com.photoclarity.ai.baseline

import com.photoclarity.ai.core.analysis.*
import com.photoclarity.ai.core.hash.PerceptualDct
import com.photoclarity.ai.core.util.DecodeBudget
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

class Phase4AlgorithmTest {
    @Test fun indexMatchesExhaustiveOracleForEveryRadiusAndBothBitWidths() {
        val random = Random(410)
        for (bits in listOf(63, 64)) {
            val mask = if (bits == 63) Long.MAX_VALUE else -1L
            val data = LongArray(180) { random.nextLong() and mask }
            // Include close neighbours, repeated nodes and the sign bit at width 64.
            val hashes = data + LongArray(bits) { data[0] xor (1L shl it) } + data.take(5)
            for (radius in 0..bits) {
                val index = HammingIndex(hashes, bits, radius)
                for (query in hashes.take(8)) {
                    val expected = hashes.filter { (it xor query).countOneBits() <= radius }.toSet()
                    assertEquals("bits=$bits radius=$radius", expected, index.neighbours(query).toSet())
                }
            }
        }
    }
    @Test fun thresholdBoundaryUsesSameFloatPredicateAsExhaustiveComparison() {
        for (bits in listOf(63, 64)) for (distance in 0..bits) {
            val threshold = 1f - distance.toFloat() / bits
            val radius = HammingIndex.radius(bits, threshold)
            assertEquals((0..bits).filter { 1f - it.toFloat() / bits >= threshold }.last(), radius)
        }
    }
    @Test fun usefulFilterCannotSuppressAnUnassignedNeighbour() {
        val index = HammingIndex(longArrayOf(1, 2, 3, 7), 63, 2)
        assertEquals(setOf(2L, 3L), index.neighbours(1, useful = { it == 2L || it == 3L }).toSet())
    }
    @Test fun keeperStarDoesNotMergeAnIndirectChain() {
        val records = listOf(VisualRecord("a", 0, 3f), VisualRecord("b", 1, 2f), VisualRecord("c", 3, 1f))
        val result = KeeperMatcher.match(records, 64, 1f - 1f / 64)
        assertEquals(1, result.groups.size)
        assertArrayEquals(intArrayOf(0, 1), result.groups.single())
    }
    @Test fun keeperPriorityAndDisjointMembershipMatchExhaustiveStarOracle() {
        val random = Random(412)
        val records = List(400) { VisualRecord("uri:$it", random.nextLong() and 65535L, random.nextInt(4).toFloat()) }
        for (threshold in listOf(.7f, .85f, .99f)) {
            val remaining = records.indices.toMutableSet()
            val expected = ArrayList<List<Int>>()
            val order = records.indices.sortedWith(compareByDescending<Int> { records[it].quality }.thenBy { records[it].key })
            for (keeper in order) if (keeper in remaining) {
                val members = order.filter { it in remaining && 1f - (records[it].hash xor records[keeper].hash).countOneBits() / 63f >= threshold }
                if (members.size >= 2) { expected.add(members); remaining.removeAll(members.toSet()) } else remaining.remove(keeper)
            }
            assertEquals(expected, KeeperMatcher.match(records, 63, threshold).groups.map { it.toList() })
        }
    }
    @Test fun twentyThousandIdenticalFeaturesCollapseToOneNodeAndOneKeeperGroup() {
        val records = List(20_000) { VisualRecord("uri:${it.toString().padStart(5, '0')}", 42, 1f) }
        val result = KeeperMatcher.match(records, 63, .85f)
        assertEquals(1, result.metrics.uniqueHashes)
        assertEquals(1L, result.metrics.comparisons)
        assertEquals(20_000, result.groups.single().size)
    }
    @Test fun twentyThousandIndependentHashesDoNotRequireAllPairsAtDefaultThreshold() {
        val random = Random(413)
        val records = List(20_000) { VisualRecord("uri:$it", random.nextLong() and Long.MAX_VALUE, 1f) }
        val result = KeeperMatcher.match(records, 63, .85f)
        assertTrue(result.metrics.comparisons < 20_000L * 20_000 / 100)
        println("PHASE4 unique20k comparisons=${result.metrics.comparisons} lookups=${result.metrics.bucketLookups}")
    }
    @Test fun linearPairMeanMatchesExhaustiveMean() {
        val data = LongArray(257) { Random(it).nextLong() }
        var total = 0.0; var pairs = 0
        for (i in data.indices) for (j in i + 1 until data.size) { total += 1.0 - (data[i] xor data[j]).countOneBits() / 64.0; pairs++ }
        assertEquals((total / pairs).toFloat(), HammingIndex.meanSimilarity(data, 64), .000001f)
    }
    @Test fun separableDctMatchesDirectReference() {
        val gray = DoubleArray(1024) { Random(it + 401).nextDouble(256.0) }
        val actual = PerceptualDct.coefficients(gray)
        for (u in 0 until 8) for (v in 0 until 8) {
            var sum = 0.0
            for (x in 0 until 32) for (y in 0 until 32)
                sum += gray[x * 32 + y] * cos((2 * x + 1) * u * Math.PI / 64) * cos((2 * y + 1) * v * Math.PI / 64)
            sum *= (if (u == 0) 1 / sqrt(32.0) else sqrt(2.0 / 32)) * (if (v == 0) 1 / sqrt(32.0) else sqrt(2.0 / 32))
            assertEquals("u=$u v=$v", sum, actual[u * 8 + v], .0000001)
        }
        assertTrue(PerceptualDct.hash(gray) >= 0)
    }
    @Test fun flatImagesHaveNoArtificialPerceptualSignature() {
        assertEquals(0L, PerceptualDct.hash(DoubleArray(1024) { 160.0 }))
        assertEquals(0L, PerceptualDct.hash(DoubleArray(1024)))
    }
    @Test fun panoramaAndLargeDimensionsStayInsideDecodeBudget() {
        for ((w, h) in listOf(80_000 to 1, 1 to 80_000, 20_000 to 20_000, Int.MAX_VALUE to Int.MAX_VALUE, 4032 to 3024)) {
            val sample = DecodeBudget.sampleSize(w, h, 256)
            val sw = (w.toLong() + sample - 1) / sample; val sh = (h.toLong() + sample - 1) / sample
            assertTrue(sw <= 256 && sh <= 256 && sw * sh <= 65536)
            assertEquals(1, sample.countOneBits())
        }
    }
    @Test fun cpuStagesPropagateCancellation() {
        val cancel = { throw CancellationException("test cancellation") }
        val operations = listOf<() -> Unit>(
            { PerceptualDct.hash(DoubleArray(1024), cancel) },
            { HammingIndex(longArrayOf(1, 2), 63, 9).neighbours(1, cancel) },
            { HammingIndex(longArrayOf(1, 2), 63, 20).neighbours(1, cancel) },
            { HammingIndex.meanSimilarity(LongArray(20_000), 63, cancel) },
            { KeeperMatcher.match(List(1000) { VisualRecord("$it", it.toLong(), 1f) }, 63, .85f, cancel) }
        )
        operations.forEach { operation -> try { operation(); fail("Cancellation swallowed") } catch (_: CancellationException) { } }
    }
}
