package com.daengs.app.ui.storage

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.care.VetVisit
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
 * 저장소 탭의 진료비 칸. 여기서 잡는 것은 **사유 표시명이 어디서 오느냐** 다 —
 * 확정 응답에는 코드만 실려 오므로, 서버가 준 지도로 그려야 앱이 한글 17개를
 * 하드코딩하지 않는다. 모르는 코드가 와도 크래시가 아니라 코드가 보여야 한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VetVisitSectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `사유 표시명은 서버가 준 지도에서 온다`() {
        compose.setContent { VetVisitSection(state = ready(listOf(visit(reason = "skin")))) }

        compose.onNodeWithText("9월 10일 · 피부 · 61,700원").assertExists()
        compose.onNodeWithText("압구정동물병원").assertExists()
    }

    @Test
    fun `모르는 코드는 코드를 그대로 보여 준다 — 크래시가 아니다`() {
        compose.setContent { VetVisitSection(state = ready(listOf(visit(reason = "dermatology")))) }

        compose.onNodeWithText("9월 10일 · dermatology · 61,700원").assertExists()
    }

    @Test
    fun `기록이 없으면 그렇게 말한다`() {
        compose.setContent { VetVisitSection(state = ready(emptyList())) }

        compose.onNodeWithText("아직 남긴 진료비 기록이 없어요").assertExists()
    }

    @Test
    fun `영수증 찍기를 누르면 알려 준다`() {
        var picked = false
        compose.setContent { VetVisitSection(state = ready(emptyList()), onPickReceipt = { picked = true }) }

        compose.onNodeWithText("영수증 찍기").performClick()

        assertTrue(picked)
    }

    @Test
    fun `병원 전화를 누르면 그 번호를 넘긴다`() {
        var called: String? = null
        compose.setContent {
            VetVisitSection(
                state = ready(listOf(visit(reason = "skin", phone = "02-543-0075"))),
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
            VetVisitSection(
                state = VetVisitState(
                    selectedPetId = "pet",
                    visits = ChatLoadState.Failed(ChatApiError(0, null, "서버에 닿지 못했어요.")),
                ),
                onRetryLoad = { retried = true },
            )
        }

        compose.onNodeWithText("서버에 닿지 못했어요.").assertExists()
        compose.onNodeWithText("다시 시도").performClick()

        assertTrue(retried)
    }

    @Test
    fun `지우기는 되묻고 나서 지운다`() {
        var deleted: VetVisit? = null
        compose.setContent {
            VetVisitSection(
                state = ready(listOf(visit(reason = "skin"))),
                onConfirmDelete = { deleted = it },
            )
        }

        compose.onNodeWithText("삭제").performClick()
        assertEquals("되묻기 전에는 안 지운다", null, deleted)

        compose.onNodeWithText("지우기").performClick()
        assertEquals("v1", deleted?.id)
    }

    // -- 배관 -----------------------------------------------------------

    private fun ready(visits: List<VetVisit>) = VetVisitState(
        selectedPetId = "pet",
        visits = ChatLoadState.Ready(visits),
        reasonLabels = mapOf("skin" to "피부"),
    )

    private fun visit(reason: String, phone: String? = null) = VetVisit(
        id = "v1", petId = "pet", visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = "압구정동물병원", hospitalAddress = null, hospitalPhone = phone,
        reasonCode = reason, reasonDetail = null, isEmergency = false, isOncology = false,
        clientEventId = "c1",
    )
}
