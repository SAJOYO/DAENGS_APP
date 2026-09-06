package com.daengs.app.auth

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 우리 서버의 앱 회원 인증 API.
 *
 * **HTTP 라이브러리를 안 쓴다.** 부를 엔드포인트가 넷이고 읽을 필드가 여섯이다.
 * `HttpURLConnection` 과 `org.json` 은 프레임워크에 있어서 의존성이 안 늘어난다.
 * Retrofit·OkHttp·직렬화 플러그인을 이것 하나 때문에 들이면, 이 저장소가 지켜 온
 * "필요할 때 늘린다"와 어긋난다. 호출이 열 개를 넘어가면 그때 다시 본다.
 *
 * 계약은 저쪽 `SAJOYO/DAENGS_dev` 의 `routers/app_auth.py` · `schemas/app_auth.py` 다.
 */
/**
 * 저장하려는 순간 **남이 그 이름을 쓰고 있었다** (서버 409).
 *
 * 미리 물어봤을 때는 비어 있었을 수 있다 — 그 사이에 채갔다는 뜻이라, 화면은
 * "다른 분이 쓰고 있어요" 가 아니라 **"방금 다른 분이 가져갔어요"** 로 말해야 한다.
 * `IllegalStateException` 을 물려받는 것은 이 저장소가 서버 문장을 그것으로 나르기
 * 때문이다 — 기존 `recoverCatching` 갈래가 그대로 통과시킨다.
 */
class NicknameTakenException : IllegalStateException("다른 분이 쓰고 있는 이름이에요.")


object AuthApi {

    /** 설정이 없으면 아무것도 못 부른다. 랜딩 화면이 이걸 보고 버튼을 막는다. */
    val configured: Boolean
        get() = BuildConfig.API_BASE_URL.isNotBlank()

    /**
     * 카카오에서 받은 `id_token` 으로 우리 세션을 받는다.
     *
     * **access token 이 아니라 `id_token` 이다.** 로그인 요청 `scope` 에 `openid` 가
     * 빠지면 카카오가 `id_token` 을 아예 안 준다 — 저쪽 문서가 "여기서 막히는 경우가
     * 대부분"이라고 적어 뒀다.
     *
     * @param nonce 로그인 요청에 넣은 값과 같아야 한다. 가로챈 토큰의 재사용을 막는다.
     */
    suspend fun loginWithKakao(idToken: String, nonce: String?): Result<Session> =
        post("/auth/app/kakao") {
            put("id_token", idToken)
            if (nonce != null) put("nonce", nonce)
        }

    /**
     * 세션을 새로 받는다. **응답의 refresh 토큰으로 반드시 덮어써야 한다** — 회전이라
     * 옛 것을 다시 쓰면 재사용 감지에 걸려 세션이 전부 끊긴다.
     */
    suspend fun refresh(refreshToken: String): Result<Session> =
        post("/auth/app/refresh") { put("refresh_token", refreshToken) }

    /** 서버 쪽 세션을 지운다. 실패해도 기기의 토큰은 지운다 — 아래 [Session] 참고. */
    suspend fun logout(refreshToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply { put("refresh_token", refreshToken) }
                open("/auth/app/logout", "POST").use {
                    it.send(body.toString())
                    // **응답 코드를 읽어야 요청이 실제로 나간다.** HttpURLConnection 은
                    // 게을러서, 본문만 쓰고 끊으면 서버에 아무것도 안 갈 수 있다.
                    // 로그아웃은 응답을 안 쓰지만 그래도 읽어야 한다.
                    it.responseCode
                }
                Unit
            }
        }

    /** 지금 로그인한 회원. 토큰이 실제로 먹히는지 확인하는 데도 쓴다. */
    suspend fun me(accessToken: String): Result<AppMe> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = open("/auth/app/me", "GET")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.use { it.readJson() }.toAppMe()
            }
        }

    /**
     * 미니룸 이름표를 정한다.
     *
     * **null 을 보내면 되돌린다** — 다시 대표 강아지 이름을 따라간다. 서버도 빈
     * 문자열을 안 받는다: "아직 안 정했다" 와 "정해서 지웠다" 가 같은 값이 되면
     * 무엇을 그릴지 못 정한다.
     */
    suspend fun setRoomName(accessToken: String, name: String?): Result<AppMe> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("room_name", name?.trim()?.takeIf(String::isNotEmpty) ?: JSONObject.NULL)
                }.toString()
                val conn = open("/auth/app/me", "PATCH")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.use { it.send(body); it.readJson() }.toAppMe()
            }.recoverCatching { cause ->
                // 서버가 준 문장은 그대로 통과시킨다 (failIfNotOk 가 IllegalStateException).
                if (cause is IllegalStateException) throw cause
                throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
            }
        }

    /**
     * 사람 이름을 바꾼다.
     *
     * ⚠️ **[setRoomName] 과 달리 비울 수 없다.** 비우면 그 회원을 가리킬 말이 없어진다
     * — 서버도 422 로 막는다. 그래서 인자가 `String` 이고 `String?` 이 아니다.
     *
     * ⚠️ **`nickname` 칸만 보낸다.** 서버가 보낸 칸만 고치므로(`model_fields_set`)
     * 이름표는 그대로 남는다. 예전 서버는 안 보낸 칸도 null 로 덮었는데, 그때 이 함수는
     * **이름표를 같이 지웠다.** 서버를 고쳤지만 여기서도 필요한 것만 보낸다.
     *
     * @return 남이 채간 이름이면 [NicknameTakenException]. 그 경우 사용자에게
     *   "방금 다른 분이 가져갔어요" 로 말해 줘야 한다 — 미리 물어봤을 때는 비어
     *   있었을 수 있어서다.
     */
    suspend fun setNickname(accessToken: String, nickname: String): Result<AppMe> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply { put("nickname", nickname.trim()) }.toString()
                val conn = open("/auth/app/me", "PATCH")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.use {
                    it.send(body)
                    // **읽기 전에 코드를 본다.** readJson 은 409 를 그냥 문장으로
                    // 바꿔서, 부르는 쪽이 "남이 채갔다" 를 구분할 수 없게 된다.
                    if (it.responseCode == HTTP_CONFLICT) throw NicknameTakenException()
                    it.readJson()
                }.toAppMe()
            }.recoverCatching { cause ->
                if (cause is IllegalStateException) throw cause
                throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
            }
        }

    /**
     * 이 이름을 쓸 수 있나. **입력하는 동안 부른다.**
     *
     * ⚠️ **답은 참고용이다.** `true` 를 받아도 저장할 때 [NicknameTakenException] 이
     * 날 수 있다 — 물어본 뒤 저장 전에 남이 채갈 수 있어서다. 진짜 방어는 서버의
     * `lower(nickname)` UNIQUE 인덱스다.
     *
     * 자기가 지금 쓰는 이름에는 서버가 `true` 를 준다. 그래도 부르는 쪽이 미리
     * 거른다 ([com.daengs.app.ui.nickname.shouldAskAvailability]) — 왕복이 아깝다.
     *
     * **실패를 조용히 `true` 로 바꾸지 않는다.** 통신이 안 될 때 초록불이 켜지면
     * 사용자는 되는 줄 알고 저장을 눌렀다가 거절당한다.
     */
    suspend fun nicknameAvailable(accessToken: String, value: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = URLEncoder.encode(value.trim(), "UTF-8")
                val conn = open("/auth/app/nickname/available?value=$query", "GET")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.use { it.readJson() }.getBoolean("available")
            }
        }

    /**
     * 회원 탈퇴. **되돌릴 수 없다.**
     *
     * ⚠️ **[readJson] 을 쓰면 안 된다.** 서버가 204 로 답해서 본문이 비어 있는데,
     * 빈 문자열을 `JSONObject` 에 넣으면 던진다 — **성공한 탈퇴가 실패로 보인다.**
     * 상태 코드만 본다.
     *
     * ⚠️ **인증 방식이 확실하지 않다.** 저쪽 OpenAPI 에 이 경로의 security scheme 이
     * 안 걸려 있다. 회원을 지우려면 누구인지는 알아야 하므로 [me] 와 같은 방식으로
     * access 토큰을 헤더에 싣는다. **서버가 이 헤더를 읽는지 확인이 필요하다** —
     * 안 읽으면서 204 를 주면 앱은 성공으로 알고 넘어간다.
     */
    suspend fun withdraw(accessToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = open("/auth/app/withdraw", "POST")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                // 응답 코드를 읽어야 요청이 실제로 나간다 ([logout] 과 같은 이유).
                conn.use { it.failIfNotOk() }
                Unit
            }.recoverCatching { cause ->
                // **서버가 준 문장은 그대로 통과시킨다** ([failIfNotOk] 가
                // IllegalStateException 으로 던진다). 나머지는 연결이 안 된 것이고,
                // 그때 예외 메시지는 `Unable to resolve host "..."` 같은 영어 한 줄이라
                // 사용자에게 그대로 보여주면 안 된다.
                if (cause is IllegalStateException) throw cause
                throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
            }
        }

    private fun JSONObject.toAppMe(): AppMe = AppMe(
        appUserId = getString("app_user_id"),
        // 없으면 아직 안 정한 것이다. 서버가 대신 지어 주지 않는다.
        roomName = if (isNull("room_name")) null else optString("room_name").takeIf { it.isNotBlank() },
        // **칸이 아예 없어도 안 깨져야 한다.** `optString` 은 없는 키에 빈 문자열을
        // 주므로 아래 `takeIf` 가 null 로 만든다 — 닉네임을 모르는 옛 서버에 붙어도
        // 로그인이 실패하지 않는다.
        nickname = if (isNull("nickname")) null else optString("nickname").takeIf { it.isNotBlank() },
    )

    // -- 아래는 배관 -------------------------------------------------------

    private suspend fun post(path: String, body: JSONObject.() -> Unit): Result<Session> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply(body).toString()
                val out = open(path, "POST").use { it.send(json); it.readJson() }
                Session(
                    appUserId = out.getString("app_user_id"),
                    accessToken = out.getString("access_token"),
                    refreshToken = out.getString("refresh_token"),
                    accessExpiresAtMs = out.getString("access_expires_at").toEpochMs(),
                    refreshExpiresAtMs = out.getString("refresh_expires_at").toEpochMs(),
                )
            }
        }

    private fun open(path: String, method: String): HttpURLConnection {
        check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
        val conn = URL(BuildConfig.API_BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private fun HttpURLConnection.send(body: String) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        outputStream.use { it.write(body.toByteArray()) }
    }

    /**
     * 200 대가 아니면 서버가 준 메시지를 그대로 예외에 담는다. 저쪽이 사용자에게 보여
     * 줄 문장으로 써 놨다 ("이용이 정지된 계정입니다" 같은).
     *
     * [readJson] 에서 떼어냈다 — **본문이 없는 응답(204)도 상태는 봐야 하기 때문**이다.
     */
    private fun HttpURLConnection.failIfNotOk() {
        if (responseCode in 200..299) return
        val detail = runCatching {
            JSONObject(errorStream?.bufferedReader()?.readText().orEmpty()).optString("detail")
        }.getOrNull()
        error(if (detail.isNullOrBlank()) "서버 오류 ($responseCode)" else detail)
    }

    private fun HttpURLConnection.readJson(): JSONObject {
        failIfNotOk()
        return JSONObject(inputStream.bufferedReader().use { it.readText() })
    }

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    /**
     * 서버는 ISO-8601 로 준다. **모양이 셋일 수 있어서 차례로 시도한다.**
     *
     * FastAPI 는 `datetime` 에 시간대가 붙어 있으면 `+00:00` 을, 없으면 아무것도 안
     * 붙여서 내보낸다. 그리고 안드로이드 8(API 26)의 `Instant.parse` 는 **`Z` 만**
     * 받는다 — `+00:00` 을 주면 거기서 던진다. 최신 폰에서만 되고 낮은 기기에서는
     * 모든 세션이 만료로 보이는, 찾기 고약한 형태로 갈린다.
     *
     * 실패하면 0 이다. **0 은 만료로 취급된다**([Session.accessAlive]) — 그래야 앱이
     * 재발급 경로를 타서 스스로 회복한다. 살아 있다고 보면 영영 401 을 맞는다.
     */
    internal fun String.toEpochMs(): Long {
        runCatching { return Instant.parse(this).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(this).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(this).toInstant(ZoneOffset.UTC).toEpochMilli() }
        return 0L
    }

    private const val TIMEOUT_MS = 10_000

    /** `HttpURLConnection` 에 상수가 없다. 서버가 중복 닉네임에 주는 코드다. */
    private const val HTTP_CONFLICT = 409
}
