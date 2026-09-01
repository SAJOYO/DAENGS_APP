package com.daengs.app.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 찍을 때 보여 주는 네모와, 찍고 나서 하는 판정이 **같은 숫자에서 나오는지.**
 *
 * 둘이 갈라지면 "가이드에 딱 맞춰 찍었는데 너무 작아요" 가 뜬다. 밴드 값은 저쪽이
 * 실측한 것이라 언젠가 바뀌는데, 그때 한쪽만 따라가는 것을 여기서 잡는다.
 */
class GuideBandTest {

    /** 찍을 때 그리는 네모가 판정에서 나쁨이면, 맞춰 찍은 사람이 퇴짜를 맞는다. */
    @Test
    fun `가이드 네모는 판정에서 나쁨이 아니다`() {
        val hint = Band.hintFor(Band.CAPTURE_WIDTH, centerOff = 0f)
        assertFalse(hint.text, hint.bad)
    }

    /** 권장 밴드 한가운데라 "딱 좋아요" 여야 한다 — 겨우 통과하는 값이 아니다. */
    @Test
    fun `가이드 네모는 권장 밴드 안이다`() {
        assertTrue(Band.CAPTURE_WIDTH.toString(), Band.hintFor(Band.CAPTURE_WIDTH, 0f).text.startsWith("딱 좋아요"))
    }

    @Test
    fun `너무 작거나 크면 나쁨이다`() {
        assertTrue(Band.hintFor(0.20f, 0f).bad)
        assertTrue(Band.hintFor(0.80f, 0f).bad)
    }

    /** 크기가 맞아도 가운데를 벗어나면 나쁨이다. 서버가 중심을 본다. */
    @Test
    fun `가운데를 벗어나면 나쁨이다`() {
        assertFalse(Band.hintFor(Band.CAPTURE_WIDTH, 0.09f).bad)
        assertTrue(Band.hintFor(Band.CAPTURE_WIDTH, 0.11f).bad)
    }
}
