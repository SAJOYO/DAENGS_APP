package com.daengs.app.ui.chat

import com.daengs.app.assistant.AssistantResponse
import com.daengs.app.assistant.WalkVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        walk: WalkVerdict? = null,
        resultCount: Int = 0,
    ) = AssistantResponse(
        requestId = "r",
        status = status,
        message = message,
        handoffs = handoffs,
        clarify = clarify,
        walk = walk,
        resultCount = resultCount,
    )

    private fun verdict(grade: WalkVerdict.Grade) = WalkVerdict(
        grade = grade,
        axes = emptyList(),
        capped = false,
        windows = emptyList(),
        timeline = emptyList(),
        locationLabel = "",
    )

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

    // ── 산책 카드 ─────────────────────────────────────────────────────────

    @Test
    fun `산책 판정이 있으면 카드가 뜬다`() {
        val r = response(
            AssistantResponse.Status.ANSWERED,
            message = "현재 산책 판단: GOOD",
            walk = verdict(WalkVerdict.Grade.GOOD),
            resultCount = 1,
        )
        assertEquals(WalkVerdict.Grade.GOOD, r.walkCard()?.grade)
    }

    @Test
    fun `산책 판정이 없으면 카드가 없다`() {
        val r = response(AssistantResponse.Status.ANSWERED, message = "훈련 답변")
        assertNull(r.walkCard())
    }

    @Test
    fun `등급을 모르면 카드 대신 서버 문장을 쓴다`() {
        // ABSTAINED. 판정이 아니라 못 하겠다는 말이라 등급 칸이 빈 카드가 된다.
        val r = response(
            AssistantResponse.Status.UNCERTAIN,
            message = "현재 관측 자료만으로 산책 조건을 판단할 수 없습니다.",
            walk = verdict(WalkVerdict.Grade.UNKNOWN),
            resultCount = 1,
        )
        assertNull(r.walkCard())
        assertNull(r.walkSentence())
        assertEquals("현재 관측 자료만으로 산책 조건을 판단할 수 없습니다.", r.bubbleMessage())
    }

    @Test
    fun `산책만 물으면 대화체 문장으로 갈음한다`() {
        val r = response(
            AssistantResponse.Status.ANSWERED,
            message = "현재 산책 판단: UNSAFE",
            walk = verdict(WalkVerdict.Grade.UNSAFE),
            resultCount = 1,
        )
        assertEquals("지금은 안 나가는 게 좋겠어요.", r.walkSentence())
    }

    @Test
    fun `능력이 둘이면 서버 문장을 그대로 쓴다`() {
        // 라벨을 붙여 이어붙은 문자열을 갈아 끼우면 훈련 답변까지 사라진다.
        val r = response(
            AssistantResponse.Status.ANSWERED,
            message = "[산책]\n현재 산책 판단: GOOD\n\n[훈련]\n앉아를 가르치려면…",
            walk = verdict(WalkVerdict.Grade.GOOD),
            resultCount = 2,
        )
        assertNull(r.walkSentence())
        assertNotNull(r.walkCard())
    }

    @Test
    fun `등급마다 문장이 다르다`() {
        assertEquals("지금은 산책하기 좋아요.", walkSentenceOf(WalkVerdict.Grade.GOOD))
        assertEquals("나가도 되지만 조심하는 게 좋아요.", walkSentenceOf(WalkVerdict.Grade.CAUTION))
        assertEquals("지금은 안 나가는 게 좋겠어요.", walkSentenceOf(WalkVerdict.Grade.UNSAFE))
    }
}
