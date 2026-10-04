package com.photoclarity.ai.domain.model

/** Bump whenever decoding, feature math or grouping semantics change. */
object AnalysisVersion {
    const val CURRENT = 2
    const val WORKERS = 4
    const val CACHE_BATCH = 256
    const val DECODE_EDGE = 256
    const val DECODE_PIXELS = 65_536
}
