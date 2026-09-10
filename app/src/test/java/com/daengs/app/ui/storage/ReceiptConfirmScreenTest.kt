package com.daengs.app.ui.storage

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.daengs.app.care.ExtractionStatus
import com.daengs.app.care.ReceiptEdits
import com.daengs.app.care.ReceiptItem
import com.daengs.app.care.ReceiptStep
import com.daengs.app.care.UnreadableReason
import com.daengs.app.care.VetReasonOption
import com.daengs.app.care.VetVisitDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 확인 화면이 지켜야 하는 것 — **총액 줄만 잘린 영수증을 빈 폼으로 만들지 않는다**,
 * 제안을 자동으로 받아들이지 않는다, 잘못 읽힌 전화번호를 통과시키지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReceiptConfirmScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `no_amount 는 총액만 비고 병원과 항목은 채워져 있다`() {
        compose.setContent { screen(noAmountDraft()) }

        compose.onNodeWithText("합계 줄을 못 읽었어요. 총액만 적어 주세요.").assertExists()
        compose.onNodeWithText("압구정동물병원").assertExists()
        compose.onNodeWithText("02-543-0075").assertExists()
        compose.onNodeWithText("초진료").assertExists()
        compose.onNodeWithText("5,500원").assertExists()
        // 총액 한 칸만 비어 있다 — 나머지를 다 버리면 거의 다 읽은 영수증을 통째로 잃는다.
        compose.onNodeWithText("확인").assertIsNotEnabled()
    }

    @Test
    fun `blurry 는 정말로 비어 있고 손으로 채우라고 말한다`() {
        compose.setContent { screen(blurryDraft()) }

        compose.onNodeWithText("사진이 흐려서 못 읽었어요. 손으로 적거나 다시 찍어 주세요.").assertExists()
        compose.onNodeWithText("확인").assertIsNotEnabled()
    }

    @Test
    fun `제안이 없으면 사유를 고르기 전까지 확인이 안 눌린다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent { screen(okDraft(suggested = null), onConfirm = { confirmed = it }) }

        compose.onNodeWithText("확인").assertIsNotEnabled()
        // performClick 은 비활성 노드에도 성공한다 — 주입일 뿐이다. 그래서 눌러 보고
        // **아무것도 안 나갔다**는 것까지 봐야 이 테스트가 무언가를 잡는다.
        compose.onNodeWithText("확인").performScrollTo().performClick()
        assertNull("사유를 고르기 전에는 안 나간다", confirmed)

        pickReason("피부")
        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertEquals("skin", confirmed?.reasonCode)
    }

    @Test
    fun `제안이 있으면 미리 골라 두되 확정되는 값은 유저가 본 그 값이다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent { screen(okDraft(suggested = "vaccination"), onConfirm = { confirmed = it }) }

        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertEquals("vaccination", confirmed?.reasonCode)
        assertEquals(61_700, confirmed?.totalKrw)
        assertEquals(LocalDate.of(2026, 9, 10), confirmed?.visitedOn)
        assertEquals("압구정동물병원", confirmed?.hospitalName)
    }

    @Test
    fun `제안을 고쳐 고르면 고친 값이 간다 — 드롭다운이 장식이 아니다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent { screen(okDraft(suggested = "vaccination"), onConfirm = { confirmed = it }) }

        pickReason("피부")
        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertEquals("skin", confirmed?.reasonCode)
    }

    @Test
    fun `전화번호 모양이 틀리면 확인이 막힌다`() {
        compose.setContent { screen(okDraft(suggested = "skin")) }

        compose.onNodeWithContentDescription("병원 전화").performScrollTo().performTextClearance()
        compose.onNodeWithContentDescription("병원 전화").performTextInput("5432-1234-5678-9012")

        compose.onNodeWithText("전화번호 모양이 올바르지 않아요.").assertExists()
        compose.onNodeWithText("확인").assertIsNotEnabled()
    }

    @Test
    fun `빈 전화번호는 통과하고 칸째로 안 보낸다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent { screen(okDraft(suggested = "skin"), onConfirm = { confirmed = it }) }

        compose.onNodeWithContentDescription("병원 전화").performScrollTo().performTextClearance()
        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertNull(confirmed?.hospitalPhone)
    }

    @Test
    fun `종양은 유저만 켠다 — 기계가 미리 켜 두지 않는다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent { screen(okDraft(suggested = "skin"), onConfirm = { confirmed = it }) }

        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertEquals(false, confirmed?.isOncology)
    }

    @Test
    fun `같은 날 같은 금액이 이미 있으면 막지 않고 한 줄 알려 준다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent {
            screen(okDraft(suggested = "skin").copy(possibleDuplicate = true), onConfirm = { confirmed = it })
        }

        compose.onNodeWithText("같은 날 같은 금액의 기록이 이미 있어요.").assertExists()
        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertEquals("막는 게 아니라 되묻는 것이다", "skin", confirmed?.reasonCode)
    }

    @Test
    fun `읽는 중에는 폼 대신 안내가 보인다`() {
        compose.setContent { screen(draft = null, step = ReceiptStep.EXTRACTING) }

        compose.onNodeWithText("영수증을 읽고 있어요").assertExists()
    }

    @Test
    fun `초안을 못 받았으면 다시 시도만 보인다`() {
        var retried = false
        compose.setContent {
            screen(
                draft = null,
                step = ReceiptStep.UPLOADING,
                error = com.daengs.app.chat.ChatApiError(0, null, "서버에 닿지 못했어요."),
                onRetry = { retried = true },
            )
        }

        compose.onNodeWithText("서버에 닿지 못했어요.").assertExists()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(true, retried)
    }

    // -- 배관 -----------------------------------------------------------

    /**
     * 사유 칩 하나를 고른다.
     *
     * **좌표가 아니라 semantics 로 누른다.** 칩은 높이를 묶은 안쪽 스크롤 안에 있어서
     * (17개를 다 펼치면 [확인] 이 화면 두 개 아래로 밀린다) `performScrollTo` 를 걸면
     * **안쪽**만 움직이고 바깥 폼은 그대로다 — 칩이 화면 밖에 있는 채로 눌려서 클릭이
     * 조용히 빗나간다. 여기서 재려는 것은 "칩이 눌리면 그 사유가 확정에 실린다" 이고,
     * 칩이 화면 어디에 놓이는가는 실기기가 본다.
     */
    private fun pickReason(label: String) {
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
    }

    @androidx.compose.runtime.Composable
    private fun screen(
        draft: VetVisitDraft?,
        step: ReceiptStep = ReceiptStep.READY,
        error: com.daengs.app.chat.ChatApiError? = null,
        onConfirm: (ReceiptEdits) -> Unit = {},
        onRetry: () -> Unit = {},
    ) = ReceiptConfirmScreen(
        photo = null,
        draft = draft,
        options = OPTIONS,
        step = step,
        error = error,
        onConfirm = onConfirm,
        onRetry = onRetry,
        today = LocalDate.of(2026, 9, 10),
    )

    private fun okDraft(suggested: String?) = VetVisitDraft(
        draftId = "d1", petId = "p1", status = ExtractionStatus.OK, unreadableReason = null,
        visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = "압구정동물병원", hospitalAddress = "서울 강남구",
        hospitalPhone = "02-543-0075",
        items = listOf(ReceiptItem("초진료", 5_500)),
        suggestedReasonCode = suggested, isEmergency = false, possibleDuplicate = false,
        reasonOptions = OPTIONS,
    )

    private fun noAmountDraft() = okDraft(suggested = "vaccination").copy(
        status = ExtractionStatus.UNREADABLE,
        unreadableReason = UnreadableReason.NO_AMOUNT,
        totalKrw = null,
    )

    private fun blurryDraft() = VetVisitDraft(
        draftId = "d2", petId = "p1", status = ExtractionStatus.UNREADABLE,
        unreadableReason = UnreadableReason.BLURRY, visitedOn = null, totalKrw = null,
        hospitalName = null, hospitalAddress = null, hospitalPhone = null, items = emptyList(),
        suggestedReasonCode = null, isEmergency = false, possibleDuplicate = false,
        reasonOptions = OPTIONS,
    )

    private companion object {
        val OPTIONS = listOf(
            VetReasonOption("skin", "피부"),
            VetReasonOption("vaccination", "예방접종"),
        )
    }
}
