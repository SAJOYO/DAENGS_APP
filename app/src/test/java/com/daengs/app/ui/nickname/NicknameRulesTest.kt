package com.daengs.app.ui.nickname

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 닉네임의 셈. **화면 없이 잡을 수 있는 것을 여기서 다 잡는다** — 이 화면은
 * 로그인해야 뜨는데 이 폰의 디버그 빌드로는 카카오 로그인이 안 돼서, 실기기 확인이
 * 안 되는 자리다.
 */
class NicknameRulesTest {

    // -- 모양 ----------------------------------------------------------------

    @Test
    fun `보통 이름은 통과한다`() {
        assertNull(nicknameErrorOf("네옹집사"))
        assertNull(nicknameErrorOf("Neo"))
        assertNull(nicknameErrorOf("댕댕이7K2Q"))
    }

    @Test
    fun `비어 있으면 막는다`() {
        assertNotNull(nicknameErrorOf(""))
        assertNotNull(nicknameErrorOf("   "))
    }

    /** 앞뒤 공백은 서버가 떼므로 여기서도 뗀 길이로 본다. */
    @Test
    fun `앞뒤 공백은 길이에 안 든다`() {
        assertNull(nicknameErrorOf("  네옹  "))
    }

    /** 서버 `app_users.nickname` 이 `VARCHAR(30)` 이다. 넘기면 저쪽이 422 를 준다. */
    @Test
    fun `서른자까지다`() {
        assertNull(nicknameErrorOf("가".repeat(MAX_NICKNAME)))
        assertNotNull(nicknameErrorOf("가".repeat(MAX_NICKNAME + 1)))
    }

    /** 서버는 받아 주지만 한 줄로 그리는 자리라 화면이 깨진다. */
    @Test
    fun `줄바꿈은 막는다`() {
        assertNotNull(nicknameErrorOf("네\n옹"))
        assertNotNull(nicknameErrorOf("네\r옹"))
    }

    /**
     * **여기서 서버보다 더 좁히지 않는다.**
     *
     * 서버가 발급하는 이름은 카카오 닉네임을 그대로 쓸 수 있는데, 거기에는 이모지도
     * 공백도 들어간다. 앱이 더 좁히면 **자기 이름을 자기가 다시 저장 못 하는** 일이
     * 생긴다 — 「마이」에서 고치려다 원래 이름으로 되돌리는 순간 막힌다.
     */
    @Test
    fun `이모지와 사이 공백은 막지 않는다`() {
        assertNull(nicknameErrorOf("네옹 집사"))
        assertNull(nicknameErrorOf("네옹🐶"))
    }

    @Test
    fun `accepts 는 errorOf 와 같은 답을 낸다`() {
        listOf("", "   ", "네옹", "가".repeat(31), "네\n옹").forEach {
            assertEquals(it, nicknameErrorOf(it) == null, nicknameAccepts(it))
        }
    }

    // -- 물어볼 때 -------------------------------------------------------------

    @Test
    fun `모양이 틀리면 안 물어본다`() {
        assertFalse(shouldAskAvailability("", current = null))
        assertFalse(shouldAskAvailability("가".repeat(31), current = null))
    }

    /**
     * **자기 이름에는 안 물어본다.** 물어보면 자기 자신에 걸려서 "다른 분이 쓰고
     * 있어요" 가 뜬다 — 고치다 원래 이름으로 되돌린 사람이 그것을 오류로 읽는다.
     * (서버도 자기 이름에는 `true` 를 주지만 그 왕복 자체가 낭비다.)
     */
    @Test
    fun `지금 쓰는 이름에는 안 물어본다`() {
        assertFalse(shouldAskAvailability("네옹", current = "네옹"))
        assertFalse(shouldAskAvailability("  네옹 ", current = "네옹"))
        // 서버 UNIQUE 가 lower() 라 대소문자만 다른 것도 자기 이름이다.
        assertFalse(shouldAskAvailability("NEO", current = "neo"))
    }

    @Test
    fun `다른 이름이면 물어본다`() {
        assertTrue(shouldAskAvailability("네옹", current = "댕댕이7K2Q"))
        assertTrue(shouldAskAvailability("네옹", current = null))
    }

    // -- 저장 버튼 -------------------------------------------------------------

    @Test
    fun `비어 있으면 저장 못 한다`() {
        assertFalse(nicknameSavable("", NicknameCheck.Free))
    }

    @Test
    fun `남이 쓰면 저장 못 한다`() {
        assertFalse(nicknameSavable("네옹", NicknameCheck.Taken))
    }

    @Test
    fun `확인하는 중에는 저장 못 한다`() {
        assertFalse(nicknameSavable("네옹", NicknameCheck.Checking))
    }

    /**
     * **못 물어봤어도 저장은 눌린다.**
     *
     * 확인 API 만 안 되는 날에 이름을 영영 못 바꾸면 안 된다. 진짜 방어는 서버의
     * `lower(nickname)` UNIQUE 인덱스와 409 이고, 그건 저장할 때 걸린다.
     */
    @Test
    fun `못 물어봤어도 저장은 눌린다`() {
        assertTrue(nicknameSavable("네옹", NicknameCheck.Unknown))
        assertTrue(nicknameSavable("네옹", NicknameCheck.Idle))
        assertTrue(nicknameSavable("네옹", NicknameCheck.Free))
    }

    // -- 문구 ------------------------------------------------------------------

    @Test
    fun `아무것도 안 물어본 상태는 조용하다`() {
        assertNull(nicknameCheckMessage(NicknameCheck.Idle))
    }

    @Test
    fun `나머지 상태는 다 할 말이 있다`() {
        listOf(
            NicknameCheck.Shape("길어요"),
            NicknameCheck.Checking,
            NicknameCheck.Free,
            NicknameCheck.Taken,
            NicknameCheck.Unknown,
        ).forEach { assertNotNull(it.toString(), nicknameCheckMessage(it)) }
    }

    /** 모양 오류는 **그 오류가 말한 그대로** 나와야 한다. 두 벌이면 어긋난다. */
    @Test
    fun `모양 오류는 받은 문장을 그대로 쓴다`() {
        assertEquals("길어요", nicknameCheckMessage(NicknameCheck.Shape("길어요")))
    }

    /**
     * **못 물어본 것과 비어 있는 것은 다른 말을 한다.**
     *
     * 이 둘이 같은 문구가 되면, 통신이 끊긴 날에 사용자가 "쓸 수 있다" 로 읽고
     * 저장을 눌렀다가 거절당한다.
     */
    @Test
    fun `못 물어본 것과 쓸 수 있는 것은 다르게 말한다`() {
        assertNotEquals(
            nicknameCheckMessage(NicknameCheck.Free),
            nicknameCheckMessage(NicknameCheck.Unknown),
        )
    }

    private fun assertNotEquals(a: Any?, b: Any?) = assertFalse("$a == $b", a == b)
}
