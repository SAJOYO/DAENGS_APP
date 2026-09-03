package com.daengs.app.chat

import com.daengs.app.assistant.AssistantResponse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저쪽 계약(`SAJOYO/DAENGS_dev` PR #131 `schemas/chat.py`)을 우리가 제대로 읽는가.
 * [PetTest]·[AssistantResponseTest] 와 같은 이유로 존재한다 — 저쪽이 필드를 바꾸면
 * 여기가 먼저 깨져야 한다.
 */
class ChatModelsTest {

    // ── 능력 라벨 ─────────────────────────────────────────────────────────────

    @Test
    fun `training 은 훈련, life 는 제도, walk 는 산책이다`() {
        assertEquals("훈련", ChatCapability.label("training"))
        assertEquals("제도", ChatCapability.label("life"))
        assertEquals("산책", ChatCapability.label("walk"))
    }

    /** `walk` 를 `생활` 로 읽는 실수를 여기서 막는다. 산책 판정은 생활 비서와 별개 능력이다. */
    @Test
    fun `walk 는 생활이 아니다`() {
        assertTrue(ChatCapability.label("walk") != "생활")
    }

    /** 피부·보행은 Chat 능력이 아니라 HANDOFF 로 넘어가는 별도 흐름이다. 라벨을 지어내지 않는다. */
    @Test
    fun `모르는 값과 진단 이름은 라벨이 없다`() {
        assertNull(ChatCapability.label("skin"))
        assertNull(ChatCapability.label("gait"))
        assertNull(ChatCapability.label("something_new"))
        assertNull(ChatCapability.label(""))
    }

    // ── 대화 ───────────────────────────────────────────────────────────────────

    @Test
    fun `대화 하나를 통째로 읽는다`() {
        val s = ChatSession.parse(JSONObject(SESSION))
        assertEquals("6ba7b810-9dad-11d1-80b4-00c04fd430c8", s.id)
        assertEquals("3f2504e0-4f89-11d3-9a0c-0305e82c3301", s.petId)
        assertEquals("배변 훈련", s.title)
        assertEquals(listOf("training", "walk"), s.agentCategories)
        assertEquals(1_788_340_800_000L, s.createdAtMs) // 2026-09-02T09:20:00Z
        assertEquals(1_788_344_400_000L, s.lastMessageAtMs) // 2026-09-02T10:20:00Z
        assertTrue(!s.isDraft)
    }

    @Test
    fun `last_message_at 이 null 이면 초안이다`() {
        val s = ChatSession.parse(JSONObject(DRAFT))
        assertNull(s.lastMessageAtMs)
        assertTrue(s.isDraft)
    }

    /**
     * **서버 순서 그대로**다. 저쪽이 최근 갱신 순으로 준다 — 앱이 제목으로도, 능력별로도
     * 다시 줄 세우지 않는다. 능력별로 묶으면 같은 대화가 두 줄에 서거나 순서가 흐트러진다.
     */
    @Test
    fun `목록은 서버 순서를 지키고 능력별로 묶지 않는다`() {
        val list = ChatSessionList.parse(JSONObject(LIST))
        assertEquals(listOf("s-walk", "s-training", "s-walk-2", "s-life"), list.sessions.map { it.id })
        assertEquals(listOf("s-walk", "s-training", "s-walk-2", "s-life"), list.recent.map { it.id })
        assertEquals(5, list.maxSessions)
    }

    @Test
    fun `초안은 최근 대화에서 빠진다`() {
        val list = ChatSessionList.parse(JSONObject("""{"sessions": [$SESSION, $DRAFT], "max_sessions": 5}"""))
        assertEquals(2, list.sessions.size)
        assertEquals(1, list.recent.size)
        assertTrue(list.recent.none { it.isDraft })
    }

    /** 상한이 안 와도 목록은 떠야 한다 — `PetList.maxPets` 와 같은 판단. */
    @Test
    fun `max_sessions 가 없어도 목록은 읽는다`() {
        val list = ChatSessionList.parse(JSONObject("""{"sessions": [$SESSION]}"""))
        assertNull(list.maxSessions)
        assertEquals(1, list.sessions.size)
    }

    // ── 문답 ───────────────────────────────────────────────────────────────────

    @Test
    fun `완료된 turn 은 public_response 를 AssistantResponse 로 되살린다`() {
        val t = ChatTurn.parse(JSONObject(TURN_COMPLETED))
        assertEquals(ChatTurn.ProcessingStatus.COMPLETED, t.processingStatus)
        assertEquals("배변 훈련 어떻게 해요?", t.userContent)
        assertEquals("같은 자리에서 반복하세요.", t.assistantContent)
        assertEquals(listOf("training"), t.agentCategories)
        assertEquals("PARTIAL", t.assistantStatus)
        assertNull(t.errorCode)
        val replayed = t.publicResponse!!
        assertEquals(AssistantResponse.Status.PARTIAL, replayed.status)
        assertEquals("req-1", replayed.requestId)
        assertEquals("vet", replayed.handoffs.single().target)
        assertEquals(1, replayed.resultCount)
        assertEquals(1_788_344_400_000L, t.completedAtMs)
    }

    @Test
    fun `실패한 turn 은 public_response 가 없고 error_code 가 있다`() {
        val t = ChatTurn.parse(JSONObject(TURN_FAILED))
        assertEquals(ChatTurn.ProcessingStatus.FAILED, t.processingStatus)
        assertNull(t.publicResponse)
        assertNull(t.assistantContent)
        assertEquals("ASSISTANT_FAILED", t.errorCode)
    }

    @Test
    fun `처리 중인 turn 은 완료 시각이 없다`() {
        val t = ChatTurn.parse(JSONObject(TURN_PROCESSING))
        assertEquals(ChatTurn.ProcessingStatus.PROCESSING, t.processingStatus)
        assertNull(t.completedAtMs)
        assertNull(t.publicResponse)
    }

    @Test
    fun `모르는 processing_status 는 UNKNOWN 으로 떨어진다`() {
        val t = ChatTurn.parse(JSONObject(TURN_PROCESSING.replace("\"processing\"", "\"queued\"")))
        assertEquals(ChatTurn.ProcessingStatus.UNKNOWN, t.processingStatus)
    }

    @Test
    fun `대화 상세는 turn 을 온 순서대로 든다`() {
        val d = ChatSessionDetail.parse(
            JSONObject("""{"session": $SESSION, "turns": [$TURN_COMPLETED, $TURN_FAILED, $TURN_PROCESSING]}"""),
        )
        assertEquals("배변 훈련", d.session.title)
        assertEquals(
            listOf(
                ChatTurn.ProcessingStatus.COMPLETED,
                ChatTurn.ProcessingStatus.FAILED,
                ChatTurn.ProcessingStatus.PROCESSING,
            ),
            d.turns.map { it.processingStatus },
        )
    }

    // ── 요약 ───────────────────────────────────────────────────────────────────

    @Test
    fun `요약 하나를 통째로 읽는다`() {
        val s = ChatSummary.parse(JSONObject(SUMMARY))
        assertEquals("6ba7b811-9dad-11d1-80b4-00c04fd430c8", s.id)
        assertEquals("6ba7b810-9dad-11d1-80b4-00c04fd430c8", s.sourceSessionId)
        assertEquals(2, s.sourceTurnCount)
        assertEquals("배변 훈련", s.title)
        assertEquals("배변 훈련을 물었습니다.", s.questionSummary)
        assertEquals("같은 자리에서 반복하라고 답했습니다.", s.answerSummary)
        assertEquals(listOf("같은 자리", "반복"), s.keyPoints)
        assertEquals(listOf("증상이 이어지면 진료가 필요합니다"), s.cautions)
        assertEquals(listOf("training"), s.agentCategories)
        assertEquals("gemini-3.1-flash-lite", s.model)
        assertEquals("chat-summary-ko-v2", s.promptVersion)
        assertEquals(1_788_344_400_000L, s.completedAtMs)
    }

    /** 출처는 구조 그대로다 — `url` 이 없는 것은 null 이지 빈 문자열이 아니다. */
    @Test
    fun `출처는 label 과 nullable url 을 그대로 든다`() {
        val s = ChatSummary.parse(JSONObject(SUMMARY))
        assertEquals(
            listOf(
                ChatCitation("동물보호법 제8조", null),
                ChatCitation("농림축산식품부 안내", "https://example.org/guide"),
            ),
            s.sourceCitations,
        )
    }

    /** 원본 대화가 5개 유지에 밀려 사라진 요약. **요약은 남고 링크만 끊긴다.** */
    @Test
    fun `source_session_id 가 null 이면 원본이 없는 것이다`() {
        val s = ChatSummary.parse(JSONObject(SUMMARY.replace("\"6ba7b810-9dad-11d1-80b4-00c04fd430c8\"", "null")))
        assertNull(s.sourceSessionId)
        assertEquals("배변 훈련", s.title)
    }

    @Test
    fun `주의가 없으면 빈 목록이다 — 지어내지 않는다`() {
        val s = ChatSummary.parse(JSONObject(SUMMARY.replace("[\"증상이 이어지면 진료가 필요합니다\"]", "[]")))
        assertTrue(s.cautions.isEmpty())
    }

    @Test
    fun `보관함 목록은 서버 순서 그대로다`() {
        val a = SUMMARY.replace("6ba7b811", "aaaaaaaa")
        val b = SUMMARY.replace("6ba7b811", "bbbbbbbb")
        val list = ChatSummaryList.parse(JSONObject("""{"summaries": [$b, $a]}"""))
        assertEquals(listOf("bbbbbbbb-9dad-11d1-80b4-00c04fd430c8", "aaaaaaaa-9dad-11d1-80b4-00c04fd430c8"), list.summaries.map { it.id })
    }

    private companion object {
        const val SESSION = """
            {"id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
             "pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "title": "배변 훈련",
             "agent_categories": ["training", "walk"],
             "created_at": "2026-09-02T09:20:00+00:00",
             "last_message_at": "2026-09-02T10:20:00+00:00"}
        """
        const val DRAFT = """
            {"id": "draft-1", "pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "title": "새 대화", "agent_categories": [],
             "created_at": "2026-09-02T09:20:00Z", "last_message_at": null}
        """

        fun session(id: String, category: String, at: String) = """
            {"id": "$id", "pet_id": "p", "title": "$id", "agent_categories": ["$category"],
             "created_at": "2026-09-01T00:00:00Z", "last_message_at": "$at"}
        """

        // 최근 갱신 순으로 저쪽이 준 것. 능력이 섞여 있어도 순서는 이대로다.
        val LIST = """
            {"sessions": [
              ${session("s-walk", "walk", "2026-09-02T12:00:00Z")},
              ${session("s-training", "training", "2026-09-02T11:00:00Z")},
              ${session("s-walk-2", "walk", "2026-09-02T10:00:00Z")},
              ${session("s-life", "life", "2026-09-02T09:00:00Z")}
            ], "max_sessions": 5}
        """

        const val PUBLIC_RESPONSE = """
            {"request_id": "req-1", "status": "PARTIAL", "message": "같은 자리에서 반복하세요.",
             "results": [{"capability": "training", "status": "OK",
                          "data": {"answer": "같은 자리에서 반복하세요.", "citations": ["동물보호법 제8조"]},
                          "elapsed_ms": 12}],
             "handoffs": [{"target": "vet", "reason": "증상이 이어지면 진료가 필요합니다"}],
             "clarify": null}
        """
        const val TURN_COMPLETED = """
            {"id": "t-1", "client_message_id": "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
             "processing_status": "completed",
             "user_content": "배변 훈련 어떻게 해요?",
             "assistant_content": "같은 자리에서 반복하세요.",
             "agent_categories": ["training"], "assistant_status": "PARTIAL",
             "public_response": $PUBLIC_RESPONSE, "error_code": null,
             "completed_at": "2026-09-02T10:20:00Z", "created_at": "2026-09-02T10:19:50Z"}
        """
        const val TURN_FAILED = """
            {"id": "t-2", "client_message_id": "6ba7b813-9dad-11d1-80b4-00c04fd430c8",
             "processing_status": "failed", "user_content": "질문",
             "assistant_content": null, "agent_categories": [], "assistant_status": null,
             "public_response": null, "error_code": "ASSISTANT_FAILED",
             "completed_at": "2026-09-02T10:21:00Z", "created_at": "2026-09-02T10:20:50Z"}
        """
        const val TURN_PROCESSING = """
            {"id": "t-3", "client_message_id": "6ba7b814-9dad-11d1-80b4-00c04fd430c8",
             "processing_status": "processing", "user_content": "질문",
             "assistant_content": null, "agent_categories": [], "assistant_status": null,
             "public_response": null, "error_code": null,
             "completed_at": null, "created_at": "2026-09-02T10:22:00Z"}
        """
        const val SUMMARY = """
            {"id": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
             "pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "source_session_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
             "source_turn_count": 2,
             "title": "배변 훈련",
             "question_summary": "배변 훈련을 물었습니다.",
             "answer_summary": "같은 자리에서 반복하라고 답했습니다.",
             "key_points": ["같은 자리", "반복"],
             "cautions": ["증상이 이어지면 진료가 필요합니다"],
             "source_citations": [{"label": "동물보호법 제8조", "url": null},
                                  {"label": "농림축산식품부 안내", "url": "https://example.org/guide"}],
             "agent_categories": ["training"],
             "model": "gemini-3.1-flash-lite", "prompt_version": "chat-summary-ko-v2",
             "completed_at": "2026-09-02T10:20:00+00:00", "created_at": "2026-09-02T10:19:00+00:00"}
        """
    }
}
