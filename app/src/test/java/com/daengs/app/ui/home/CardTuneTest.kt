package com.daengs.app.ui.home

import com.daengs.app.ui.dex.CARD_BGM
import com.daengs.app.ui.dex.DEX_CARDS
import com.daengs.app.ui.dex.DexDeck
import com.daengs.app.ui.dex.IMMERSIVE_SCENES
import com.daengs.app.ui.dex.bgmFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 턴테이블 목록이 **`CARD_BGM` 한 군데에서만** 온다는 것을 고정한다.
 *
 * 예전에는 이머시브 장면에서 뽑았고 이 테스트도 그것을 잡고 있었다. 곡이 있는 카드가
 * 곧 무대가 있는 카드였기 때문인데 **이제 아니다** — 시금치·당근은 무대 없이 곡만
 * 있다. 장면에 매달아 두면 곡 하나 붙이려고 무대 한 벌을 만들어야 한다.
 *
 * 대신 반대 방향을 잡는다: **무대가 곡을 갖고 있으면 그 곡은 `CARD_BGM` 의 것과 같아야
 * 한다.** 두 군데가 갈라지면 턴테이블에는 있는데 무대에서는 조용한 카드가 생긴다.
 */
class CardTuneTest {

    @Test
    fun `곡이 있는 카드만 목록에 든다`() {
        val expected = DEX_CARDS.filter { bgmFor(it.id) != null }.map { it.no }
        assertEquals(expected, CARD_TUNES.map { it.card.no })
    }

    @Test
    fun `목록의 곡 경로는 CARD_BGM 의 것과 같다`() {
        CARD_TUNES.forEach { assertEquals(bgmFor(it.card.id), it.asset) }
    }

    /** 무대가 제 곡을 따로 적어 두면 언젠가 갈라진다. */
    @Test
    fun `무대의 곡도 CARD_BGM 을 따른다`() {
        DEX_CARDS.forEach { card ->
            val scene = IMMERSIVE_SCENES[card.id] ?: return@forEach
            assertEquals("No.${card.no} ${card.id}", bgmFor(card.id), scene.bgm)
        }
    }

    /**
     * **곡이 있는 카드와 무대가 있는 카드는 다르다.**
     *
     * 이 둘을 같은 것으로 보면 화면이 갈린다 — 뽑기 화면이 무대에 물어서 당근·시금치를
     * "이 카드에는 아직 노래가 없어요" 라고 했는데, 턴테이블에는 그 곡이 떠 있었다.
     * 한 앱이 같은 카드를 두고 두 가지 말을 한 셈이다.
     *
     * 그래서 **어긋나는 카드가 실제로 있다는 것**을 여기서 못 박는다. 이 테스트가
     * 깨지면 둘이 다시 같아진 것이고, 그때는 "무대에 물어도 되겠지" 가 다시 참이 된다.
     */
    @Test
    fun `곡만 있고 무대는 없는 카드가 있다`() {
        val tuneOnly = DEX_CARDS.filter { bgmFor(it.id) != null && IMMERSIVE_SCENES[it.id] == null }

        assertTrue("곡과 무대가 같아졌다면 이 테스트를 지워도 된다", tuneOnly.isNotEmpty())
        assertEquals(
            listOf("apple", "carrot", "mango", "spinach", "strawberry", "tomato"),
            tuneOnly.map { it.id }.sorted(),
        )
    }

    /**
     * 도감 순서와 다르면 "No.01 다음이 No.10" 이라는 감각이 깨진다.
     *
     * **번호 전체를 한 줄로 세워 보면 안 된다.** 도감이 두 벌이라 번호가 겹쳐서,
     * 야채 마지막(No.12 상추) 다음에 과일 첫 장(No.01 사과)이 온다 — 그게 맞는
     * 차례인데 `sorted()` 로 보면 어긋난 것으로 나온다. 벌 안에서만 오름차순이다.
     */
    @Test
    fun `벌 안에서는 번호 차례다`() {
        DexDeck.entries.forEach { deck ->
            val nos = CARD_TUNES.filter { it.card.deck == deck }.map { it.card.no }
            assertEquals(deck.label, nos.sorted(), nos)
        }
    }

    /** 야채가 다 지나간 뒤에 과일이 온다. 섞이면 도감과 다른 차례가 된다. */
    @Test
    fun `야채가 먼저고 과일이 나중이다`() {
        val decks = CARD_TUNES.map { it.card.deck }
        assertEquals(decks.sortedBy { it.ordinal }, decks)
    }

    /**
     * **한 줄에 같은 글자가 둘 뜨지 않는다.**
     *
     * 목록은 야채와 과일을 섞어 세우는데 번호는 벌마다 다시 1번부터다. 번호만
     * 찍으면 배추와 사과가 둘 다 `No. 01` 이라 같은 카드가 두 번 뜬 것으로 읽힌다.
     */
    @Test
    fun `번호에 벌 이름이 붙는다`() {
        val labels = CARD_TUNES.map { tuneNumberLabel(it.card) }
        assertEquals("겹치는 줄이 있다: $labels", labels.size, labels.toSet().size)
        assertEquals("야채 No. 01", tuneNumberLabel(DEX_CARDS.first { it.id == "cabbage" }))
        assertEquals("과일 No. 01", tuneNumberLabel(DEX_CARDS.first { it.id == "apple" }))
    }

    /** 모르는 id 를 적어 두면 그 줄은 조용히 무시된다 — 곡을 넣었는데 안 나온다. */
    @Test
    fun `CARD_BGM 의 id 가 전부 실재하는 카드다`() {
        val known = DEX_CARDS.map { it.id }.toSet()
        CARD_BGM.keys.forEach { assertTrue("$it 는 카드 id 가 아니다", it in known) }
    }

    /**
     * **파일이 실제로 있는지 본다.**
     *
     * 경로를 적어 두고 파일을 안 넣으면 `SceneMusic` 이 조용히 무음이 된다 —
     * `runCatching{}.getOrNull()` 로 물러서게 돼 있어서 화면에는 아무 표시가 없다.
     * 곡을 뺄 때 표에서만 지우고 파일을 남기는(또는 그 반대) 실수도 여기서 걸린다.
     */
    @Test
    fun `적어 둔 곡 파일이 실제로 있다`() {
        val roots = listOf(File("src/main/assets"), File("app/src/main/assets"))
        val assets = roots.firstOrNull { it.isDirectory } ?: return
        CARD_BGM.forEach { (id, path) ->
            assertTrue("$id: $path 가 없다", File(assets, path).isFile)
        }
    }

    // -- 턴테이블이 보여 주는 것 --------------------------------------------

    private fun drawn(templateId: String, at: Long = 0L) = com.daengs.app.dogcard.DrawnCard(
        id = "$templateId-$at",
        appUserId = null,
        templateId = templateId,
        dogId = null,
        dogName = "네옹",
        drawnAtMillis = at,
        codeText = "DG-0824",
        core = androidx.compose.ui.unit.IntRect(0, 0, 10, 10),
    )

    /**
     * **한 장도 안 뽑았으면 한 곡도 안 뜬다.**
     *
     * 예전에는 카탈로그를 다 늘어놓아서, 아무것도 없는 사람에게도 곡이 전부 들렸다.
     * 그러면 카드를 뽑을 이유가 그만큼 없어진다.
     */
    @Test
    fun `안 뽑았으면 곡이 없다`() {
        assertTrue(ownedTunes(emptyList()).isEmpty())
    }

    @Test
    fun `뽑은 카드의 곡만 뜬다`() {
        val mine = ownedTunes(listOf(drawn("cabbage"), drawn("carrot")))
        assertEquals(listOf("cabbage", "carrot"), mine.map { it.card.id })
    }

    /** 곡이 없는 카드를 뽑아도 목록은 안 는다. 스물다섯 장 중 아홉 장에만 곡이 있다. */
    @Test
    fun `곡 없는 카드는 목록에 안 든다`() {
        assertTrue(ownedTunes(listOf(drawn("pepper"), drawn("eggplant"))).isEmpty())
    }

    /** 같은 야채를 여러 장 뽑아도 곡은 하나다. 한 곡이 두 줄로 뜨면 안 된다. */
    @Test
    fun `같은 야채를 두 장 뽑아도 곡은 하나다`() {
        assertEquals(1, ownedTunes(listOf(drawn("cabbage"), drawn("cabbage"))).size)
    }

    /** 뽑은 순서가 아니라 도감 순서다. 어제 뽑은 곡이 매번 자리를 옮기면 안 된다. */
    @Test
    fun `카탈로그 순서를 따른다`() {
        val mine = ownedTunes(listOf(drawn("lettuce"), drawn("cabbage")))
        assertEquals(listOf("cabbage", "lettuce"), mine.map { it.card.id })
    }

    // -- 목록에 그릴 그림 --------------------------------------------------
    //
    // 목록은 **내가 뽑은 카드**를 그린다. 카탈로그 원화를 그리다가 곡이 과일까지
    // 늘면서 드러났다 — 야채 원화에는 저쪽이 그린 네오 강아지가 구워져 있는데
    // 과일 원화는 구멍만 뚫린 판이라, 목록에 얼굴 없는 빈 구멍 카드가 떴다.

    @Test
    fun `내가 뽑은 카드를 달고 나온다`() {
        val mine = ownedTunes(listOf(drawn("apple")))
        assertEquals("apple-0", mine.single().mine?.id)
    }

    /** 카탈로그 목록은 누구의 것도 아니다. 여기에 카드가 붙으면 남의 얼굴이 뜬다. */
    @Test
    fun `카탈로그 목록에는 내 카드가 없다`() {
        CARD_TUNES.forEach { assertNull(it.mine) }
    }

    /**
     * 같은 종류를 여러 장 뽑았으면 **가장 최근 것**이다.
     *
     * 도감이 칸 안에서 최근을 앞에 두는 것과 같은 규칙이다(`dexSlots`). 갈리면
     * 도감에서 보던 얼굴과 턴테이블의 얼굴이 다른 장이 된다.
     */
    @Test
    fun `같은 종류는 가장 최근 장을 싣는다`() {
        val mine = ownedTunes(listOf(drawn("cabbage", at = 10), drawn("cabbage", at = 30)))
        assertEquals("cabbage-30", mine.single().mine?.id)
    }

    @Test
    fun `곡 경로가 비어 있지 않다`() {
        CARD_TUNES.forEach { assertTrue(it.asset.isNotBlank()) }
    }
}
