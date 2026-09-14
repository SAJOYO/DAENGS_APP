package com.daengs.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 링크 토큰이 프로세스에 하나로 모이는 규칙과, 로그인 중 링크로 생긴 두 번째 MainActivity 가
 * 넘기고 닫히는 판단, 대기 초대를 열 차례의 판단.
 */
class InviteInboxTest {

    private val token = "abc_DEF-123"
    private val other = "zzz_YYY-999"

    @Test
    fun `나중 링크가 이기고 가져가면 비운다`() {
        val inbox = InviteInbox()
        inbox.receive(token)
        inbox.receive(other)

        assertEquals(other, inbox.take())
        assertNull(inbox.pendingToken)
        assertNull(inbox.take())
    }

    @Test
    fun `같은 링크가 두 번 와도 하나다`() {
        val inbox = InviteInbox()
        inbox.receive(token)
        inbox.receive(token)

        assertEquals(token, inbox.take())
        assertNull(inbox.take())
    }

    /** 로그아웃·계정 전환은 받아 둔 토큰까지 버린다 — 다음 계정으로 이어지면 안 된다. */
    @Test
    fun `로그아웃은 받아 둔 토큰을 버린다`() {
        val inbox = InviteInbox()
        inbox.receive(token)

        inbox.signOut()

        assertNull(inbox.pendingToken)
    }

    @Test
    fun `다시 로그인하러 가면 이어지는 로그아웃 한 번만 토큰을 넘긴다`() {
        val inbox = InviteInbox()
        inbox.holdForLogin(token)

        inbox.signOut()
        assertEquals(token, inbox.pendingToken)

        inbox.signOut()
        assertNull("표시는 한 번만 먹는다", inbox.pendingToken)
    }

    /** 두 인스턴스가 같은 저장소를 보면, 한쪽이 받은 토큰을 다른 쪽이 연다. */
    @Test
    fun `같은 저장소를 쓰는 두 화면은 같은 대기 초대를 본다`() {
        val inbox = InviteInbox()
        val original = InviteEntryViewModel(InviteAcceptHolder(InviteAcceptApi { "http://127.0.0.1:1" }), inbox)
        val duplicate = InviteEntryViewModel(InviteAcceptHolder(InviteAcceptApi { "http://127.0.0.1:1" }), inbox)

        duplicate.receive(token)

        assertEquals(token, original.pendingToken)
        assertTrue(original.openFromLink())
        assertNull(duplicate.pendingToken)
    }

    @Test
    fun `살아 있는 화면 수를 센다`() {
        val inbox = InviteInbox()
        assertFalse(inbox.hasLiveEntry)

        inbox.register()
        assertTrue(inbox.hasLiveEntry)
        inbox.unregister()
        inbox.unregister()
        assertFalse("0 아래로 안 내려간다", inbox.hasLiveEntry)
    }

    // -- 두 번째 MainActivity 는 넘기고 닫힌다 ------------------------------------

    @Test
    fun `다른 화면이 살아 있을 때 링크로 새로 뜨면 넘기고 닫힌다`() {
        assertTrue(shouldHandOffInvite(restoring = false, hasInviteToken = true, otherEntryAlive = true))
    }

    @Test
    fun `처음 뜨는 링크 진입은 그대로 연다`() {
        assertFalse(shouldHandOffInvite(restoring = false, hasInviteToken = true, otherEntryAlive = false))
    }

    @Test
    fun `링크가 아니거나 복원이면 넘기지 않는다`() {
        assertFalse("런처·알림 진입", shouldHandOffInvite(restoring = false, hasInviteToken = false, otherEntryAlive = true))
        assertFalse("재생성·프로세스 복원", shouldHandOffInvite(restoring = true, hasInviteToken = true, otherEntryAlive = true))
    }

    // -- 대기 초대를 열 차례 (모의 인증 상태) ------------------------------------

    private fun step(
        pending: Boolean = true,
        restoring: Boolean = false,
        loggedIn: Boolean = true,
        blocked: Boolean = false,
        home: Boolean = true,
    ) = inviteOpenStep(pending, restoring, loggedIn, blocked, home)

    @Test
    fun `로그인 중이거나 취소·실패로 로그인 전이면 보관만 한다`() {
        assertEquals("로그인 화면(랜딩) — 로그인 진행 중", InviteOpenStep.Wait, step(loggedIn = false, blocked = true, home = false))
        assertEquals("취소·실패 뒤 여전히 로그인 전", InviteOpenStep.Wait, step(loggedIn = false, blocked = false, home = true))
    }

    @Test
    fun `로그인은 됐지만 닉네임 확인 같은 끊으면 안 되는 화면이면 기다린다`() {
        assertEquals(InviteOpenStep.Wait, step(loggedIn = true, blocked = true, home = false))
    }

    @Test
    fun `세션 복원 중에는 성급하게 열지 않는다`() {
        assertEquals(InviteOpenStep.Wait, step(restoring = true))
    }

    @Test
    fun `로그인을 마치고 홈에 닿으면 연다`() {
        assertEquals(InviteOpenStep.Open, step())
    }

    @Test
    fun `로그인된 채 다른 화면이면 홈으로 돌아와 연다`() {
        assertEquals(InviteOpenStep.GoHomeThenOpen, step(home = false))
    }

    @Test
    fun `대기 초대가 없으면 아무것도 안 한다`() {
        assertEquals(InviteOpenStep.Wait, step(pending = false))
    }
}
