package com.daengs.app.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 오케스트레이션 v1 은 상태가 없다 — 보내는 건 질문 한 줄뿐이어야 한다.
 * `requested_capability`·대화 기록 같은 걸 몰래 얹으면 서버 의미 라우터가
 * 앱의 판단을 신뢰하게 되므로, 요청 본문 모양을 여기서 고정해 둔다.
 */
class AssistantApiTest {

    @Test
    fun `일반 텍스트는 query 하나만 담는다`() {
        val body = JSONObject(AssistantApi.requestBody("강아지가 손을 물어요"))
        assertEquals("강아지가 손을 물어요", body.getString("query"))
        assertFalse(body.has("requested_capability"))
        assertFalse(body.has("source"))
        assertFalse(body.has("action"))
        assertFalse(body.has("active_dog_id"))
        assertFalse(body.has("location"))
        assertEquals(1, body.keys().asSequence().count())
    }
}
