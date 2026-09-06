package com.daengs.app.ui.chat

import com.daengs.app.assistant.AssistantResponse
import com.daengs.app.chat.ChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionRestoreTest {
    @Test
    fun `저장된 turn은 서버 순서대로 질문과 답을 복원한다`() {
        val restored = restoredChatEntries(
            listOf(
                turn("첫 질문", "첫 답"),
                turn("둘째 질문", "둘째 답"),
            ),
        )

        assertEquals(4, restored.size)
        assertEquals("첫 질문", (restored[0] as ChatEntry.Mine).text)
        assertEquals("첫 답", (restored[1] as ChatEntry.Theirs).text)
        assertEquals("둘째 질문", (restored[2] as ChatEntry.Mine).text)
        assertEquals("둘째 답", (restored[3] as ChatEntry.Theirs).text)
    }

    @Test
    fun `복원한 답변은 어느 turn 인지 들고 있다`() {
        val response = AssistantResponse(
            requestId = "request",
            status = AssistantResponse.Status.ANSWERED,
            message = "서버 공개 응답",
            handoffs = emptyList(),
            clarify = null,
        )
        val restored = restoredChatEntries(
            listOf(turn("첫 질문", "첫 답"), turn("둘째 질문", "낡은 답", response = response)),
        )

        // 신고가 이 id 를 요구한다. 답변 문자열만 남기면 어느 turn 인지 잃는다.
        assertEquals("turn-첫 질문", (restored[1] as ChatEntry.Theirs).turnId)
        assertEquals("turn-둘째 질문", (restored[3] as ChatEntry.Theirs).turnId)
    }

    @Test
    fun `완료되지 않은 turn은 내부 오류 코드를 노출하지 않는다`() {
        val restored = restoredChatEntries(
            listOf(
                turn(
                    question = "느린 질문",
                    answer = null,
                    status = ChatTurn.ProcessingStatus.FAILED,
                    errorCode = "PROVIDER_SECRET_FAILURE",
                ),
            ),
        )

        val failed = restored[1] as ChatEntry.Failed
        assertTrue("PROVIDER_SECRET_FAILURE" !in failed.message)
    }

    @Test
    fun `저장된 public response가 있으면 화면용 응답을 사용한다`() {
        val response = AssistantResponse(
            requestId = "request",
            status = AssistantResponse.Status.ANSWERED,
            message = "서버 공개 응답",
            handoffs = emptyList(),
            clarify = null,
        )
        val restored = restoredChatEntries(listOf(turn("질문", "낡은 답", response = response)))

        assertEquals("서버 공개 응답", (restored[1] as ChatEntry.Theirs).text)
    }

    private fun turn(
        question: String,
        answer: String?,
        status: ChatTurn.ProcessingStatus = ChatTurn.ProcessingStatus.COMPLETED,
        errorCode: String? = null,
        response: AssistantResponse? = null,
    ) = ChatTurn(
        id = "turn-$question",
        clientMessageId = "message-$question",
        processingStatus = status,
        userContent = question,
        assistantContent = answer,
        agentCategories = emptyList(),
        assistantStatus = response?.status?.name,
        publicResponse = response,
        errorCode = errorCode,
        completedAtMs = if (status == ChatTurn.ProcessingStatus.COMPLETED) 2 else null,
        createdAtMs = 1,
    )
}
