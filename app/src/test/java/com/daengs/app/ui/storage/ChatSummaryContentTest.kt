package com.daengs.app.ui.storage

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.chat.ChatCitation
import com.daengs.app.chat.ChatSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatSummaryContentTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * **카드는 접혀서 시작한다** — 제목을 눌러 펴야 내용이 나온다
     * ([ChatSummaryCardFoldTest]). 사용자가 그렇게 정했다: 카드 한 장에 다 들어 있어서
     * 펼친 채로 두면 한 장만으로도 저장소 탭을 넘긴다.
     */
    @Test
    fun `펼친 요약은 모든 내용과 출처를 표시한다`() {
        compose.setContent { ChatSummaryCard(summary(sourceSessionId = "session"), {}, {}, {}) }

        compose.onNodeWithText("요약 제목").performClick()

        listOf(
            "요약 제목",
            "질문을 요약했어요",
            "답을 요약했어요",
            "• 핵심 하나",
            "• 주의 하나",
            "• 법령 안내",
            "• 연결 출처",
            "원본 대화 보기",
        ).forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }
    }

    @Test
    fun `source session id 가 null 이어도 요약은 보이고 원본 동작만 숨긴다`() {
        compose.setContent { ChatSummaryCard(summary(sourceSessionId = null), {}, {}, {}) }

        compose.onNodeWithText("요약 제목").assertIsDisplayed()
        compose.onNodeWithText("요약 제목").performClick()
        compose.onNodeWithText("답을 요약했어요").assertIsDisplayed()
        compose.onAllNodesWithText("원본 대화 보기").assertCountEquals(0)
    }

    private fun summary(sourceSessionId: String?) = ChatSummary(
        id = "summary",
        petId = "pet",
        sourceSessionId = sourceSessionId,
        sourceTurnCount = 1,
        title = "요약 제목",
        questionSummary = "질문을 요약했어요",
        answerSummary = "답을 요약했어요",
        keyPoints = listOf("핵심 하나"),
        cautions = listOf("주의 하나"),
        sourceCitations = listOf(
            ChatCitation("법령 안내", null),
            ChatCitation("연결 출처", "https://example.org"),
        ),
        agentCategories = listOf("training"),
        model = "model",
        promptVersion = "v1",
        completedAtMs = 2,
        createdAtMs = 1,
    )
}
