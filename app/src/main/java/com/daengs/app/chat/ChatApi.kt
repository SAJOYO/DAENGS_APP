package com.daengs.app.chat

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 대화 기록·AI 요약 API. 계약은 저쪽 `SAJOYO/DAENGS_dev` PR #131 의 `routers/chat.py` ·
 * `schemas/chat.py` 다 (검증한 SHA `5611c42`).
 *
 * `PetApi`·`WalkApi` 와 같은 서버·같은 토큰이라 같은 방식이다 — `HttpURLConnection` +
 * `org.json`, 라이브러리 없이. 다른 점은 둘이다.
 *
 * - **실패가 문장이 아니라 [ChatApiError] 다.** 409 하나에도 갈래가 여섯이고 갈래마다
 *   화면이 할 일이 달라서, 상태·코드·동봉 데이터를 잃으면 안 된다.
 * - **전송을 [HttpTransport] 로 뗐다.** 테스트가 어느 경로에 어떤 헤더로 무엇을
 *   보냈는지 통째로 본다. `PlaceApi` 가 `baseUrl` 을 생성자로 받는 것과 같은 결이다.
 *
 * **질문을 보내는 엔드포인트는 여기 없다.** 저장하는 질문도 `POST /assistant/query`
 * 하나로 들어간다 ([com.daengs.app.assistant.AssistantApi.query] 에 [ChatPersistence]
 * 를 얹는다) — 저쪽이 실행 경로를 둘로 만들지 않았다.
 *
 * ⚠️ **토큰을 들고 있지 않는다.** 매 호출에 받는다. 5분짜리 access 는 `MainActivity`
 * 의 `freshToken` 이 재발급까지 해서 넘겨 준다 — 여기서 `TokenStore` 를 읽지 않는다.
 *
 * ⚠️ **로그를 남기지 않는다.** 응답에 대화 원문이 실려 오고, 저쪽 D-048 이 원문을
 * 관측 계층에 두지 말라고 못박았다. 실패도 [ChatApiError] 가 본문을 안 담는다.
 */
class ChatApi internal constructor(
    private val baseUrl: () -> String,
    private val transport: HttpTransport,
) {
    constructor() : this({ BuildConfig.API_BASE_URL }, UrlConnectionTransport)

    val configured: Boolean
        get() = baseUrl().isNotBlank()

    // -- 대화 -----------------------------------------------------------------

    /**
     * 새 대화 초안. `POST /app/chats` → 201.
     *
     * **초안은 최근 대화에 안 들어간다.** 첫 답변이 사용자에게 전달되는 순간 활성이
     * 되고, 그때 여섯째가 되면 가장 오래된 활성 대화가 사라진다 — 저쪽 규칙이다.
     * 같은 강아지에 초안이 이미 있으면 저쪽이 **그것을 돌려준다** (새로 안 만든다).
     */
    suspend fun createSession(accessToken: String, petId: String, title: String? = null): Result<ChatSession> =
        call(accessToken, "POST", "", body = JSONObject().apply {
            put("pet_id", petId)
            // `extra="forbid"` 라 없는 제목은 칸을 뺀다. 저쪽이 첫 질문으로 짓는다.
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { put("title", it) }
        }) { ChatSession.parse(JSONObject(it)) }

    /** 최근 대화. `GET /app/chats?pet_id=…` — 내 계정 + 이 강아지, 최근 갱신 순, 상한 동봉. */
    suspend fun listSessions(accessToken: String, petId: String): Result<ChatSessionList> =
        call(accessToken, "GET", "?pet_id=${encode(petId)}") { ChatSessionList.parse(JSONObject(it)) }

    /** 대화 하나와 메시지 전부. `GET /app/chats/{session_id}`. 남의 것도 404 다. */
    suspend fun session(accessToken: String, sessionId: String): Result<ChatSessionDetail> =
        call(accessToken, "GET", "/${encode(sessionId)}") { ChatSessionDetail.parse(JSONObject(it)) }

    /** 대화를 지운다. `DELETE /app/chats/{session_id}` → 204. **보관함의 요약은 남는다.** */
    suspend fun deleteSession(accessToken: String, sessionId: String): Result<Unit> =
        call(accessToken, "DELETE", "/${encode(sessionId)}") { }

    // -- 요약 -----------------------------------------------------------------

    /** 보관함. `GET /app/chats/summaries?pet_id=…` — 완성된 요약만, 저장한 순서의 역순. */
    suspend fun listSummaries(accessToken: String, petId: String): Result<ChatSummaryList> =
        call(accessToken, "GET", "/summaries?pet_id=${encode(petId)}") { ChatSummaryList.parse(JSONObject(it)) }

    /**
     * 이 대화를 요약해 보관함에 넣는다. `POST /app/chats/{session_id}/summary` → 201.
     *
     * **사용자가 누를 때만.** 저쪽이 Gemini 를 부르므로 오래 걸린다 — 읽기 제한을
     * 챗봇과 같은 90초로 둔다. 같은 대화 상태를 이미 요약했으면 409
     * `SUMMARY_ALREADY_EXISTS` 에 그 id 가 실려 온다 ([ChatApiError.existingSummaryId]) —
     * **실패가 아니라 그리로 가라는 뜻**이다.
     *
     * @param clientRequestId UUID. 규칙은 [ChatRequestIds] 가 정한다 — 같은 시도는 같은
     *   값, 실패로 끝난 값은 다시 안 쓴다.
     */
    suspend fun createSummary(accessToken: String, sessionId: String, clientRequestId: String): Result<ChatSummary> =
        call(
            accessToken,
            "POST",
            "/${encode(sessionId)}/summary",
            body = JSONObject().put("client_request_id", clientRequestId),
            readTimeoutMs = SUMMARY_READ_TIMEOUT_MS,
        ) { ChatSummary.parse(JSONObject(it)) }

    /**
     * 요약 하나를 지운다. `DELETE /app/chats/summaries/{summary_id}` → 204. 원본 대화는
     * 그대로다. 만드는 중이면 409 `SUMMARY_PROCESSING` 이라 끝난 뒤에 지워야 한다.
     */
    suspend fun deleteSummary(accessToken: String, summaryId: String): Result<Unit> =
        call(accessToken, "DELETE", "/summaries/${encode(summaryId)}") { }

    // -- 아래는 배관 -------------------------------------------------------

    private suspend fun <T> call(
        accessToken: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val reply = transport.exchange(
                HttpCall(
                    method = method,
                    url = "${baseUrl().trimEnd('/')}/app/chats$path",
                    headers = mapOf(
                        "Accept" to "application/json",
                        "Authorization" to "Bearer $accessToken",
                    ),
                    body = body?.toString(),
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = readTimeoutMs,
                ),
            )
            if (reply.status !in 200..299) {
                throw ChatApiError.from(reply.status, reply.body) { status ->
                    if (status in 502..504) SLOW else "서버 오류 ($status)"
                }
            }
            parse(reply.body)
        }.recoverCatching { cause ->
            // 서버가 준 것은 그대로 통과시킨다. 나머지는 연결이 안 된 것이고, 그때
            // 메시지는 `Unable to resolve host` 같은 영어 한 줄이라 화면에 못 띄운다.
            if (cause is IllegalStateException) throw cause
            throw ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000

        /** 목록·상세·삭제는 금방 온다. */
        const val READ_TIMEOUT_MS = 30_000

        /**
         * 요약만 길다. 저쪽이 Gemini 를 한 번(스키마 실패면 두 번) 부르고, 게이트웨이가
         * 60초에 끊는다. 그 502~504 를 받아서 [SLOW] 로 말해 주려면 우리가 더 늦게
         * 끊어야 한다 — `AssistantApi` 와 같은 숫자다.
         */
        const val SUMMARY_READ_TIMEOUT_MS = 90_000

        const val SLOW = "서버가 오래 걸리고 있어요. 조금 뒤에 다시 시도해 주세요."
    }
}
