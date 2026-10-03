package com.photoclarity.ai.core.media

/** Decisions are independent of Android; grants must always come from the OS. */
enum class PhotoAccess { FULL, LIMITED, DENIED }

object PhotoAccessPolicy {
    fun resolve(sdk: Int, legacy: Boolean, images: Boolean, selected: Boolean): PhotoAccess = when {
        sdk >= 33 && images -> PhotoAccess.FULL
        sdk >= 34 && selected -> PhotoAccess.LIMITED
        sdk < 33 && legacy -> PhotoAccess.FULL
        else -> PhotoAccess.DENIED
    }
}
