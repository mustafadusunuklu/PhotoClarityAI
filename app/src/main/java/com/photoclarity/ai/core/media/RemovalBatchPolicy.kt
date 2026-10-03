package com.photoclarity.ai.core.media

object RemovalBatchPolicy {
    // Required for target API 36+; use the same safe cap on every system-trash version.
    const val MAX_TRASH_URIS = 2000
    fun count(photoCount: Int): Int = if (photoCount <= 0) 0 else (photoCount - 1) / MAX_TRASH_URIS + 1
}
