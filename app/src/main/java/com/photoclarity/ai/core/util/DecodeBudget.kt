package com.photoclarity.ai.core.util

import com.photoclarity.ai.domain.model.AnalysisVersion

/** Pure bound calculation; uses ceiling dimensions and Long arithmetic for panoramas. */
object DecodeBudget {
    fun sampleSize(width: Int, height: Int, requestedEdge: Int): Int {
        require(width > 0 && height > 0 && requestedEdge > 0)
        val edge = requestedEdge.coerceAtMost(AnalysisVersion.DECODE_EDGE).toLong()
        var sample = 1L
        while (true) {
            val w = ((width.toLong() + sample - 1) / sample).coerceAtLeast(1)
            val h = ((height.toLong() + sample - 1) / sample).coerceAtLeast(1)
            if (w <= edge && h <= edge && w * h <= AnalysisVersion.DECODE_PIXELS) return sample.toInt()
            check(sample < (1L shl 30)) { "Image dimensions exceed supported sample range" }
            sample *= 2
        }
    }
}
