package com.daengs.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저쪽 오류 봉투를 상태·코드·동봉 데이터로 읽는가. 봉투는 넷이다 — `detail` 문장,
 * `detail` 객체(`code` + 나머지), `detail` 배열(FastAPI 검증), 그리고 nginx 의 HTML.
 */
class ChatApiErrorTest {

    private val fallback: (Int) -> String = { "서버 오류 ($it)" }

    @Test
    fun `ACTIVE_DOG_MISMATCH 는 대화의 강아지 id 를 든다`() {
        val e = ChatApiError.from(409, """{"detail": {"code": "ACTIVE_DOG_MISMATCH", "session_pet_id": "pet-9"}}""", fallback)
        assertEquals(ChatApiError.ACTIVE_DOG_MISMATCH, e.code)
        assertEquals("pet-9", e.sessionPetId)
        assertFalse(e.stillProcessing)
        assertFalse(e.retryWithFreshClientMessageId)
    }

    @Test
    fun `CLIENT_MESSAGE_ID_REUSED 는 turn id 를 든다`() {
        val e = ChatApiError.from(409, """{"detail": {"code": "CLIENT_MESSAGE_ID_REUSED", "turn_id": "t-1"}}""", fallback)
        assertEquals("t-1", e.turnId)
        assertEquals("요청이 꼬였어요. 질문을 다시 보내 주세요.", e.message)
    }

    @Test
    fun `TURN_PROCESSING 은 기다리라는 뜻이다`() {
        val e = ChatApiError.from(409, """{"detail": {"code": "TURN_PROCESSING", "turn_id": "t-1"}}""", fallback)
        assertTrue(e.stillProcessing)
        assertFalse(e.retryWithFreshClientMessageId)
    }

    @Test
    fun `TURN_FAILED 는 새 UUID 이고 실패 이유 코드를 든다`() {
        val e = ChatApiError.from(
            409,
            """{"detail": {"code": "TURN_FAILED", "turn_id": "t-1", "error_code": "STALE_PROCESSING"}}""",
            fallback,
        )
        assertTrue(e.retryWithFreshClientMessageId)
        assertEquals("STALE_PROCESSING", e.turnErrorCode)
        assertEquals("t-1", e.turnId)
    }

    @Test
    fun `503 TURN_PERSISTENCE_FAILED 는 플래그와 내부 코드를 든다`() {
        val e = ChatApiError.from(
            503,
            """{"detail": {"code": "TURN_PERSISTENCE_FAILED", "turn_id": "t-1",
                "persistence_error_code": "ASSISTANT_CONTENT_TOO_LONG",
                "retry_with_fresh_client_message_id": true}}""",
            fallback,
        )
        assertEquals(503, e.status)
        assertTrue(e.retryWithFreshClientMessageId)
        assertEquals("ASSISTANT_CONTENT_TOO_LONG", e.persistenceErrorCode)
        assertFalse("코드가 있는 503 은 게이트웨이가 아니다", e.gatewayTimeout)
    }

    @Test
    fun `QUESTION_TOO_LONG 은 422 이고 상한을 문장에 넣는다`() {
        val e = ChatApiError.from(422, """{"detail": {"code": "QUESTION_TOO_LONG", "limit": 2000}}""", fallback)
        assertTrue(e.invalidRequest)
        assertEquals(2000, e.limit)
        assertEquals("질문이 너무 길어요. 2000자 안으로 줄여 주세요.", e.message)
    }

    @Test
    fun `TURN_LIMIT_EXCEEDED 는 상한을 든다`() {
        val e = ChatApiError.from(409, """{"detail": {"code": "TURN_LIMIT_EXCEEDED", "limit": 30}}""", fallback)
        assertEquals(30, e.limit)
    }

    @Test
    fun `403 CHAT_PERSISTENCE_APP_USER_ONLY`() {
        val e = ChatApiError.from(403, """{"detail": {"code": "CHAT_PERSISTENCE_APP_USER_ONLY"}}""", fallback)
        assertEquals(403, e.status)
        assertFalse(e.needsReauth)
        assertEquals("이 계정으로는 대화를 저장할 수 없어요.", e.message)
    }

    /** 저쪽이 코드를 하나 더 만드는 날 — 크래시도, 엉뚱한 문장도 아니어야 한다. */
    @Test
    fun `모르는 코드는 기본 문장으로 떨어지되 코드는 남는다`() {
        val e = ChatApiError.from(409, """{"detail": {"code": "SOMETHING_NEW", "x": 1}}""", fallback)
        assertEquals("SOMETHING_NEW", e.code)
        assertEquals("서버 오류 (409)", e.message)
        assertEquals("1", e.data["x"])
    }

    @Test
    fun `문장 detail 은 그대로 문장이 된다`() {
        val e = ChatApiError.from(401, """{"detail": "인증이 만료되었습니다."}""", fallback)
        assertEquals("인증이 만료되었습니다.", e.message)
        assertNull(e.code)
        assertTrue(e.needsReauth)
        assertTrue(e.data.isEmpty())
    }

    @Test
    fun `빈 문장 detail 은 기본 문장이다`() {
        assertEquals("서버 오류 (500)", ChatApiError.from(500, """{"detail": ""}""", fallback).message)
    }

    @Test
    fun `배열 detail 은 검증 오류다`() {
        val e = ChatApiError.from(422, """{"detail": [{"loc": ["body", "query"], "msg": "x", "type": "y"}]}""", fallback)
        assertTrue(e.invalidRequest)
        assertNull(e.code)
        assertTrue(e.data.isEmpty())
    }

    @Test
    fun `JSON 이 아니면 기본 문장이다`() {
        val e = ChatApiError.from(502, "<html>502 Bad Gateway</html>", fallback)
        assertEquals("서버 오류 (502)", e.message)
        assertTrue(e.gatewayTimeout)
        assertTrue(e.providerFailed)
        assertNull(e.code)
        val none = ChatApiError.from(500, null, fallback)
        assertEquals("서버 오류 (500)", none.message)
    }

    @Test
    fun `unreachable 은 상태 0 이고 원인을 든다`() {
        val cause = java.net.SocketTimeoutException("read timed out")
        val e = ChatApiError.unreachable("닿지 못했어요", cause)
        assertTrue(e.unreachable)
        assertEquals(0, e.status)
        assertEquals(cause, e.cause)
        assertFalse(e.gatewayTimeout)
    }
}
