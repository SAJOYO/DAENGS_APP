package com.daengs.app.assistant

import com.daengs.app.BuildConfig
import com.daengs.app.location.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 자유 텍스트 챗봇 → 오케스트레이션. 계약은 저쪽 `SAJOYO/DAENGS_dev` 의
 * `routers/assistant.py` · `schemas/assistant.py` 다.
 *
 * [AuthApi][com.daengs.app.auth.AuthApi] · `WalkApi` 와 같은 이유로 HTTP
 * 라이브러리를 안 쓴다 — 부를 엔드포인트가 하나다.
 *
 * **오케스트레이션 v1 은 상태가 없다.** 대화 기록·이전 답변을 안 싣는다. 매 전송이
 * 독립된 질의고, 자연어 해석은 서버의 의미 라우터가 전부 맡는다 — 앱에서 키워드로
 * 먼저 갈래를 나누지 않는다.
 *
 * 다만 **대표 강아지 id 는 싣는다** ([query] 의 `activeDogId`). 프로필을 앱이 지어
 * 보내는 게 아니라 **id 만 주고 저쪽이 `pets` 에서 읽는 것**이라 무상태는 그대로다.
 */
object AssistantApi {

    val configured: Boolean
        get() = BuildConfig.API_BASE_URL.isNotBlank()

    /**
     * @param where 지금 있는 곳. **없어도 된다** — 위치가 필요 없는 질문이 대부분이고,
     *   좌표를 못 구했다고 질문까지 막으면 안 된다.
     * @param activeDogId 대표 강아지의 `pets.id`. **없어도 된다** — 아직 한 마리도
     *   등록하지 않았으면 없고, 그때는 견종·나이 없이 답이 온다. **기본값을 두지
     *   않는다** — 안 넘기면 조용히 예전 동작으로 돌아가서, 부르는 쪽이 매번 정하게 한다.
     */
    suspend fun query(
        accessToken: String,
        text: String,
        where: GeoPoint? = null,
        activeDogId: String?,
    ): Result<AssistantResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
                val conn = open()
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.use {
                    it.send(requestBody(text, where, activeDogId))
                    AssistantResponse.parse(it.readJson())
                }
            }.recoverCatching { cause ->
                // 서버가 준 문장은 그대로 통과시킨다 (아래 [readJson] 이
                // IllegalStateException 으로 던진다). 나머지는 연결이 안 된 것이다.
                if (cause is IllegalStateException) throw cause
                throw IllegalStateException("AI 서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
            }
        }

    /**
     * 보내는 것은 **질문과 (있으면) 좌표·대표 강아지 id** 다.
     *
     * `requested_capability` 는 넣지 않는다 — 자연어 해석은 서버 의미 라우터에게
     * 그대로 맡긴다.
     *
     * `active_dog_id` 는 저쪽이 **그 id 로 `pets` 를 읽어 견종·나이를 Life 프롬프트에
     * 얹는 데 쓴다** (`SAJOYO/DAENGS_dev#202`). 예전에는 서버가 받기만 하고 아무 기능도
     * 안 써서 일부러 뺐었다. 틀린 값이어도 **질문은 안 죽는다** — 남의 강아지거나 없는
     * id 면 저쪽이 조용히 무시하고 프로필 없이 답한다 (소유권이 쿼리 조건으로 묶여 있어
     * 남의 프로필은 못 읽는다).
     *
     * ⚠️ **서버 스키마가 `extra="forbid"` 다.** 모르는 칸이 하나라도 있으면 422 로
     * 질문이 통째로 죽는다. 그래서 좌표나 대표 강아지가 없을 때 `null` 을 넣지 않고
     * **칸 자체를 뺀다.**
     */
    internal fun requestBody(text: String, where: GeoPoint?, activeDogId: String?): String =
        JSONObject().put("query", text).apply {
            where?.takeIf { it.inKorea() }?.let {
                put("location", JSONObject().put("lat", it.latitude).put("lon", it.longitude))
            }
            activeDogId?.takeIf { it.isNotBlank() }?.let { put("active_dog_id", it) }
        }.toString()

    /**
     * 서버가 받아 주는 범위인가 (`lat` 33~39, `lon` 124~132).
     *
     * 밖이면 422 다. 위치는 곁들이는 정보고 질문이 본체이므로, 범위 밖이면
     * **좌표만 빼고 질문은 보낸다.** 비행기 안이나 해외에서도 훈련 질문은 돼야 한다.
     */
    private fun GeoPoint.inKorea(): Boolean =
        latitude in 33.0..39.0 && longitude in 124.0..132.0

    private fun open(): HttpURLConnection {
        val conn = URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/assistant/query")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        // 배포 스모크에서 Training 질의가 약 11.5초 걸려 성공했다. 10초로 두면
        // 실제로 성공한 요청을 앱이 실패로 본다.
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private fun HttpURLConnection.send(body: String) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        outputStream.use { it.write(body.toByteArray()) }
    }

    private fun HttpURLConnection.readJson(): JSONObject {
        if (responseCode !in 200..299) {
            // ⚠️ **오류 본문이 JSON 이 아닐 수 있다.** nginx 가 `proxy_read_timeout`
            //    (60초)에 걸리면 **HTML 504 페이지**를 준다. 그걸 JSONObject 에
            //    넣으면 파싱이 터지고, 사용자는 "AI 서버에 닿지 못했어요" 라는
            //    엉뚱한 말을 본다 — 실제로는 닿았고 서버가 오래 걸린 것이다.
            val detail = runCatching {
                JSONObject(errorStream?.bufferedReader()?.readText().orEmpty()).optString("detail")
            }.getOrNull()
            error(
                when {
                    !detail.isNullOrBlank() -> detail
                    responseCode in 502..504 -> SLOW
                    else -> "AI 서버 오류 ($responseCode)"
                },
            )
        }
        return JSONObject(inputStream.bufferedReader().use { it.readText() })
    }

    /**
     * 서버가 제 시간에 못 끝냈을 때.
     *
     * 게이트웨이(nginx)가 60초에 끊으면 502~504 가 온다. **닿지 못한 것이 아니라
     * 오래 걸린 것**이라 문구를 갈라 준다 — 사용자가 통신 문제로 오해하면 와이파이를
     * 만지러 간다.
     */
    private const val SLOW = "답을 만드는 데 오래 걸리고 있어요. 조금 뒤에 다시 물어봐 주세요."

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    private const val CONNECT_TIMEOUT_MS = 10_000
    /**
     * 읽기 제한.
     *
     * 서버 최악은 의미 라우터(최대 30초) + 스키마 실패 시 **재시도 1회** + 능력
     * 실행(임베딩·pgvector·생성)이라 60초를 넘길 수 있다. 60초로 두면 그때
     * 우리가 먼저 끊어서, 서버는 답을 만들었는데 앱만 실패로 보게 된다.
     *
     * 게이트웨이(nginx)가 60초에 끊으므로 대개는 저쪽이 먼저 502~504 를 준다.
     * 그 응답을 받아서 [SLOW] 로 말해 주려면 우리가 그보다 늦게 끊어야 한다.
     */
    private const val READ_TIMEOUT_MS = 90_000
}
