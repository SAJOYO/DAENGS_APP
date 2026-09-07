package com.daengs.app.chat

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/app/chats` 로 **무엇을 어디에 어떻게 보내는가**, 그리고 실패했을 때 무엇을 잃지
 * 않는가. 서버 없이 가짜 전송이 요청을 받아 적고 준비된 답을 돌려준다.
 *
 * 저쪽(`routers/chat.py`)이 못박아 둔 것 셋을 여기서 지킨다 — 요약 경로는
 * `/summaries` 접두사(세션 id 자리에 "summaries" 가 들어가면 422), 삭제는 204 라
 * 본문이 없다, 오류는 `detail` 이 문장이거나 `{"code": …}` 객체다.
 */
class ChatApiTest {

    private val transport = FakeTransport()
    private val api = ChatApi(baseUrl = { "http://server:8000/" }, transport = transport)

    private val token = "acc-token-secret-xyz"
    private val pet = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
    private val session = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    private val summary = "6ba7b811-9dad-11d1-80b4-00c04fd430c8"

    // ── 엔드포인트 ─────────────────────────────────────────────────────────────

    @Test
    fun `대화 만들기는 POST app chats 에 pet_id 를 담는다`() = runBlocking {
        transport.reply = HttpReply(201, SESSION_JSON)
        val got = api.createSession(token, pet).getOrThrow()

        val call = transport.only()
        assertEquals("POST", call.method)
        assertEquals("http://server:8000/app/chats", call.url)
        assertEquals("Bearer $token", call.headers["Authorization"])
        assertEquals("application/json", call.headers["Accept"])
        val body = JSONObject(call.body!!)
        assertEquals(pet, body.getString("pet_id"))
        assertFalse("제목이 없으면 칸을 뺀다 (extra=forbid 에 null 을 넣지 않는다)", body.has("title"))
        assertEquals(1, body.keys().asSequence().count())
        assertEquals("배변 훈련", got.title)
    }

    @Test
    fun `제목을 주면 다듬어서 싣는다`() = runBlocking {
        transport.reply = HttpReply(201, SESSION_JSON)
        api.createSession(token, pet, title = "  산책 문의 ").getOrThrow()
        assertEquals("산책 문의", JSONObject(transport.only().body!!).getString("title"))
    }

    @Test
    fun `최근 대화는 GET app chats 에 pet_id 쿼리다`() = runBlocking {
        transport.reply = HttpReply(200, """{"sessions": [], "max_sessions": 5}""")
        val got = api.listSessions(token, pet).getOrThrow()

        val call = transport.only()
        assertEquals("GET", call.method)
        assertEquals("http://server:8000/app/chats?pet_id=$pet", call.url)
        assertEquals("Bearer $token", call.headers["Authorization"])
        assertNull("GET 은 본문이 없다", call.body)
        assertEquals(5, got.maxSessions)
    }

    @Test
    fun `대화 하나는 GET app chats 세션 id 다`() = runBlocking {
        transport.reply = HttpReply(200, """{"session": $SESSION_JSON, "turns": []}""")
        val got = api.session(token, session).getOrThrow()
        val call = transport.only()
        assertEquals("GET", call.method)
        assertEquals("http://server:8000/app/chats/$session", call.url)
        assertEquals(session, got.session.id)
        assertTrue(got.turns.isEmpty())
    }

    @Test
    fun `대화 삭제는 DELETE 이고 204 의 빈 본문을 성공으로 본다`() = runBlocking {
        transport.reply = HttpReply(204, "")
        assertTrue(api.deleteSession(token, session).isSuccess)
        val call = transport.only()
        assertEquals("DELETE", call.method)
        assertEquals("http://server:8000/app/chats/$session", call.url)
        assertEquals("Bearer $token", call.headers["Authorization"])
        assertNull(call.body)
    }

    @Test
    fun `보관함은 GET app chats summaries 에 pet_id 쿼리다`() = runBlocking {
        transport.reply = HttpReply(200, """{"summaries": []}""")
        api.listSummaries(token, pet).getOrThrow()
        val call = transport.only()
        assertEquals("GET", call.method)
        assertEquals("http://server:8000/app/chats/summaries?pet_id=$pet", call.url)
        assertEquals("Bearer $token", call.headers["Authorization"])
    }

    @Test
    fun `요약 만들기는 POST 세션 summary 에 client_request_id 하나만 담고 오래 기다린다`() = runBlocking {
        transport.reply = HttpReply(201, SUMMARY_JSON)
        val got = api.createSummary(token, session, clientRequestId = summary).getOrThrow()

        val call = transport.only()
        assertEquals("POST", call.method)
        assertEquals("http://server:8000/app/chats/$session/summary", call.url)
        assertEquals("Bearer $token", call.headers["Authorization"])
        val body = JSONObject(call.body!!)
        assertEquals(summary, body.getString("client_request_id"))
        assertEquals("extra=forbid 라 다른 칸을 넣으면 422 다", 1, body.keys().asSequence().count())
        // Gemini 를 부르는 유일한 호출. 게이트웨이(60초)보다 늦게 끊어야 그 502 를 받아 말해 준다.
        assertTrue("요약 읽기 제한 ${call.readTimeoutMs} 은 60초를 넘어야 한다", call.readTimeoutMs > 60_000)
        assertEquals("배변 훈련 요약", got.title)
    }

    @Test
    fun `목록·상세·삭제는 짧게 기다린다`() = runBlocking {
        transport.reply = HttpReply(200, """{"sessions": [], "max_sessions": 5}""")
        api.listSessions(token, pet)
        assertTrue(transport.only().readTimeoutMs <= 30_000)
    }

    @Test
    fun `요약 삭제는 DELETE app chats summaries 요약 id 다`() = runBlocking {
        transport.reply = HttpReply(204, "")
        assertTrue(api.deleteSummary(token, summary).isSuccess)
        val call = transport.only()
        assertEquals("DELETE", call.method)
        assertEquals("http://server:8000/app/chats/summaries/$summary", call.url)
    }

    @Test
    fun `주소 끝의 슬래시는 한 번만 붙는다`() = runBlocking {
        val noSlash = ChatApi(baseUrl = { "http://server:8000" }, transport = transport)
        transport.reply = HttpReply(204, "")
        noSlash.deleteSession(token, session)
        assertEquals("http://server:8000/app/chats/$session", transport.only().url)
    }

    @Test
    fun `서버 주소가 없으면 아무것도 안 보낸다`() = runBlocking {
        val unset = ChatApi(baseUrl = { "" }, transport = transport)
        val result = unset.listSessions(token, pet)
        assertTrue(result.isFailure)
        assertTrue(transport.calls.isEmpty())
    }

    // ── 실패 ───────────────────────────────────────────────────────────────────

    @Test
    fun `401 은 다시 로그인이다`() = runBlocking {
        transport.reply = HttpReply(401, """{"detail": "다시 로그인해 주세요."}""")
        val e = api.listSessions(token, pet).error()
        assertEquals(401, e.status)
        assertTrue(e.needsReauth)
        assertNull(e.code)
        assertEquals("다시 로그인해 주세요.", e.message)
    }

    @Test
    fun `남의 대화도 없는 대화도 404 다`() = runBlocking {
        transport.reply = HttpReply(404, """{"detail": "대화를 찾을 수 없습니다."}""")
        val e = api.session(token, session).error()
        assertTrue(e.notFound)
        assertEquals("대화를 찾을 수 없습니다.", e.message)
    }

    @Test
    fun `SUMMARY_ALREADY_EXISTS 는 실패가 아니라 그 요약의 id 다`() = runBlocking {
        transport.reply = HttpReply(
            409,
            """{"detail": {"code": "SUMMARY_ALREADY_EXISTS", "summary_id": "$summary"}}""",
        )
        val e = api.createSummary(token, session, "6ba7b812-9dad-11d1-80b4-00c04fd430c8").error()
        assertEquals(409, e.status)
        assertEquals(ChatApiError.SUMMARY_ALREADY_EXISTS, e.code)
        assertEquals(summary, e.existingSummaryId)
        assertFalse(e.retryWithFreshClientRequestId)
    }

    @Test
    fun `SUMMARY_PROCESSING 은 기다리라는 뜻이다`() = runBlocking {
        transport.reply = HttpReply(409, """{"detail": {"code": "SUMMARY_PROCESSING", "summary_id": "$summary"}}""")
        val e = api.deleteSummary(token, summary).error()
        assertTrue(e.stillProcessing)
        assertEquals(summary, e.summaryId)
        assertNull("만드는 중인 것은 '이미 있는 요약' 이 아니다", e.existingSummaryId)
    }

    @Test
    fun `SUMMARY_REQUEST_ALREADY_FAILED 는 새 id 로 다시다`() = runBlocking {
        transport.reply = HttpReply(409, """{"detail": {"code": "SUMMARY_REQUEST_ALREADY_FAILED"}}""")
        val e = api.createSummary(token, session, summary).error()
        assertTrue(e.retryWithFreshClientRequestId)
        assertFalse(e.stillProcessing)
    }

    @Test
    fun `요약 공급자 502 는 문장이 오고 아무것도 저장되지 않은 것이다`() = runBlocking {
        transport.reply = HttpReply(502, """{"detail": "요약을 만들지 못했습니다. 잠시 후 다시 시도해 주세요."}""")
        val e = api.createSummary(token, session, summary).error()
        assertTrue(e.providerFailed)
        assertNull(e.code)
        assertEquals("요약을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.", e.message)
    }

    @Test
    fun `503 SUMMARY_PERSISTENCE_FAILED 는 새 id 플래그를 그대로 든다`() = runBlocking {
        transport.reply = HttpReply(
            503,
            """{"detail": {"code": "SUMMARY_PERSISTENCE_FAILED", "summary_id": "$summary",
                "persistence_error_code": "COMPLETION_CONFLICT",
                "retry_with_fresh_client_request_id": true}}""",
        )
        val e = api.createSummary(token, session, summary).error()
        assertEquals(503, e.status)
        assertEquals(ChatApiError.SUMMARY_PERSISTENCE_FAILED, e.code)
        assertTrue(e.retryWithFreshClientRequestId)
        assertEquals("COMPLETION_CONFLICT", e.persistenceErrorCode)
        assertEquals(summary, e.summaryId)
    }

    @Test
    fun `빈 대화 409 는 코드 없이 문장만 온다`() = runBlocking {
        transport.reply = HttpReply(409, """{"detail": "요약할 대화 내용이 없습니다."}""")
        val e = api.createSummary(token, session, summary).error()
        assertNull(e.code)
        assertEquals("요약할 대화 내용이 없습니다.", e.message)
    }

    @Test
    fun `FastAPI 검증 422 는 배열이라 앱 버그로 읽는다`() = runBlocking {
        transport.reply = HttpReply(
            422,
            """{"detail": [{"loc": ["query", "pet_id"], "msg": "Input should be a valid UUID", "type": "uuid_parsing"}]}""",
        )
        val e = api.listSessions(token, "not-a-uuid").error()
        assertTrue(e.invalidRequest)
        assertNull(e.code)
        assertFalse("검증 메시지(영어)를 사용자에게 보여 주지 않는다", e.message!!.contains("Input should"))
    }

    @Test
    fun `게이트웨이가 HTML 로 끊으면 오래 걸린다고 말한다`() = runBlocking {
        transport.reply = HttpReply(504, "<html><body>504 Gateway Time-out</body></html>")
        val e = api.createSummary(token, session, summary).error()
        assertTrue(e.gatewayTimeout)
        assertNull(e.code)
        assertFalse(e.message!!.contains("<html>"))
    }

    @Test
    fun `연결이 안 되면 상태 0 이고 영어 예외 문장을 안 보여 준다`() = runBlocking {
        transport.failure = java.net.UnknownHostException("server")
        val e = api.listSessions(token, pet).error()
        assertTrue(e.unreachable)
        assertEquals(0, e.status)
        assertEquals("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", e.message)
    }

    // ── 새면 안 되는 것 ───────────────────────────────────────────────────────

    /**
     * 실패 본문에 대화 원문이 실려 올 수 있고, 예외는 로그로 새기 쉽다. 저쪽 D-048 —
     * 관측 계층에 원문 금지. 그래서 [ChatApiError] 는 본문을 안 담고, 클라이언트는 토큰을
     * 예외에 안 싣는다. 여기서는 예외의 **모든 문자열 표현**을 뒤진다.
     */
    @Test
    fun `예외에 토큰도 대화 원문도 실리지 않는다`() = runBlocking {
        val transcript = "우리 강아지가 밤마다 짖어요 어떻게 하죠"
        transport.reply = HttpReply(
            409,
            """{"detail": {"code": "TURN_FAILED", "turn_id": "t-1", "error_code": "ASSISTANT_FAILED",
                "echo": "$transcript"}}""",
        )
        val e = api.createSummary(token, session, summary).error()
        val everything = listOf(e.message.orEmpty(), e.toString(), e.localizedMessage.orEmpty())
        for (text in everything) {
            assertFalse("토큰이 새면 안 된다: $text", text.contains(token))
            assertFalse("대화 원문이 새면 안 된다: $text", text.contains(transcript))
        }
        // 동봉 데이터는 값으로 남되 toString 에는 키만 나온다.
        assertEquals(transcript, e.data["echo"])
        assertFalse(e.toString().contains(transcript))
    }

    // ── 대역 ───────────────────────────────────────────────────────────────────

    private fun <T> Result<T>.error(): ChatApiError {
        val cause = exceptionOrNull() ?: error("실패여야 한다")
        return cause as? ChatApiError ?: error("ChatApiError 여야 한다: $cause")
    }

    private class FakeTransport : HttpTransport {
        val calls = mutableListOf<HttpCall>()
        var reply = HttpReply(200, "{}")
        var failure: Exception? = null

        override fun exchange(call: HttpCall): HttpReply {
            calls += call
            failure?.let { throw it }
            return reply
        }

        fun only(): HttpCall = calls.single()
    }

    private companion object {
        const val SESSION_JSON = """
            {"id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
             "pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "title": "배변 훈련", "agent_categories": ["training"],
             "created_at": "2026-09-03T01:00:00+00:00", "last_message_at": null}
        """
        const val SUMMARY_JSON = """
            {"id": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
             "pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "source_session_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
             "source_turn_count": 2, "title": "배변 훈련 요약",
             "question_summary": "q", "answer_summary": "a",
             "key_points": [], "cautions": [], "source_citations": [],
             "agent_categories": ["training"], "model": "m", "prompt_version": "v",
             "completed_at": "2026-09-03T01:00:00Z", "created_at": "2026-09-03T01:00:00Z"}
        """
    }
}
