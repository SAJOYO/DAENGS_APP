package com.daengs.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 방 이름표 짓는 규칙.
 *
 * **서버가 아니라 여기서 짓는다.** 받침에 따라 "이네"/"네" 가 갈리는 것은 한국어
 * 규칙이라, 서버가 같이 지으면 규칙이 두 벌이 되어 언젠가 갈라진다.
 */
class RoomLabelTest {

    /** 받침이 있으면 "이네" 다. */
    @Test
    fun `받침이 있으면 이네를 붙인다`() {
        assertEquals("네옹이네", defaultRoomLabel("네옹"))
        assertEquals("초코롱이네", defaultRoomLabel("초코롱"))
        // "댕댕" 의 "댕" 에도 받침(ㅇ)이 있다. 그래서 "댕댕이네" 다.
        assertEquals("댕댕이네", defaultRoomLabel("댕댕"))
    }

    /** 받침이 없으면 "네" 다 — "댕댕이네"는 소리가 안 맞는다. */
    @Test
    fun `받침이 없으면 네만 붙인다`() {
        assertEquals("보리네", defaultRoomLabel("보리"))
        assertEquals("코코네", defaultRoomLabel("코코"))
    }

    /**
     * 한글이 아닌 이름은 "네" 만 붙인다.
     *
     * 영문의 받침을 우리가 알 수 없고, 소리를 흉내 내려다 틀리면 자기 개 이름이
     * 이상해 보인다.
     */
    @Test
    fun `영문 이름은 네만 붙인다`() {
        assertEquals("Max네", defaultRoomLabel("Max"))
    }

    /** 등록한 강아지가 없으면 아무 이름이나 지어내지 않는다. */
    @Test
    fun `강아지가 없으면 우리집이다`() {
        assertEquals(FALLBACK, defaultRoomLabel(null))
        assertEquals(FALLBACK, defaultRoomLabel("   "))
    }

    @Test
    fun `사용자가 정한 이름이 이긴다`() {
        assertEquals("우리 아지트", roomLabel("우리 아지트", "네옹"))
    }

    /**
     * 비운 이름은 **안 정한 것과 같다.**
     *
     * 서버도 빈 문자열을 안 받는다 — "아직 안 정했다" 와 "정해서 지웠다" 가 같은
     * 값이 되면 무엇을 그릴지 못 정한다.
     */
    @Test
    fun `비운 이름은 지어진 이름으로 돌아간다`() {
        assertEquals("보리네", roomLabel(null, "보리"))
        assertEquals("보리네", roomLabel("   ", "보리"))
    }
}
