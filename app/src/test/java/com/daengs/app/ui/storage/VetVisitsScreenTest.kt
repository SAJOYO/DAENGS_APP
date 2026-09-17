package com.daengs.app.ui.storage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.care.VetRange
import com.daengs.app.care.VetVisit
import com.daengs.app.care.VetVisitPage
import com.daengs.app.care.VetVisitState
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 진료비 전체보기 화면 (PR #416).
 *
 * 여기서 잡는 것은 **안내가 조건부라는 것** 하나가 제일 크다. `older_count` 가 0 이면
 * 안내 자리가 통째로 없어야 한다 — 늘 띄우면 기록이 1년 안에만 있는 대부분의 화면에서
 * 소음이고, **소음이 되면 정작 감춰진 게 있을 때도 안 읽힌다.**
 *
 * 목록·삭제·전화는 저장소 칸에서 이리로 옮겨온 것이라 그 테스트도 같이 왔다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VetVisitsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 16)

    // -- 안내 (이 화면의 핵심) -------------------------------------------

    @Test
    fun `창 밖에 기록이 있으면 언제부터 보이는지 말해 준다`() {
        compose.setContent {
            VetVisitsScreen(
                state = ready(listOf(visit("v1")), olderCount = 3, start = LocalDate.of(2025, 9, 15)),
                today = today,
            )
        }

        compose.onNodeWithText("2025-09-15 이후만 보입니다").assertExists()
        compose.onNodeWithText("이전 기록 3건 보기").assertExists()
    }

    @Test
    fun `창 밖에 아무것도 없으면 안내를 아예 안 그린다`() {
        compose.setContent {
            VetVisitsScreen(
                state = ready(listOf(visit("v1")), olderCount = 0, start = LocalDate.of(2025, 9, 15)),
                today = today,
            )
        }

        compose.onNodeWithText("2025-09-15 이후만 보입니다").assertDoesNotExist()
    }

    /**
     * 창이 안 온 응답에서도 **목록은 보여야 한다.** 안내만 못 그릴 뿐이다
     * ([VetVisitPage.start] 가 `null` 을 허용하는 이유).
     */
    @Test
    fun `창을 모르면 안내 없이 목록만 그린다`() {
        compose.setContent {
            VetVisitsScreen(state = ready(listOf(visit("v1")), olderCount = 3, start = null), today = today)
        }

        compose.onNodeWithText("9월 10일 · 피부 · 61,700원").assertExists()
        compose.onNodeWithText("이전 기록 3건 보기").assertDoesNotExist()
    }

    // -- 기간 -----------------------------------------------------------

    @Test
    fun `프리셋은 최근 1년·작년·재작년·전체다`() {
        compose.setContent { VetVisitsScreen(state = ready(listOf(visit("v1"))), today = today) }

        compose.onNodeWithText("최근 1년").assertExists()
        compose.onNodeWithText("2025년").assertExists()
        compose.onNodeWithText("2024년").assertExists()
        compose.onNodeWithText("전체").assertExists()
    }

    @Test
    fun `프리셋을 누르면 그 기간을 알려 준다`() {
        var picked: VetRange? = null
        compose.setContent {
            VetVisitsScreen(
                state = ready(listOf(visit("v1"))),
                today = today,
                onSelectRange = { picked = it },
            )
        }

        compose.onNodeWithText("2024년").performClick()

        assertEquals(VetRange.Year(2024), picked)
    }

    @Test
    fun `전체를 누르면 전체 기간을 알려 준다`() {
        var picked: VetRange? = null
        compose.setContent {
            VetVisitsScreen(
                state = ready(listOf(visit("v1"))),
                today = today,
                onSelectRange = { picked = it },
            )
        }

        compose.onNodeWithText("전체").performClick()

        assertEquals(VetRange.All, picked)
    }

    /** 달력은 **프리셋 뒤에 숨는다.** 눌러야 나온다. */
    @Test
    fun `직접 고르기를 누르기 전에는 달력이 없다`() {
        compose.setContent { VetVisitsScreen(state = ready(listOf(visit("v1"))), today = today) }

        compose.onNodeWithText("기간 고르기").assertDoesNotExist()
    }

    @Test
    fun `직접 고르기를 누르면 기간 시트가 열린다`() {
        compose.setContent { VetVisitsScreen(state = ready(listOf(visit("v1"))), today = today) }

        compose.onNodeWithText("직접 고르기").performClick()

        compose.onNodeWithText("기간 고르기").assertExists()
        // **보이기까지 해야 한다.** 시트 안이 넘쳐 잘리면 탭이 뒤의 어둠에 떨어져
        // 시트가 닫히기만 한다 — 눌러도 아무 일이 없는 것처럼 보이는 실패다.
        compose.onNodeWithText("이 기간으로 보기").assertIsDisplayed()
    }

    /**
     * 시트를 열고 아무것도 안 돌리고 확인하면 **지금 보고 있는 창이 그대로** 나간다.
     * 휠을 손대지 않았는데 기간이 바뀌면 조용히 틀린 것이다.
     */
    @Test
    fun `시트에서 확인하면 보고 있던 창이 그대로 나간다`() {
        var picked: VetRange? = null
        compose.setContent {
            VetVisitsScreen(
                state = ready(listOf(visit("v1")), range = VetRange.RecentYear),
                today = today,
                onSelectRange = { picked = it },
            )
        }

        compose.onNodeWithText("직접 고르기").performClick()
        compose.onNodeWithText("이 기간으로 보기").performClick()

        assertEquals(VetRange.Custom(LocalDate.of(2025, 9, 16), today), picked)
    }

    /**
     * **「전체」에서 시트를 열면 휠이 표현할 수 없는 날에서 시작한다.** 전체의 창은
     * `0001-01-01` 인데 휠은 스무 해까지만 연다 — 그대로 넣으면 휠이 제 목록에 없는 값을
     * 걸고 있게 되고, 유저가 아무것도 안 돌리고 확인하면 **화면에 보이던 것과 다른 날짜**가
     * 나간다. 휠이 실제로 보여 줄 수 있는 가장 오래된 날로 당겨서 연다.
     */
    @Test
    fun `전체에서 시트를 열면 휠이 보여 줄 수 있는 가장 오래된 날에서 시작한다`() {
        var picked: VetRange? = null
        compose.setContent {
            VetVisitsScreen(
                state = ready(listOf(visit("v1")), range = VetRange.All),
                today = today,
                onSelectRange = { picked = it },
            )
        }

        compose.onNodeWithText("직접 고르기").performClick()
        compose.onNodeWithText("이 기간으로 보기").performClick()

        assertEquals(VetRange.Custom(LocalDate.of(2006, 1, 1), today), picked)
    }

    // -- 목록 (저장소 칸에서 옮겨온 것) -----------------------------------

    @Test
    fun `사유 표시명은 서버가 준 지도에서 온다`() {
        compose.setContent { VetVisitsScreen(state = ready(listOf(visit("v1"))), today = today) }

        compose.onNodeWithText("9월 10일 · 피부 · 61,700원").assertExists()
        compose.onNodeWithText("압구정동물병원").assertExists()
    }

    @Test
    fun `모르는 코드는 코드를 그대로 보여 준다 — 크래시가 아니다`() {
        compose.setContent {
            VetVisitsScreen(state = ready(listOf(visit("v1", reason = "dermatology"))), today = today)
        }

        compose.onNodeWithText("9월 10일 · dermatology · 61,700원").assertExists()
    }

    @Test
    fun `그 기간에 기록이 없으면 그렇게 말한다`() {
        compose.setContent { VetVisitsScreen(state = ready(emptyList()), today = today) }

        compose.onNodeWithText("이 기간에는 기록이 없어요").assertExists()
    }

    /** **되돌릴 수 없다** — 기록과 영수증 사진이 같이 사라진다. 한 번 되묻는다. */
    @Test
    fun `지우기는 되묻고 나서 지운다`() {
        var deleted: VetVisit? = null
        compose.setContent {
            VetVisitsScreen(
                state = ready(listOf(visit("v1"))),
                today = today,
                onConfirmDelete = { deleted = it },
            )
        }

        compose.onNodeWithText("삭제").performClick()
        assertEquals("되묻기 전에는 안 지운다", null, deleted)

        compose.onNodeWithText("지우기").performClick()
        assertEquals("v1", deleted?.id)
    }

    @Test
    fun `병원 전화를 누르면 그 번호를 넘긴다`() {
        var called: String? = null
        compose.setContent {
            VetVisitsScreen(
                state = ready(listOf(visit("v1", phone = "02-543-0075"))),
                today = today,
                onCallHospital = { called = it },
            )
        }

        compose.onNodeWithText("02-543-0075").performClick()

        assertEquals("02-543-0075", called)
    }

    @Test
    fun `읽지 못하면 다시 시도를 준다`() {
        var retried = false
        compose.setContent {
            VetVisitsScreen(
                state = VetVisitState(
                    selectedPetId = "pet",
                    visits = ChatLoadState.Failed(ChatApiError(0, null, "서버에 닿지 못했어요.")),
                ),
                today = today,
                onRetryLoad = { retried = true },
            )
        }

        compose.onNodeWithText("서버에 닿지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()

        assertTrue(retried)
    }

    @Test
    fun `뒤로를 누르면 알려 준다`() {
        var back = false
        compose.setContent {
            VetVisitsScreen(state = ready(listOf(visit("v1"))), today = today, onBack = { back = true })
        }

        compose.onNodeWithContentDescriptionSafe("뒤로").performClick()

        assertTrue(back)
    }

    // -- 배관 -----------------------------------------------------------

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule
        .onNodeWithContentDescriptionSafe(label: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(label))

    private fun ready(
        visits: List<VetVisit>,
        olderCount: Int = 0,
        start: LocalDate? = null,
        range: VetRange = VetRange.RecentYear,
    ) = VetVisitState(
        selectedPetId = "pet",
        visits = ChatLoadState.Ready(
            VetVisitPage(start = start, end = null, olderCount = olderCount, visits = visits),
        ),
        range = range,
        reasonLabels = mapOf("skin" to "피부"),
    )

    private fun visit(id: String, reason: String = "skin", phone: String? = null) = VetVisit(
        id = id, petId = "pet", visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = "압구정동물병원", hospitalAddress = null, hospitalPhone = phone,
        reasonCode = reason, reasonDetail = null, isEmergency = false, isOncology = false,
        clientEventId = "c-$id",
    )
}
