package com.daengs.app.chat

import java.net.HttpURLConnection
import java.net.URL

/**
 * [ChatApi] 가 실제로 바깥에 내보내는 한 번의 요청.
 *
 * `HttpURLConnection` 을 그대로 쓰면 단위 테스트가 **어느 경로에 어떤 헤더로 무엇을
 * 보냈는지** 볼 수 없다. `PetApi`·`WalkApi` 는 그래서 요청 본문을 만드는 순수 함수만
 * 테스트했는데, 대화 API 는 엔드포인트가 일곱이고 메서드·경로가 섞여 있어(요약 경로가
 * `/{session_id}` 보다 앞에 와야 하는 함정까지) 요청 한 벌을 통째로 잡아 두는 편이
 * 낫다. 그래서 전송만 이 인터페이스로 떼어 두고, 테스트는 가짜 전송을 꽂는다.
 *
 * **HTTP 라이브러리가 아니다.** 연결 열기·본문 쓰기·상태 코드 읽기 그 이상은 없다 —
 * `AuthApi` 가 "필요할 때 늘린다" 고 적어 둔 그 선을 넘지 않는다.
 */
internal data class HttpCall(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
)

/** 상태 코드와 본문. **204 는 본문이 비어 있다** — `null` 이 아니라 빈 문자열이다. */
internal data class HttpReply(val status: Int, val body: String)

internal fun interface HttpTransport {
    /** 연결이 안 되면 예외를 던진다. 상태 코드가 200 대가 아닌 것은 예외가 아니다. */
    fun exchange(call: HttpCall): HttpReply
}

/** 진짜 전송. `PetApi` 의 배관을 그대로 옮긴 것이라 동작이 다르지 않다. */
internal object UrlConnectionTransport : HttpTransport {
    override fun exchange(call: HttpCall): HttpReply {
        val conn = (URL(call.url).openConnection() as HttpURLConnection).apply {
            requestMethod = call.method
            connectTimeout = call.connectTimeoutMs
            readTimeout = call.readTimeoutMs
            call.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (call.body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(call.body.toByteArray()) }
            }
            // **응답 코드를 먼저 읽어야 요청이 실제로 나간다.** 204 는 본문이 없어서
            // 읽으려 들면 빈 문자열이므로, 파서에 넘기기 전에 코드로 가른다.
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = if (status == 204) "" else stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpReply(status, body)
        } finally {
            conn.disconnect()
        }
    }
}
