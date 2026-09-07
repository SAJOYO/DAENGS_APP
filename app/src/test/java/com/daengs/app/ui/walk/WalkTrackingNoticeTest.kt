package com.daengs.app.ui.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 산책 안내가 **잠깐 떴다 사라지는가.**
 *
 * 예전에는 지우는 곳이 아예 없어서, 거리가 짧아 기록이 안 된 날의 붉은 알림이 다음
 * 산책을 시작할 때까지 화면에 붙어 있었다. 다시 걸으려고 들어와도 지난 실패가 먼저
 * 보인다.
 */
class WalkTrackingNoticeTest {

    private val tooShort = "이동 거리나 시간이 너무 짧아서 산책으로 기록하지 않았어요."

    @Test
    fun `아직 안 거뒀으면 보여 준다`() {
        assertEquals(tooShort, visibleTrackingError(tooShort, dismissed = null))
    }

    @Test
    fun `거둔 말은 다시 안 보여 준다`() {
        assertNull(visibleTrackingError(tooShort, dismissed = tooShort))
    }

    @Test
    fun `말이 바뀌면 다시 보여 준다`() {
        // 다음 산책이 다른 이유로 실패했으면 그건 새 소식이다.
        assertEquals(
            "산책을 저장하지 못했어요.",
            visibleTrackingError("산책을 저장하지 못했어요.", dismissed = tooShort),
        )
    }

    @Test
    fun `안내가 없으면 없는 것이다`() {
        assertNull(visibleTrackingError(null, dismissed = null))
        assertNull(visibleTrackingError(null, dismissed = tooShort))
    }
}
