package com.daengs.app.gait

import android.content.Context
import android.net.Uri
import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * 보행 분석 서버. **`/gait/…` 를 부르는 곳은 여기 하나다.**
 *
 * 계약은 저쪽 저장소(`SAJOYO/DAENGS_dev`)의 `backend/src/daengs_gait/API.md` 다.
 * `daengs_backend` 의 `openapi.json` 에는 안 나온다 — nginx 가 같은 호스트에서
 * `/gait` 접두사로 **별도 컨테이너**에 넘기기 때문이고, 스크리닝(`/screen`)과 같은
 * 모양이다.
 *
 * [AuthApi][com.daengs.app.auth.AuthApi] · [ScreeningApi][com.daengs.app.screening.ScreeningApi]
 * 와 같은 이유로 HTTP 라이브러리를 안 쓴다. 부를 엔드포인트가 여섯이고 배관은
 * 스크리닝에서 이미 한 번 썼다.
 *
 * ### 인증이 아직 없다
 *
 * 저쪽 API.md 가 못박아 뒀다 — 이 서비스는 인증·인가를 구현하지 않고, `dog_id` 는
 * **넘어온 값을 그대로 믿는다.** 소유권 검증은 `daengs_backend` 의 auth 계층 몫이고,
 * 최종 모양은 `앱 → daengs_backend → gait` 다. 지금 `daengback.~/gait/…` 가 직접
 * 열려 있는 것은 개발·검증 편의다.
 *
 * **그래서 토큰이 붙을 자리를 [authHeaders] 하나로 미리 뚫어 뒀다.** 인증이 생기면
 * 이 람다만 채우면 되고, 여섯 호출부는 안 건드린다. 주소가 backend 뒤로 옮겨가도
 * [BuildConfig.GAIT_BASE_URL] 한 줄이다.
 */
object GaitApi {

    /** 주소가 없으면 아무것도 못 부른다. 화면이 이걸 보고 보행 줄을 막는다. */
    val configured: Boolean
        get() = BuildConfig.GAIT_BASE_URL.isNotBlank()

    /**
     * 매 요청에 얹을 헤더. **인증이 붙을 자리다.**
     *
     * 지금은 비어 있다. `daengs_backend` 뒤로 옮겨가면 여기서 access token 을
     * 돌려주면 되고, 그러면 [analyze] 부터 [delete] 까지 전부 같이 따라간다.
     */
    var authHeaders: () -> Map<String, String> = { emptyMap() }

    // -- 엔드포인트 ---------------------------------------------------------

    /**
     * 영상 한 편을 올려 분석한다. `POST /gait/analyze` (multipart).
     *
     * ⚠️ **분 단위로 걸린다.** 사진 한 장이 아니라 영상 전체를 5fps 로 훑고 overlay
     * 까지 인코딩한다 — 저쪽 실측이 480x854 · 37초 영상에 CPU 약 2분이다. nginx 가
     * `proxy_read_timeout 600s` 로 열어 뒀고 [READ_TIMEOUT_MS] 도 거기 맞췄다.
     *
     * ⚠️ **`dogId` 를 안 주면 [records] 로 다시 못 찾는다.** 목록이 `dog_id` 로만
     * 거른다. 그래서 필수 인자로 뒀다 — 선택 인자로 두면 언젠가 빠뜨린다.
     */
    suspend fun analyze(
        context: Context,
        video: Uri,
        dogId: String,
        date: LocalDate?,
        note: String? = null,
    ): Result<GaitAnalyzed> = call {
        val conn = open("/analyze", "POST", READ_TIMEOUT_MS)
        conn.use {
            it.writeVideoMultipart(context, video, dogId, date, note)
            GaitAnalyzed.parse(it.readJson())
        }
    }

    /**
     * 한 강아지의 기록 목록. `GET /gait/records`.
     *
     * 저쪽은 **오래된 것부터** 준다 (시간 변화를 보는 서비스라 시계열 순서가
     * 자연스럽다는 이유). 앱 목록은 최근이 앞이라 [GaitAnalyzed.toRecord] 를 거친
     * 뒤 화면 쪽에서 뒤집는다.
     */
    suspend fun records(dogId: String, limit: Int = 20, cursor: String? = null): Result<GaitPage> =
        call {
            val query = buildString {
                append("?dog_id=").append(encode(dogId))
                append("&limit=").append(limit)
                if (cursor != null) append("&cursor=").append(encode(cursor))
            }
            open("/records$query", "GET").use { GaitPage.parse(it.readJson()) }
        }

    /** 기록 단건. `GET /gait/records/{id}`. 목록에 없는 `features` 가 여기 있다. */
    suspend fun record(recordId: String): Result<GaitAnalyzed> = call {
        open("/records/${encode(recordId)}", "GET").use { GaitAnalyzed.parse(it.readJson()) }
    }

    /**
     * 기록과 영상 파일을 지운다. `DELETE /gait/records/{id}`.
     *
     * ⚠️ **부분 실패를 성공으로 감추지 않는다.** 저쪽이 원본만 못 지웠으면 500 에
     * 무엇이 남았는지를 담아 준다. 개인 데이터라 그걸 삼키면 안 된다.
     */
    suspend fun delete(recordId: String): Result<Unit> = call {
        open("/records/${encode(recordId)}", "DELETE").use { it.readJson() }
        Unit
    }

    /**
     * 두 기록을 나란히 본다. `POST /gait/compare`.
     *
     * 둘 다 `quality.status == "ok"` 여야 한다. 아니면 저쪽이 `unavailable` 과 사유를
     * 돌려준다 — 앱은 목록의 `comparable` 로 미리 걸러 그 상황을 잘 안 만든다.
     */
    suspend fun compare(recordIdA: String, recordIdB: String): Result<GaitCompared> = call {
        val body = JSONObject().put("record_id_a", recordIdA).put("record_id_b", recordIdB)
        open("/compare", "POST").use {
            it.writeJson(body)
            GaitCompared.parse(it.readJson())
        }
    }

    /**
     * 스켈레톤 영상 주소.
     *
     * 응답의 `overlay_url` 은 **앱 기준 절대 경로**(`/gait/records/…/overlay`)라
     * 호스트를 앞에 붙여야 한다. 그런데 우리가 든 것은 `/gait` 까지 포함된 주소라
     * 거기에 그대로 이으면 `/gait/gait/…` 가 된다. **그래서 응답 값을 쓰지 않고
     * 여기서 짓는다** — 규칙이 하나뿐이라 이쪽이 덜 깨진다.
     */
    fun overlayUrl(recordId: String): String =
        BuildConfig.GAIT_BASE_URL.trimEnd('/') + "/records/" + encode(recordId) + "/overlay"

    // -- 배관 ---------------------------------------------------------------

    private suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) {
                "보행 서버 주소가 없습니다. local.properties 의 daengs.gaitUrl 을 채우세요."
            }
            block()
        }.recoverCatching { cause ->
            // 서버가 준 문장은 그대로 통과시킨다. 나머지(연결 실패)는 영어 한 줄이라
            // 말풍선에 그대로 띄우면 안 된다 — 스크리닝과 같은 판단이다.
            if (cause is IllegalStateException) throw cause
            throw IllegalStateException("보행 서버에 닿지 못했어요.\n${BuildConfig.GAIT_BASE_URL}", cause)
        }
    }

    private fun open(path: String, method: String, readTimeout: Int = SHORT_READ_TIMEOUT_MS) =
        (URL(BuildConfig.GAIT_BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection)
            .apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                this.readTimeout = readTimeout
                setRequestProperty("Accept", "application/json")
                authHeaders().forEach { (k, v) -> setRequestProperty(k, v) }
            }

    private fun HttpURLConnection.writeJson(body: JSONObject) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        outputStream.use { it.write(body.toString().toByteArray()) }
    }

    /**
     * `multipart/form-data` 본문을 직접 쓴다.
     *
     * **영상을 메모리에 통째로 올리지 않는다.** 한도가 150MB 라 `ByteArray` 로 읽으면
     * 그 자리에서 OOM 이다. `setChunkedStreamingMode` 로 흘려보내고 원본은
     * `ContentResolver` 스트림에서 바로 복사한다.
     */
    private fun HttpURLConnection.writeVideoMultipart(
        context: Context,
        video: Uri,
        dogId: String,
        date: LocalDate?,
        note: String?,
    ) {
        doOutput = true
        setChunkedStreamingMode(0)
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
        DataOutputStream(outputStream).use { out ->
            fun field(name: String, value: String) {
                out.writeBytes("--$BOUNDARY\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.write(value.toByteArray())
                out.writeBytes("\r\n")
            }
            field("dog_id", dogId)
            date?.let { field("date", it.toString()) }
            note?.let { field("note", it) }

            out.writeBytes("--$BOUNDARY\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"video\"; filename=\"gait.mp4\"\r\n")
            out.writeBytes("Content-Type: video/mp4\r\n\r\n")
            val stream: InputStream = context.contentResolver.openInputStream(video)
                ?: error("영상을 열 수 없습니다.")
            stream.use { it.copyTo(out) }
            out.writeBytes("\r\n--$BOUNDARY--\r\n")
        }
    }

    /**
     * 본문을 읽는다. 오류면 **저쪽 `detail` 을 꺼내 던진다.**
     *
     * 저쪽이 사용자에게 보여 줄 말로 써 놨다("영상을 읽을 수 없습니다" 같은).
     * 삭제 실패는 `detail` 이 객체라 그 안의 `message` 를 쓴다.
     */
    private fun HttpURLConnection.readJson(): JSONObject {
        val ok = responseCode in 200..299
        val text = (if (ok) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }
        if (!ok) error(detailOf(text) ?: "보행 서버가 ${responseCode} 를 돌려줬어요.")
        return JSONObject(text ?: "{}")
    }

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

    private const val BOUNDARY = "----daengs-gait-boundary-0f3a9c1e"
    private const val CONNECT_TIMEOUT_MS = 15_000

    /** 목록·삭제·비교는 금방 온다. */
    private const val SHORT_READ_TIMEOUT_MS = 30_000

    /** 분석만 길다. nginx 가 열어 둔 600s 에 맞춘다. */
    private const val READ_TIMEOUT_MS = 600_000
}

/**
 * 서버가 돌려준 기록 한 건.
 *
 * **점수·등급을 담는 필드가 없다.** 저쪽 응답에는 `internal_feature_vector` 와
 * `_dev_only_*` 가 있는데, API.md 가 **화면 노출 금지**라고 못박아 뒀다 — 수백 개의
 * 숫자가 화면에 나오면 사용자는 그것을 건강 점수로 읽는다. 여기서 아예 안 받으면
 * 화면이 지어낼 수 없다 ([GaitRecord] 가 같은 방법을 쓴다).
 */
data class GaitAnalyzed(
    val recordId: String,
    val date: LocalDate?,
    val qualityOk: Boolean,
    /** `good` / `low`. `qualityOk` 가 false 면 null 이다. */
    val qualityTier: String?,
    /** 왜 못 쓰는지. 저쪽이 사용자에게 보여 줄 말로 써 준다. */
    val reason: String?,
    val recommendation: String?,
    val hasOverlay: Boolean,
) {
    companion object {
        fun parse(json: JSONObject): GaitAnalyzed {
            val quality = json.optJSONObject("quality")
            val ok = quality?.optString("status") == "ok"
            return GaitAnalyzed(
                recordId = json.getString("record_id"),
                date = json.optStringOrNull("date")?.let(LocalDate::parse),
                qualityOk = ok,
                qualityTier = quality?.optStringOrNull("quality_tier"),
                reason = quality?.optStringOrNull("reason"),
                recommendation = quality?.optStringOrNull("recommendation"),
                hasOverlay = json.optBoolean("has_overlay", false),
            )
        }
    }
}

/** 목록 한 장. 저쪽은 요약만 준다 — `trajectories` · `features` 는 단건에만 있다. */
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
    val date: LocalDate?,
    val comparable: Boolean,
    val hasOverlay: Boolean,
    /** 필터 버전. 서로 다른 버전끼리 비교하면 저쪽이 경고를 붙인다. */
    val filterVersion: String?,
) {
    companion object {
        fun parse(json: JSONObject): GaitSummary = GaitSummary(
            recordId = json.getString("record_id"),
            date = json.optStringOrNull("date")?.let(LocalDate::parse),
            comparable = json.optBoolean("comparable", false),
            hasOverlay = json.optBoolean("has_overlay", false),
            filterVersion = json.optStringOrNull("gait_filter_version"),
        )
    }
}

/**
 * 비교 결과.
 *
 * **문장을 앱이 짓지 않는다.** [messageForUi] 가 저쪽이 실제 계산에서 유도한 한 줄이고,
 * API.md 가 화면에 쓸 것으로 지목한 값이다. `_dev_only_*` 는 받지 않는다.
 */
data class GaitCompared(
    val available: Boolean,
    val messageForUi: String?,
    /** 관절 이름 → `"차이 관찰됨"` / `"비슷함"`. */
    val jointComparison: Map<String, String>,
    val reliabilityNote: String?,
    /** 두 기록의 필터 버전이 다를 때. **표시해야 한다** — 같은 영상도 달라 보인다. */
    val versionWarning: String?,
) {
    companion object {
        fun parse(json: JSONObject): GaitCompared {
            val joints = json.optJSONObject("joint_movement_range_comparison")
            return GaitCompared(
                available = json.optString("status", "ok") != "unavailable",
                messageForUi = json.optStringOrNull("message_for_ui"),
                jointComparison = joints?.keys()?.asSequence()
                    ?.associateWith { joints.optString(it) } ?: emptyMap(),
                reliabilityNote = json.optStringOrNull("reliability_note"),
                versionWarning = json.optStringOrNull("version_warning"),
            )
        }
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
 * 없다. 표지는 `has_overlay` 가 true 면 오버레이 영상에서 뽑을 수 있지만 목록을
 * 그리자고 영상 넷을 내려받을 이유가 없다 — 화면이 발바닥 자리표시로 물러선다.
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
 * 비교 응답을 표 세 줄로 옮긴다.
 *
 * 저쪽은 관절 이름별로 `"차이 관찰됨"` / `"비슷함"` 을 준다. **문자열을 그대로
 * 믿지 않고 아는 값만 옮긴다** — 모르는 문자열이 오면 [GaitDelta.Unknown] 이다.
 * "차이 관찰됨" 을 놓쳐서 "유사" 로 떨어지면 없는 안심을 주게 된다.
 *
 * 비교 자체가 불가(`status: unavailable`)면 표를 통째로 [GaitDelta.Unknown] 으로
 * 만든다. 그러면 [GaitComparison] 이 판정을 `NotEnough` 로 끌어낸다.
 */
fun GaitCompared.toMetrics(): List<GaitMetric> {
    if (!available) return emptyList()
    return jointComparison.map { (joint, verdict) ->
        GaitMetric(
            name = joint,
            delta = when (verdict) {
                "비슷함" -> GaitDelta.Similar
                "차이 관찰됨" -> GaitDelta.Slight
                else -> GaitDelta.Unknown
            },
        )
    }
}
