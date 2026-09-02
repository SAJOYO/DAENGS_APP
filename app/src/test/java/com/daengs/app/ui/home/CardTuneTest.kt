package com.daengs.app.ui.home

import com.daengs.app.ui.dex.CARD_BGM
import com.daengs.app.ui.dex.DEX_CARDS
import com.daengs.app.ui.dex.IMMERSIVE_SCENES
import com.daengs.app.ui.dex.bgmFor
import org.junit.Assert.assertEquals
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
            val scene = IMMERSIVE_SCENES[card.no] ?: return@forEach
            assertEquals("No.${card.no} ${card.id}", bgmFor(card.id), scene.bgm)
        }
    }

    @Test
    fun `카드 순서를 그대로 따른다`() {
        // 도감 순서와 다르면 "No.01 다음이 No.10" 이라는 감각이 깨진다.
        assertEquals(CARD_TUNES.map { it.card.no }.sorted(), CARD_TUNES.map { it.card.no })
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

    @Test
    fun `곡 경로가 비어 있지 않다`() {
        CARD_TUNES.forEach { assertTrue(it.asset.isNotBlank()) }
    }
}
