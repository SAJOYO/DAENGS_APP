package com.daengs.app.ui.storage

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.daengs.app.pet.Pet
import com.daengs.app.screening.PreparedPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
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

        assertEquals("skin", confirmed?.splits?.single()?.reasonCode)
    }

    @Test
    fun `제안이 있으면 미리 골라 두되 확정되는 값은 유저가 본 그 값이다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent { screen(okDraft(suggested = "vaccination"), onConfirm = { confirmed = it }) }

        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertEquals("vaccination", confirmed?.splits?.single()?.reasonCode)
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

        assertEquals("skin", confirmed?.splits?.single()?.reasonCode)
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

        assertEquals(false, confirmed?.splits?.single()?.isOncology)
    }

    @Test
    fun `같은 날 같은 금액이 이미 있으면 막지 않고 한 줄 알려 준다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent {
            screen(okDraft(suggested = "skin").copy(possibleDuplicate = true), onConfirm = { confirmed = it })
        }

        compose.onNodeWithText("같은 날 같은 금액의 기록이 이미 있어요.").assertExists()
        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertEquals("막는 게 아니라 되묻는 것이다", "skin", confirmed?.splits?.single()?.reasonCode)
    }

    @Test
    fun `영수증을 눌러 크게 볼 수 있다 — 대조가 이 화면의 목적이다`() {
        // 세로로 긴 실물 영수증은 작은 미리보기로는 글자를 못 읽는다. 못 읽으면
        // 기계가 채운 값을 대조할 원본이 없는 것과 같다.
        compose.setContent { screen(okDraft(suggested = "skin"), photo = preparedPhoto()) }

        compose.onNodeWithText("눌러서 크게 보기").assertExists()
        compose.onNodeWithContentDescription("찍은 영수증").performClick()

        compose.onNodeWithContentDescription("크게 본 영수증").assertExists()
    }

    @Test
    fun `크게 본 뒤 닫으면 폼으로 돌아온다`() {
        compose.setContent { screen(okDraft(suggested = "skin"), photo = preparedPhoto()) }

        compose.onNodeWithContentDescription("찍은 영수증").performClick()
        compose.onNodeWithContentDescription("크게 본 영수증").performClick()

        compose.onNodeWithText("눌러서 크게 보기").assertExists()
    }

    @Test
    fun `사진이 없으면 크게 보기 자리도 없다`() {
        compose.setContent { screen(okDraft(suggested = "skin")) }

        compose.onAllNodesWithText("눌러서 크게 보기").assertCountEquals(0)
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

    @Test
    fun `강아지가 한 마리뿐인 계정에는 분할을 안 묻는다`() {
        // patient_count 가 2 로 잘못 세어져도(보호자명·수의사명 오인) 물어볼 이유가 없다.
        compose.setContent { screen(twoPetDraft(), pets = listOf(pet("p1", "초코"))) }

        compose.onAllNodesWithText("아이별로 나누기").assertCountEquals(0)
    }

    @Test
    fun `블록이 하나면 분할을 안 묻는다`() {
        compose.setContent { screen(okDraft(suggested = "skin"), pets = twoPets()) }

        compose.onAllNodesWithText("아이별로 나누기").assertCountEquals(0)
    }

    @Test
    fun `블록마다 항목을 더한 금액이 미리 채워져 있다`() {
        compose.setContent { screen(twoPetDraft(), pets = twoPets()) }

        compose.onNodeWithText("109200").assertExists()
        compose.onNodeWithText("82100").assertExists()
    }

    @Test
    fun `아이를 안 고른 블록이 있으면 확인이 잠긴다`() {
        compose.setContent { screen(twoPetDraft(), pets = twoPets()) }

        // 금액은 맞지만 둘째 블록의 아이를 아직 안 골랐다.
        compose.onNodeWithText("확인").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `합이 영수증 총액과 다르면 모자란 금액을 말하고 확인을 잠근다`() {
        compose.setContent { screen(twoPetDraft(), pets = twoPets()) }
        pickPet(block = 1, name = "초코")
        pickPet(block = 2, name = "보리")

        compose.onNodeWithContentDescription("블록 1 금액").performTextClearance()
        compose.onNodeWithContentDescription("블록 1 금액").performTextInput("100000")

        compose.onNodeWithText("9,200원이 남았어요.", substring = true).assertExists()
        compose.onNodeWithText("확인").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `나눠서 확인하면 블록 수만큼 행이 가고 아이와 금액이 따라간다`() {
        var confirmed: ReceiptEdits? = null
        compose.setContent { screen(twoPetDraft(), pets = twoPets(), onConfirm = { confirmed = it }) }
        pickPet(block = 1, name = "초코")
        pickPet(block = 2, name = "보리")
        pickBlockReason(block = 1, label = "피부")
        pickBlockReason(block = 2, label = "예방접종")

        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertEquals(2, confirmed?.splits?.size)
        assertEquals(listOf("p1", "p2"), confirmed?.splits?.map { it.petId })
        assertEquals(listOf(109_200, 82_100), confirmed?.splits?.map { it.totalKrw })
        assertEquals(listOf(0, 1), confirmed?.splits?.map { it.patientIndex })
        assertEquals("영수증 총액은 그대로 간다", 191_300, confirmed?.totalKrw)
    }

    @Test
    fun `나눌 때는 사유를 미리 골라 주지 않는다 — 한 제안을 모든 아이에게 복사하지 않는다`() {
        // 제안은 영수증 하나에 하나뿐인데 블록마다 사유는 다르다. 미리 채워 두면 유저가
        // 그대로 넘겨 **둘째 아이의 병력에 첫째 아이의 사유가** 남는다. 실기기에서 실제로
        // 그렇게 눌렸다 — 예방접종을 맞은 아이의 기록이 "귀" 로 저장됐다.
        var confirmed: ReceiptEdits? = null
        compose.setContent { screen(twoPetDraft(), pets = twoPets(), onConfirm = { confirmed = it }) }
        pickPet(block = 1, name = "초코")
        pickPet(block = 2, name = "보리")

        // 아이도 금액도 다 맞지만 사유를 아직 안 골랐다.
        compose.onNodeWithText("확인").performScrollTo().assertIsNotEnabled()
        assertNull(confirmed)
    }

    @Test
    fun `분할을 끄면 한 줄로 확정하고 어느 블록인지는 말하지 않는다`() {
        // patient_count 가 높게 세어졌을 때 유저가 끄는 길이다. 이때 블록 번호를 0 으로
        // 접으면 첫 블록의 항목만 이 기록에 붙는다 — 모른다고 말하는 편이 맞다.
        var confirmed: ReceiptEdits? = null
        compose.setContent { screen(twoPetDraft(), pets = twoPets(), onConfirm = { confirmed = it }) }

        compose.onNodeWithText("아이별로 나누기").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("확인").performScrollTo().performClick()

        assertEquals(1, confirmed?.splits?.size)
        assertEquals(191_300, confirmed?.splits?.single()?.totalKrw)
        assertNull(confirmed?.splits?.single()?.patientIndex)
    }

    @Test
    fun `항목 합이 총액과 안 맞으면 금액을 제안하지 않는다`() {
        // 추출이 항목을 놓친 것이라, 그럴듯한 틀린 금액을 미리 채우지 않는다.
        val draft = twoPetDraft().copy(
            items = listOf(ReceiptItem("진료-초진", 10_000, 0), ReceiptItem("종합백신", 20_000, 1)),
        )
        compose.setContent { screen(draft, pets = twoPets()) }

        compose.onAllNodesWithText("109200").assertCountEquals(0)
        compose.onNodeWithText("확인").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `블록을 빼면 그 돈이 남고 확인은 잠긴 채로 있다`() {
        // patient_count 가 높게 세어졌거나 남의 아이가 섞여 찍힌 경우다. 뺀 블록의 돈을
        // 말없이 다른 아이에게 옮기지 않는다 — 합이 비는 것을 보여 주고 유저가 정하게 한다.
        compose.setContent { screen(twoPetDraft(), pets = twoPets()) }
        pickPet(block = 1, name = "초코")

        compose.onNodeWithContentDescription("블록 2 빼기")
            .performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("82,100원이 남았어요.", substring = true).assertExists()
        compose.onNodeWithText("확인").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `미래 날짜면 확인이 잠긴다 — 눌러 보고 422 를 받게 하지 않는다`() {
        compose.setContent {
            screen(okDraft(suggested = "skin").copy(visitedOn = LocalDate.of(2026, 12, 25)))
        }

        compose.onNodeWithText("영수증 날짜가 오늘보다 뒤예요.", substring = true).assertExists()
        compose.onNodeWithText("확인").performScrollTo().assertIsNotEnabled()
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
    private fun pickBlockReason(block: Int, label: String) {
        compose.onNodeWithContentDescription("블록 $block 사유 $label")
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun pickPet(block: Int, name: String) {
        compose.onNodeWithContentDescription("블록 $block 아이 $name")
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun pickReason(label: String) {
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
    }

    @androidx.compose.runtime.Composable
    private fun screen(
        draft: VetVisitDraft?,
        step: ReceiptStep = ReceiptStep.READY,
        error: com.daengs.app.chat.ChatApiError? = null,
        photo: PreparedPhoto? = null,
        pets: List<Pet> = listOf(pet("p1", "초코")),
        onConfirm: (ReceiptEdits) -> Unit = {},
        onRetry: () -> Unit = {},
    ) = ReceiptConfirmScreen(
        photo = photo,
        draft = draft,
        options = OPTIONS,
        step = step,
        error = error,
        pets = pets,
        onConfirm = onConfirm,
        onRetry = onRetry,
        today = LocalDate.of(2026, 9, 10),
    )

    private fun pet(id: String, name: String) = Pet(
        id = id, name = name, breed = "mix", sex = null, neutered = null,
        weightKg = null, birthDate = null, birthDateKind = null, isPrimary = id == "p1",
    )

    /** 두 아이가 찍힌 영수증. 카드의 실측 판독값과 같은 숫자다. */
    private fun twoPetDraft() = okDraft(suggested = "vaccination").copy(
        totalKrw = 191_300,
        patientCount = 2,
        items = listOf(
            ReceiptItem("진료-초진", 109_200, 0),
            ReceiptItem("종합백신 5차", 82_100, 1),
        ),
    )

    private fun twoPets() = listOf(pet("p1", "초코"), pet("p2", "보리"))

    /** 세로로 긴 영수증 한 장. 실물과 같은 비율(대략 1:2.2)로 만든다. */
    private fun preparedPhoto(): PreparedPhoto {
        val bitmap = Bitmap.createBitmap(240, 520, Bitmap.Config.ARGB_8888)
        val jpeg = ByteArrayOutputStream()
            .also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            .toByteArray()
        return PreparedPhoto(bitmap, jpeg)
    }

    private fun okDraft(suggested: String?) = VetVisitDraft(
        draftId = "d1", petId = "p1", status = ExtractionStatus.OK, unreadableReason = null,
        visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = "압구정동물병원", hospitalAddress = "서울 강남구",
        hospitalPhone = "02-543-0075",
        items = listOf(ReceiptItem("초진료", 5_500)),
        patientCount = 1,
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
        patientCount = 1,
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
