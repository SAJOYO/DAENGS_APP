package com.daengs.app.ui.storage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.chat.ChatSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 대화 보관함 카드는 **접혀서 시작한다.**
 *
 * 카드 한 장에 물어본 내용·답변 요약·핵심·주의·출처·원본 대화가 다 들어 있어서, 펼친
 * 채로 두면 **한 장만으로도 화면을 넘는다.** 사용자가 짚었다 — *"대화보관함은 기본
 * 하나만 해도 너무 긴데, 제목과 삭제만 보이게 한 줄에 누르면 지금처럼 물어본 내용 요약
 * 핵심 주의 이런거 다 뜨게"*.
 *
 * 저장소 탭을 접는 일(요약 + 전체보기)과 **다른 층의 문제**다. 그쪽은 몇 장을 보여 줄지고
 * 이쪽은 한 장이 얼마나 긴지다. 둘 다 해야 탭이 짧아진다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatSummaryCardFoldTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `접힌 카드는 제목과 삭제만 보여 준다`() {
        rule.setContent { ChatSummaryCard(summary = SUMMARY) }

        rule.onNodeWithText("요약 제목").assertIsDisplayed()
        rule.onNodeWithText("삭제").assertIsDisplayed()
        rule.onNodeWithText("물어본 내용").assertDoesNotExist()
        rule.onNodeWithText("답변 요약").assertDoesNotExist()
    }

    @Test
    fun `제목을 누르면 펼쳐진다`() {
        rule.setContent { ChatSummaryCard(summary = SUMMARY) }

        rule.onNodeWithText("요약 제목").performClick()

        rule.onNodeWithText("물어본 내용").assertIsDisplayed()
        rule.onNodeWithText("답변 요약").assertIsDisplayed()
    }

    @Test
    fun `한 번 더 누르면 다시 접힌다`() {
        rule.setContent { ChatSummaryCard(summary = SUMMARY) }

        rule.onNodeWithText("요약 제목").performClick()
        rule.onNodeWithText("요약 제목").performClick()

        rule.onNodeWithText("물어본 내용").assertDoesNotExist()
    }

    /**
     * **챗에서 골라 들어온 요약은 펼친 채로 시작한다.** 보러 온 것을 한 번 더 누르게
     * 하지 않는다 — 원본 대화에서 "이 요약" 으로 찾아온 경우다.
     */
    @Test
    fun `고른 요약은 펼쳐진 채로 시작한다`() {
        rule.setContent { ChatSummaryCard(summary = SUMMARY, selected = true) }

        rule.onNodeWithText("물어본 내용").assertIsDisplayed()
    }

    private companion object {
        val SUMMARY = ChatSummary(
            id = "summary",
            petId = "pet",
            sourceSessionId = null,
            sourceTurnCount = 1,
            title = "요약 제목",
            questionSummary = "질문",
            answerSummary = "답",
            keyPoints = emptyList(),
            cautions = emptyList(),
            sourceCitations = emptyList(),
            agentCategories = listOf("training"),
            model = "model",
            promptVersion = "v1",
            completedAtMs = 2,
            createdAtMs = 1,
        )
    }
}
