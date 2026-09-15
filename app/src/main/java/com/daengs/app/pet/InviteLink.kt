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
     * 확인된 개발 서버 호스트 **하나**. 디버그에서만 링크를 만들 수 있는 자리다.
     *
     * **도메인 접미사로 열지 않는다.** `.weareithero.cloud` 아래를 통째로 허용하면
     * 아직 없는 호스트나 다른 용도의 호스트까지 들어오고, 그런 주소로 만든 초대는
     * 어디에도 없는 채로 남아 상한 자리만 먹는다. 개발 서버가 늘면 여기에 더한다.
     */
    private const val DEV_HOST = "daengback.weareithero.cloud"

    /**
     * 공유할 링크. 만들 수 없는 환경이면 null 이다.
     *
     * ⚠️ **링크 호스트는 언제나 [HOST] 다.** 붙여넣기가 그 호스트만 받아 주기 때문이고
     * ([tokenOf]), 나중에 App Links 를 얹을 때 `assetlinks.json` 이 올라가는 곳도 거기다.
     * **토큰이 어느 서버 것인지는 링크에 안 적힌다** — 받는 앱이 자기
     * `API_BASE_URL` 로 물어본다.
     *
     * 그래서 원래는 운영을 보고 있을 때만 만들었다. 개발 API 로 뽑은 토큰에 이 링크를
     * 씌우면, 받는 사람이 **운영을 보는 앱**일 때 운영에는 그런 초대가 없어 404 를
     * 받는다 — 링크가 조용히 죽고 원인은 안 보인다.
     *
     * 그 위험은 **받는 쪽이 다른 서버를 볼 때**만 생긴다. 두 기기가 같은 개발 서버를
     * 보는 디버그 빌드라면 토큰이 그대로 통한다. 그래서 [available] 은 디버그에서
     * 확인된 개발 호스트 하나([DEV_HOST])도 통과시킨다 — 실연동을 보려고 운영 API 로
     * 갈아타지 않아도 되게.
     */
    fun of(
        token: String,
        apiBaseUrl: String = BuildConfig.API_BASE_URL,
        debug: Boolean = BuildConfig.DEBUG,
    ): String? {
        if (!isValidToken(token)) return null
        if (!available(apiBaseUrl, debug)) return null
        return "https://$HOST$PATH#$token"
    }

    /**
     * 지금 환경에서 쓸 수 있는 링크를 만들 수 있나.
     *
     * **화면이 초대를 만들기 전에 묻는다.** 만들고 나서야 [of] 가 null 이라는 것을 알면,
     * 아무도 못 쓰는 초대가 서버에 하나 생기고 상한 자리만 먹는다.
     *
     * - **출시 빌드는 운영 호스트일 때만** — 예전과 같다.
     * - 디버그 빌드는 **확인된 개발 호스트([DEV_HOST]) 하나**만 통과한다. 도메인 접미사로
     *   열지 않는다 — 오타나 남의 주소를 넣어 두고 링크를 만들면, 그 링크로 만든 초대가
     *   어디에도 없는 채로 남는다.
     */
    fun available(
        apiBaseUrl: String = BuildConfig.API_BASE_URL,
        debug: Boolean = BuildConfig.DEBUG,
    ): Boolean {
        val host = hostOf(apiBaseUrl) ?: return false
        if (host == HOST) return true
        return debug && host == DEV_HOST
    }

    /**
     * 이 링크가 **디버그에서만 통하는** 것인가. 화면이 그 사실을 적어 준다.
     *
     * 링크 호스트는 운영과 같은 글자라 눈으로는 구별이 안 된다. 받는 사람이 출시 앱이면
     * 404 를 받으므로, 만든 사람이 "이건 개발끼리만" 이라는 것을 알아야 한다.
     */
    fun devOnly(
        apiBaseUrl: String = BuildConfig.API_BASE_URL,
        debug: Boolean = BuildConfig.DEBUG,
    ): Boolean = available(apiBaseUrl, debug) && hostOf(apiBaseUrl) != HOST

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
     * 웹 안내 페이지의 「이미 설치했나요? 앱에서 초대 열기」버튼이 토큰을 싣는 **Intent
     * extra 의 이름.** 서버 `routers/invite_web.py` 의 `intent://` 문자열과 반드시 같다.
     *
     * 이름에 패키지를 박아 두는 것은 다른 앱·브라우저가 쓰는 extra 와 안 겹치게 하려는
     * 것이다. Chrome 이 지우는 것은 `browser_fallback_url` 하나뿐이라 이 extra 는 그대로
     * 앱에 닿는다 (Chrome 의 "Android Intents with Chrome" 문서).
     */
    const val WEB_FALLBACK_EXTRA = "com.daengs.app.extra.INVITE_TOKEN"

    /**
     * 웹 안내 페이지의 「이미 설치했나요? 앱에서 초대 열기」버튼 **하나만을 위한 보조
     * 통로다.** [tokenOf] 의 계약을 넓히지 않는다 — 공유 링크·붙여넣기·App Links 는
     * 전부 그대로 [tokenOf] 만 쓴다.
     *
     * ⚠️ **왜 extra 인가.** 이 버튼은 Chrome 의 `intent://` 문법으로 앱을 연다. 그런데
     * `intent://…#Intent;…;end` 는 `#` 뒤를 자기 문법으로 쓰기 때문에, 우리 계약대로
     * 토큰을 진짜 URL 프래그먼트에 실을 자리가 없다. 그렇다고 쿼리나 경로에 실으면
     * 토큰이 URL 의 일부가 된다 — 그래서 `S.<이름>=<값>` 문자열 extra 로 싣는다.
     * 데이터 URI 는 `https://daengapi.weareithero.cloud/invite` 그대로라 매니페스트의
     * 필터와 맞고, 토큰은 URL 어디에도 없다.
     *
     * ⚠️ **네트워크로는 안 나간다.** `intent://` 는 브라우저가 로컬에서 안드로이드
     * Intent 로 바꾸는 문자열일 뿐 그 자체로 HTTP 요청을 만들지 않는다. 리졸브에
     * 실패했을 때만(앱이 없을 때) 별도의, **토큰이 없는** `S.browser_fallback_url`
     * (스토어 주소)로 진짜 요청이 나간다.
     *
     * ⚠️ **열린다고 단정하지 않는다.** Chrome(과 그 기반 브라우저)이 사용자 제스처 안에서
     * 만든 `intent://` 만 처리하고, 카카오톡 인앱 브라우저 같은 WebView 는 이 문법을
     * 아예 못 알아들을 수 있다 — 그때는 이 버튼이 조용히 아무 일도 안 한다. 그래서 웹
     * 페이지는 이 버튼과 별개로 「초대 링크 복사」·「링크 직접 보기」를 늘 보여 준다.
     *
     * **데이터 URI 가 우리 `/invite` 일 때만** extra 를 본다 — 아무 인텐트에나 이 이름의
     * extra 를 실어 보내도, 이 액티비티의 다른 진입(런처·알림)에서는 읽지 않는다.
     *
     * @param uri 인텐트의 데이터 URI. 프래그먼트가 있으면 [tokenOf] 가 먼저 가져간다
     * @param extra 인텐트의 [WEB_FALLBACK_EXTRA] 값 그대로
     */
    fun tokenOfWebFallback(uri: String?, extra: String?): String? {
        val parsed = runCatching { URI(uri ?: return null) }.getOrNull() ?: return null
        if (!parsed.scheme.equals("https", ignoreCase = true)) return null
        if (!parsed.host.equals(HOST, ignoreCase = true)) return null
        if (parsed.path?.trimEnd('/') != PATH) return null
        return extra?.takeIf { isValidToken(it) }
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
