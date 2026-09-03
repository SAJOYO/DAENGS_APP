package com.daengs.app.walk.sync

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    @Test
    fun `로컬 완료 뒤에만 같은 세션의 전달을 예약한다`() = runBlocking {
        val calls = mutableListOf<String>()

        completeAndEnqueueWalk(
            sessionId = "walk-1",
            complete = { calls += "complete"; true },
            enqueue = { calls += "enqueue:$it" },
            onCompletionFailure = { fail("완료 실패가 아니다") },
            onEnqueueFailure = { fail("예약 실패가 아니다") },
        )

        assertEquals(listOf("complete", "enqueue:walk-1"), calls)
    }

    @Test
    fun `산책으로 인정하지 않은 기록은 전달하지 않는다`() = runBlocking {
        var enqueued = false

        completeAndEnqueueWalk(
            sessionId = "walk-1",
            complete = { false },
            enqueue = { enqueued = true },
            onCompletionFailure = { fail("완료 실패가 아니다") },
            onEnqueueFailure = { fail("예약 실패가 아니다") },
        )

        assertFalse(enqueued)
    }

    @Test
    fun `로컬 완료 실패는 결과 실패로 보고하고 전달하지 않는다`() = runBlocking {
        val expected = IllegalStateException("room")
        var reported: Throwable? = null
        var enqueued = false

        completeAndEnqueueWalk(
            sessionId = "walk-1",
            complete = { throw expected },
            enqueue = { enqueued = true },
            onCompletionFailure = { reported = it },
            onEnqueueFailure = { fail("예약까지 가지 않는다") },
        )

        assertSame(expected, reported)
        assertFalse(enqueued)
    }

    @Test
    fun `전달 예약 실패는 이미 끝난 산책을 실패로 되돌리지 않는다`() = runBlocking {
        val expected = IOException("work manager")
        var completionFailure: Throwable? = null
        var enqueueFailure: Throwable? = null

        completeAndEnqueueWalk(
            sessionId = "walk-1",
            complete = { true },
            enqueue = { throw expected },
            onCompletionFailure = { completionFailure = it },
            onEnqueueFailure = { enqueueFailure = it },
        )

        assertEquals(null, completionFailure)
        assertSame(expected, enqueueFailure)
    }

    @Test
    fun `취소는 완료나 예약 실패로 삼키지 않는다`() = runBlocking {
        val expected = CancellationException("cancel")

        try {
            completeAndEnqueueWalk(
                sessionId = "walk-1",
                complete = { true },
                enqueue = { throw expected },
                onCompletionFailure = { fail("취소를 완료 실패로 바꾸면 안 된다") },
                onEnqueueFailure = { fail("취소를 예약 실패로 바꾸면 안 된다") },
            )
            fail("CancellationException이 다시 던져져야 한다")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }
}
