package com.daengs.app.care

/**
 * 영수증 한 장을 아이별로 가르는 산수. **화면이 아니라 여기 있다** — 틀렸을 때 조용히
 * 틀리는 값이라, Compose 없이 돌려 볼 수 있어야 한다.
 *
 * 저쪽 계약의 정본은 `SAJOYO/DAENGS_dev` 의 `schemas/vet_visit.py` 이고 근거는 같은
 * 저장소 `docs/vet-visits.md` §2 다 — **한쪽만 고치지 말 것.**
 */

/** 영수증에서 센 `동물명` 블록 하나. 이 블록 하나가 기록 한 줄이 된다. */
data class ReceiptBlock(
    /** 몇 번째 블록인가. 확정 본문의 `splits[].patient_index` 로 그대로 간다. */
    val patientIndex: Int,
    /** 이 블록에 붙은 항목들. 어느 블록인지 모르는 항목은 여기 안 들어온다. */
    val items: List<ReceiptItem>,
    /**
     * 이 아이 몫으로 제안할 금액. **`null` 이면 제안하지 않는다** — 유저가 직접 적는다.
     *
     * 항목 합이 영수증 총액과 다르면 추출이 항목을 놓친 것이라(저쪽 docs §2), 그 상태로
     * 나누면 **틀린 금액이 그럴듯하게** 제안된다. 그럴 때는 차라리 안 채우는 편이 낫다.
     */
    val suggestedKrw: Int?,
)

/**
 * 초안을 블록으로 가른다. **블록 수의 정본은 `patient_count` 다** — 항목이 하나도 안
 * 붙은 블록에도 자리를 만든다. 자리를 안 만들면 유저가 그 아이를 고를 데가 없어진다.
 */
fun VetVisitDraft.receiptBlocks(): List<ReceiptBlock> {
    val suggestable = suggestsAmounts()
    return List(patientCount) { index ->
        val mine = items.filter { it.patientIndex == index }
        ReceiptBlock(
            patientIndex = index,
            items = mine,
            suggestedKrw = if (suggestable) mine.sumOf { it.amountKrw } else null,
        )
    }
}

/**
 * 항목만으로 금액을 제안해도 되나.
 *
 * 둘 다여야 한다 — **항목 합이 총액과 같고**, **모든 항목이 제 블록을 알고** 있어야 한다.
 * 뒤엣것이 빠지면 어느 블록에도 안 붙은 돈이 생겨, 제안된 금액의 합이 총액에 영영 못
 * 미치고 유저는 [확인] 이 왜 안 눌리는지 모른 채 숫자를 만지게 된다.
 */
private fun VetVisitDraft.suggestsAmounts(): Boolean =
    totalKrw != null &&
        items.isNotEmpty() &&
        items.all { it.patientIndex != null } &&
        items.sumOf { it.amountKrw } == totalKrw

/**
 * 영수증 총액에서 아이별 금액의 합을 뺀 값. **0 이어야 확정할 수 있다.**
 *
 * 부호가 말을 가른다 — 양수면 아직 덜 나눈 것이고, 음수면 넘게 나눈 것이다. 서버도
 * `sum(splits.total_krw) != total_krw` 를 422 로 막지만, 유저가 눌러 본 뒤에 알게
 * 하면 안 된다 (카드 "꼭 지켜야 하는 것" 2).
 */
fun remainderKrw(splitAmounts: List<Int>, totalKrw: Int): Int = totalKrw - splitAmounts.sum()
