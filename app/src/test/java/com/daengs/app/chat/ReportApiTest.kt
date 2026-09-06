package com.daengs.app.chat

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `POST /app/reports` 로 **무엇을 어디에 보내는가**, 그리고 실패의 갈래를 잃지 않는가.
 *
 * 저쪽(#235)이 못박아 둔 것 — 몸통은 `turn_id` 와 `reason` 둘뿐이고, 409 는 이미 이
 * 사람이 신고한 것, 404 는 없거나 남의 답변이다.
 */
class ReportApiTest {

    private val transport = FakeTransport()
    private val api = ReportApi(baseUrl = { "http://server:8000/" }, transport = transport)

    private val token = "acc-token-secret-xyz"
    private val turn = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"

    @Test
    fun `신고는 POST app reports 에 turn id 와 사유만 담는다`() = runBlocking {
        transport.reply = HttpReply(201, CREATED_JSON)
        api.report(token, turn, "사실과 달라요").getOrThrow()

        val call = transport.only()
        assertEquals("POST", call.method)
        assertEquals("http://server:8000/app/reports", call.url)
        assertEquals("Bearer $token", call.headers["Authorization"])
        assertEquals("application/json", call.headers["Accept"])

        val body = JSONObject(call.body!!)
        assertEquals(turn, body.getString("turn_id"))
        assertEquals("사실과 달라요", body.getString("reason"))
        // 답변 원문은 안 싣는다 — 저쪽이 turn_id 로 자기 DB 에서 읽는다.
        assertEquals(2, body.keys().asSequence().count())
    }

    @Test
    fun `이미 신고한 답변은 409 다`() = runBlocking {
        transport.reply = HttpReply(409, """{"detail": "이미 신고한 답변입니다."}""")
        val e = api.report(token, turn, "사유").error()
        assertEquals(409, e.status)
    }

    @Test
    fun `없는 답변도 남의 답변도 404 다`() = runBlocking {
        transport.reply = HttpReply(404, """{"detail": "답변을 찾을 수 없습니다."}""")
        val e = api.report(token, turn, "사유").error()
        assertTrue(e.notFound)
    }

    @Test
    fun `사유가 규격을 벗어나면 422 다`() = runBlocking {
        transport.reply = HttpReply(422, """{"detail": [{"loc": ["body", "reason"], "msg": "too long"}]}""")
        val e = api.report(token, turn, "ㄱ".repeat(501)).error()
        assertTrue(e.invalidRequest)
    }

    @Test
    fun `토큰이 죽었으면 다시 로그인이다`() = runBlocking {
        transport.reply = HttpReply(401, """{"detail": "다시 로그인해 주세요."}""")
        assertTrue(api.report(token, turn, "사유").error().needsReauth)
    }

    @Test
    fun `서버에 닿지 못하면 영어 예외가 아니라 우리말 한 문장이다`() = runBlocking {
        transport.failure = java.net.UnknownHostException("Unable to resolve host")
        val e = api.report(token, turn, "사유").error()
        assertTrue(e.unreachable)
        assertFalse(e.message!!.contains("Unable to resolve host"))
    }

    @Test
    fun `실패에 토큰도 신고 사유도 안 실린다`() = runBlocking {
        val reason = "이 답변은 위험한 조언이에요"
        transport.reply = HttpReply(500, """{"detail": {"code": "OOPS", "echo": "$reason"}}""")
        val e = api.report(token, turn, reason).error()

        // 예외는 로그로 새기 쉽다 (저쪽 D-048 — 관측 계층에 원문 금지).
        listOf(e.message.orEmpty(), e.toString()).forEach { text ->
            assertFalse("토큰이 새면 안 된다: $text", text.contains(token))
            assertFalse("신고 사유가 새면 안 된다: $text", text.contains(reason))
        }
    }

    @Test
    fun `서버 주소가 없으면 요청을 아예 안 보낸다`() = runBlocking {
        val unset = ReportApi(baseUrl = { "" }, transport = transport)
        assertTrue(unset.report(token, turn, "사유").isFailure)
        assertTrue(transport.calls.isEmpty())
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
        const val CREATED_JSON = """
            {"id": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
             "created_at": "2026-09-05T01:00:00+00:00"}
        """
    }
}
