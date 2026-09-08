package com.daengs.app.dogcard

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * 도감 카드 서버 API. 계약은 저쪽 `routers/dogcard.py` 다.
 *
 * ## 왜 PUT 인가 — 다른 API 와 방향이 반대다
 *
 * 프로필 사진·피부 기록은 **서버가 id 를 만들고** 앱이 받아 쓴다. 카드는 반대다:
 * **앱이 만든 id 를 서버가 받는다.** 카드가 오프라인에서 먼저 만들어지기 때문이다 —
 * 로그인 없이 둘러보기로도 뽑고, 그 순간 이미 Room 에 id 가 박혀 있다.
 *
 * `DrawnCardRow.id` 주석이 이 계약을 예고해 뒀다: *"서버가 붙어도 이 id 를 그대로
 * 올려서 **재전송이 멱등해진다**"*. 저쪽이 그 말을 그대로 받았다.
 *
 * ⚠️ **이미 올린 카드는 다시 올려도 서버가 값을 안 바꾼다.** 카드는 뽑힌 뒤로 안 바뀌는
 *    물건이라 저쪽이 그렇게 막아 뒀다 — 앱의 버그 하나가 이미 뽑아 둔 카드를 조용히
 *    바꾸지 못하게. 그래서 재전송이 안전하다.
 *
 * ⚠️ **남이 가진 id 로 올리면 409 다** (`card_belongs_to_someone_else`).
 *    404 가 아닌 이유가 이것이다 — 그때 앱은 **id 를 새로 만들어 다시 올려야** 한다.
 */
object CardApi {

    val configured: Boolean
        get() = BuildConfig.API_BASE_URL.isNotBlank()

    /**
     * 카드 한 장을 올린다. **여러 번 보내도 한 장이다.**
     *
     * 돌려주는 [Upserted] 의 `faceUpload` 는 **얼굴이 아직 없을 때만** 온다 —
     * null 이면 이 카드는 다 된 것이다.
     */
    suspend fun upsert(accessToken: String, card: DrawnCard): Result<Upserted> =
        json(accessToken, "/${card.id}", "PUT", card.toBody()) {
            Upserted.parse(JSONObject(it))
        }

    /**
     * 얼굴 그림을 올린다. **PNG 뿐이다** — 구멍에 끼우려면 알파가 필요해서 JPEG 은 못 쓴다.
     *
     * 주소도 헤더도 티켓이 준 것을 그대로 쓴다. 토큰을 안 붙인다 —
     * 저쪽 bridge 는 인증 헤더를 안 받고 **키가 자격**이다.
     */
    suspend fun uploadFace(ticket: FaceTicket, png: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(ticket.uploadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = UPLOAD_TIMEOUT_MS
                    doOutput = true
                    ticket.uploadHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                    setFixedLengthStreamingMode(png.size)
                }
                conn.use {
                    it.outputStream.use { out -> out.write(png) }
                    if (it.responseCode !in 200..299) it.fail()
                }
            }.recoverCatching { rethrow(it) }
        }

    /** 올라온 얼굴을 확정한다. */
    suspend fun confirmFace(accessToken: String, cardId: String): Result<Unit> =
        json(accessToken, "/$cardId/face/confirm", "POST") { }

    /** 내 카드 전부, **최근에 뽑은 것부터.** 새 기기 복원이 이걸 쓴다. */
    suspend fun list(accessToken: String): Result<List<RemoteCard>> =
        json(accessToken, "", "GET") { body ->
            val arr = JSONObject(body).getJSONArray("cards")
            (0 until arr.length()).map { RemoteCard.parse(arr.getJSONObject(it)) }
        }

    /** 카드 하나. **여기서만 얼굴 주소가 온다** (목록은 안 싣는다). */
    suspend fun get(accessToken: String, cardId: String): Result<RemoteCard> =
        json(accessToken, "/$cardId", "GET") { RemoteCard.parse(JSONObject(it)) }

    /** 얼굴 그림 바이트. 주소는 [RemoteCard.faceUrl] 이다. */
    suspend fun face(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = UPLOAD_TIMEOUT_MS
            }
            conn.use {
                if (it.responseCode !in 200..299) it.fail()
                it.inputStream.use { input -> input.readBytes() }
            }
        }.recoverCatching { rethrow(it) }
    }

    /** 서버에서 지운다. 얼굴 그림까지 지워진다. */
    suspend fun delete(accessToken: String, cardId: String): Result<Unit> =
        json(accessToken, "/$cardId", "DELETE") { }

    // -- 아래는 배관 -------------------------------------------------------

    /**
     * Room 의 칸을 저쪽 본문으로.
     *
     * ⚠️ **`drawnAtMillis` 는 epoch millis 인데 저쪽은 시각 문자열을 받는다.**
     *    그대로 보내면 422 다.
     */
    private fun DrawnCard.toBody(): JSONObject = JSONObject().apply {
        put("template_id", templateId)
        if (dogId != null) put("dog_id", dogId)
        put("dog_name", dogName)
        put("drawn_at", Instant.ofEpochMilli(drawnAtMillis).toString())
        put("code_text", codeText)
        put("user_framed", userFramed)
        put("core_left", core.left)
        put("core_top", core.top)
        put("core_right", core.right)
        put("core_bottom", core.bottom)
    }

    private suspend fun <T> json(
        accessToken: String,
        path: String,
        method: String,
        body: JSONObject? = null,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val conn = (URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/app/cards$path")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            conn.use {
                if (body != null) {
                    it.doOutput = true
                    it.setRequestProperty("Content-Type", "application/json")
                    it.outputStream.use { out -> out.write(body.toString().toByteArray()) }
                }
                if (it.responseCode !in 200..299) it.fail(it.responseCode)
                parse(if (it.responseCode == 204) "" else it.inputStream.bufferedReader().use { r -> r.readText() })
            }
        }.recoverCatching { rethrow(it) }
    }

    /**
     * 저쪽 문장을 그대로 통과시킨다 (`PetApi.fail` 과 같은 이유).
     *
     * **409 는 따로 알아볼 수 있게 [CardConflict] 로 던진다** — 남이 가진 id 라는
     * 뜻이라, 부르는 쪽이 id 를 새로 만들어 다시 올려야 한다.
     */
    private fun HttpURLConnection.fail(code: Int = responseCode): Nothing {
        val body = runCatching {
            JSONObject(errorStream?.bufferedReader()?.readText().orEmpty())
        }.getOrNull()
        val raw = body?.opt("detail")
        val message = when (raw) {
            is String -> raw.takeIf(String::isNotBlank)
            is JSONObject -> raw.optString("message").takeIf(String::isNotBlank)
            else -> null
        } ?: "서버 오류 ($code)"
        val codeName = (raw as? JSONObject)?.optString("code").orEmpty()
        if (code == 409 && codeName == "card_belongs_to_someone_else") throw CardConflict(message)
        error(message)
    }

    private fun rethrow(cause: Throwable): Nothing {
        if (cause is CardConflict) throw cause
        if (cause is IllegalStateException) throw cause
        throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
    }

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    /** 저쪽 얼굴은 PNG 뿐이다. */
    const val PNG = "image/png"

    private const val TIMEOUT_MS = 10_000
    private const val UPLOAD_TIMEOUT_MS = 30_000
}

/** **남이 가진 id 다.** 부르는 쪽이 id 를 새로 만들어 다시 올려야 한다. */
class CardConflict(message: String) : Exception(message)

/** 올린 결과. [faceUpload] 가 null 이면 얼굴까지 다 된 것이다. */
data class Upserted(
    val card: RemoteCard,
    val faceUpload: FaceTicket?,
    /** 이번 요청으로 **새로 생겼나.** 동기화 진행률을 세는 데 쓴다. */
    val created: Boolean,
) {
    companion object {
        fun parse(json: JSONObject): Upserted = Upserted(
            card = RemoteCard.parse(json.getJSONObject("card")),
            faceUpload = json.optJSONObject("face_upload")?.let(FaceTicket::parse),
            created = json.optBoolean("created"),
        )
    }
}

/** 얼굴 PNG 를 올릴 자리. 주소와 헤더를 **우리가 만들지 않는다.** */
data class FaceTicket(
    val storageKey: String,
    val uploadUrl: String,
    val uploadHeaders: Map<String, String>,
    val expiresInSeconds: Int,
) {
    companion object {
        fun parse(json: JSONObject): FaceTicket {
            val headers = json.optJSONObject("upload_headers")
            return FaceTicket(
                storageKey = json.getString("storage_key"),
                uploadUrl = json.getString("upload_url"),
                uploadHeaders = headers?.keys()?.asSequence()
                    ?.associateWith { headers.getString(it) }
                    .orEmpty(),
                expiresInSeconds = json.optInt("expires_in_seconds"),
            )
        }
    }
}

/**
 * 서버가 들고 있는 카드 한 장.
 *
 * **[DrawnCard] 와 따로 둔다.** 그쪽은 Room 의 모양이고 여기는 서버의 모양이라,
 * 하나로 합치면 `hasFace` 같은 서버 사정이 기기 표에 스며든다.
 */
data class RemoteCard(
    val id: String,
    val templateId: String,
    val dogId: String?,
    val dogName: String,
    val drawnAtMillis: Long,
    val codeText: String,
    val userFramed: Boolean,
    val coreLeft: Int,
    val coreTop: Int,
    val coreRight: Int,
    val coreBottom: Int,
    /** 얼굴 그림이 서버에 있나. **false 면 앱이 자기 기기의 파일을 쓴다.** */
    val hasFace: Boolean,
    /** **단건 조회에서만 온다.** 목록은 안 싣는다. */
    val faceUrl: String?,
) {
    companion object {
        fun parse(json: JSONObject): RemoteCard = RemoteCard(
            id = json.getString("id"),
            templateId = json.getString("template_id"),
            dogId = json.optStringOrNull("dog_id"),
            dogName = json.optString("dog_name"),
            // 저쪽은 ISO 시각을 준다. Room 은 millis 라 여기서 되돌린다.
            drawnAtMillis = json.optStringOrNull("drawn_at")
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: 0L,
            codeText = json.optString("code_text"),
            userFramed = json.optBoolean("user_framed"),
            coreLeft = json.optInt("core_left"),
            coreTop = json.optInt("core_top"),
            coreRight = json.optInt("core_right"),
            coreBottom = json.optInt("core_bottom"),
            hasFace = json.optBoolean("has_face"),
            faceUrl = json.optStringOrNull("face_url"),
        )

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}
