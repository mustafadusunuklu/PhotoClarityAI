package com.photoclarity.ai.core.analysis

/** Exact multi-index radius search. A radius-r neighbour must have at least one
 * of four disjoint blocks at distance <= floor(r/4). No approximate pruning.
 * Wide radii use an exhaustive bounded-memory fallback instead of mask explosion.
 */
class HammingIndex(input: LongArray, val bits: Int, val radius: Int) {
    val hashes = input.distinct().toLongArray()
    private val widths = IntArray(4) { bits / 4 + if (it < bits % 4) 1 else 0 }
    private val shifts = IntArray(4) { widths.take(it).sum() }
    private val tables = Array(4) { HashMap<Int, MutableList<Int>>() }
    private val masks = Array(4) { band -> if (radius <= 12) masks(widths[band], radius / 4) else intArrayOf() }
    private val seen = IntArray(hashes.size)
    private var stamp = 0
    var comparisons = 0L; private set
    var bucketLookups = 0L; private set

    init {
        require(bits in 1..64 && radius in 0..bits)
        require(bits == 64 || hashes.all { it ushr bits == 0L })
        if (radius <= 12) hashes.forEachIndexed { index, hash ->
            for (band in 0 until 4) tables[band].getOrPut(part(hash, band)) { ArrayList() }.add(index)
        }
    }
    private fun part(hash: Long, band: Int) = ((hash ushr shifts[band]) and ((1L shl widths[band]) - 1)).toInt()

    fun neighbours(hash: Long, checkCancelled: () -> Unit = {}, useful: (Long) -> Boolean = { true }): LongArray {
        checkCancelled()
        val found = ArrayList<Long>()
        fun check(index: Int) {
            if (comparisons % 256 == 0L) checkCancelled()
            val candidate = hashes[index]
            if (!useful(candidate)) return
            comparisons++
            if ((hash xor candidate).countOneBits() <= radius) found.add(candidate)
        }
        if (radius > 12) {
            hashes.indices.forEach { if (it % 256 == 0) checkCancelled(); check(it) }
        } else {
            if (stamp == Int.MAX_VALUE) { seen.fill(0); stamp = 0 }
            stamp++
            for (band in 0 until 4) {
                val key = part(hash, band)
                masks[band].forEach { mask ->
                    if (bucketLookups % 128 == 0L) checkCancelled()
                    bucketLookups++
                    tables[band][key xor mask]?.forEach { index ->
                        if (seen[index] != stamp) { seen[index] = stamp; check(index) }
                    }
                }
            }
        }
        return found.toLongArray()
    }

    companion object {
        fun radius(bits: Int, threshold: Float): Int {
            require(bits in 1..64)
            require(threshold.isFinite() && threshold in 0f..1f)
            return (0..bits).last { 1f - it.toFloat() / bits >= threshold }
        }
        private fun masks(width: Int, distance: Int): IntArray {
            val result = ArrayList<Int>()
            fun add(value: Int, from: Int, left: Int) {
                result.add(value)
                if (left > 0) for (bit in from until width) add(value or (1 shl bit), bit + 1, left - 1)
            }
            add(0, 0, distance)
            return result.toIntArray()
        }
        /** Exact all-pair mean in O(bits*n), without building or visiting pairs. */
        fun meanSimilarity(hashes: LongArray, bits: Int, checkCancelled: () -> Unit = {}): Float {
            require(bits in 1..64)
            if (hashes.size < 2) return 1f
            val ones = LongArray(bits)
            hashes.forEachIndexed { index, hash ->
                if (index % 256 == 0) checkCancelled()
                for (bit in 0 until bits) if (hash and (1L shl bit) != 0L) ones[bit]++
            }
            val size = hashes.size.toLong()
            val differences = ones.sumOf { it * (size - it) }
            val pairs = size * (size - 1) / 2
            return (1.0 - differences.toDouble() / pairs / bits).toFloat()
        }
    }
}
