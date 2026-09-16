package com.daengs.app.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 영수증 한 장을 아이별로 가르는 산수. **이 파일이 잡는 것은 돈이다** — 어느 아이에게
 * 얼마가 제안되는지, 그리고 합이 안 맞을 때 얼마가 모자란지.
 *
 * 화면이 아니라 여기서 잡는 이유는, 틀렸을 때 조용히 틀리는 값이기 때문이다. 금액도
 * 병원도 맞아 보이는 확인 화면에서 유저는 그대로 [확인] 을 누른다.
 */
class ReceiptSplitTest {

    @Test
    fun `블록마다 항목을 묶어 그 아이 몫을 제안한다`() {
        val blocks = draft(
            totalKrw = 191_300,
            patientCount = 2,
            items = listOf(
                item("진료-초진", 15_000, 0),
                item("*검사-귀-도말", 94_200, 0),
                item("종합백신 5차", 82_100, 1),
            ),
        ).receiptBlocks()

        assertEquals(listOf(0, 1), blocks.map { it.patientIndex })
        assertEquals(listOf(109_200, 82_100), blocks.map { it.suggestedKrw })
    }

    @Test
    fun `항목 합이 총액과 다르면 아무 금액도 제안하지 않는다`() {
        // 추출이 항목을 놓친 것이다 (저쪽 docs §2). 놓친 채로 나누면 **틀린 금액이
        // 그럴듯하게** 제안되므로, 그때는 직접 적게 한다.
        val blocks = draft(
            totalKrw = 191_300,
            patientCount = 2,
            items = listOf(item("진료-초진", 15_000, 0), item("종합백신 5차", 82_100, 1)),
        ).receiptBlocks()

        assertEquals(2, blocks.size)
        assertNull(blocks[0].suggestedKrw)
        assertNull(blocks[1].suggestedKrw)
    }

    @Test
    fun `항목이 하나도 안 붙은 블록도 자리를 받는다`() {
        // patient_count 는 블록 수가 정본이다. 항목이 그 블록에 하나도 안 붙었다고
        // 자리를 안 만들면, 유저가 그 아이를 고를 자리 자체가 사라진다.
        val blocks = draft(
            totalKrw = 50_000,
            patientCount = 3,
            items = listOf(item("진료-초진", 50_000, 0)),
        ).receiptBlocks()

        assertEquals(listOf(0, 1, 2), blocks.map { it.patientIndex })
        assertEquals(emptyList<ReceiptItem>(), blocks[2].items)
    }

    @Test
    fun `어느 블록인지 모르는 항목은 어느 블록에도 안 붙는다`() {
        // null 을 0 으로 접으면 남의 아이 몫이 첫째 아이에게 붙는다.
        val blocks = draft(
            totalKrw = 30_000,
            patientCount = 2,
            items = listOf(item("진료-초진", 10_000, 0), item("알 수 없는 줄", 20_000, null)),
        ).receiptBlocks()

        assertEquals(listOf("진료-초진"), blocks[0].items.map { it.name })
        assertEquals(emptyList<String>(), blocks[1].items.map { it.name })
    }

    @Test
    fun `어느 블록인지 모르는 항목이 섞여 있으면 금액을 제안하지 않는다`() {
        // 항목 합은 총액과 같지만 20,000원이 어느 블록에도 안 붙는다. 그대로 제안하면
        // 블록 합이 10,000원이 되어, 유저는 [확인] 이 왜 안 눌리는지 모른 채 숫자를 만진다.
        val blocks = draft(
            totalKrw = 30_000,
            patientCount = 2,
            items = listOf(item("진료-초진", 10_000, 0), item("알 수 없는 줄", 20_000, null)),
        ).receiptBlocks()

        assertNull(blocks[0].suggestedKrw)
        assertNull(blocks[1].suggestedKrw)
    }

    @Test
    fun `모자란 금액을 원 단위로 말해 준다`() {
        assertEquals(9_200, remainderKrw(listOf(100_000, 82_100), totalKrw = 191_300))
    }

    @Test
    fun `넘친 금액은 음수로 온다 — 모자란 것과 다른 말을 해야 한다`() {
        assertEquals(-5_000, remainderKrw(listOf(120_000, 76_300), totalKrw = 191_300))
    }

    @Test
    fun `합이 맞으면 0 이다`() {
        assertEquals(0, remainderKrw(listOf(109_200, 82_100), totalKrw = 191_300))
    }

    private fun item(name: String, amountKrw: Int, patientIndex: Int?) =
        ReceiptItem(name, amountKrw, patientIndex)

    private fun draft(totalKrw: Int, patientCount: Int, items: List<ReceiptItem>) = VetVisitDraft(
        draftId = "d1",
        petId = "p1",
        status = ExtractionStatus.OK,
        unreadableReason = null,
        visitedOn = LocalDate.of(2026, 9, 15),
        totalKrw = totalKrw,
        hospitalName = null,
        hospitalAddress = null,
        hospitalPhone = null,
        items = items,
        patientCount = patientCount,
        suggestedReasonCode = null,
        isEmergency = false,
        possibleDuplicate = false,
        reasonOptions = emptyList(),
    )
}
