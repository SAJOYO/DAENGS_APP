package com.daengs.app.ui.storage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import com.daengs.app.care.CareDaySummary
import com.daengs.app.care.CareEvent
import com.daengs.app.care.CareGateway
import com.daengs.app.care.CareKind
import com.daengs.app.care.CareLogCoordinator
import com.daengs.app.chat.ChatHistoryState
import com.daengs.app.chat.ChatSummary
import com.daengs.app.chat.ChatSummaryCoordinator
import com.daengs.app.chat.ChatSummaryGateway
import com.daengs.app.chat.ChatSummaryList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime

/**
 * 저장소 탭은 **한 번에 스크롤되는 한 목록**이다 — 위에 오늘의 케어, 그 아래 대화 보관함,
 * 맨 밑에 사진·영상 안내. 스크롤 안에 스크롤을 두지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatSummaryRouteTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `케어 기록·대화 보관함·사진 안내가 한 목록에 순서대로 있다`() {
        val care = FakeCareGateway()
        val chat = FakeChatSummaryGateway()
        compose.setContent { route(care, chat) }
        compose.waitForIdle()

        compose.onNodeWithText("오늘의 케어 기록").assertIsDisplayed()
        compose.onNodeWithText("밥 1 · 약 0 · 간식 0 · 산책 2").assertIsDisplayed()
        compose.onNodeWithTag("storage-list").performScrollToNode(hasText("요약 제목"))
        compose.onNodeWithText("요약 제목").assertIsDisplayed()
        compose.onNodeWithTag("storage-list").performScrollToNode(hasText("사진과 영상은 준비 중이에요"))
        compose.onNodeWithText("사진과 영상은 준비 중이에요").assertIsDisplayed()
    }

    @Test
    fun `밥을 누르면 케어 서버로 기록이 간다`() {
        val care = FakeCareGateway()
        compose.setContent { route(care, FakeChatSummaryGateway()) }
        compose.waitForIdle()

        compose.onNodeWithText("밥").performClick()
        compose.waitForIdle()

        assertEquals(listOf(CareKind.MEAL), care.recorded)
    }

    @Test
    fun `강아지가 없으면 케어 버튼 대신 로그인 안내다`() {
        compose.setContent { route(FakeCareGateway(), FakeChatSummaryGateway(), petId = null) }
        compose.waitForIdle()

        compose.onNodeWithText("로그인하고 대표 강아지를 골라 주세요.").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun route(care: CareGateway, chat: ChatSummaryGateway, petId: String? = "pet") {
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        ChatSummaryRoute(
            petId = petId,
            historyState = ChatHistoryState(),
            coordinator = ChatSummaryCoordinator(scope, chat),
            careCoordinator = CareLogCoordinator(scope, care),
            accessTokenProvider = { "token" },
            onOpenSource = {},
            onOpenCitation = {},
        )
    }

    private class FakeCareGateway : CareGateway {
        val recorded = mutableListOf<CareKind>()
        override suspend fun today(accessToken: String, petId: String) = Result.success(
            CareDaySummary(petId, "2026-09-08", "Asia/Seoul", meal = 1, medication = 0, snack = 0, walk = 2, events = emptyList()),
        )

        override suspend fun record(
            accessToken: String,
            petId: String,
            kind: CareKind,
            occurredAt: OffsetDateTime,
            clientEventId: String,
        ): Result<CareEvent> {
            recorded += kind
            return Result.success(CareEvent("e", petId, kind, occurredAt.toInstant().toEpochMilli(), null, clientEventId))
        }

        override suspend fun delete(accessToken: String, eventId: String) = Result.success(Unit)
    }

    private class FakeChatSummaryGateway : ChatSummaryGateway {
        override suspend fun listSummaries(accessToken: String, petId: String) =
            Result.success(ChatSummaryList(listOf(SUMMARY)))

        override suspend fun createSummary(accessToken: String, sessionId: String, clientRequestId: String) =
            Result.success(SUMMARY)

        override suspend fun deleteSummary(accessToken: String, summaryId: String) = Result.success(Unit)
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
