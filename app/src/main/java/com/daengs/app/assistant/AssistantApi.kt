package com.daengs.app.assistant

import com.daengs.app.BuildConfig
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
 * **오케스트레이션 v1 은 상태가 없다.** 대화 기록·강아지 프로필·이전 답변을
 * 안 싣는다. 매 전송이 독립된 질의고, 자연어 해석은 서버의 의미 라우터가
 * 전부 맡는다 — 앱에서 키워드로 먼저 갈래를 나누지 않는다.
 */
object AssistantApi {

    val configured: Boolean
        get() = BuildConfig.API_BASE_URL.isNotBlank()

    suspend fun query(accessToken: String, text: String): Result<AssistantResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
                val conn = open()
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.use {
                    it.send(requestBody(text))
                    AssistantResponse.parse(it.readJson())
                }
            }.recoverCatching { cause ->
                // 서버가 준 문장은 그대로 통과시킨다 (아래 [readJson] 이
                // IllegalStateException 으로 던진다). 나머지는 연결이 안 된 것이다.
                if (cause is IllegalStateException) throw cause
                throw IllegalStateException("AI 서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
            }
        }

    /** 지금 보내는 건 질문 한 줄뿐이다. `requested_capability` 등은 넣지 않는다 —
     * 이 카드의 목적이 서버 의미 라우터에게 자연어 해석을 그대로 맡기는 것이다. */
    internal fun requestBody(text: String): String = JSONObject().put("query", text).toString()

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
            val detail = runCatching {
                JSONObject(errorStream?.bufferedReader()?.readText().orEmpty()).optString("detail")
            }.getOrNull()
            error(if (detail.isNullOrBlank()) "AI 서버 오류 ($responseCode)" else detail)
        }
        return JSONObject(inputStream.bufferedReader().use { it.readText() })
    }

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000
}
