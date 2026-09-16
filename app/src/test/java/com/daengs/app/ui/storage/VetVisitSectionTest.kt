package com.daengs.app.ui.storage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.care.VetVisit
import com.daengs.app.care.VetVisitPage
import com.daengs.app.care.VetVisitState
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 저장소 탭의 진료비 칸. **PR #416 부터 목록이 아니라 요약 카드 하나다** — 목록·기간
 * 필터·삭제는 [VetVisitsScreen] 으로 옮겼고, 여기 남은 일은 셋이다: 이번 달 얼마 썼나,
 * 마지막으로 언제 갔나, 그리고 전체보기로 가는 길.
 *
 * ⚠️ **"이번 달" 은 KST 로 센다.** 저쪽이 `Asia/Seoul` 로 오늘을 정하므로 기기 시간대로
 *    재면 달이 바뀌는 순간이 서버와 어긋난다. 그래서 오늘을 인자로 받는다 —
 *    `LocalDate.now()` 를 안에서 부르면 이 규칙을 테스트가 붙잡을 수 없다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VetVisitSectionTest {

    @get:Rule
    val compose = createComposeRule()

    /** 2026-09-16 (KST). 이 파일의 모든 "오늘". */
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun `이번 달 합계를 보여 준다`() {
        compose.setContent {
            VetVisitSection(
                state = ready(listOf(visit("v1", day(9, 10), 61_700), visit("v2", day(9, 2), 80_000))),
                today = today,
            )
        }

        compose.onNodeWithText("이번 달").assertExists()
        compose.onNodeWithText("141,700원").assertExists()
    }

    /**
     * 달이 바뀌는 자리를 한 건씩 놓고 센다. **지난달 마지막 날은 이번 달이 아니다** —
     * 여기가 틀리면 8월 31일 영수증이 9월 합계에 섞여 들어가고, 유저는 자기가 쓴 적 없는
     * 금액을 본다.
     */
    @Test
    fun `지난달 마지막 날은 이번 달 합계에 안 들어간다`() {
        compose.setContent {
            VetVisitSection(
                state = ready(
                    listOf(
                        visit("v1", LocalDate.of(2026, 9, 1), 10_000),
                        visit("v2", LocalDate.of(2026, 8, 31), 999_000),
                    ),
                ),
                today = today,
            )
        }

        compose.onNodeWithText("10,000원").assertExists()
    }

    @Test
    fun `이번 달에 간 적이 없으면 0원이다`() {
        compose.setContent {
            VetVisitSection(state = ready(listOf(visit("v1", LocalDate.of(2026, 7, 18), 340_000))), today = today)
        }

        compose.onNodeWithText("0원").assertExists()
    }

    @Test
    fun `마지막 방문은 가장 최근 기록이다`() {
        compose.setContent {
            VetVisitSection(
                state = ready(listOf(visit("v1", day(9, 2), 80_000), visit("v2", day(9, 10), 61_700))),
                today = today,
            )
        }

        compose.onNodeWithText("마지막 방문").assertExists()
        compose.onNodeWithText("9월 10일 · 피부").assertExists()
    }

    @Test
    fun `기록이 아예 없으면 그렇게 말하고 전체보기를 안 준다`() {
        compose.setContent { VetVisitSection(state = ready(emptyList()), today = today) }

        compose.onNodeWithText("아직 남긴 진료비 기록이 없어요").assertExists()
        compose.onNodeWithText("전체보기").assertDoesNotExist()
    }

    /**
     * **이 PR 이 고치려는 바로 그 버그가 요약 카드에서 한 번 더 난다.**
     *
     * 기록이 전부 1년보다 오래됐으면 기본 창은 빈 목록을 준다. 그때 "아직 남긴 기록이
     * 없어요" 라고 말하고 전체보기까지 감추면, **유저는 기록이 감춰져 있다고 알려 줄
     * 화면에 영영 못 들어간다** — 저장소가 기록을 삼킨 것으로 보이는 것이 애초에 이
     * PR 의 출발점이었다.
     *
     * `older_count` 가 그것을 아는 유일한 근거다. 0 보다 크면 **비어 있어도 길을 준다.**
     */
    @Test
    fun `창은 비었어도 더 오래된 기록이 있으면 길을 준다`() {
        compose.setContent {
            VetVisitSection(state = ready(emptyList(), olderCount = 2), today = today)
        }

        compose.onNodeWithText("아직 남긴 진료비 기록이 없어요").assertDoesNotExist()
        compose.onNodeWithText("이 기간에 남긴 기록이 없어요").assertExists()
        compose.onNodeWithText("이전 기록 2건 보기").assertExists()
    }

    @Test
    fun `창이 비었어도 전체보기로 들어갈 수 있다`() {
        var opened = false
        compose.setContent {
            VetVisitSection(
                state = ready(emptyList(), olderCount = 2),
                today = today,
                onOpenAll = { opened = true },
            )
        }

        compose.onNodeWithText("이전 기록 2건 보기").performClick()

        assertTrue(opened)
    }

    @Test
    fun `전체보기를 누르면 알려 준다`() {
        var opened = false
        compose.setContent {
            VetVisitSection(
                state = ready(listOf(visit("v1", day(9, 10), 61_700))),
                today = today,
                onOpenAll = { opened = true },
            )
        }

        compose.onNodeWithText("전체보기").performClick()

        assertTrue(opened)
    }

    /** 영수증 찍기는 **저장소에 남는다.** 찍으려고 전체보기까지 들어갈 이유가 없다. */
    @Test
    fun `영수증 찍기를 누르면 알려 준다`() {
        var picked = false
        compose.setContent {
            VetVisitSection(state = ready(emptyList()), today = today, onPickReceipt = { picked = true })
        }

        compose.onNodeWithText("영수증 찍기").performClick()

        assertTrue(picked)
    }

    @Test
    fun `읽지 못하면 다시 시도를 준다`() {
        var retried = false
        compose.setContent {
            VetVisitSection(
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

    // -- 배관 -----------------------------------------------------------

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    private fun ready(visits: List<VetVisit>, olderCount: Int = 0) = VetVisitState(
        selectedPetId = "pet",
        visits = ChatLoadState.Ready(
            VetVisitPage(start = null, end = null, olderCount = olderCount, visits = visits),
        ),
        reasonLabels = mapOf("skin" to "피부"),
    )

    private fun visit(id: String, visitedOn: LocalDate, totalKrw: Int) = VetVisit(
        id = id, petId = "pet", visitedOn = visitedOn, totalKrw = totalKrw,
        hospitalName = "압구정동물병원", hospitalAddress = null, hospitalPhone = null,
        reasonCode = "skin", reasonDetail = null, isEmergency = false, isOncology = false,
        clientEventId = "c-$id",
    )
}
