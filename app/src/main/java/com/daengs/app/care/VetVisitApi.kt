package com.daengs.app.care

import com.daengs.app.BuildConfig
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.HttpCall
import com.daengs.app.chat.HttpTransport
import com.daengs.app.chat.UrlConnectionTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 진료비 기록 API (`/app/vet-visits`). 계약은 저쪽 SAJOYO/DAENGS_dev#353 의
 * `routers/vet_visit.py` · `schemas/vet_visit.py` 와 `docs/vet-visits.md` 다 —
 * **한쪽만 고치지 말 것** (`CareApi` 머리말과 같은 규칙).
 *
 * 배관은 [CareApi] 와 같다: `org.json`, 전송은 [HttpTransport] 로 떼어 테스트가 요청을
 * 통째로 보고, 실패는 상태 코드를 든 [ChatApiError] 이며, **토큰은 매 호출에 받는다.**
 *
 * 다른 점은 **걸음이 넷**이라는 것이다.
 *
 * ```
 * ① POST /app/vet-visits       초안 + 업로드 티켓 (201 새로, 200 있던 것 — 둘 다 성공)
 * ② PUT  티켓의 upload_url     사진 바이트. 우리 API 가 아니다
 * ③ POST /{draft_id}/extract   Gemini 추출. 못 읽어도 200
 * ④ POST /{draft_id}/confirm   여기서만 기록이 생긴다
 * ```
 *
 * ⚠️ **②는 [transport] 를 안 탄다.** [HttpCall] 의 본문이 `String` 이고 Content-Type 을
 *    `application/json` 으로 고정하기 때문이다. 사진은 바이트이고 헤더도 티켓이 준
 *    것을 그대로 써야 해서, 그 한 걸음만 [BridgeUploader] 로 따로 떼었다 — 테스트가
 *    무엇을 어디에 올렸는지 볼 수 있어야 하는 것은 같다.
 */
class VetVisitApi internal constructor(
    private val baseUrl: () -> String,
    private val transport: HttpTransport,
    private val uploader: BridgeUploader,
) {
    constructor() : this({ BuildConfig.API_BASE_URL }, UrlConnectionTransport, UrlConnectionUploader)

    val configured: Boolean
        get() = baseUrl().isNotBlank()

    /**
     * ① 초안을 열고 사진 올릴 자리를 받는다.
     *
     * **[clientEventId] 는 촬영 시점에 만든 것을 그대로 넘긴다.** 업로드 버튼을 누를 때
     * 새로 만들면, 화면이 얼어 보여 두 번 눌린 순간 초안이 둘 생기고 **Gemini 가 두 번
     * 불린다**(요금이 는다). 같은 키면 저쪽이 200 으로 있던 초안을 그대로 준다.
     */
    suspend fun startDraft(
        accessToken: String,
        petId: String,
        clientEventId: String,
    ): Result<VetVisitTicket> = call(
        accessToken,
        "POST",
        "",
        body = JSONObject()
            .put("pet_id", petId)
            .put("content_type", JPEG)
            .put("client_event_id", clientEventId),
    ) { status, body -> VetVisitTicket.parse(JSONObject(body), status) }

    /**
     * ② 티켓이 준 자리에 사진을 올린다.
     *
     * **여기는 우리 API 가 아니다.** 주소도 헤더도 티켓이 준 것을 그대로 쓰고 토큰을
     * 안 붙인다 — 저쪽 bridge 는 인증 헤더를 안 받고 **키가 자격**이다.
     *
     * ⚠️ **409 를 성공으로 접는다.** 저쪽이 `open_write(exclusive=True)` 라 같은 키에
     *    두 번 쓰면 409 인데, 그 뜻은 **그 자리에 이미 바이트가 있다**는 것이다 (반쯤
     *    쓰다 끊긴 파일은 저쪽이 지운다). 여기서 실패로 보고 새 초안을 만들면 같은
     *    영수증에 Gemini 를 한 번 더 태운다. 진짜로 사진이 없으면 ③이 409
     *    `photo_not_uploaded` 로 말해 준다.
     */
    suspend fun upload(ticket: VetVisitTicket, jpeg: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val status = uploader.put(ticket.uploadUrl, ticket.uploadHeaders, jpeg)
                if (status == HttpURLConnection.HTTP_CONFLICT) return@runCatching
                if (status !in 200..299) {
                    throw ChatApiError(status, code = null, message = uploadSentence(status))
                }
            }.recoverCatching { cause ->
                if (cause is ChatApiError) throw cause
                throw ChatApiError.unreachable(
                    "영수증 사진을 올리지 못했어요. 잠시 뒤 다시 시도해 주세요.",
                    cause,
                )
            }
        }

    /**
     * ③ 올라온 영수증을 읽는다. **`ok`·`unreadable`·`failed` 모두 200 이고 셋 다
     * 성공이다** — 유저는 셋 모두에서 손으로 채워 확정할 수 있다.
     *
     * ⚠️ **이 화면을 다시 불러 복구하려 들지 마라.** 이미 추출된 초안을 다시 부르면
     *    저장된 결과가 오는데, 유저가 **미동의** 였다면 항목이 애초에 저장되지 않아
     *    그때도 안 온다. 화면 상태는 앱이 들고 있어야 한다 (저쪽 docs §3).
     */
    suspend fun extract(accessToken: String, draftId: String): Result<VetVisitDraft> =
        call(
            accessToken,
            "POST",
            "/${encode(draftId)}/extract",
            readTimeoutMs = EXTRACT_TIMEOUT_MS,
        ) { _, body -> VetVisitDraft.parse(JSONObject(body)) }

    /** ④ 확정. **`vet_visits` 에 행이 생기는 유일한 자리다.** */
    suspend fun confirm(
        accessToken: String,
        draftId: String,
        confirmation: VetVisitConfirmation,
    ): Result<VetVisit> =
        call(
            accessToken,
            "POST",
            "/${encode(draftId)}/confirm",
            body = confirmation.toJson(),
        ) { _, body -> VetVisit.parse(JSONObject(body)) }

    /** 목록, 최근 먼저. 창을 안 보내면 저쪽 기본값(최근 1년)이다. */
    suspend fun list(accessToken: String, petId: String): Result<List<VetVisit>> =
        call(accessToken, "GET", "?pet_id=${encode(petId)}") { _, body ->
            JSONObject(body).optJSONArray("visits").toObjectList(VetVisit::parse)
        }

    /**
     * 사유 드롭다운의 목록. 이 강아지가 최근 쓴 사유가 앞이다.
     *
     * **목록 화면도 이걸 쓴다** — 확정 응답에는 `reason_code` 만 있고 표시명이 없어서,
     * 코드→표시명 지도가 여기서 온다. 17개 한글을 앱에 적으면 닫힌 목록을 서버가
     * 지키는 이유가 그 자리에서 다시 샌다.
     */
    suspend fun reasonOptions(accessToken: String, petId: String): Result<List<VetReasonOption>> =
        call(accessToken, "GET", "/reason-options?pet_id=${encode(petId)}") { _, body ->
            JSONArray(body).toObjectList(VetReasonOption::parse)
        }

    /** 지운다. `DELETE /app/vet-visits/{id}` → 204. 내 기록이 아니면 404. */
    suspend fun delete(accessToken: String, visitId: String): Result<Unit> =
        call(accessToken, "DELETE", "/${encode(visitId)}") { _, _ -> }

    // -- 아래는 배관 -------------------------------------------------------

    private suspend fun <T> call(
        accessToken: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
        parse: (Int, String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val reply = transport.exchange(
                HttpCall(
                    method = method,
                    url = "${baseUrl().trimEnd('/')}/app/vet-visits$path",
                    headers = mapOf(
                        "Accept" to "application/json",
                        "Authorization" to "Bearer $accessToken",
                    ),
                    body = body?.toString(),
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = readTimeoutMs,
                ),
            )
            if (reply.status !in 200..299) throw vetError(reply.status, reply.body)
            parse(reply.status, reply.body)
        }.recoverCatching { cause ->
            if (cause is IllegalStateException) throw cause
            throw ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
        }
    }

    /**
     * 저쪽 409 는 `{"code": ..., "message": ...}` 다 (`photo_not_uploaded` ·
     * `invalid_photo_size`). [ChatApiError.from] 은 **모르는 코드의 문장을 버리고**
     * 기본 문구로 떨어지므로 — 대화 API 에는 코드별 문장이 앱에 있어서 맞는 규칙이다 —
     * 여기서 서버가 준 문장을 다시 얹는다. 진료비 쪽 코드를 앱에 또 적지 않으려는 것이다.
     */
    private fun vetError(status: Int, body: String): ChatApiError {
        val base = ChatApiError.from(status, body) { "서버 오류 ($it)" }
        val sentence = base.data["message"]?.takeIf { it.isNotBlank() && it != "null" } ?: return base
        return ChatApiError(status, base.code, sentence, base.data)
    }

    private fun uploadSentence(status: Int): String = when (status) {
        HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> "영수증 사진이 너무 커요. 다시 찍어 주세요."
        else -> "영수증 사진을 올리지 못했어요 ($status)."
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun <T> JSONArray?.toObjectList(parse: (JSONObject) -> T): List<T> =
        List(this?.length() ?: 0) { parse(this!!.getJSONObject(it)) }

    private companion object {
        const val JPEG = "image/jpeg"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000

        /** ③은 Gemini 를 태운다. 30초로는 짧다 (`ScreeningRecordApi` 의 confirm 과 같은 이유). */
        const val EXTRACT_TIMEOUT_MS = 60_000
    }
}

/**
 * bridge 로 바이트 한 덩이를 올리는 한 번. **상태 코드만 돌려준다** — 본문에 볼 것이 없다.
 * 닿지 못하면 예외를 던진다 ([HttpTransport] 와 같은 규칙).
 */
internal fun interface BridgeUploader {
    fun put(url: String, headers: Map<String, String>, bytes: ByteArray): Int
}

/** 진짜 업로드. `ScreeningRecordApi.upload` 의 배관을 그대로 옮긴 것이라 동작이 다르지 않다. */
internal object UrlConnectionUploader : BridgeUploader {
    override fun put(url: String, headers: Map<String, String>, bytes: ByteArray): Int {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = UPLOAD_TIMEOUT_MS
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            // 사진 하나가 메모리에 두 번 앉지 않게 길이를 먼저 알려 준다.
            setFixedLengthStreamingMode(bytes.size)
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val UPLOAD_TIMEOUT_MS = 30_000
}
