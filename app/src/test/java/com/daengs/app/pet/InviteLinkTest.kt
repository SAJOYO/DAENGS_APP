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
     * **출시 빌드는 예전 그대로다.** 개발 API 로 뽑은 토큰에 운영 링크를 씌우면, 받는
     * 사람은 운영 서버에 그 토큰을 내밀고 404 를 받는다 — 링크가 조용히 죽는다.
     */
    @Test
    fun `출시 빌드는 운영 서버를 보고 있을 때만 만든다`() {
        assertNull(InviteLink.of(token, apiBaseUrl = dev, debug = false))
        assertNull(InviteLink.of(token, apiBaseUrl = "", debug = false))
        assertNull(InviteLink.of(token, apiBaseUrl = "https://example.com", debug = false))
        assertFalse(InviteLink.available(dev, debug = false))
    }

    /**
     * 디버그에서는 개발 서버로도 만든다 — **두 기기가 같은 서버를 보면 토큰이 통한다.**
     * 실연동을 보려고 운영 API 로 갈아타지 않아도 되게 하려는 것이다.
     */
    @Test
    fun `디버그 빌드는 개발 서버로도 만든다`() {
        assertEquals(
            "https://daengapi.weareithero.cloud/invite#$token",
            InviteLink.of(token, apiBaseUrl = dev, debug = true),
        )
        assertTrue(InviteLink.available(dev, debug = true))
    }

    /**
     * **확인된 개발 호스트 하나만 연다.** 도메인 접미사로 열면 아직 없는 호스트나 다른
     * 용도의 호스트까지 들어오고, 그런 주소로 만든 초대는 어디에도 없는 채로 남아 상한
     * 자리만 먹는다.
     */
    @Test
    fun `디버그여도 확인된 개발 호스트가 아니면 막는다`() {
        // 같은 도메인 아래여도 다른 호스트는 안 된다.
        assertNull(InviteLink.of(token, apiBaseUrl = "https://staging.weareithero.cloud", debug = true))
        assertNull(InviteLink.of(token, apiBaseUrl = "https://daengback2.weareithero.cloud", debug = true))
        assertFalse(InviteLink.available("https://other.weareithero.cloud", debug = true))

        // 도메인 밖은 당연히 안 된다. 마지막은 접미사 검사를 속이는 모양이다.
        assertNull(InviteLink.of(token, apiBaseUrl = "https://example.com", debug = true))
        assertNull(InviteLink.of(token, apiBaseUrl = "https://evil.weareithero.cloud.attacker.io", debug = true))
        assertNull(InviteLink.of(token, apiBaseUrl = "", debug = true))
    }

    /** 링크 글자는 운영과 같아서, 개발용이라는 것을 화면이 따로 말해야 한다. */
    @Test
    fun `개발 서버로 만든 링크는 개발 전용으로 표시된다`() {
        assertTrue(InviteLink.devOnly(dev, debug = true))
        assertFalse("운영은 개발 전용이 아니다", InviteLink.devOnly(prod, debug = true))
        assertFalse("출시 빌드에서는 애초에 못 만든다", InviteLink.devOnly(dev, debug = false))
    }

    /**
     * **붙여넣기 규칙은 안 바뀐다.** 링크 호스트와 토큰 검증은 그대로라, 개발 환경이라고
     * 아무 링크나 받아 주지 않는다.
     */
    @Test
    fun `파싱은 디버그와 무관하게 그대로다`() {
        assertEquals(token, InviteLink.tokenOf("https://daengapi.weareithero.cloud/invite#$token"))
        assertNull(InviteLink.tokenOf("https://daengback.weareithero.cloud/invite#$token"))
        assertNull(InviteLink.tokenOf("http://daengapi.weareithero.cloud/invite#$token"))
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
