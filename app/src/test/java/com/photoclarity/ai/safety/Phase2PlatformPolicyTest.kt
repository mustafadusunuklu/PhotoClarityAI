package com.photoclarity.ai.safety

import com.photoclarity.ai.core.media.PhotoAccess
import com.photoclarity.ai.core.media.PhotoAccessPolicy
import com.photoclarity.ai.core.media.RemovalBatchPolicy
import org.junit.Assert.*
import org.junit.Test

class Phase2PlatformPolicyTest {
    @Test fun permissionMatrixUsesOnlyTheGrantForThatVersion() {
        for (sdk in listOf(26, 28, 29, 30, 32, 33, 34, 35, 36, 37)) {
            assertEquals(PhotoAccess.DENIED, PhotoAccessPolicy.resolve(sdk, false, false, false))
            assertEquals(if (sdk < 33) PhotoAccess.FULL else PhotoAccess.DENIED,
                PhotoAccessPolicy.resolve(sdk, true, false, false))
            assertEquals(if (sdk >= 33) PhotoAccess.FULL else PhotoAccess.DENIED,
                PhotoAccessPolicy.resolve(sdk, false, true, false))
            assertEquals(if (sdk >= 34) PhotoAccess.LIMITED else PhotoAccess.DENIED,
                PhotoAccessPolicy.resolve(sdk, false, false, true))
        }
    }
    @Test fun fullGrantTakesPrecedenceOverSelectedPhotoGrant() {
        assertEquals(PhotoAccess.FULL, PhotoAccessPolicy.resolve(36, false, true, true))
    }
    @Test fun requestCountsRespectBoundaryAndDoNotOverflow() {
        assertEquals(0, RemovalBatchPolicy.count(0))
        assertEquals(1, RemovalBatchPolicy.count(1))
        assertEquals(1, RemovalBatchPolicy.count(2000))
        assertEquals(2, RemovalBatchPolicy.count(2001))
        assertEquals(2, RemovalBatchPolicy.count(4000))
        assertEquals(3, RemovalBatchPolicy.count(4001))
        assertEquals(1073742, RemovalBatchPolicy.count(Int.MAX_VALUE))
    }
}
