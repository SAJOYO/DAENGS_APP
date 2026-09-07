package com.daengs.app.screening

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **옛 경로로 안 물러서는지** (#134).
 *
 * 예전에는 토큰이 없으면 `/screen/v1/screen` 으로 갔다. 그래서 이 상황에서도 판정이
 * 나왔고, 기록만 조용히 안 남았다. 지금은 판정을 포기하고 사용자에게 말해야 한다.
 *
 * 네트워크를 안 탄다 — 막는 판단이 **아무것도 부르기 전에** 나기 때문이다. 그게
 * 이 테스트가 확인하는 것이기도 하다. 옛 코드였다면 여기서 서버를 한 번 두드렸다.
 */
class ScreeningRunTest {

    @Test
    fun `토큰이 없으면 판정하지 않고 실패로 끝난다`() = runTest {
        val outcome = ScreeningRun(accessToken = { null })
            .run(petId = null, jpeg = ByteArray(8), box = null)

        val failed = outcome as? ScreeningRun.Outcome.Failed
        assertTrue("옛 경로로 물러섰다: $outcome", failed != null)
        assertEquals(ScreeningRun.NEEDS_LOGIN, failed!!.message)
    }
}
