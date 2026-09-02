package com.daengs.app.ui.dex

import androidx.compose.runtime.Immutable
import com.daengs.app.dogcard.DrawnCard

/**
 * 도감 한 칸.
 *
 * **칸은 언제나 열두 개다.** 같은 야채를 세 번 뽑아도 칸이 세 개가 되지 않고 [owned] 가
 * 세 장이 된다 — 도감은 "무엇을 가졌나" 를 보여 주는 자리이지 뽑은 순서를 늘어놓는
 * 자리가 아니다.
 *
 * 다만 **뽑은 장은 한 장도 안 버린다.** 같은 아이라도 사진마다 표정이 달라서 두 번째
 * 배추가 첫 번째와 다른 카드다. 칸 안에서 넘겨 볼 수 있어야 한다.
 */
@Immutable
data class DexSlot(
    val card: DexCard,
    /** 최근이 앞이다. 비어 있으면 아직 안 뽑은 칸이다 */
    val owned: List<DrawnCard>,
) {
    val locked: Boolean get() = owned.isEmpty()
    val count: Int get() = owned.size
}

/**
 * 카탈로그 열두 장에 내가 뽑은 것을 겹친다.
 *
 * `DEX_CARDS` 에 없는 `templateId` 는 **조용히 버린다.** 저쪽이 카드를 갈아엎으면 생길
 * 수 있는데, 그릴 칸이 없으니 그릴 수가 없다. 다만 [ownedTotal] 에서도 빼서 "내 카드
 * 7장" 이라고 해 놓고 여섯 장만 보이는 화면이 안 나오게 한다.
 */
fun dexSlots(cards: List<DexCard> = DEX_CARDS, drawn: List<DrawnCard>): List<DexSlot> {
    val byTemplate = drawn.sortedByDescending { it.drawnAtMillis }.groupBy { it.templateId }
    return cards.map { DexSlot(it, byTemplate[it.id].orEmpty()) }
}

/** 몇 종을 모았나. 머리글의 분자다. */
fun List<DexSlot>.collectedKinds(): Int = count { !it.locked }

/** 모두 몇 장인가. 같은 종류를 여러 장 뽑았으면 그만큼 는다. */
fun List<DexSlot>.ownedTotal(): Int = sumOf { it.count }
