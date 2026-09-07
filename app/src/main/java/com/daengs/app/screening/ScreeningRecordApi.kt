package com.daengs.app.screening

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 피부 **변화 기록** API. 계약은 저쪽 `routers/screening.py` 의 `/app/screening/…` 다.
 *
 * ## 옛 경로는 없어졌다 (#134)
 *
 * 예전에는 `/screen/v1/screen` 을 부르는 `ScreeningApi` 가 나란히 있었고, 토큰이
 * 없거나 저장소가 안 켜졌을 때 [ScreeningRun] 이 그리로 물러섰다. 그쪽은 판정만
 * 받아 오고 **아무것도 안 남아서**, 물러선 날에는 기록이 조용히 안 생겼다.
 *
 * 저쪽이 `/screen/…` 에 인증과 rate limit 을 걸려면 앱이 먼저 떠야 해서 뗐다.
 * **서버 라우터는 그대로 있다** — 콘솔(웹)이 아직 그 경로를 쓴다.
 *
 * ## 흐름이 왜 세 걸음인가
 *
 * `기록 열기 → 사진 PUT → confirm`. 보행·프로필 사진과 같은 모양이고, 저쪽이 GCS 로
 * 되돌아가도 앱이 안 바뀌게 하려는 계약이다. **`confirm` 이 판정까지 한다** — 그래서
 * 그 응답이 0.6~3초 걸린다. 옛 경로도 같은 시간을 기다렸으니 새로 생긴 지연은 아니다.
 *
 * ⚠️ 주석에 경로를 쓸 때 뒤에 별표를 붙이지 말 것 — **Kotlin 은 블록 주석이 중첩돼서**
 *    그 두 글자가 새 주석을 열고 파일 끝까지 먹는다 (`Unclosed comment`). 실제로 그렇게
 *    한 번 막혔다.
 */
object ScreeningRecordApi {

    val configured: Boolean
        get() = BuildConfig.API_BASE_URL.isNotBlank()

    /**
     * 기록을 열고 사진 올릴 자리를 받는다.
     *
     * @param petId 어느 아이의 피부인가. **없어도 된다** — 아이를 아직 등록 안 했을 수 있다.
     * @param box 가이드 프레임. **정규화 `[x, y, w, h]` (0~1)** 이고, 픽셀 좌표를 보내면
     *   저쪽이 422 로 막는다. 안 보내면 저쪽이 화면 중앙으로 물러서는데, 1단계는 큰
     *   차이가 없지만 **2단계 분포가 학습 크롭과 어긋난다.**
     */
    suspend fun start(
        accessToken: String,
        petId: String?,
        box: FloatArray?,
    ): Result<ScreeningTicket> {
        val body = JSONObject().put("content_type", JPEG)
        if (petId != null) body.put("pet_id", petId)
        if (box != null && box.size == 4) {
            body.put("box", JSONArray().apply { box.forEach { put(it.toDouble()) } })
        }
        return json(accessToken, "/records", "POST", body) { ScreeningTicket.parse(JSONObject(it)) }
    }

    /**
     * 티켓이 준 자리에 사진을 올린다.
     *
     * **여기는 우리 API 가 아니다.** 주소도 헤더도 티켓이 준 것을 그대로 쓰고 토큰을
     * 붙이지 않는다 — 저쪽 bridge 는 인증 헤더를 안 받고 **키가 자격**이다.
     */
    suspend fun upload(ticket: ScreeningTicket, jpeg: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(ticket.uploadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = UPLOAD_TIMEOUT_MS
                    doOutput = true
                    ticket.uploadHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                    setFixedLengthStreamingMode(jpeg.size)
                }
                conn.use {
                    it.outputStream.use { out -> out.write(jpeg) }
                    if (it.responseCode !in 200..299) it.fail()
                }
            }.recoverCatching { rethrow(it) }
        }

    /**
     * 사진을 확인하고 **판정까지** 받는다.
     *
     * ⚠️ **여기가 오래 걸린다** (0.6~3초). 첫 요청은 저쪽이 가중치를 올리느라 더 걸린다.
     */
    suspend fun confirm(accessToken: String, recordId: String): Result<ScreeningRecord> =
        json(accessToken, "/records/$recordId/confirm", "POST") {
            ScreeningRecord.parse(JSONObject(it))
        }

    /** 기록 목록. **최근 순.** `petId` 를 주면 그 아이 것만. */
    suspend fun list(accessToken: String, petId: String? = null): Result<List<ScreeningRecord>> =
        json(accessToken, "/records" + (petId?.let { "?pet_id=$it" } ?: ""), "GET") { body ->
            val arr = JSONObject(body).getJSONArray("records")
            (0 until arr.length()).map { ScreeningRecord.parse(arr.getJSONObject(it)) }
        }

    /** 기록 하나. **여기서만 사진 주소가 온다** (목록은 안 싣는다). */
    suspend fun get(accessToken: String, recordId: String): Result<ScreeningRecord> =
        json(accessToken, "/records/$recordId", "GET") { ScreeningRecord.parse(JSONObject(it)) }

    /** 기록과 사진을 지운다. */
    suspend fun delete(accessToken: String, recordId: String): Result<Unit> =
        json(accessToken, "/records/$recordId", "DELETE") { }

    /** 사진 바이트를 받아 온다. 주소는 [ScreeningRecord.photoUrl] 이다. */
    suspend fun photo(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
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

    // -- 아래는 배관 -------------------------------------------------------

    private suspend fun <T> json(
        accessToken: String,
        path: String,
        method: String,
        body: JSONObject? = null,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val conn = (URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/app/screening$path")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                // confirm 이 판정까지 한다. 10초로는 짧다.
                readTimeout = if (path.endsWith("/confirm")) SCREEN_TIMEOUT_MS else TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            conn.use {
                if (body != null) {
                    it.doOutput = true
                    it.setRequestProperty("Content-Type", "application/json")
                    it.outputStream.use { out -> out.write(body.toString().toByteArray()) }
                }
                if (it.responseCode !in 200..299) it.fail()
                parse(if (it.responseCode == 204) "" else it.inputStream.bufferedReader().use { r -> r.readText() })
            }
        }.recoverCatching { rethrow(it) }
    }

    /**
     * 저쪽이 사용자에게 보여 줄 문장을 준다. 모양이 셋이라 갈라 읽는다
     * (`PetApi.fail` 과 같은 이유). 여기는 **`{"code": …, "message": …}`** 가 더 온다 —
     * 409 가 그 모양이다 (`photo_not_uploaded` · `screening_failed` 등).
     */
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

    private fun rethrow(cause: Throwable): Nothing {
        if (cause is IllegalStateException) throw cause
        throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
    }

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    private const val JPEG = "image/jpeg"
    private const val TIMEOUT_MS = 10_000
    private const val UPLOAD_TIMEOUT_MS = 30_000

    /** 판정이 CPU 로 돈다. 첫 요청은 가중치를 올리느라 더 걸린다. */
    private const val SCREEN_TIMEOUT_MS = 60_000
}

/** 사진을 올릴 자리. 주소와 헤더를 **우리가 만들지 않는다.** */
data class ScreeningTicket(
    val recordId: String,
    val storageKey: String,
    val uploadUrl: String,
    val uploadHeaders: Map<String, String>,
    val expiresInSeconds: Int,
) {
    companion object {
        fun parse(json: JSONObject): ScreeningTicket {
            val headers = json.optJSONObject("upload_headers")
            return ScreeningTicket(
                recordId = json.getString("record_id"),
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
 * 기록 한 줄.
 *
 * **[report] 가 null 일 수 있다.** 아직 사진을 안 올렸거나(`PENDING_UPLOAD`) 판정이
 * 실패한(`FAILED`) 기록이다. 실패한 기록도 **사진은 볼 수 있다** — 다시 찍을지
 * 정하려면 봐야 한다.
 */
data class ScreeningRecord(
    val recordId: String,
    val petId: String?,
    val status: Status,
    /** 서버가 준 ISO 시각 문자열. 화면이 필요할 때만 파싱한다. */
    val createdAt: String,
    val report: ScreeningReport?,
    /** 무엇으로 판정했나. 옛 기록을 그릴 때 본다. */
    val contractVersion: String?,
    /** **단건 조회에서만 온다.** 목록은 안 싣는다 — N 장마다 저장소를 두드리게 된다. */
    val photoUrl: String?,
) {
    enum class Status { PENDING_UPLOAD, DONE, FAILED }

    companion object {
        fun parse(json: JSONObject): ScreeningRecord = ScreeningRecord(
            recordId = json.getString("record_id"),
            petId = json.optStringOrNull("pet_id"),
            status = when (json.optString("status")) {
                "DONE" -> Status.DONE
                "FAILED" -> Status.FAILED
                else -> Status.PENDING_UPLOAD
            },
            createdAt = json.optString("created_at"),
            // 판정 전이거나 실패면 없다. 옛 경로와 **같은 모양**이라 그대로 판다.
            report = json.optJSONObject("result")?.let(ScreeningReport::parse),
            contractVersion = json.optStringOrNull("contract_version"),
            photoUrl = json.optStringOrNull("photo_url"),
        )

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}
