package com.daengs.app.ui.storage

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.care.CareActor
import com.daengs.app.care.CareDaySummary
import com.daengs.app.care.CareEvent
import com.daengs.app.care.CareKind
import com.daengs.app.care.CareLogState
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CareLogSectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `오늘 요약은 건수 넷과 기록 줄을 보여 준다`() {
        compose.setContent {
            CareLogSection(state = CareLogState("pet", today = ChatLoadState.Ready(summary(listOf(event())))), zone = SEOUL)
        }

        compose.onNodeWithText("밥 2 · 약 1 · 간식 3 · 산책 1").assertIsDisplayed()
        compose.onNodeWithText("약 · 13:30").assertIsDisplayed()
        compose.onAllNodesWithText("삭제").assertCountEquals(1)
    }

    @Test
    fun `밥을 누르면 밥 기록을 요청한다`() {
        var recorded: CareKind? = null
        compose.setContent {
            CareLogSection(state = CareLogState("pet", today = ChatLoadState.Ready(summary())), onRecord = { recorded = it })
        }
        compose.onNodeWithText("밥").performClick()
        assertEquals(CareKind.MEAL, recorded)
    }

    @Test
    fun `보내는 중에는 다른 버튼도 안 눌린다`() {
        var recorded: CareKind? = null
        compose.setContent {
            CareLogSection(
                state = CareLogState("pet", today = ChatLoadState.Ready(summary()), recording = CareKind.MEAL),
                onRecord = { recorded = it },
            )
        }
        compose.onNodeWithText("간식").performClick()
        assertNull(recorded)
    }

    @Test
    fun `기록이 없으면 빈 화면 대신 한 줄을 둔다`() {
        compose.setContent { CareLogSection(state = CareLogState("pet", today = ChatLoadState.Ready(summary()))) }
        compose.onNodeWithText("아직 오늘 챙긴 기록이 없어요").assertIsDisplayed()
    }

    @Test
    fun `읽기 실패는 문장과 다시 시도다`() {
        var retried = false
        compose.setContent {
            CareLogSection(
                state = CareLogState("pet", today = ChatLoadState.Failed(ChatApiError(0, null, "서버에 닿지 못했어요."))),
                onRetryLoad = { retried = true },
            )
        }
        compose.onNodeWithText("서버에 닿지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `기록 실패는 문장과 다시 시도이고 다시 시도는 같은 종류를 다시 보낸다`() {
        var recorded: CareKind? = null
        compose.setContent {
            CareLogSection(
                state = CareLogState(
                    "pet",
                    today = ChatLoadState.Ready(summary()),
                    recordError = ChatApiError(0, null, "서버에 닿지 못했어요."),
                    lastRecordKind = CareKind.SNACK,
                ),
                onRecord = { recorded = it },
            )
        }
        compose.onNodeWithText("서버에 닿지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(CareKind.SNACK, recorded)
    }

    @Test
    fun `삭제를 누르면 확인을 묻고 확인해야 지운다`() {
        var deleted: CareEvent? = null
        compose.setContent {
            CareLogSection(
                state = CareLogState("pet", today = ChatLoadState.Ready(summary(listOf(event())))),
                onConfirmDelete = { deleted = it },
                zone = SEOUL,
            )
        }
        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithText("이 기록을 지울까요?").assertIsDisplayed()
        assertNull(deleted)
        compose.onNodeWithText("지우기").performClick()
        assertEquals("event-1", deleted?.id)
    }

    @Test
    fun `돌보미는 자신이 쓴 기록만 지울 수 있다`() {
        val mine = event().copy(actor = CareActor("me", "나"))
        val others = event().copy(id = "event-2", actor = CareActor("other", "키키"))
        // 작성자를 모르는 기록(옛 기록·탈퇴자)을 "모르니까 내 것" 으로 치면 안 된다.
        val nameless = event().copy(id = "event-3", actor = CareActor(null, null))
        assertEquals(true, canDeleteCareEvent(mine, currentUserId = "me", petIsOwner = false))
        assertEquals(false, canDeleteCareEvent(others, currentUserId = "me", petIsOwner = false))
        assertEquals(false, canDeleteCareEvent(nameless, currentUserId = "me", petIsOwner = false))
        assertEquals(true, canDeleteCareEvent(others, currentUserId = "me", petIsOwner = true))
        assertEquals(true, canDeleteCareEvent(nameless, currentUserId = "me", petIsOwner = true))

        compose.setContent {
            CareLogSection(
                state = CareLogState("pet", today = ChatLoadState.Ready(summary(listOf(mine, others)))),
                canDelete = { canDeleteCareEvent(it, "me", petIsOwner = false) },
                zone = SEOUL,
            )
        }
        compose.onNodeWithText("나").assertIsDisplayed()
        compose.onNodeWithText("키키").assertIsDisplayed()
        compose.onAllNodesWithText("삭제").assertCountEquals(1)
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        fun event() = CareEvent(
            id = "event-1",
            petId = "pet",
            kind = CareKind.MEDICATION,
            occurredAtMs = 1_756_701_000_000L, // 2025-09-01T13:30+09:00
            note = null,
            clientEventId = "client-1",
        )

        fun summary(events: List<CareEvent> = emptyList()) =
            CareDaySummary("pet", "2025-09-01", "Asia/Seoul", meal = 2, medication = 1, snack = 3, walk = 1, events = events)
    }
}
