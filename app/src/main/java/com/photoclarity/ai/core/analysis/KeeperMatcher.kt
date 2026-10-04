package com.photoclarity.ai.core.analysis

data class VisualRecord(val key: String, val hash: Long, val quality: Float)
data class MatchMetrics(val uniqueHashes: Int, val comparisons: Long, val bucketLookups: Long)
data class KeeperMatches(val groups: List<IntArray>, val metrics: MatchMetrics)

/** Disjoint keeper stars: every member meets the threshold against its keeper.
 * A-B and B-C do not imply A-C. No recursive union-find or stored pair graph.
 */
object KeeperMatcher {
    fun match(records: List<VisualRecord>, bits: Int, threshold: Float, checkCancelled: () -> Unit = {}): KeeperMatches {
        require(records.map { it.key }.distinct().size == records.size)
        val byHash = records.indices.groupBy { records[it].hash }
        val remaining = byHash.mapValues { it.value.size }.toMutableMap()
        val index = HammingIndex(byHash.keys.toLongArray(), bits, HammingIndex.radius(bits, threshold))
        val assigned = BooleanArray(records.size)
        val order = records.indices.sortedWith(compareByDescending<Int> { records[it].quality }.thenBy { records[it].key })
        val groups = ArrayList<IntArray>()
        for (keeper in order) {
            checkCancelled()
            if (assigned[keeper]) continue
            val members = ArrayList<Int>()
            index.neighbours(records[keeper].hash, checkCancelled) { (remaining[it] ?: 0) > 0 }.forEach { hash ->
                byHash.getValue(hash).forEach { if (!assigned[it]) members.add(it) }
            }
            if (members.size >= 2) {
                val sorted = members.sortedWith(compareByDescending<Int> { records[it].quality }.thenBy { records[it].key })
                groups.add(sorted.toIntArray())
                sorted.forEach { assigned[it] = true; val hash = records[it].hash; remaining[hash] = remaining.getValue(hash) - 1 }
            } else { assigned[keeper] = true; val hash = records[keeper].hash; remaining[hash] = remaining.getValue(hash) - 1 }
        }
        return KeeperMatches(groups, MatchMetrics(index.hashes.size, index.comparisons, index.bucketLookups))
    }
}
