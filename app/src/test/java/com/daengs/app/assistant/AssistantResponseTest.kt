package com.daengs.app.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저쪽 계약(`SAJOYO/DAENGS_dev` 의 `orchestration/contracts.py` `AssistantResponse`)을
 * 우리가 제대로 읽는가. [ScreeningReportTest] 와 같은 이유로 존재한다 — 저쪽이 필드를
 * 바꾸면 여기가 먼저 깨져야 한다.
 */
class AssistantResponseTest {

    @Test
    fun `ANSWERED 응답을 통째로 읽는다`() {
        val json = """
            {
              "request_id": "req-1",
              "status": "ANSWERED",
              "message": "훈련 답변입니다.",
              "results": [],
              "handoffs": [],
              "clarify": null
            }
        """.trimIndent()
        val r = AssistantResponse.parse(JSONObject(json))
        assertEquals("req-1", r.requestId)
        assertEquals(AssistantResponse.Status.ANSWERED, r.status)
        assertEquals("훈련 답변입니다.", r.message)
        assertTrue(r.handoffs.isEmpty())
        assertNull(r.clarify)
    }

    @Test
    fun `CLARIFY 는 question 과 missing 을 읽는다`() {
        val json = """
            {
              "request_id": "req-2",
              "status": "CLARIFY",
              "message": "위치가 필요해요.",
              "clarify": {
                "question": "어디서 산책하시나요?",
                "missing": ["location.lat", "location.lon"]
              }
            }
        """.trimIndent()
        val r = AssistantResponse.parse(JSONObject(json))
        assertEquals(AssistantResponse.Status.CLARIFY, r.status)
        assertEquals("어디서 산책하시나요?", r.clarify?.question)
        assertEquals(listOf("location.lat", "location.lon"), r.clarify?.missing)
    }

    @Test
    fun `HANDOFF gait 의 target과 reason을 읽는다`() {
        val json = """
            {
              "request_id": "req-3",
              "status": "HANDOFF",
              "message": "보행 분석으로 넘길게요.",
              "handoffs": [{"target": "gait", "reason": "영상 분석이 필요합니다"}]
            }
        """.trimIndent()
        val r = AssistantResponse.parse(JSONObject(json))
        assertEquals(1, r.handoffs.size)
        assertEquals("gait", r.handoffs[0].target)
        assertEquals("영상 분석이 필요합니다", r.handoffs[0].reason)
    }

    @Test
    fun `HANDOFF skin 의 target과 reason을 읽는다`() {
        val json = """
            {
              "request_id": "req-4",
              "status": "HANDOFF",
              "message": "사진 진단으로 넘길게요.",
              "handoffs": [{"target": "skin", "reason": "사진이 필요합니다"}]
            }
        """.trimIndent()
        val r = AssistantResponse.parse(JSONObject(json))
        assertEquals("skin", r.handoffs[0].target)
    }

    @Test
    fun `모르는 필드가 있어도 죽지 않는다`() {
        val json = """
            {
              "request_id": "req-5",
              "status": "ANSWERED",
              "message": "ok",
              "results": [{"capability": "training", "status": "OK", "data": {"x": 1}, "elapsed_ms": 10}],
              "meta": {"trace_id": "abc"}
            }
        """.trimIndent()
        val r = AssistantResponse.parse(JSONObject(json))
        assertEquals("ok", r.message)
    }

    @Test
    fun `HTTP 200 플러스 FAILED 는 예외가 아니라 파싱된 응답이다`() {
        val json = """
            {"request_id": "req-6", "status": "FAILED", "message": "죄송해요, 처리하지 못했어요."}
        """.trimIndent()
        val r = AssistantResponse.parse(JSONObject(json))
        assertEquals(AssistantResponse.Status.FAILED, r.status)
        assertEquals("죄송해요, 처리하지 못했어요.", r.message)
    }

    @Test
    fun `모르는 status 값은 UNKNOWN으로 떨어진다`() {
        val json = """{"request_id": "req-7", "status": "SOMETHING_NEW", "message": "?"}"""
        val r = AssistantResponse.parse(JSONObject(json))
        assertEquals(AssistantResponse.Status.UNKNOWN, r.status)
    }
}
