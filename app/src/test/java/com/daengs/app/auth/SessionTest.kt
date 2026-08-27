package com.daengs.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 세션이 살아 있는지 판정하는 규칙.
 *
 * 여기가 틀리면 **말없이 로그아웃되거나, 만료된 토큰으로 요청을 보내 401 을 맞는다.**
 * 둘 다 화면에서는 "가끔 로그인이 풀린다"로만 보여서 원인을 찾기 어렵다.
 *
 * 서버가 없어도 도는 순수 계산이라 단위 테스트로 잡아 둔다.
 */
class SessionTest {

    private val now = 1_800_000_000_000L

    private fun session(accessInMs: Long, refreshInMs: Long) = Session(
        appUserId = "u",
        accessToken = "a",
        refreshToken = "r",
        accessExpiresAtMs = now + accessInMs,
        refreshExpiresAtMs = now + refreshInMs,
    )

    @Test
    fun `넉넉히 남았으면 살아 있다`() {
        val s = session(accessInMs = 5 * 60_000, refreshInMs = 7 * 24 * 3600_000)
        assertTrue(s.accessAlive(now))
        assertTrue(s.refreshAlive(now))
    }

    @Test
    fun `지났으면 죽었다`() {
        val s = session(accessInMs = -1, refreshInMs = -1)
        assertFalse(s.accessAlive(now))
        assertFalse(s.refreshAlive(now))
    }

    /**
     * **만료 직전을 미리 죽은 것으로 친다.**
     *
     * 남은 시간이 5초일 때 살아 있다고 답하면, 그 토큰으로 요청을 보내는 사이에
     * 넘어가서 401 을 맞는다. 요청 왕복 시간만큼 여유를 두는 것이 요점이다.
     */
    @Test
    fun `만료 직전은 미리 죽은 것으로 본다`() {
        val s = session(accessInMs = 5_000, refreshInMs = 5_000)
        assertFalse("5초 남은 것을 살아 있다고 하면 보내는 사이에 만료된다", s.accessAlive(now))
        assertFalse(s.refreshAlive(now))
    }

    @Test
    fun `여유 시간을 넘기면 살아 있다`() {
        val s = session(accessInMs = 90_000, refreshInMs = 90_000)
        assertTrue(s.accessAlive(now))
        assertTrue(s.refreshAlive(now))
    }

    /**
     * 앱을 켤 때 가장 흔한 상태 — access 만 죽고 refresh 는 살아 있다.
     * 이때 랜딩으로 보내면 안 된다. 재발급을 타야 한다.
     */
    @Test
    fun `access 만 죽은 상태가 구별된다`() {
        val s = session(accessInMs = -1, refreshInMs = 3 * 24 * 3600_000)
        assertFalse(s.accessAlive(now))
        assertTrue("여기서 랜딩으로 보내면 7일마다가 아니라 5분마다 다시 로그인하게 된다", s.refreshAlive(now))
    }

    /**
     * 시각을 못 읽으면 0 이 들어온다 ([AuthApi] 의 `toEpochMs`). 그 경우 **만료로
     * 쳐야** 재발급 경로를 타서 스스로 회복한다. 살아 있다고 보면 영영 401 을 맞는다.
     */
    @Test
    fun `시각을 못 읽어 0 이면 만료로 본다`() {
        val s = Session("u", "a", "r", accessExpiresAtMs = 0L, refreshExpiresAtMs = 0L)
        assertFalse(s.accessAlive(now))
        assertFalse(s.refreshAlive(now))
    }
}

/**
 * 서버가 주는 시각 문자열을 못 읽으면 **모든 세션이 만료로 보인다.**
 *
 * 안드로이드 8 의 `Instant.parse` 는 `Z` 만 받는다. FastAPI 가 `+00:00` 으로 내보내면
 * 거기서 던져서, **최신 폰에서는 되고 낮은 기기에서만 로그인이 안 풀리는** 형태로
 * 갈린다. 그 조합을 여기서 고정한다.
 */
class IsoTimeTest {

    private fun parse(text: String): Long = with(AuthApi) { text.toEpochMs() }

    private val expected = 1_800_000_000_000L // 2027-01-15T08:00:00Z

    @Test
    fun `Z 로 끝나는 것`() {
        assertEquals(expected, parse("2027-01-15T08:00:00Z"))
    }

    @Test
    fun `소수 자리가 붙은 것`() {
        assertEquals(expected, parse("2027-01-15T08:00:00.000Z"))
    }

    @Test
    fun `시간대가 오프셋으로 붙은 것`() {
        assertEquals(expected, parse("2027-01-15T08:00:00+00:00"))
    }

    @Test
    fun `한국 시간 오프셋`() {
        assertEquals(expected, parse("2027-01-15T17:00:00+09:00"))
    }

    /** 시간대가 없으면 UTC 로 읽는다. 서버가 naive datetime 을 줄 때다. */
    @Test
    fun `시간대가 없는 것은 UTC 로 본다`() {
        assertEquals(expected, parse("2027-01-15T08:00:00"))
    }

    /** 못 읽으면 0. 0 은 만료라서 앱이 재발급으로 스스로 회복한다. */
    @Test
    fun `못 읽으면 0`() {
        assertEquals(0L, parse("어제쯤"))
        assertEquals(0L, parse(""))
    }
}
