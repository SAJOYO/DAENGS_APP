package com.daengs.app.ui.chat

import com.daengs.app.assistant.WalkVerdict
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** 칩에 찍히는 시간 표기. 화면 없이 잡을 수 있는 부분만 본다. */
class WalkVerdictCardTest {

    private val noon = LocalDateTime.of(2026, 9, 2, 12, 0)

    @Test
    fun `좋은 시각이 하나뿐이면 범위로 안 쓴다`() {
        // 실기기에서 "12시~12시" 로 나온 것을 고친 자리다.
        val window = WalkVerdict.Window(noon, noon, WalkVerdict.Grade.GOOD)
        assertEquals("12시", windowSpan(window))
    }

    @Test
    fun `이어지면 범위로 쓴다`() {
        val window = WalkVerdict.Window(noon, noon.plusHours(3), WalkVerdict.Grade.GOOD)
        assertEquals("12시~15시", windowSpan(window))
    }
}
