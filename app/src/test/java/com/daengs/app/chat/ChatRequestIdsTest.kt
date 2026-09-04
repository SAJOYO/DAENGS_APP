package com.daengs.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * `client_message_id` · `client_request_id` 를 언제 같은 값으로, 언제 새 값으로 보내는가.
 *
 * 저쪽 멱등 표(`docs/chat-transaction-flow.md`)를 앱 쪽에서 그대로 따른다 — 같은 키 +
 * 같은 질문만 재시도이고, 다른 질문에 같은 키는 409, 실패로 끝난 키는 탄 것이다.
 */
class ChatRequestIdsTest {

    private var counter = 0
    private val ids = ChatRequestIds(newId = { "id-${++counter}" })

    private val s1 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    private val s2 = "6ba7b811-9dad-11d1-80b4-00c04fd430c8"

    // ── 질문 ─────────────────────────────────────────────────────────────────

    @Test
    fun `기본은 UUID v4 다`() {
        val real = ChatRequestIds()
        val id = real.clientMessageId(s1, "질문")
        assertEquals(4, UUID.fromString(id).version())
        assertEquals(4, UUID.fromString(real.clientRequestId(s1)).version())
    }

    /** 응답을 잃고 **글자 그대로 같은 질문**을 다시 보내면 같은 키다 — 저쪽이 저장된 답을 준다. */
    @Test
    fun `같은 대화에 같은 질문을 다시 보내면 같은 키다`() {
        val first = ids.clientMessageId(s1, "밤에 짖어요")
        assertEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
        assertEquals(1, counter)
    }

    /** 다른 질문에 같은 키를 쓰면 저쪽이 409 `CLIENT_MESSAGE_ID_REUSED` 다. 여기서 먼저 막는다. */
    @Test
    fun `질문이 한 글자라도 다르면 새 키다`() {
        val first = ids.clientMessageId(s1, "밤에 짖어요")
        val second = ids.clientMessageId(s1, "밤에 짖어요!")
        assertNotEquals(first, second)
        // 옛 키는 버려졌다 — 원래 질문으로 돌아가도 그 키를 다시 쓰지 않는다.
        assertNotEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
    }

    @Test
    fun `대화가 다르면 같은 질문이어도 새 키다`() {
        val first = ids.clientMessageId(s1, "밤에 짖어요")
        assertNotEquals(first, ids.clientMessageId(s2, "밤에 짖어요"))
    }

    /** 답이 오면 키를 버린다. 같은 질문을 또 하면 **새 turn** 이어야 한다. */
    @Test
    fun `답을 받은 뒤 같은 질문은 새 키다`() {
        val first = ids.clientMessageId(s1, "밤에 짖어요")
        ids.turnDelivered()
        assertNotEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
    }

    @Test
    fun `TURN_FAILED 뒤에는 같은 질문이어도 새 키다`() {
        val first = ids.clientMessageId(s1, "밤에 짖어요")
        val retrySame = ids.turnFailed(error(409, ChatApiError.TURN_FAILED, "turn_id" to "t", "error_code" to "ASSISTANT_FAILED"))
        assertFalse(retrySame)
        assertNotEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
    }

    @Test
    fun `503 의 retry_with_fresh_client_message_id 를 따른다`() {
        val first = ids.clientMessageId(s1, "밤에 짖어요")
        val e = error(
            503, ChatApiError.TURN_PERSISTENCE_FAILED,
            "turn_id" to "t", "persistence_error_code" to "COMPLETION_CONFLICT",
            "retry_with_fresh_client_message_id" to "true",
        )
        assertTrue(e.retryWithFreshClientMessageId)
        assertFalse(ids.turnFailed(e))
        assertNotEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
    }

    /** 아직 답하는 중이면 **기다렸다가 같은 키로** 다시다. 새 키면 같은 질문이 두 번 돈다. */
    @Test
    fun `TURN_PROCESSING 은 같은 키를 지킨다`() {
        val first = ids.clientMessageId(s1, "밤에 짖어요")
        assertTrue(ids.turnFailed(error(409, ChatApiError.TURN_PROCESSING, "turn_id" to "t")))
        assertEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
    }

    /** 응답을 잃었을 때 — 서버가 예약을 잡았을 수 있다. 같은 키가 그 예약을 찾아 준다. */
    @Test
    fun `연결 실패와 게이트웨이 끊김은 같은 키를 지킨다`() {
        val first = ids.clientMessageId(s1, "밤에 짖어요")
        assertTrue(ids.turnFailed(ChatApiError.unreachable("닿지 못함", null)))
        assertEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
        assertTrue(ids.turnFailed(ChatApiError(504, null, "오래 걸림")))
        assertEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
    }

    /** 다시 로그인한 뒤 같은 질문을 보내는 것은 같은 시도다. */
    @Test
    fun `401 은 같은 키를 지킨다`() {
        val first = ids.clientMessageId(s1, "밤에 짖어요")
        assertTrue(ids.turnFailed(ChatApiError(401, null, "다시 로그인해 주세요.")))
        assertEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
    }

    /** 그 대화로는 못 보내는 것들. 다음 시도는 다른 대화거나 다른 질문이라 새 키다. */
    @Test
    fun `ACTIVE_DOG_MISMATCH · CLIENT_MESSAGE_ID_REUSED · 404 · 422 는 새 키다`() {
        listOf(
            error(409, ChatApiError.ACTIVE_DOG_MISMATCH, "session_pet_id" to "p"),
            error(409, ChatApiError.CLIENT_MESSAGE_ID_REUSED, "turn_id" to "t"),
            ChatApiError(404, null, "대화를 찾을 수 없습니다."),
            error(422, ChatApiError.QUESTION_TOO_LONG, "limit" to "2000"),
        ).forEach { e ->
            val first = ids.clientMessageId(s1, "밤에 짖어요")
            assertFalse("$e 뒤에는 같은 키를 안 쓴다", ids.turnFailed(e))
            assertNotEquals(first, ids.clientMessageId(s1, "밤에 짖어요"))
        }
    }

    // ── 요약 ─────────────────────────────────────────────────────────────────

    @Test
    fun `같은 대화의 요약 요청은 결론이 날 때까지 같은 키다`() {
        val first = ids.clientRequestId(s1)
        assertEquals(first, ids.clientRequestId(s1))
        assertNotEquals(first, ids.clientRequestId(s2))
    }

    @Test
    fun `요약이 저장되면 다음 요청은 새 키다`() {
        val first = ids.clientRequestId(s1)
        ids.summaryDelivered(s1)
        assertNotEquals(first, ids.clientRequestId(s1))
    }

    @Test
    fun `SUMMARY_PROCESSING 은 같은 키로 기다린다`() {
        val first = ids.clientRequestId(s1)
        assertTrue(ids.summaryFailed(s1, error(409, ChatApiError.SUMMARY_PROCESSING, "summary_id" to "m")))
        assertEquals(first, ids.clientRequestId(s1))
    }

    /** 이미 요약이 있다 — 실패가 아니다. 키를 버리고 그 요약으로 간다. */
    @Test
    fun `SUMMARY_ALREADY_EXISTS 는 요약 id 를 주고 키를 버린다`() {
        val first = ids.clientRequestId(s1)
        val e = error(409, ChatApiError.SUMMARY_ALREADY_EXISTS, "summary_id" to "existing-1")
        assertEquals("existing-1", e.existingSummaryId)
        assertFalse(ids.summaryFailed(s1, e))
        assertNotEquals(first, ids.clientRequestId(s1))
    }

    @Test
    fun `SUMMARY_REQUEST_ALREADY_FAILED 와 503 플래그는 새 키다`() {
        listOf(
            error(409, ChatApiError.SUMMARY_REQUEST_ALREADY_FAILED),
            error(
                503, ChatApiError.SUMMARY_PERSISTENCE_FAILED,
                "summary_id" to "m", "persistence_error_code" to "COMPLETION_CONFLICT",
                "retry_with_fresh_client_request_id" to "true",
            ),
        ).forEach { e ->
            assertTrue(e.retryWithFreshClientRequestId)
            val first = ids.clientRequestId(s1)
            assertFalse(ids.summaryFailed(s1, e))
            assertNotEquals(first, ids.clientRequestId(s1))
        }
    }

    /** 공급자 502 는 그 예약을 실패로 닫는다. 같은 키는 다음에 `SUMMARY_REQUEST_ALREADY_FAILED` 다. */
    @Test
    fun `공급자 502 뒤에는 새 키다`() {
        val first = ids.clientRequestId(s1)
        assertFalse(ids.summaryFailed(s1, ChatApiError(502, null, "요약을 만들지 못했습니다.")))
        assertNotEquals(first, ids.clientRequestId(s1))
    }

    @Test
    fun `요약도 연결 실패·게이트웨이·401 은 같은 키다`() {
        val first = ids.clientRequestId(s1)
        assertTrue(ids.summaryFailed(s1, ChatApiError.unreachable("닿지 못함", null)))
        assertTrue(ids.summaryFailed(s1, ChatApiError(504, null, "오래 걸림")))
        assertTrue(ids.summaryFailed(s1, ChatApiError(401, null, "다시 로그인해 주세요.")))
        assertEquals(first, ids.clientRequestId(s1))
    }

    @Test
    fun `forget 은 전부 버린다`() {
        val m = ids.clientMessageId(s1, "질문")
        val r = ids.clientRequestId(s1)
        ids.forget()
        assertNotEquals(m, ids.clientMessageId(s1, "질문"))
        assertNotEquals(r, ids.clientRequestId(s1))
    }

    private fun error(status: Int, code: String, vararg data: Pair<String, String>) =
        ChatApiError(status, code, "msg", data.toMap())
}
