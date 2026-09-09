package com.daengs.app.ui.pet

import com.daengs.app.pet.PetDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 별표가 붙는 칸과 **실제로 요구하는 칸이 같은가.**
 *
 * 갈리면 화면은 "선택" 이라고 해 놓고 저장 버튼이 안 눌린다 — 사용자는 무엇이
 * 모자란지 모른 채로 막힌다.
 */
class RequiredFieldsTest {

    private fun draft(name: String = "네옹", breed: String = "beagle") = PetDraft(
        name = name,
        breed = breed,
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = null,
        birthDateKind = null,
    )

    @Test
    fun `별표는 이름과 견종에만 붙는다`() {
        assertTrue("이름" in REQUIRED_FIELDS)
        assertTrue("견종" in REQUIRED_FIELDS)
        assertFalse("성별" in REQUIRED_FIELDS)
        assertFalse("중성화" in REQUIRED_FIELDS)
        assertFalse("생일" in REQUIRED_FIELDS)
        // 돌봄 칸(#200)도 선택이다. 모르는 지병을 필수로 하면 아무 말이나 적는다.
        assertFalse("급식 방식" in REQUIRED_FIELDS)
        assertFalse("앓는 병" in REQUIRED_FIELDS)
        assertFalse("먹는 약" in REQUIRED_FIELDS)
    }

    @Test
    fun `별표 붙은 칸만 채우면 보낼 수 있다`() {
        // 나머지를 다 비워도 통과해야 "선택" 이라는 말이 참이 된다.
        assertTrue(draft().valid)
    }

    @Test
    fun `별표 붙은 칸이 비면 못 보낸다`() {
        assertFalse("이름", draft(name = " ").valid)
        assertFalse("견종", draft(breed = "").valid)
    }
}
