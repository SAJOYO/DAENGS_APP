package com.daengs.app.pet

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 다중 강아지 초대 API. 계약은 저쪽 `routers/pet_member.py` 의 `/app/pet-invites` 넷이다.
 *
 * **경로에 `pet_id` 가 없다.** 묶음은 강아지 하나에 매이지 않아서, 목록도 취소도 주보호자
 * 기준이다 — 그래서 활성 초대 상한도 강아지당이 아니라 **주보호자당 3묶음**이다
 * ([MAX_ACTIVE_INVITE_BUNDLES]).
 *
 * 구 경로 셋(`/app/pets/{id}/invites`)은 서버에 그대로 남아 있다 ([PetInviteApi]).
 * 이 클래스는 그것을 대체하는 것이지 지우는 것이 아니다.
 *
 * ⚠️ **토큰을 로그·예외 문구에 싣지 않는다.**
 */
class PetInviteBundleApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) {

    val configured: Boolean
        get() = baseUrl().isNotBlank()

    /**
     * 묶음을 만든다. **평문 토큰은 이 응답에만 있다** ([CreatedInviteBundle]).
     *
     * @param petIds 1~[MAX_PETS_PER_INVITE] 마리. 중복이 있으면 서버가 422 로 막는다.
     */
    suspend fun create(accessToken: String, petIds: List<String>): Result<CreatedInviteBundle> =
        call(accessToken, "/app/pet-invites", "POST", JSONObject().put("pet_ids", JSONArray(petIds))) {
            CreatedInviteBundle.parse(JSONObject(it))
        }

    /** 내가 만든 묶음 전부(수락된 것·만료된 것 포함). 서버가 준 순서를 그대로 쓴다. */
    suspend fun list(accessToken: String): Result<InviteBundleList> =
        call(accessToken, "/app/pet-invites", "GET", null) { InviteBundleList.parse(JSONObject(it)) }

    /** 묶음 취소. **담긴 아이 전부가 함께 취소된다.** 성공하면 204 라 본문이 없다. */
    suspend fun cancel(accessToken: String, inviteId: String): Result<Unit> =
        call(accessToken, "/app/pet-invites/$inviteId", "DELETE", null) { }

    /**
     * 수락 전에 무엇이 든 초대인지 본다.
     *
     * **토큰을 본문으로 보낸다** — URL 에 실으면 nginx 액세스 로그·Referer·브라우저
     * 히스토리에 평문이 남는다. 수락이 본문으로 받는 것과 같은 이유다.
     *
     * **404 를 두 가지로 가른다.** 서버가 이 경로 자체를 모르면(구 서버) 새 계약을 쓰면
     * 안 되므로 [PreviewOutcome.Unsupported] 이고, 경로는 있는데 토큰이 없으면
     * [PreviewOutcome.NotFound] 다. 가르는 근거는 응답 본문의 `detail` 이다 — FastAPI 의
     * 라우팅 404 는 `"Not Found"` 한 줄이고, 우리 404 는 서버가 쓴 한국어 문장이다.
     */
    suspend fun preview(accessToken: String, inviteToken: String): PreviewOutcome =
        withContext(Dispatchers.IO) {
            if (!configured) {
                return@withContext PreviewOutcome.Failed("서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요.")
            }
            runCatching {
                open(accessToken, "/app/pet-invites/preview", "POST").use { conn ->
                    conn.outputStream.use { out ->
                        out.write(JSONObject().put("token", inviteToken).toString().toByteArray())
                    }
                    when (val code = conn.responseCode) {
                        in 200..299 -> PreviewOutcome.Ready(
                            InvitePreview.parse(JSONObject(conn.inputStream.bufferedReader().use { it.readText() })),
                        )
                        404 -> if (conn.looksLikeMissingRoute()) {
                            PreviewOutcome.Unsupported
                        } else {
                            PreviewOutcome.NotFound
                        }
                        410 -> PreviewOutcome.Expired
                        else -> PreviewOutcome.Failed(
                            InviteFailure.parse(conn.errorStream?.bufferedReader()?.readText(), "서버 오류 ($code)").message,
                        )
                    }
                }
            }.getOrElse { cause ->
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                PreviewOutcome.Failed("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.")
            }
        }

    /**
     * 이 404 가 "경로가 없다" 인가.
     *
     * FastAPI 가 라우팅에서 내는 404 는 `{"detail":"Not Found"}` 로 고정이다. 우리 핸들러의
     * 404 는 서버가 쓴 한국어 문장이라 겹치지 않는다. **틀렸을 때 안전한 쪽으로 기운다** —
     * 못 가르면 `Unsupported` 로 보고 새 계약을 안 쓰는 편이, 선택이 조용히 무시되는 것보다 낫다.
     */
    private fun HttpURLConnection.looksLikeMissingRoute(): Boolean {
        val detail = InviteFailure.parse(errorStream?.bufferedReader()?.readText(), "").message
        return detail.isBlank() || detail.equals("Not Found", ignoreCase = true)
    }

    private suspend fun <T> call(
        accessToken: String,
        path: String,
        method: String,
        body: JSONObject?,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            open(accessToken, path, method).use { conn ->
                if (body != null) {
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val fallback = "요청을 처리하지 못했어요 ($code)"
                    error(InviteFailure.parse(conn.errorStream?.bufferedReader()?.readText(), fallback).message)
                }
                parse(if (code == 204) "" else conn.inputStream.bufferedReader().use { it.readText() })
            }
        }
    }

    private fun open(accessToken: String, path: String, method: String): HttpURLConnection =
        (URL("${baseUrl().trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = method == "POST"
            setRequestProperty("Accept", "application/json")
            if (method == "POST") setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
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
