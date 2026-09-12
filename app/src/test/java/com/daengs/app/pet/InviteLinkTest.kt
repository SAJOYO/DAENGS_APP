package com.daengs.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteLinkTest {

    private val prod = "https://daengapi.weareithero.cloud"
    private val dev = "http://daengback.weareithero.cloud"
    private val token = "abc_DEF-123"

    @Test
    fun `토큰을 경로가 아니라 프래그먼트에 싣는다`() {
        val link = InviteLink.of(token, apiBaseUrl = prod)

        assertEquals("https://daengapi.weareithero.cloud/invite#$token", link)
        // 경로에 실리면 서버 로그·Referer 에 토큰이 남는다.
        assertFalse(link!!.contains("/invite/$token"))
    }

    /**
     * **개발 API 로 뽑은 토큰에 운영 링크를 씌우면 안 된다.** 받는 사람은 운영 서버에
     * 그 토큰을 내밀고 404 를 받는다 — 링크가 조용히 죽는다.
     */
    @Test
    fun `운영 서버를 보고 있지 않으면 링크를 만들지 않는다`() {
        assertNull(InviteLink.of(token, apiBaseUrl = dev))
        assertNull(InviteLink.of(token, apiBaseUrl = ""))
        assertNull(InviteLink.of(token, apiBaseUrl = "https://example.com"))
    }

    @Test
    fun `빈 토큰이나 이상한 토큰으로는 링크를 만들지 않는다`() {
        assertNull(InviteLink.of("", apiBaseUrl = prod))
        assertNull(InviteLink.of("has space", apiBaseUrl = prod))
        assertNull(InviteLink.of("슬래시/포함", apiBaseUrl = prod))
        assertNull(InviteLink.of("x".repeat(201), apiBaseUrl = prod))
    }

    @Test
    fun `우리 링크의 프래그먼트에서 토큰을 꺼낸다`() {
        assertEquals(token, InviteLink.tokenOf("https://daengapi.weareithero.cloud/invite#$token"))
        assertEquals(token, InviteLink.tokenOf("https://daengapi.weareithero.cloud/invite/#$token"))
    }

    @Test
    fun `우리 링크가 아니면 토큰을 꺼내지 않는다`() {
        assertNull("http 는 App Links 가 아니다", InviteLink.tokenOf("http://daengapi.weareithero.cloud/invite#$token"))
        assertNull("남의 호스트", InviteLink.tokenOf("https://evil.example.com/invite#$token"))
        assertNull("다른 경로", InviteLink.tokenOf("https://daengapi.weareithero.cloud/other#$token"))
        assertNull("커스텀 스킴은 안 쓴다", InviteLink.tokenOf("daengs://invite?token=$token"))
        assertNull(InviteLink.tokenOf(null))
        assertNull(InviteLink.tokenOf("이건 URI 가 아니다"))
    }

    /** 경로에 실려 온 토큰은 우리 계약이 아니다 — 받아 주면 계약이 둘이 된다. */
    @Test
    fun `경로에 실린 토큰은 받지 않는다`() {
        assertNull(InviteLink.tokenOf("https://daengapi.weareithero.cloud/invite/$token"))
    }

    @Test
    fun `프래그먼트가 비면 토큰이 없다`() {
        assertNull(InviteLink.tokenOf("https://daengapi.weareithero.cloud/invite"))
        assertNull(InviteLink.tokenOf("https://daengapi.weareithero.cloud/invite#"))
    }

    @Test
    fun `토큰 모양 검사`() {
        assertTrue(InviteLink.isValidToken("abc_DEF-123"))
        assertFalse(InviteLink.isValidToken(""))
        assertFalse(InviteLink.isValidToken(null))
        assertFalse(InviteLink.isValidToken("a b"))
    }
}
