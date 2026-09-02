package com.daengs.app.ui.dex

import androidx.compose.ui.unit.IntRect
import com.daengs.app.dogcard.DrawnCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 도감이 "무엇을 가졌나" 를 정직하게 말하는지 잡는다.
 *
 * 예전에는 머리글이 `${DEX_CARDS.size} / ${DEX_CARDS.size}` 라 분자·분모가 같은 값이었고,
 * **뽑지도 않은 카드를 12/12 수집이라고 말했다.** 그런 종류의 거짓말은 화면만 봐서는
 * 안 보인다 — 늘 그럴듯하게 보이기 때문이다.
 */
class DexSlotsTest {

    private fun card(templateId: String, at: Long, id: String = "$templateId-$at") = DrawnCard(
        id = id,
        appUserId = "user-1",
        templateId = templateId,
        dogId = "dog-1",
        dogName = "네옹",
        drawnAtMillis = at,
        codeText = "NEO-0824",
        core = IntRect(0, 0, 10, 10),
    )

    @Test
    fun `한 장도 없으면 열두 칸이 다 잠긴다`() {
        val slots = dexSlots(drawn = emptyList())
        assertEquals(12, slots.size)
        assertTrue(slots.all { it.locked })
        assertEquals(0, slots.collectedKinds())
        assertEquals(0, slots.ownedTotal())
    }

    /** 결정: 중복은 칸을 늘리지 않고 개수만 올린다. */
    @Test
    fun `같은 종류를 세 번 뽑아도 칸은 하나고 개수가 셋이다`() {
        val slots = dexSlots(drawn = listOf(card("cabbage", 10), card("cabbage", 20), card("cabbage", 30)))
        assertEquals("칸이 늘면 안 된다", 12, slots.size)
        val cabbage = slots.first { it.card.id == "cabbage" }
        assertEquals(3, cabbage.count)
        assertEquals("한 종류만 모았다", 1, slots.collectedKinds())
        assertEquals("장수는 셋이다", 3, slots.ownedTotal())
    }

    /** 표지는 가장 최근에 뽑은 것이다. 방금 뽑은 카드가 도감에 안 보이면 뽑은 것 같지가 않다. */
    @Test
    fun `칸 안에서 최근이 앞이다`() {
        val slots = dexSlots(drawn = listOf(card("cabbage", 10), card("cabbage", 30), card("cabbage", 20)))
        val owned = slots.first { it.card.id == "cabbage" }.owned
        assertEquals(listOf(30L, 20L, 10L), owned.map { it.drawnAtMillis })
    }

    @Test
    fun `안 뽑은 칸만 잠긴다`() {
        val slots = dexSlots(drawn = listOf(card("lettuce", 10)))
        assertFalse(slots.first { it.card.id == "lettuce" }.locked)
        assertTrue(slots.filterNot { it.card.id == "lettuce" }.all { it.locked })
        assertEquals(1, slots.collectedKinds())
    }

    /**
     * 저쪽이 카드를 갈아엎으면 우리가 모르는 `templateId` 가 남는다. 그릴 칸이 없으니
     * 못 그리는데, **총 장수에서도 빼야 한다** — 안 그러면 "내 카드 2장" 이라고 해 놓고
     * 한 장만 보이는 화면이 나온다.
     */
    @Test
    fun `모르는 종류는 버려지고 장수에도 안 센다`() {
        val slots = dexSlots(drawn = listOf(card("cabbage", 10), card("당근아님", 20)))
        assertEquals(12, slots.size)
        assertEquals(1, slots.collectedKinds())
        assertEquals(1, slots.ownedTotal())
    }

    @Test
    fun `카탈로그 순서를 그대로 따른다`() {
        val slots = dexSlots(drawn = listOf(card("lettuce", 10)))
        assertEquals(DEX_CARDS.map { it.id }, slots.map { it.card.id })
    }
}
