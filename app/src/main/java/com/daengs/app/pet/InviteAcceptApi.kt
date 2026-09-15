package com.daengs.app.pet

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

    /**
     * @param links 초대 강아지별 선택. **묶음이면 모든 항목을 보내야 한다** — 빠지면 서버가
     *   409 `link_selection_required` 로 막는다. 비워 보내는 것은 옛 계약(전부 연결 없이
     *   참여)이라 **한 마리 묶음에서만** 통과한다.
     *
     *   ⚠️ **구 서버에 [links] 를 보내면 조용히 무시되고 200 이 온다** (pydantic
     *   `extra="ignore"`). 그래서 부르는 쪽은 미리보기가 200 이었을 때만 값을 채운다 —
     *   [InvitePreview] 의 주석을 보라.
     */
    suspend fun accept(
        accessToken: String,
        inviteToken: String,
        links: List<InviteLinkChoice> = emptyList(),
    ): AcceptOutcome =
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
                        out.write(body(inviteToken, links).toString().toByteArray())
                    }
                    when (val code = it.responseCode) {
                        in 200..299 -> AcceptOutcome.Joined(
                            AcceptedInvite.parse(JSONObject(it.inputStream.bufferedReader().use { r -> r.readText() })),
                        )
                        404 -> AcceptOutcome.NotFound
                        410 -> AcceptOutcome.Expired
                        409 -> it.failure("지금은 참여할 수 없어요.").let { f ->
                            AcceptOutcome.Conflict(f.message, f.code, f.missingPetIds, f.reason, f.petId)
                        }
                        422 -> it.failure("초대 정보가 바뀌었어요. 다시 불러와 주세요.").let { f ->
                            AcceptOutcome.Invalid(f.message, f.code)
                        }
                        else -> AcceptOutcome.Failed(it.failure("서버 오류 ($code)").message)
                    }
                }
            }.getOrElse { cause ->
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                // 연결이 안 된 것이다. 그때 메시지는 영어 한 줄이라 화면에 그대로 띄우면 안 된다.
                AcceptOutcome.Failed("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.")
            }
        }

    /**
     * 요청 본문. **선택이 없으면 `links` 키를 아예 넣지 않는다** — 빈 배열을 보내도 서버는
     * 같게 읽지만, 옛 계약과 바이트가 같아야 구 서버에서도 뜻이 안 흔들린다.
     */
    private fun body(inviteToken: String, links: List<InviteLinkChoice>): JSONObject {
        val json = JSONObject().put("token", inviteToken)
        if (links.isEmpty()) return json
        val arr = JSONArray()
        links.forEach { choice ->
            arr.put(
                JSONObject()
                    .put("pet_id", choice.petId)
                    // `null` 은 "연결 없이 참여" 라는 **선택**이다. 키를 빼면 뜻이 달라진다.
                    .put("link_to_pet_id", choice.linkToPetId ?: JSONObject.NULL),
            )
        }
        return json.put("links", arr)
    }

    /**
     * 저쪽이 사용자에게 보여 줄 문장으로 `detail` 을 써 놨다. 연결 관련 오류는 `code` 가
     * 붙은 객체이고 상한·이미 대표는 문장 한 줄이라, [InviteFailure] 가 둘 다 읽는다.
     */
    private fun HttpURLConnection.failure(fallback: String): InviteFailure =
        InviteFailure.parse(errorStream?.bufferedReader()?.readText(), fallback)

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
