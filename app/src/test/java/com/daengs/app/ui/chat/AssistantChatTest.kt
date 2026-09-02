package com.daengs.app.ui.chat

import com.daengs.app.assistant.AssistantResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [bubbleMessage] · [knownHandoff] 가 보는 것은 서버가 준 `status`/`handoffs` 뿐임을
 * 고정한다 — 사용자 원문 텍스트를 보고 갈래를 나누는 로컬 라우팅(예전 `GAIT_ASK`)이
 * 되살아나면 안 된다는 것을, 이 테스트들이 텍스트를 아예 안 준다는 사실로 보인다.
 */
class AssistantChatTest {

    private fun response(
        status: AssistantResponse.Status,
        message: String = "",
        handoffs: List<AssistantResponse.Handoff> = emptyList(),
        clarify: AssistantResponse.Clarify? = null,
    ) = AssistantResponse(requestId = "r", status = status, message = message, handoffs = handoffs, clarify = clarify)

    @Test
    fun `CLARIFY는 질문을 우선한다`() {
        val r = response(
            AssistantResponse.Status.CLARIFY,
            message = "위치가 필요해요.",
            clarify = AssistantResponse.Clarify("어디서 산책하시나요?", listOf("location.lat")),
        )
        assertEquals("어디서 산책하시나요?", r.bubbleMessage())
    }

    @Test
    fun `CLARIFY인데 clarify가 없으면 message로 물러선다`() {
        val r = response(AssistantResponse.Status.CLARIFY, message = "무엇이 더 필요한지 알려주세요.")
        assertEquals("무엇이 더 필요한지 알려주세요.", r.bubbleMessage())
    }

    @Test
    fun `ANSWERED는 message를 그대로 쓴다`() {
        val r = response(AssistantResponse.Status.ANSWERED, message = "답변입니다.")
        assertEquals("답변입니다.", r.bubbleMessage())
    }

    @Test
    fun `gait HANDOFF는 GAIT로 매핑된다`() {
        val r = response(
            AssistantResponse.Status.HANDOFF,
            handoffs = listOf(AssistantResponse.Handoff("gait", "영상 분석이 필요합니다")),
        )
        assertEquals(KnownHandoff.GAIT, r.knownHandoff())
    }

    @Test
    fun `skin HANDOFF는 SKIN으로 매핑된다`() {
        val r = response(
            AssistantResponse.Status.HANDOFF,
            handoffs = listOf(AssistantResponse.Handoff("skin", "사진이 필요합니다")),
        )
        assertEquals(KnownHandoff.SKIN, r.knownHandoff())
    }

    @Test
    fun `모르는 target은 null이라 화면이 서버 메시지만 보여준다`() {
        val r = response(
            AssistantResponse.Status.HANDOFF,
            handoffs = listOf(AssistantResponse.Handoff("walk", "산책 적합도가 필요합니다")),
        )
        assertNull(r.knownHandoff())
    }

    @Test
    fun `handoffs가 비어 있으면 null이다`() {
        val r = response(AssistantResponse.Status.ANSWERED, message = "ok")
        assertNull(r.knownHandoff())
    }
}
