package com.daengs.app.gait

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * 보행 분석 API. **`/app/gait/…` 를 부르는 곳은 여기 하나다.**
 *
 * 계약은 저쪽 저장소(`SAJOYO/DAENGS_dev`)의 `routers/gait.py` · `schemas/gait.py` 다
 * (결정 D-043). `daengs_backend` 의 `openapi.json` 에 나온다 —
 * [PetApi][com.daengs.app.pet.PetApi] · [AuthApi][com.daengs.app.auth.AuthApi] 와
 * **같은 서버 · 같은 토큰**이라 [BuildConfig.API_BASE_URL] 을 그대로 쓴다.
 *
 * ### 옛 `/gait/…` 에서 무엇이 바뀌었나 (#64)
 *
 * ⚠️ 주소를 KDoc 에 쓸 때 `/gait/` 뒤에 `*` 를 붙이지 않는다. **Kotlin 은 블록 주석이
 *    중첩돼서** 그 `/` + `*` 가 새 주석을 열고, 파일 끝에서 "Unclosed comment" 로 터진다.
 *    원본이 `…` 를 쓴 이유가 이것이다 (실제로 한 번 밟았다).
 *
 * 옛 주소는 **인증이 없었다.** `dog_id` 를 넘어온 대로 믿어서, 남의 id 를 넣으면 남의
 * 기록을 받아오고 지울 수 있었다. 이제 backend 가 토큰으로 사람을 확인하고
 * `pet_id → pets.app_user_id` 로 소유권을 검증한다. **없는 것과 남의 것은 똑같이 404 다**
 * — 403 을 주면 "그 기록이 존재한다"가 새기 때문이다.
 *
 * 그리고 **한 방 업로드가 아니라 세 걸음**이 됐다. 영상이 backend 를 통과하지 않고
 * 저장소로 직접 가는 구조라서다:
 *
 * ```
 * ① POST /app/gait/analyze      → 기록(PENDING) + 업로드 티켓
 * ② PUT  <티켓의 upload_url>     → 영상 바이트 (backend 아님)
 * ③ POST /app/gait/records/{id}/confirm → 서버가 실존 확인 후 분석 큐에 넣음
 * ④ GET  /app/gait/records/{id} → status 가 DONE/FAILED 가 될 때까지 폴링
 * ```
 *
 * ⚠️ **②의 주소·헤더를 앱이 해석하지 않는다.** 지금 서버는 임시로 자기 자신을 가리키는
 *    주소를 주지만(LocalBridge), 곧 GCS Signed URL 로 바뀐다. 티켓이 준 `upload_url` 에
 *    `upload_headers` 를 그대로 얹어 보내면 **두 경우 모두 그대로 동작한다** — 그래서
 *    여기서 호스트를 뜯어보거나 우리 토큰을 얹지 않는다. Signed URL 에 우리 헤더를
 *    얹으면 서명이 깨진다.
 *
 * [ScreeningRecordApi][com.daengs.app.screening.ScreeningRecordApi] 와 같은 이유로 HTTP
 * 라이브러리를 안 쓴다 — 부를 엔드포인트가 여섯이고 배관은 이미 여러 번 썼다.
 */
object GaitApi {

    /**
     * 보행을 부를 수 있는 상태인가. 화면이 이걸 보고 보행 줄을 막는다.
     *
     * 두 가지를 같이 본다:
     *
     * - **주소** — 옛 `GAIT_BASE_URL` 이 아니라 [BuildConfig.API_BASE_URL] 이다.
     *   보행이 backend 뒤로 들어왔기 때문이다.
     * - **스위치** — [BuildConfig.GAIT_ENABLED]. `daengs.gaitUrl`(릴리즈는
     *   `daengs.gaitUrlRelease`)을 비우면 꺼진다.
     *
     * ⚠️ **스위치를 따로 둔 이유**: 주소만 보면 **릴리즈에서 보행을 끌 방법이 사라진다**
     *    (`API_BASE_URL` 은 릴리즈에 반드시 있다). #64 가 "테스터 빌드에서는 꺼 두는 편이
     *    안전하다"며 남겨 둔 손잡이라, 주소를 옮기면서 그것까지 없애면 안 된다.
     */
    val configured: Boolean
        get() = BuildConfig.API_BASE_URL.isNotBlank() && BuildConfig.GAIT_ENABLED

    // -- 엔드포인트 ---------------------------------------------------------

    /**
     * ① 기록을 만들고 업로드 티켓을 받는다. `POST /app/gait/analyze`.
     *
     * 아직 영상은 안 보낸다 — 여기서 오는 것은 `record_id` 와 **어디에 올릴지**다.
     * 저장 키는 **backend 가 만든다.** 앱이 정할 수 없고, 보내는 [sourceFile] 은
     * 표시용 이름일 뿐이다 (확장자만 키에 반영된다).
     */
    suspend fun startAnalysis(
        accessToken: String,
        petId: String,
        sourceFile: String,
        contentType: String,
        capturedAt: LocalDate?,
        note: String? = null,
    ): Result<GaitTicket> = call {
        val body = JSONObject()
            .put("pet_id", petId)
            .put("source_file", sourceFile)
            .put("content_type", contentType)
        capturedAt?.let { body.put("captured_at", it.toString()) }
        note?.let { body.put("note", it) }
        open("/analyze", "POST", accessToken).use {
            it.writeJson(body)
            GaitTicket.parse(it.readJson())
        }
    }

    /**
     * ② 영상 바이트를 티켓이 가리키는 곳에 올린다.
     *
     * **backend 가 아니라 저장소로 간다** (지금은 임시로 backend 를 경유하지만 앱은
     * 그것을 몰라야 한다 — 위 클래스 주석). 그래서 여기서는:
     *
     * - 주소를 [GaitTicket.uploadUrl] 그대로 쓴다 (base URL 을 붙이지 않는다)
     * - 헤더도 [GaitTicket.uploadHeaders] 그대로 얹는다
     * - **우리 access token 을 얹지 않는다** — GCS Signed URL 이면 서명이 깨진다
     *
     * **영상을 메모리에 통째로 올리지 않는다.** 한도가 150MB 라 `ByteArray` 로 읽으면
     * 그 자리에서 OOM 이다. `setChunkedStreamingMode` 로 흘려보낸다.
     */
    suspend fun upload(context: Context, ticket: GaitTicket, video: Uri): Result<Unit> = call {
        val conn = (URL(ticket.uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = UPLOAD_TIMEOUT_MS
            doOutput = true
            setChunkedStreamingMode(0)
            ticket.uploadHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.use {
            val stream: InputStream = context.contentResolver.openInputStream(video)
                ?: error("영상을 열 수 없습니다.")
            stream.use { input -> it.outputStream.use { out -> input.copyTo(out) } }
            // 저장소는 본문 없이 200/204 만 준다. 실패면 여기서 문장을 뽑아 던진다.
            if (it.responseCode !in 200..299) error(uploadFailureFor(it.responseCode))
        }
    }

    /**
     * ③ "올렸다"고 알린다. `POST /app/gait/records/{id}/confirm`.
     *
     * **서버가 앱의 말만 믿지 않는다** — 저장소에 실제로 있는지 확인한 뒤에야 분석
     * 큐에 넣는다. 그래서 업로드가 중간에 끊겼으면 여기서 409 가 온다.
     */
    suspend fun confirm(accessToken: String, recordId: String): Result<GaitSummary> = call {
        open("/records/${encode(recordId)}/confirm", "POST", accessToken)
            .use { GaitSummary.parse(it.readJson()) }
    }

    /**
     * ④ 기록 단건. `GET /app/gait/records/{id}`. 진행 상태([GaitAnalyzed.status])도 여기 있다.
     *
     * 분석이 끝났는지 아는 방법이 이것뿐이다 — 옛 주소처럼 한 방에 결과가 오지 않는다.
     */
    suspend fun record(accessToken: String, recordId: String): Result<GaitAnalyzed> = call {
        open("/records/${encode(recordId)}", "GET", accessToken)
            .use { GaitAnalyzed.parse(it.readJson()) }
    }

    /**
     * 한 강아지의 기록 목록. `GET /app/gait/records?pet_id=`.
     *
     * **`dog_id` 가 아니라 `pet_id` 다.** 서버가 만든 진짜 `pets.id` UUID 이고, 남의 것을
     * 넣으면 404 다 (옛 주소에서는 그대로 통했다 — 그게 #64 였다).
     *
     * 저쪽은 **오래된 것부터** 준다. 앱 목록은 최근이 앞이라 [GaitHolder] 가 뒤집는다.
     */
    suspend fun records(
        accessToken: String,
        petId: String,
        limit: Int = 20,
        cursor: String? = null,
    ): Result<GaitPage> = call {
        val query = buildString {
            append("?pet_id=").append(encode(petId))
            append("&limit=").append(limit)
            if (cursor != null) append("&cursor=").append(encode(cursor))
        }
        open("/records$query", "GET", accessToken).use { GaitPage.parse(it.readJson()) }
    }

    /**
     * 같은 반려견의 두 기록 비교. `POST /app/gait/compare`.
     *
     * **순서는 상관없다** — 서버가 날짜로 past/recent 를 정한다. 그래서 A 진입(방금
     * 분석한 기록이 기준)과 B 진입(둘 다 고름)이 같은 호출을 쓴다.
     *
     * ⚠️ **영상을 읽지 않는다.** 비교는 저쪽 DB 의 분석 데이터만으로 끝난다 — 저장소가
     *    무엇이든(local·gcs) 이 호출은 그대로 동작한다.
     */
    suspend fun compare(
        accessToken: String,
        recordIdA: String,
        recordIdB: String,
    ): Result<GaitCompared> = call {
        val body = JSONObject().put("record_id_a", recordIdA).put("record_id_b", recordIdB)
        open("/compare", "POST", accessToken).use {
            it.writeJson(body)
            GaitCompared.parse(it.readJson())
        }
    }

    /**
     * 기록을 지운다. `DELETE /app/gait/records/{id}`.
     *
     * 서버는 지우기로 표시만 하고 **저장소 파일 정리는 워커가 이어서** 한다. 앱에서는
     * 지운 순간 목록에서 사라지고 다시 조회하면 404 다.
     */
    suspend fun delete(accessToken: String, recordId: String): Result<Unit> = call {
        open("/records/${encode(recordId)}", "DELETE", accessToken).use { it.readJson() }
        Unit
    }

    // -- 올릴 파일의 이름과 형식 -------------------------------------------

    /**
     * 표시용 파일 이름. 저장 키는 서버가 만들지만 **확장자는 여기서 온 것을 쓴다.**
     * 못 읽으면 `gait.mp4` 로 둔다 — 확장자가 분석 결과를 바꾸지는 않는다.
     */
    fun displayNameOf(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
    }.getOrNull() ?: "gait.mp4"

    fun contentTypeOf(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri) ?: "video/mp4"

    /**
     * 보내기 전에 크기를 잰다. **150MB 를 다 올리고 413 을 받으면 데이터도 시간도 버린다.**
     * 콘텐츠 제공자가 크기를 모르면 null 이고, 그때는 재지 않고 그냥 올린다.
     */
    fun oversizeMessage(context: Context, uri: Uri): String? {
        val bytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length.takeIf { len -> len >= 0 }
            }
        }.getOrNull() ?: return null
        if (bytes <= MAX_UPLOAD_BYTES) return null
        return "영상이 너무 커요 (${bytes.asMegabytes()}MB). " +
            "${MAX_UPLOAD_BYTES.asMegabytes()}MB 아래로 줄이거나 더 짧게 찍어 주세요."
    }

    // -- 배관 ---------------------------------------------------------------

    private suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) {
                "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요."
            }
            block()
        }.recoverCatching { cause ->
            // 서버가 준 문장은 그대로 통과시킨다. 나머지(연결 실패)는 영어 한 줄이라
            // 말풍선에 그대로 띄우면 안 된다 — 스크리닝·펫과 같은 판단이다.
            if (cause is IllegalStateException) throw cause
            throw IllegalStateException("보행 서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
        }
    }

    private fun open(path: String, method: String, accessToken: String) =
        (URL(BuildConfig.API_BASE_URL.trimEnd('/') + BASE_PATH + path)
            .openConnection() as HttpURLConnection)
            .apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = SHORT_READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

    private fun HttpURLConnection.writeJson(body: JSONObject) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        outputStream.use { it.write(body.toString().toByteArray()) }
    }

    /**
     * 본문을 읽는다. 오류면 **저쪽 `detail` 을 꺼내 던진다.**
     *
     * 저쪽이 사용자에게 보여 줄 말로 써 놨다("강아지를 찾을 수 없습니다" 같은).
     */
    private fun HttpURLConnection.readJson(): JSONObject {
        val ok = responseCode in 200..299
        val text = (if (ok) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }
        if (!ok) error(detailOf(text) ?: sentenceFor(responseCode))
        return JSONObject(text.orEmpty().ifBlank { "{}" })
    }

    /**
     * 저쪽이 문장을 안 줄 때 앱이 대신 하는 말. **새 계약의 오류들이다.**
     *
     * 코드를 그대로 띄우면("409 를 돌려줬어요") 사용자가 무엇을 해야 할지 모른다.
     */
    private fun sentenceFor(code: Int): String = when (code) {
        // 토큰이 없거나 만료됐다. 앱이 재발급을 시도하고도 여기 오면 다시 로그인해야 한다.
        401 -> "로그인이 필요해요. 다시 로그인해 주세요."
        // **남의 것도 여기로 온다** — 없는 것과 구분하지 않는 것이 서버의 규칙이다.
        404 -> "그 기록을 찾지 못했어요."
        // confirm 을 두 번 불렀거나, 업로드가 끝나기 전에 불렀다.
        409 -> "업로드가 끝나지 않았어요. 다시 시도해 주세요."
        413 -> "영상이 너무 커요. ${MAX_UPLOAD_BYTES.asMegabytes()}MB 아래로 줄여 주세요."
        // 요청 모양이 틀렸다. 사용자가 할 수 있는 게 없어 개발 중에만 보인다.
        422 -> "요청을 처리하지 못했어요. 앱을 최신 버전으로 업데이트해 주세요."
        // 저장소가 아직 설정되지 않았다(서버 준비 중). 사용자가 할 수 있는 게 없다.
        503 -> "보행 분석이 아직 준비되지 않았어요. 잠시 뒤에 다시 해주세요."
        else -> "보행 서버가 응답하지 못했어요. (${code})"
    }

    /**
     * 업로드는 저장소가 받는다 — **backend 의 `detail` 문장이 없다.** 그래서 코드로만
     * 말을 고른다. 만료된 티켓(403/404)은 다시 시도하면 새 티켓이 나온다.
     */
    private fun uploadFailureFor(code: Int): String = when (code) {
        403, 404 -> "업로드 시간이 지났어요. 다시 시도해 주세요."
        413 -> "영상이 너무 커요. ${MAX_UPLOAD_BYTES.asMegabytes()}MB 아래로 줄여 주세요."
        else -> "영상을 올리지 못했어요. 연결을 확인하고 다시 시도해 주세요. (${code})"
    }

    private fun Long.asMegabytes(): Long = this / (1024 * 1024)

    private fun detailOf(text: String?): String? = runCatching {
        when (val detail = JSONObject(text ?: "").opt("detail")) {
            is String -> detail
            is JSONObject -> detail.optString("message").ifBlank { null }
            else -> null
        }
    }.getOrNull()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }

    /** backend 의 보행 계약이 사는 자리. nginx 가 접두사를 떼지 않는다 (`/screen` 과 같다). */
    private const val BASE_PATH = "/app/gait"

    /** 저쪽 nginx `/app/gait/` 는 200m 를 받는다. 앱은 그보다 낮게 먼저 막는다. */
    private const val MAX_UPLOAD_BYTES = 150L * 1024 * 1024

    private const val CONNECT_TIMEOUT_MS = 15_000

    /** 티켓 발급·confirm·목록·삭제는 금방 온다. **분석을 기다리지 않는다.** */
    private const val SHORT_READ_TIMEOUT_MS = 30_000

    /** 업로드만 길다. 116MB 가 모바일 회선으로 나갈 시간을 준다. */
    private const val UPLOAD_TIMEOUT_MS = 600_000
}

/**
 * ①이 돌려준 것 — 기록 하나와 **어디에 올릴지**.
 *
 * ⚠️ [uploadUrl] 과 [uploadHeaders] 를 **해석하지 않고 그대로 쓴다.** 지금은 서버가
 *    자기 주소를 주지만(임시 bridge) 곧 GCS Signed URL 이 온다. 그대로 쓰면 둘 다
 *    동작하고, 뜯어보기 시작하면 저장소가 바뀔 때마다 앱이 바뀐다.
 */
data class GaitTicket(
    val recordId: String,
    val status: String,
    val uploadUrl: String,
    val uploadHeaders: Map<String, String>,
    val expiresInSeconds: Int,
) {
    companion object {
        fun parse(json: JSONObject): GaitTicket {
            val headers = json.optJSONObject("upload_headers")
            return GaitTicket(
                recordId = json.getString("record_id"),
                status = json.optString("status", GaitStatus.PENDING),
                uploadUrl = json.getString("upload_url"),
                uploadHeaders = headers?.keys()?.asSequence()
                    ?.associateWith { headers.optString(it) } ?: emptyMap(),
                expiresInSeconds = json.optInt("expires_in_seconds", 0),
            )
        }
    }
}

/** 서버가 쓰는 진행 상태. 폴링이 이 값으로 끝을 판단한다. */
object GaitStatus {
    const val PENDING = "PENDING"
    const val UPLOADED = "UPLOADED"
    const val PROCESSING = "PROCESSING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"

    /** 더 기다려도 안 바뀌는 상태. */
    fun settled(status: String): Boolean = status == DONE || status == FAILED
}

/**
 * 서버가 돌려준 기록 한 건.
 *
 * **점수·등급을 담는 필드가 없다.** 저쪽에는 `internal_feature_vector` 가 있는데
 * **응답 스키마가 아예 그 필드를 모른다** — 화면에 수백 개의 숫자가 나오면 사용자는
 * 그것을 건강 점수로 읽는다. 여기서도 안 받으므로 화면이 지어낼 수 없다.
 *
 * [status] 가 새로 생겼다. 옛 주소는 한 방에 결과를 줘서 이 값이 필요 없었다.
 */
data class GaitAnalyzed(
    val recordId: String,
    val status: String,
    val date: LocalDate?,
    val qualityOk: Boolean,
    /** `good` / `low`. `qualityOk` 가 false 면 null 이다. */
    val qualityTier: String?,
    /** 왜 못 쓰는지. 저쪽이 사용자에게 보여 줄 말로 써 준다. */
    val reason: String?,
    val recommendation: String?,
    val hasOverlay: Boolean,
    /**
     * 워커가 실패한 사유. **운영 진단용이라 화면에 그대로 띄우지 않는다** —
     * 스택 조각이나 내부 경로가 들어 있을 수 있다.
     */
    val failureReason: String?,
) {
    val settled: Boolean get() = GaitStatus.settled(status)

    companion object {
        fun parse(json: JSONObject): GaitAnalyzed {
            val quality = json.optJSONObject("quality")
            return GaitAnalyzed(
                recordId = json.getString("record_id"),
                status = json.optString("status", GaitStatus.DONE),
                // 옛 응답의 `date` 가 `captured_at` 으로 바뀌었다.
                date = json.optStringOrNull("captured_at")?.let(LocalDate::parse),
                // **앱이 정하지 않는다.** 저쪽 quality_status 가 그대로 온다.
                qualityOk = json.optStringOrNull("quality_status") == "ok",
                qualityTier = json.optStringOrNull("quality_tier"),
                reason = quality?.optStringOrNull("reason"),
                recommendation = quality?.optStringOrNull("recommendation"),
                hasOverlay = json.optBoolean("has_overlay", false),
                failureReason = json.optStringOrNull("failure_reason"),
            )
        }
    }
}

/** 목록 한 장. 저쪽은 요약만 준다 — `quality` 세부는 단건에만 있다. */
data class GaitPage(val records: List<GaitSummary>, val nextCursor: String?) {
    companion object {
        fun parse(json: JSONObject): GaitPage {
            val array: JSONArray = json.optJSONArray("records") ?: JSONArray()
            return GaitPage(
                records = (0 until array.length()).map { GaitSummary.parse(array.getJSONObject(it)) },
                nextCursor = json.optStringOrNull("next_cursor"),
            )
        }
    }
}

/**
 * 목록의 한 줄.
 *
 * **길이(초)가 없다.** 저쪽 목록 응답에 담기지 않는다 — 방금 분석한 것은 앱이
 * 기기에서 읽어 알지만([PreparedVideo]), 서버에서 받아 온 지난 기록은 모른다.
 * 그래서 [GaitRecord.seconds] 가 null 을 받을 수 있다.
 */
data class GaitSummary(
    val recordId: String,
    val status: String,
    val date: LocalDate?,
    val comparable: Boolean,
    val hasOverlay: Boolean,
    /** 필터 버전. 서로 다른 버전끼리 비교하면 저쪽이 경고를 붙인다. */
    val filterVersion: String?,
) {
    companion object {
        fun parse(json: JSONObject): GaitSummary = GaitSummary(
            recordId = json.getString("record_id"),
            status = json.optString("status", GaitStatus.DONE),
            date = json.optStringOrNull("captured_at")?.let(LocalDate::parse),
            comparable = json.optBoolean("comparable", false),
            hasOverlay = json.optBoolean("has_overlay", false),
            filterVersion = json.optStringOrNull("gait_filter_version"),
        )
    }
}

/** `optString` 은 없는 키에 빈 문자열을 준다. null 과 "" 를 갈라야 하는 자리가 많다. */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }

// -- 서버 모양 → 화면 모양 --------------------------------------------------

/**
 * 목록 한 줄을 화면이 아는 [GaitRecord] 로 옮긴다.
 *
 * **길이와 표지가 없다.** 저쪽 목록 응답에 담기지 않고, 지난 기록의 원본은 기기에
 * 없다. 화면이 발바닥 자리표시로 물러선다.
 *
 * 아직 끝나지 않은 기록(`PENDING`·`PROCESSING`)도 목록에 온다. **비교 대상으로는
 * 세우지 않는다** — 서버의 `comparable` 이 이미 false 라 그대로 따라가면 된다.
 */
fun GaitSummary.toRecord(): GaitRecord = GaitRecord(
    id = recordId,
    date = date ?: LocalDate.now(),
    seconds = null,
    video = null,
    thumbnail = null,
    comparable = comparable,
)


/**
 * 비교 결과.
 *
 * **문장을 앱이 짓지 않는다.** [messageForUi] 가 저쪽이 실제 계산에서 유도한 한 줄이고,
 * 화면에 쓸 값으로 지목된 것이다. `_dev_only_*` 는 서버가 아예 안 내려준다.
 */
data class GaitCompared(
    val available: Boolean,
    val messageForUi: String?,
    /** 관절 이름 → (x 판정, y 판정). **합치지 않는다** — 아래 [toMetrics] 주석 참고. */
    val jointComparison: Map<String, JointNote>,
    val reliabilityNote: String?,
    /** 두 기록의 필터 버전이 다를 때. **표시해야 한다** — 같은 영상도 달라 보인다. */
    val versionWarning: String?,
) {
    /** 관절 하나의 축별 판정. */
    data class JointNote(val x: String?, val y: String?)

    companion object {
        fun parse(json: JSONObject): GaitCompared {
            val joints = json.optJSONObject("joint_movement_range_comparison")
            val notes = joints?.keys()?.asSequence()?.associateWith { joint ->
                // 저쪽은 관절마다 {record_a, record_b, comparison_note:{x,y}} 를 준다.
                val note = joints.optJSONObject(joint)?.optJSONObject("comparison_note")
                JointNote(note?.optStringOrNull("x"), note?.optStringOrNull("y"))
            } ?: emptyMap()
            return GaitCompared(
                available = json.optString("status", "ok") != "unavailable",
                messageForUi = json.optStringOrNull("message_for_ui"),
                jointComparison = notes,
                reliabilityNote = json.optStringOrNull("reliability_note"),
                versionWarning = json.optStringOrNull("version_warning"),
            )
        }
    }
}

/**
 * 비교 응답을 표의 줄들로 옮긴다.
 *
 * **관절 하나가 두 줄이 된다** (`Hock (좌우)` · `Hock (상하)`). x·y 를 하나로 합치지
 * 않는 것은 의도다 — 두 축은 뜻이 다르고(앞뒤 이동 vs 위아래 흔들림), 합치면 **어느 쪽이
 * 움직였는지가 사라진다.** 합치는 규칙은 모델이나 비교 로직을 바꿀 때 그때 정한다 (D-058).
 *
 * **문자열을 그대로 믿지 않고 아는 값만 옮긴다** — 모르는 값이 오면 [GaitDelta.Unknown]
 * 이다. "차이 관찰됨" 을 놓쳐 "유사" 로 떨어지면 **없는 안심**을 주게 된다.
 *
 * 비교 자체가 불가(`status: unavailable`)면 표를 비운다. 그러면 [GaitComparison] 이
 * 판정을 `NotEnough` 로 끌어낸다.
 */
fun GaitCompared.toMetrics(): List<GaitMetric> {
    if (!available) return emptyList()
    fun delta(v: String?) = when (v) {
        "비슷함" -> GaitDelta.Similar
        "차이 관찰됨" -> GaitDelta.Slight
        else -> GaitDelta.Unknown
    }
    return jointComparison.flatMap { (joint, note) ->
        listOf(
            GaitMetric("$joint (좌우)", delta(note.x)),
            GaitMetric("$joint (상하)", delta(note.y)),
        )
    }
}
