package com.daengs.app.pet

import com.daengs.app.BuildConfig
import java.net.URI

/**
 * 초대 링크를 만들고 읽는다.
 *
 * **토큰을 경로가 아니라 프래그먼트에 둔다** (`.../invite#<token>`). 경로에 두면 토큰이
 * 웹 서버 액세스 로그·Referer·안내 페이지의 분석 도구에 그대로 남는데, 이 토큰은 남의
 * 강아지 초대를 가로챌 수 있는 자격증명이다. 프래그먼트는 **서버로 전송되지 않으면서**
 * App Links 매칭(스킴·호스트·경로)에는 영향이 없고, 앱은 전체 URI 를 그대로 받는다.
 */
object InviteLink {

    /** 합의된 운영 링크. 서버가 여기서 안내 페이지와 `assetlinks.json` 을 서빙한다. */
    const val HOST = "daengapi.weareithero.cloud"
    const val PATH = "/invite"

    /**
     * 공유할 링크. **운영 서버를 보고 있을 때만 만든다** — null 이면 만들 수 없는 환경이다.
     *
     * ⚠️ **개발 API 로 뽑은 토큰에 운영 링크를 씌우면 안 된다.** 받는 사람의 앱은 운영
     * 서버에 그 토큰을 내밀고, 운영에는 그런 초대가 없으니 404 를 받는다 — 링크가 조용히
     * 죽고 원인은 안 보인다. 그래서 [apiBaseUrl] 의 호스트가 [HOST] 일 때만 만든다.
     * (앱 주소는 빌드 변형마다 다르고 `assetlinks.json` 은 운영 호스트에만 올라간다.)
     */
    fun of(token: String, apiBaseUrl: String = BuildConfig.API_BASE_URL): String? {
        if (!isValidToken(token)) return null
        if (!available(apiBaseUrl)) return null
        return "https://$HOST$PATH#$token"
    }

    /**
     * 지금 환경에서 쓸 수 있는 링크를 만들 수 있나.
     *
     * **화면이 초대를 만들기 전에 묻는다.** 만들고 나서야 [of] 가 null 이라는 것을 알면,
     * 아무도 못 쓰는 초대가 서버에 하나 생기고 상한(3개) 자리만 먹는다.
     */
    fun available(apiBaseUrl: String = BuildConfig.API_BASE_URL): Boolean =
        hostOf(apiBaseUrl) == HOST

    /**
     * 링크에서 토큰을 꺼낸다. 우리 링크가 아니거나 토큰 자리가 비면 null 이다.
     *
     * 프래그먼트만 본다 — 경로에 실린 값은 우리 계약이 아니므로 받아 주지 않는다.
     */
    fun tokenOf(uri: String?): String? {
        val parsed = runCatching { URI(uri ?: return null) }.getOrNull() ?: return null
        if (!parsed.scheme.equals("https", ignoreCase = true)) return null
        if (!parsed.host.equals(HOST, ignoreCase = true)) return null
        if (parsed.path?.trimEnd('/') != PATH) return null
        return parsed.fragment?.takeIf { isValidToken(it) }
    }

    /**
     * 토큰답게 생겼나. 서버는 `secrets.token_urlsafe(32)` 를 주고 본문에서 1~200자를
     * 받는다(`InviteAccept`). **여기서 거르는 것은 보안이 아니라 잡음이다** — 빈 값이나
     * 남의 링크 조각을 서버까지 들고 가지 않는다. 진짜 판정은 서버가 한다.
     */
    fun isValidToken(token: String?): Boolean =
        token != null && token.length in 1..200 && token.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun hostOf(url: String): String? =
        runCatching { URI(url.trim()).host }.getOrNull()
}
