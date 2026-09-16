package com.daengs.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 링크로 받은 토큰이 화면으로 넘어가고, 닫히고, 로그아웃에 버려지는 규칙.
 * 재생성을 넘어 사는 것은 [InviteEntryRecreateTest] 가 본다.
 */
class InviteEntryViewModelTest {

    private val token = "abc_DEF-123"
    private val other = "zzz_YYY-999"

    // 프로세스 공용 저장소를 안 쓴다 — 테스트끼리 토큰이 새지 않게 새로 만든다.
    private fun model() = InviteEntryViewModel(InviteAcceptHolder(InviteAcceptApi { "http://127.0.0.1:1" }), InviteInbox())

    @Test
    fun `받아 둔 토큰은 열 때 홀더로 넘어가고 비워진다`() {
        val model = model()
        model.receive(token)
        assertEquals(token, model.pendingToken)
        assertFalse("받기만 해서는 화면이 안 뜬다", model.accepting)

        assertTrue(model.openFromLink())

        assertNull(model.pendingToken)
        assertTrue(model.accepting)
        assertTrue(model.autoEntered)
        assertEquals(InvitePaste.Result.Found(token), model.holder.parsed)
    }

    @Test
    fun `받아 둔 토큰이 없으면 여는 것도 없다`() {
        val model = model()

        assertFalse(model.openFromLink())

        assertFalse(model.accepting)
        assertEquals(InvitePaste.Result.Empty, model.holder.parsed)
    }

    /** 로그인을 기다리는 사이 다른 초대를 누르면 그것이 열려야 한다. */
    @Test
    fun `기다리는 동안 다른 링크가 오면 나중 것이 이긴다`() {
        val model = model()
        model.receive(token)
        model.receive(other)

        model.openFromLink()

        assertEquals(InvitePaste.Result.Found(other), model.holder.parsed)
    }

    /** 같은 링크가 두 번 와도(연타·재실행) 보고 있던 선택을 지우지 않는다. */
    @Test
    fun `열린 채로 같은 링크가 다시 오면 선택을 지우지 않는다`() {
        val model = model()
        model.receive(token)
        model.openFromLink()
        model.holder.choose("pet-1", PetChoice.Join)

        model.receive(token)
        model.openFromLink()

        assertEquals(mapOf("pet-1" to PetChoice.Join), model.holder.choices)
        assertTrue(model.autoEntered)
    }

    @Test
    fun `열린 채로 다른 링크가 오면 앞선 선택을 지운다`() {
        val model = model()
        model.receive(token)
        model.openFromLink()
        model.holder.choose("pet-1", PetChoice.Join)

        model.receive(other)
        model.openFromLink()

        assertEquals(InvitePaste.Result.Found(other), model.holder.parsed)
        assertEquals(emptyMap<String, PetChoice>(), model.holder.choices)
    }

    @Test
    fun `수동으로 열면 붙여넣기 화면이고 링크 진입 표시가 아니다`() {
        val model = model()

        model.openManually()

        assertTrue(model.accepting)
        assertFalse(model.autoEntered)
    }

    @Test
    fun `닫으면 토큰과 화면 상태를 같이 버린다`() {
        val model = model()
        model.receive(token)
        model.openFromLink()

        model.close()

        assertFalse(model.accepting)
        assertFalse(model.autoEntered)
        assertEquals(InvitePaste.Result.Empty, model.holder.parsed)
        assertEquals("", model.holder.pasted)
    }

    /** 이전 계정이 받아 둔(아직 안 연) 초대가 다음 계정 화면으로 이어지면 안 된다. */
    @Test
    fun `로그아웃은 아직 못 넘긴 토큰까지 버린다`() {
        val model = model()
        model.receive(token)

        model.signOut()

        assertNull(model.pendingToken)
        assertFalse(model.openFromLink())
    }

    @Test
    fun `로그아웃은 열려 있던 미리보기 선택도 버린다`() {
        val model = model()
        model.receive(token)
        model.openFromLink()
        model.holder.choose("pet-1", PetChoice.Join)

        model.signOut()

        assertFalse(model.accepting)
        assertEquals(emptyMap<String, PetChoice>(), model.holder.choices)
        assertEquals(InvitePaste.Result.Empty, model.holder.parsed)
    }

    /**
     * 로그인이 만료돼 다시 로그인하러 가면 **토큰만** 들고 간다. 미리보기·선택은 만료된 계정의
     * 후보로 만든 것이라 버리고, 곧 이어지는 로그아웃 정리도 그 토큰은 남긴다.
     */
    @Test
    fun `다시 로그인하러 가면 토큰만 남기고 미리보기와 선택은 버린다`() {
        val model = model()
        model.receive(token)
        model.openFromLink()
        model.holder.choose("pet-1", PetChoice.Join)
        model.reportAuth(InviteAuthProblem.LoginRequired)

        model.holdForLogin()
        model.signOut()

        assertEquals(token, model.pendingToken)
        assertFalse(model.accepting)
        assertNull(model.authProblem)
        assertEquals(emptyMap<String, PetChoice>(), model.holder.choices)
        assertEquals(InvitePaste.Result.Empty, model.holder.parsed)
    }

    /** 표시는 한 번만 먹는다 — 다시 로그인해 연 뒤의 로그아웃은 평소처럼 토큰까지 버린다. */
    @Test
    fun `다시 로그인한 뒤의 로그아웃은 토큰까지 버린다`() {
        val model = model()
        model.receive(token)
        model.openFromLink()
        model.holdForLogin()
        model.signOut()

        model.openFromLink()
        model.signOut()

        assertNull(model.pendingToken)
        assertEquals(InvitePaste.Result.Empty, model.holder.parsed)
    }

    @Test
    fun `세션 문제 안내는 화면을 닫거나 새 링크로 열면 지운다`() {
        val model = model()
        model.receive(token)
        model.openFromLink()
        model.reportAuth(InviteAuthProblem.Unreachable)
        assertEquals(InviteAuthProblem.Unreachable, model.authProblem)

        model.receive(other)
        model.openFromLink()
        assertNull(model.authProblem)

        model.reportAuth(InviteAuthProblem.Unreachable)
        model.close()
        assertNull(model.authProblem)
    }
}
