package com.daengs.app.ui.dex

import androidx.compose.ui.unit.IntRect
import com.daengs.app.dogcard.DrawnCard
import com.daengs.app.dogcard.photo.PhotoCard
import com.daengs.app.dogcard.photo.PhotoCardStatus
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
    fun `한 장도 없으면 모든 칸이 잠긴다`() {
        val slots = dexSlots(drawn = emptyList())
        assertEquals(DEX_CARDS.size, slots.size)
        assertTrue(slots.all { it.locked })
        assertEquals(0, slots.collectedKinds())
        assertEquals(0, slots.ownedTotal())
    }

    /** 결정: 중복은 칸을 늘리지 않고 개수만 올린다. */
    @Test
    fun `같은 종류를 세 번 뽑아도 칸은 하나고 개수가 셋이다`() {
        val slots = dexSlots(drawn = listOf(card("cabbage", 10), card("cabbage", 20), card("cabbage", 30)))
        assertEquals("칸이 늘면 안 된다", DEX_CARDS.size, slots.size)
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
        assertEquals(listOf(30L, 20L, 10L), owned.map { it.madeAtMillis })
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
        assertEquals(DEX_CARDS.size, slots.size)
        assertEquals(1, slots.collectedKinds())
        assertEquals(1, slots.ownedTotal())
    }

    @Test
    fun `카탈로그 순서를 그대로 따른다`() {
        val slots = dexSlots(drawn = listOf(card("lettuce", 10)))
        assertEquals(DEX_CARDS.map { it.id }, slots.map { it.card.id })
    }

    /**
     * **마지막 한 장을 지우면 칸이 다시 잠긴다.**
     *
     * 지우기를 붙이면서 생긴 갈래다. 확인창이 그렇게 말하고 있으니 실제로도 그래야
     * 한다 — 말과 다르면 모은 것이 줄어든 이유를 알 수가 없다.
     */
    @Test
    fun `마지막 한 장을 지우면 그 칸이 다시 잠긴다`() {
        val one = dexSlots(drawn = listOf(card("cabbage", 100)))
        val cabbage = one.first { it.card.id == "cabbage" }
        assertFalse(cabbage.locked)

        val none = dexSlots(drawn = emptyList())
        assertTrue(none.first { it.card.id == "cabbage" }.locked)
    }

    /** 여러 장 중 하나만 지우면 칸은 열려 있고 장수만 준다. */
    @Test
    fun `여러 장 중 하나를 지우면 칸은 열려 있다`() {
        val two = listOf(card("cabbage", 100), card("cabbage", 200))
        val after = dexSlots(drawn = two.drop(1))
        val cabbage = after.first { it.card.id == "cabbage" }
        assertFalse(cabbage.locked)
        assertEquals(1, cabbage.count)
    }

    private fun photo(id: String, month: Int, status: PhotoCardStatus, at: Long) = PhotoCard(
        id = id, dogId = null, month = month, dogName = "콩이", title = "T",
        status = status, errorCode = null, likeness = null, createdAtMillis = at,
    )

    private val all = DEX_CARDS + PHOTO_CARDS

    @Test
    fun `포토는 달 칸에 겹치고 최근이 앞이다`() {
        val slots = dexSlots(all, emptyList(), listOf(photo("a", 4, PhotoCardStatus.Ready, 10), photo("b", 4, PhotoCardStatus.Ready, 20)))
        val april = slots.first { it.card.id == "photo-04" }
        assertEquals(listOf("b", "a"), april.owned.map { it.id })
        assertTrue(slots.first { it.card.id == "photo-09" }.locked)
    }

    /** 실패한 카드는 칸을 열지 않는다 — 머리말의 한 줄이 알린다. */
    @Test
    fun `실패한 포토는 칸에 안 들어간다`() {
        val slots = dexSlots(all, emptyList(), listOf(photo("x", 9, PhotoCardStatus.Failed, 10)))
        assertTrue(slots.first { it.card.id == "photo-09" }.locked)
    }

    /** 만드는 중이면 칸은 열리지만 아직 "만든 장수" 는 아니다. */
    @Test
    fun `만드는 중인 포토는 칸을 열되 장수에는 안 센다`() {
        val slots = dexSlots(all, emptyList(), listOf(photo("g", 9, PhotoCardStatus.Generating, 10)))
        val sep = slots.first { it.card.id == "photo-09" }
        assertFalse(sep.locked)
        assertEquals(0, sep.drawnCount)
        assertTrue((sep.owned.single() as OwnedCard.Photo).pending)
    }

    @Test
    fun `완성인데 그림 파일이 있어야 만든 장으로 센다`() {
        val ready = photo("r", 4, PhotoCardStatus.Ready, 10)
        val noFile = dexSlots(all, emptyList(), listOf(ready)).first { it.card.id == "photo-04" }
        assertEquals(0, noFile.drawnCount)
        val withFile = dexSlots(all, emptyList(), listOf(ready), mapOf("r" to java.io.File("r.png")))
            .first { it.card.id == "photo-04" }
        assertEquals(1, withFile.drawnCount)
        assertFalse((withFile.owned.single() as OwnedCard.Photo).pending)
    }

    @Test
    fun `포토를 넣어도 야채 칸은 그대로다`() {
        val before = dexSlots(drawn = listOf(card("cabbage", 10)))
        val after = dexSlots(all, listOf(card("cabbage", 10)), listOf(photo("a", 4, PhotoCardStatus.Ready, 5)))
        assertEquals(before.map { it.card.id to it.count }, after.take(DEX_CARDS.size).map { it.card.id to it.count })
    }

    @Test
    fun `포토 표지는 파일이고 파일이 없으면 빈 판이다`() {
        val april = photoCardFor(4)!!
        assertEquals(null, coverOf(april, null))
        assertEquals(null, coverOf(april, OwnedCardArt(drawn = null, photoFile = null)))
        val f = java.io.File("a.png")
        assertEquals(CardArt.Local(f), coverOf(april, OwnedCardArt(drawn = null, photoFile = f)))
        assertEquals(CardArt.Asset("neo-hologram/art/cabbage.webp"), coverOf(DEX_CARDS.first(), null))
    }
}
