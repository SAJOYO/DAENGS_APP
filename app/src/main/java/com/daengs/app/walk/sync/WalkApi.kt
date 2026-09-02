package com.daengs.app.walk.sync

import com.daengs.app.BuildConfig
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWeather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * 산책 기록 API. 계약은 저쪽 `routers/walk.py` · `schemas/walk.py` 다.
 *
 * `PetApi` 와 같은 서버·같은 토큰이라 같은 방식으로 짰다 — `HttpURLConnection` +
 * `org.json`, 라이브러리 없이.
 *
 * ⚠️ **`/walk` 가 아니라 `/app/walks` 다.** 단수 `/walk` 는 산책 **적합도**(날씨 조언)라
 * 하는 일이 전혀 다르다. 저쪽 `main.py` 도 같은 이름 때문에 라우터가 가려질 뻔했다.
 *
 * 시각은 **epoch 밀리초 ↔ ISO-8601** 로 오간다. 로컬 DB 는 밀리초로 들고 있고 서버는
 * `TIMESTAMPTZ` 라, 경계에서 한 번만 바꾼다.
 */
object WalkApi {

    val configured: Boolean
        get() = BuildConfig.API_BASE_URL.isNotBlank()

    /**
     * 산책 한 건을 올린다.
     *
     * **같은 것을 다시 올려도 안전하다** — 서버가 `client_session_id` 로 알아보고
     * 있던 것을 돌려준다(201 대신 200). 앱은 둘 다 "올라갔다"로 본다.
     */
    suspend fun upload(
        accessToken: String,
        session: RecordedSession,
        fixes: List<RecordedFix>,
    ): Result<String> = call(accessToken, "", "POST", uploadBody(session, fixes)) {
        JSONObject(it).getString("id")
    }

    /**
     * 좌표를 이어 붙인다. **긴 산책을 나눠 올릴 때** 쓴다.
     *
     * 두 시간 산책이면 좌표가 5천 점 가까이 된다. 지금 nginx 한도가 20MB 라 한 번에
     * 보내도 되지만, 한도는 서버 설정이라 바뀔 수 있고 그때 **실패하는 쪽이 사용자의
     * 기록**이다. 큰 산책만 나눠 보낸다.
     */
    suspend fun appendPoints(
        accessToken: String,
        walkId: String,
        fixes: List<RecordedFix>,
    ): Result<Unit> = call(
        accessToken,
        "/$walkId/points",
        "POST",
        JSONObject().put("points", fixes.toJsonArray()),
    ) { }

    /**
     * 서버에 저장된 좌표열을 봉인하고 계산한다.
     *
     * 응답을 잃어 다시 불러도 서버는 같은 analysis를 200으로 돌려준다. 앱은 200과
     * 첫 완료의 201을 모두 성공으로 보고, 응답이 `derived`인지까지 확인한다.
     */
    suspend fun finalize(
        accessToken: String,
        walkId: String,
        manifest: WalkFinalizeManifest,
    ): Result<Unit> = call(
        accessToken,
        "/$walkId/finalize",
        "POST",
        JSONObject().apply {
            put("expected_point_count", manifest.expectedPointCount)
            put("terminal_client_seq", manifest.terminalClientSeq ?: JSONObject.NULL)
        },
    ) { text ->
        val response = JSONObject(text)
        check(response.getString("analysis_state") == "derived") {
            "서버가 산책 계산 완료를 확인하지 않았습니다."
        }
        check(response.getInt("point_count") == manifest.expectedPointCount) {
            "서버 계산의 좌표 수가 업로드 manifest와 다릅니다."
        }
    }

    /** 서버에 있는 내 산책 목록. **좌표는 안 온다** — 뭐가 있는지만 본다. */
    suspend fun list(accessToken: String): Result<List<RemoteWalk>> =
        call(accessToken, "", "GET") { text ->
            val array = JSONObject(text).getJSONArray("walks")
            (0 until array.length()).map { RemoteWalk.parse(array.getJSONObject(it)) }
        }

    /** 한 건과 좌표. 되찾을 때 쓴다. */
    suspend fun detail(accessToken: String, walkId: String): Result<RemoteWalkDetail> =
        call(accessToken, "/$walkId", "GET") { RemoteWalkDetail.parse(JSONObject(it)) }

    // -- 아래는 배관 -------------------------------------------------------

    private fun uploadBody(session: RecordedSession, fixes: List<RecordedFix>) =
        JSONObject().apply {
            // 기기의 세션 id 를 그대로 쓴다. 되찾을 때 같은 id 로 맞춰 보므로
            // 여기서 새 id 를 만들면 같은 산책이 두 벌이 된다.
            put("client_session_id", session.id)
            put("pet_ids", JSONArray(session.dogIds))
            put("started_at", session.startedAtMillis.toIso())
            put("ended_at", (session.endedAtMillis ?: session.startedAtMillis).toIso())
            putWeather(session.weather)
            put("points", fixes.toJsonArray())
        }

    private fun JSONObject.putWeather(weather: RecordedWeather?) {
        // 못 받은 날씨를 "맑음"으로 채우지 않는다 — null 그대로 보낸다.
        put("weather_code", weather?.weatherCode ?: JSONObject.NULL)
        put("is_day", weather?.isDay ?: JSONObject.NULL)
        put("temperature_c", weather?.temperatureC ?: JSONObject.NULL)
    }

    private fun List<RecordedFix>.toJsonArray(): JSONArray = JSONArray().also { array ->
        for (fix in this) {
            array.put(
                JSONObject().apply {
                    put("client_seq", fix.clientSeq)
                    put("chain_index", fix.chainIndex)
                    put("at", fix.atMillis.toIso())
                    put("lat", fix.lat)
                    put("lng", fix.lng)
                    put("accuracy_m", fix.accuracyM ?: JSONObject.NULL)
                    put("is_mock", fix.isMock)
                },
            )
        }
    }

    private suspend fun <T> call(
        accessToken: String,
        path: String,
        method: String,
        body: JSONObject? = null,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val conn = (URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/app/walks$path")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                // 좌표 수천 점이 오갈 수 있어 읽기는 넉넉히 준다.
                readTimeout = READ_TIMEOUT_MS
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
        }.recoverCatching { cause ->
            if (cause is IllegalStateException) throw cause
            throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
        }
    }

    private fun HttpURLConnection.fail(): Nothing {
        val detail = runCatching {
            JSONObject(errorStream?.bufferedReader()?.readText().orEmpty()).optString("detail")
        }.getOrNull()
        error(if (detail.isNullOrBlank()) "서버 오류 ($responseCode)" else detail)
    }

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    private const val TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
}

data class WalkFinalizeManifest(
    val expectedPointCount: Int,
    val terminalClientSeq: Int?,
) {
    init {
        require(expectedPointCount >= 0)
        require(terminalClientSeq == expectedPointCount.takeIf { it > 0 }?.minus(1))
    }
}

/** 밀리초 → ISO-8601(UTC). 서버가 `TIMESTAMPTZ` 라 시간대를 붙여 보낸다. */
internal fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

/** ISO-8601 → 밀리초. 서버가 준 것을 로컬 DB 모양으로 되돌린다. */
internal fun String.isoToMillis(): Long = Instant.parse(this).toEpochMilli()
