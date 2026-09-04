package com.daengs.app.chat

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AI 답변 신고. 계약은 저쪽 `SAJOYO/DAENGS_dev` #235 의 `POST /app/reports` 다.
 *
 * [ChatApi] 와 **같은 서버·같은 토큰·같은 전송**이라 배관을 그대로 쓴다 —
 * `HttpURLConnection` + `org.json`, 실패는 [ChatApiError]. 다른 점은 엔드포인트가
 * 하나뿐이라는 것뿐이다.
 *
 * **응답의 `id` 와 `created_at` 을 안 읽는다.** 저쪽은 201 에 그 둘을 주지만 앱이 쓸
 * 자리가 없다 — 신고 목록도, 취소도 앱에 없다 (운영 콘솔이 본다). 안 쓰는 값을 모델로
 * 만들어 두면 저쪽이 칸을 바꿀 때 여기가 같이 깨진다.
 *
 * ⚠️ **답변 원문을 안 보낸다.** 저쪽이 `turn_id` 로 자기 DB 에서 읽는다. 원문을 실으면
 * 같은 글이 두 벌이 되고, 운영자가 보는 것이 사용자가 본 것과 갈라질 수 있다.
 *
 * ⚠️ **로그를 남기지 않는다.** [ChatApi] 와 같은 이유다 (저쪽 D-048 — 관측 계층에
 * 대화 원문 금지). 신고 사유도 사용자가 쓴 글이다.
 */
class ReportApi internal constructor(
    private val baseUrl: () -> String,
    private val transport: HttpTransport,
) {
    constructor() : this({ BuildConfig.API_BASE_URL }, UrlConnectionTransport)

    val configured: Boolean
        get() = baseUrl().isNotBlank()

    /**
     * 이 답변을 운영자에게 신고한다. `POST /app/reports` → 201.
     *
     * 실패는 갈래를 그대로 넘긴다. 화면이 상태로 가른다 — **409 는 이미 신고한
     * 답변**이고(실패가 아니라 두 번째다), 404 는 없거나 남의 답변, 401 은 다시 로그인.
     * 문구를 여기서 정하지 않는 이유는 그 판단이 화면마다 다르기 때문이다.
     *
     * @param turnId 신고할 답변의 turn. [ChatTurn.id] 다.
     * @param reason 사용자가 고르거나 적은 사유. 저쪽 상한은 500자다.
     */
    suspend fun report(accessToken: String, turnId: String, reason: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
                val reply = transport.exchange(
                    HttpCall(
                        method = "POST",
                        url = "${baseUrl().trimEnd('/')}/app/reports",
                        headers = mapOf(
                            "Accept" to "application/json",
                            "Authorization" to "Bearer $accessToken",
                        ),
                        body = JSONObject()
                            .put("turn_id", turnId)
                            .put("reason", reason)
                            .toString(),
                        connectTimeoutMs = CONNECT_TIMEOUT_MS,
                        readTimeoutMs = READ_TIMEOUT_MS,
                    ),
                )
                if (reply.status !in 200..299) {
                    throw ChatApiError.from(reply.status, reply.body) { status -> "신고를 보내지 못했어요 ($status)" }
                }
            }.recoverCatching { cause ->
                // [ChatApi] 와 같은 규칙 — 서버가 준 것은 그대로, 나머지는 연결이 안 된 것이다.
                if (cause is IllegalStateException) throw cause
                throw ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
