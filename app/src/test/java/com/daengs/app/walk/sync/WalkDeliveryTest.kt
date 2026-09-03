package com.daengs.app.walk.sync

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkDeliveryTest {
    @Test
    fun `같은 세션은 같은 unique work 이름을 쓴다`() {
        assertTrue(walkDeliveryWorkName("walk-1") == walkDeliveryWorkName("walk-1"))
        assertFalse(walkDeliveryWorkName("walk-1") == walkDeliveryWorkName("walk-2"))
    }

    @Test
    fun `작업은 연결된 네트워크와 지수 backoff를 요구한다`() {
        val spec = walkDeliveryRequest("walk-1").workSpec

        assertEquals("walk-1", spec.input.getString(KEY_SESSION_ID))
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
    }

    @Test
    fun `네트워크와 일시 서버 오류만 재시도한다`() {
        assertTrue(IOException("offline").isRetryableWalkDeliveryFailure())
        assertTrue(
            IllegalStateException("wrapped", IOException("timeout"))
                .isRetryableWalkDeliveryFailure(),
        )
        assertTrue(WalkHttpException(429, "later").isRetryableWalkDeliveryFailure())
        assertTrue(WalkHttpException(503, "down").isRetryableWalkDeliveryFailure())
        assertFalse(WalkHttpException(401, "expired").isRetryableWalkDeliveryFailure())
        assertFalse(IllegalStateException("contract").isRetryableWalkDeliveryFailure())
    }
}
