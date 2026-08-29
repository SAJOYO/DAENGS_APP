package com.daengs.app.ui.home

import com.daengs.app.ui.dex.DEX_CARDS
import com.daengs.app.ui.dex.IMMERSIVE_SCENES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 턴테이블 목록이 **이머시브 장면에서만** 온다는 것을 고정한다.
 *
 * 곡 목록을 따로 들고 있으면 카드가 늘 때 한쪽만 고쳐져서 어긋난다 — 장면에는
 * 곡이 있는데 턴테이블에는 안 뜨거나, 그 반대가 된다. 여기가 그걸 잡는다.
 */
class CardTuneTest {

    @Test
    fun `곡이 있는 카드만 목록에 든다`() {
        val fromScenes = DEX_CARDS
            .filter { IMMERSIVE_SCENES[it.no]?.bgm != null }
            .map { it.no }
        assertEquals(fromScenes, CARD_TUNES.map { it.card.no })
    }

    @Test
    fun `목록의 곡 경로는 장면의 것과 같다`() {
        CARD_TUNES.forEach { tune ->
            assertEquals(IMMERSIVE_SCENES[tune.card.no]?.bgm, tune.asset)
        }
    }

    @Test
    fun `카드 순서를 그대로 따른다`() {
        // 도감 순서와 다르면 "No.01 다음이 No.10" 이라는 감각이 깨진다.
        assertEquals(CARD_TUNES.map { it.card.no }.sorted(), CARD_TUNES.map { it.card.no })
    }

    @Test
    fun `곡 경로가 비어 있지 않다`() {
        // 빈 문자열이면 SceneMusic 이 조용히 무음이 되어 원인을 못 찾는다.
        CARD_TUNES.forEach { assertTrue(it.asset.isNotBlank()) }
    }
}
