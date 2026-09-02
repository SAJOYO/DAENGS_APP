package com.daengs.app.dogcard

import com.daengs.app.ui.dex.DEX_CARDS
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * 뽑기가 정말 12종 균등인지 잡는다.
 *
 * **이 테스트의 절반은 `CARD_TEMPLATES.size` 를 세는 것이다.** 자리를 비운 카드는
 * 열두 장이 다 들어와 있었는데 코드는 두 장만 알고 있었다 — 그대로 뽑기를 붙였으면
 * "12종 균등"이라고 적힌 채 배추/고구마 반반이 나갔다. 화면으로는 안 보이는 종류의
 * 고장이라 여기서 잡는다.
 */
class CardDrawTest {

    @Test
    fun `템플릿이 열두 장이다`() {
        assertEquals(12, CARD_TEMPLATES.size)
    }

    /**
     * 카드 목록의 원본은 저쪽 저장소(`cards.mjs`)이고 우리는 두 벌을 손으로 옮겨 적었다 —
     * 도감의 `DEX_CARDS` 와 뽑기의 `CARD_TEMPLATES` 다. **id 가 어긋나면 뽑은 카드가
     * 도감 어느 칸에도 안 얹힌다.** 지금 우연히 맞아 있어서 변환표를 안 두고 있으므로,
     * 그 우연을 여기서 잠근다.
     */
    @Test
    fun `도감과 id 가 순서까지 같다`() {
        assertEquals(DEX_CARDS.map { it.id }, CARD_TEMPLATES.map { it.id })
    }

    @Test
    fun `그림 경로가 id 를 따른다`() {
        CARD_TEMPLATES.forEach {
            assertEquals("neo-hologram/art/${it.id}-card-slots.webp", it.art)
        }
    }

    /** 구멍이 카드 밖으로 나가면 얼굴이 잘린 채로만 보인다. */
    @Test
    fun `구멍이 카드 안에 있다`() {
        CARD_TEMPLATES.forEach { t ->
            listOf("face" to t.face, "avatar" to t.avatar).forEach { (which, hole) ->
                val where = "${t.id} $which"
                assertTrue("$where rx", hole.rx > 0f)
                assertTrue("$where ry", hole.ry > 0f)
                assertTrue("$where 왼쪽", hole.cx - hole.rx >= 0f)
                assertTrue("$where 오른쪽", hole.cx + hole.rx <= 100f)
                assertTrue("$where 위", hole.cy - hole.ry >= 0f)
                assertTrue("$where 아래", hole.cy + hole.ry <= 100f)
            }
        }
    }

    @Test
    fun `열두 종이 고르게 나온다`() {
        val random = Random(42)
        val rounds = 120_000
        val counts = mutableMapOf<String, Int>()
        repeat(rounds) {
            val id = drawTemplate(random = random).id
            counts[id] = (counts[id] ?: 0) + 1
        }

        assertEquals("열두 종이 다 나와야 한다", 12, counts.size)
        val expected = rounds / 12.0
        counts.forEach { (id, n) ->
            val off = abs(n - expected) / rounds
            assertTrue("$id 가 $n 번 (기대 ${expected.toInt()}) — 0.5%p 를 벗어났다", off < 0.005)
        }
    }

    /** 한 장짜리 목록을 줘도 죽지 않는다. 프리뷰·테스트가 그렇게 부른다. */
    @Test
    fun `목록이 한 장이면 그 한 장이 나온다`() {
        val one = listOf(CARD_TEMPLATES.first())
        repeat(20) { assertEquals(one.first().id, drawTemplate(one, Random(it)).id) }
    }
}
