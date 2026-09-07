package com.daengs.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 몸무게 칸이 받는 값.
 *
 * 비공개 테스트에서 18자리를 넣었더니 저쪽 검증 오류가 JSON 그대로 화면에 찍혔다.
 * 막을 자리는 화면이므로, **무엇을 받고 무엇을 막는지**를 여기서 못 박는다.
 */
class PetWeightTest {

    @Test
    fun `보통 몸무게는 그대로 받는다`() {
        listOf("4.2", "0.5", "12", "199.9", "200").forEach {
            assertTrue("$it 은 찍혀야 한다", PetWeight.accepts(it))
            assertNull("$it 은 보낼 수 있어야 한다", PetWeight.errorOf(it))
        }
    }

    @Test
    fun `빈 칸은 잘못이 아니다`() {
        // 이름 말고는 전부 비울 수 있는 것이 이 폼의 규칙이다.
        assertTrue(PetWeight.accepts(""))
        assertNull(PetWeight.errorOf(""))
        assertNull(PetWeight.errorOf("   "))
    }

    @Test
    fun `말이 안 되는 길이는 찍히지도 않는다`() {
        // 실제로 들어갔던 값.
        assertFalse(PetWeight.accepts("800000000000000000"))
        assertFalse(PetWeight.accepts("2000"))
        assertFalse(PetWeight.accepts("1.234"))
    }

    @Test
    fun `지우는 중에도 막지 않는다`() {
        // 한 글자씩 지우다 보면 이런 모양을 지난다. 여기서 막으면 지울 수가 없다.
        listOf("", "4", "4.", "12.", "0"). forEach {
            assertTrue("$it 은 찍혀야 한다", PetWeight.accepts(it))
        }
    }

    @Test
    fun `한계를 넘으면 이유를 말한다`() {
        val why = PetWeight.errorOf("201")
        assertNotNull(why)
        assertTrue("한계를 문장에 담아야 한다: $why", why!!.contains("${PetWeight.MAX_KG}"))
    }

    @Test
    fun `0 이하는 막는다`() {
        assertNotNull(PetWeight.errorOf("0"))
        assertNotNull(PetWeight.errorOf("0.0"))
    }

    @Test
    fun `숫자가 아니면 막는다`() {
        // `accepts` 가 먼저 걸러 주지만, 붙여넣기처럼 다른 길로 들어올 수 있다.
        assertEquals("숫자로 적어 주세요.", PetWeight.errorOf("."))
        assertEquals("숫자로 적어 주세요.", PetWeight.errorOf("abc"))
    }

    @Test
    fun `한계는 저쪽 계약과 같다`() {
        // 422 본문의 `ctx.le` 가 200 이었다. 저쪽이 바꾸면 이 테스트가 먼저 깨진다.
        assertEquals(200, PetWeight.MAX_KG)
        assertNull(PetWeight.errorOf("200"))
        assertNotNull(PetWeight.errorOf("200.01"))
    }
}
