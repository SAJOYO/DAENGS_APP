package com.daengs.app.pet

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 초대 수락. 계약은 저쪽 `routers/pet_member.py` 의 `POST /app/pet-invites/accept` 다.
 *
 * **경로에 `pet_id` 가 없는 것이 의도다** — 수락 전에는 그 아이에 아무 권한이 없어서,
 * 주소나 본문에 아이 id 를 실으면 남의 id 를 넣어 보는 자리가 생긴다. 토큰만 보낸다.
 *
 * ⚠️ **토큰을 로그·예외 문구에 싣지 않는다.** 실패 메시지는 서버가 준 `detail` 이거나
 * 우리가 지은 안내 문장뿐이고, 어느 쪽에도 토큰이 들어가지 않는다.
 */
class InviteAcceptApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) {

    val configured: Boolean
        get() = baseUrl().isNotBlank()

    suspend fun accept(accessToken: String, inviteToken: String): AcceptOutcome =
        withContext(Dispatchers.IO) {
            if (!configured) {
                return@withContext AcceptOutcome.Failed("서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요.")
            }
            runCatching {
                val conn = (URL("${baseUrl().trimEnd('/')}/app/pet-invites/accept")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                conn.use {
                    it.outputStream.use { out ->
                        out.write(JSONObject().put("token", inviteToken).toString().toByteArray())
                    }
                    when (val code = it.responseCode) {
                        in 200..299 -> AcceptOutcome.Joined(
                            AcceptedInvite.parse(JSONObject(it.inputStream.bufferedReader().use { r -> r.readText() })),
                        )
                        404 -> AcceptOutcome.NotFound
                        410 -> AcceptOutcome.Expired
                        409 -> AcceptOutcome.Conflict(it.detail() ?: "지금은 참여할 수 없어요.")
                        else -> AcceptOutcome.Failed(it.detail() ?: "서버 오류 ($code)")
                    }
                }
            }.getOrElse { cause ->
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                // 연결이 안 된 것이다. 그때 메시지는 영어 한 줄이라 화면에 그대로 띄우면 안 된다.
                AcceptOutcome.Failed("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.")
            }
        }

    /** 저쪽이 사용자에게 보여 줄 문장으로 `detail` 을 써 놨다. 모르는 모양이면 null 이다. */
    private fun HttpURLConnection.detail(): String? {
        val body = runCatching {
            JSONObject(errorStream?.bufferedReader()?.readText().orEmpty())
        }.getOrNull()
        return when (val raw = body?.opt("detail")) {
            is String -> raw.takeIf(String::isNotBlank)
            is JSONObject -> raw.optString("message").takeIf(String::isNotBlank)
            else -> null
        }
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
