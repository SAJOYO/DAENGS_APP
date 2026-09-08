package com.daengs.app.care

import com.daengs.app.BuildConfig
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.HttpCall
import com.daengs.app.chat.HttpTransport
import com.daengs.app.chat.UrlConnectionTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 케어 로그 API (`/app/care-events`). 계약은 저쪽 SAJOYO/DAENGS_dev#332 의
 * `routers/care_event.py` · `schemas/care_event.py` 다 — **한쪽만 고치지 말 것.**
 *
 * `ChatApi` 와 같은 배관이다: `HttpURLConnection` + `org.json`, 전송은 [HttpTransport] 로
 * 떼어 테스트가 요청을 통째로 본다, 실패는 상태 코드를 든 [ChatApiError] 다. 다른 점은
 * 엔드포인트가 셋뿐이라 짧다는 것이다.
 *
 * - **멱등키는 앱이 만든다** ([record] 의 `clientEventId`, 바디 필드). 같은 키를 다시 보내면
 *   저쪽이 있던 행을 200 으로 돌려주고 새로 만들면 201 이다 — **둘 다 "올라갔다"** 다.
 *   같은 키에 다른 내용이 와도 덮어쓰지 않는다. 고치려면 지우고 다시 적는다.
 * - `occurred_at` 은 **timezone 이 붙은 시각**이어야 한다. 없이 보내면 422 다.
 * - 산책을 적는 엔드포인트는 없다. 산책은 `walks` 가 진실이고 [today] 가 수만 돌려준다.
 *
 * ⚠️ 토큰을 들고 있지 않는다. 매 호출에 받는다 (`MainActivity` 의 `freshToken`).
 */
class CareApi internal constructor(
    private val baseUrl: () -> String,
    private val transport: HttpTransport,
) {
    constructor() : this({ BuildConfig.API_BASE_URL }, UrlConnectionTransport)

    val configured: Boolean
        get() = baseUrl().isNotBlank()

    /**
     * 하루 요약. `GET /app/care-events/today?pet_id=…` — 서울 기준 오늘의 밥·약·간식 건수,
     * `walks` 에서 센 산책 수, 그날 기록 전부(최근 먼저). 내 강아지가 아니면 404.
     */
    suspend fun today(accessToken: String, petId: String): Result<CareDaySummary> =
        call(accessToken, "GET", "/today?pet_id=${encode(petId)}") { CareDaySummary.parse(JSONObject(it)) }

    /**
     * 기록 한 건. `POST /app/care-events` → 201 (새로), 200 (같은 `client_event_id` 가 이미 있음).
     *
     * 메모 칸은 이 카드에 없어 아예 안 보낸다 — 저쪽이 `note` 없음을 null 로 받는다.
     */
    suspend fun record(
        accessToken: String,
        petId: String,
        kind: CareKind,
        occurredAt: OffsetDateTime,
        clientEventId: String,
    ): Result<CareEvent> =
        call(
            accessToken,
            "POST",
            "",
            body = JSONObject()
                .put("pet_id", petId)
                .put("kind", kind.wire)
                .put("occurred_at", occurredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .put("client_event_id", clientEventId),
        ) { CareEvent.parse(JSONObject(it)) }

    /** 지운다. `DELETE /app/care-events/{id}` → 204. 내 기록이 아니면 404. */
    suspend fun delete(accessToken: String, eventId: String): Result<Unit> =
        call(accessToken, "DELETE", "/${encode(eventId)}") { }

    private suspend fun <T> call(
        accessToken: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val reply = transport.exchange(
                HttpCall(
                    method = method,
                    url = "${baseUrl().trimEnd('/')}/app/care-events$path",
                    headers = mapOf(
                        "Accept" to "application/json",
                        "Authorization" to "Bearer $accessToken",
                    ),
                    body = body?.toString(),
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                ),
            )
            if (reply.status !in 200..299) {
                throw ChatApiError.from(reply.status, reply.body) { status -> "서버 오류 ($status)" }
            }
            parse(reply.body)
        }.recoverCatching { cause ->
            if (cause is IllegalStateException) throw cause
            throw ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
