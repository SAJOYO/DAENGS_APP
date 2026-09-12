package com.daengs.app.pet

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 공동 돌봄 초대 API. 계약은 저쪽 `routers/pet_member.py` · `schemas/pet_member.py` 다.
 *
 * **셋 다 대표 전용이다.** 돌보미·제3자에게는 403 이 아니라 **404** 가 온다 — 그 강아지가
 * 있다는 사실 자체를 안 알려주려는 것이라, 앱도 404 를 "권한 없음" 으로 번역하지 않는다.
 *
 * [PetMemberApi] 와 같은 방식으로 짰다(생성자로 받는 `baseUrl`, `HttpURLConnection`,
 * `detail` 파싱).
 */
class PetInviteApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) {

    val configured: Boolean
        get() = baseUrl().isNotBlank()

    /**
     * 초대를 만든다. **평문 토큰은 이 응답에만 있다** ([CreatedInvite]).
     *
     * 살아 있는 초대가 상한(3개)을 넘으면 서버가 409 와 함께 사용자에게 보여 줄 문장을
     * 준다 — 앱이 문장을 새로 짓지 않고 그대로 쓴다.
     */
    suspend fun create(accessToken: String, petId: String): Result<CreatedInvite> =
        call(accessToken, "/app/pets/$petId/invites", "POST") { CreatedInvite.parse(JSONObject(it)) }

    /** 그 아이의 초대 전부(수락된 것·만료된 것 포함). 서버가 준 순서를 그대로 쓴다. */
    suspend fun list(accessToken: String, petId: String): Result<PetInviteList> =
        call(accessToken, "/app/pets/$petId/invites", "GET") { PetInviteList.parse(JSONObject(it)) }

    /** 초대 취소. 성공하면 204 라 본문이 없다. */
    suspend fun cancel(accessToken: String, petId: String, inviteId: String): Result<Unit> =
        call(accessToken, "/app/pets/$petId/invites/$inviteId", "DELETE") { }

    private suspend fun <T> call(
        accessToken: String,
        path: String,
        method: String,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val conn = (URL("${baseUrl().trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            conn.use {
                // **응답 코드를 먼저 읽어야 요청이 실제로 나간다.** 204 는 본문이 없어서
                // 읽으려 들면 빈 문자열이므로, 파서에 넘기기 전에 코드로 가른다.
                if (it.responseCode !in 200..299) it.fail()
                parse(if (it.responseCode == 204) "" else it.inputStream.bufferedReader().use { r -> r.readText() })
            }
        }.onFailure { cause ->
            if (cause is kotlinx.coroutines.CancellationException) throw cause
        }.recoverCatching { cause ->
            if (cause is IllegalStateException) throw cause
            throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
        }
    }

    /** 저쪽이 사용자에게 보여 줄 문장으로 `detail` 을 써 놨다. 모르는 모양이면 상태 코드만 말한다. */
    private fun HttpURLConnection.fail(): Nothing {
        val body = runCatching {
            JSONObject(errorStream?.bufferedReader()?.readText().orEmpty())
        }.getOrNull()
        val detail = when (val raw = body?.opt("detail")) {
            is String -> raw.takeIf(String::isNotBlank)
            is JSONObject -> raw.optString("message").takeIf(String::isNotBlank)
            else -> null
        }
        error(detail ?: "서버 오류 ($responseCode)")
    }

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
