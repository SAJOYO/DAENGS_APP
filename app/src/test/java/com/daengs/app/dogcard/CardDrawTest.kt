package com.daengs.app.dogcard

import com.daengs.app.ui.dex.CARD_BGM
import com.daengs.app.ui.dex.DEX_CARDS
import com.daengs.app.ui.dex.IMMERSIVE_SCENES
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * 뽑기가 정말 스물다섯 종에 제 무게로 나오는지 잡는다.
 *
 * **이 테스트의 절반은 `CARD_TEMPLATES.size` 를 세는 것이다.** 자리를 비운 카드는
 * 열두 장이 다 들어와 있었는데 코드는 두 장만 알고 있었다 — 그대로 뽑기를 붙였으면
 * "12종 균등"이라고 적힌 채 배추/고구마 반반이 나갔다. 화면으로는 안 보이는 종류의
 * 고장이라 여기서 잡는다.
 */
class CardDrawTest {

    /** 야채 열두 장 + 과일 열세 장. 벌이 늘면 여기서 먼저 알게 된다. */
    @Test
    fun `템플릿이 스물다섯 장이다`() {
        assertEquals(25, CARD_TEMPLATES.size)
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

    /**
     * 이름칸·번호판이 **열두 장 다 있다.**
     *
     * 열 장이 `code = null` 이던 때가 있었다. 저쪽이 번호 자리를 못 지워 줘서 우리도
     * 안 그렸고, 그래서 **생일이 안 나오고 저쪽 개 이름 `NEO-0824` 가 인쇄된 채로
     * 남아 있었다.** 다시 null 로 돌아가면 조용히 그 상태가 된다.
     */
    @Test
    fun `이름칸과 번호판이 열두 장 다 있다`() {
        CARD_TEMPLATES.forEach {
            assertTrue("${it.id} 이름칸", it.name != null)
            assertTrue("${it.id} 번호판", it.code != null)
        }
    }

    @Test
    fun `글자 칸이 카드 안에 있고 서로 안 겹친다`() {
        CARD_TEMPLATES.forEach { t ->
            val name = t.name!!
            val code = t.code!!
            listOf("이름칸" to name, "번호판" to code).forEach { (which, s) ->
                val where = "${t.id} $which"
                assertTrue("$where 가로", s.x1 > s.x0)
                assertTrue("$where 세로", s.y1 > s.y0)
                assertTrue("$where 왼쪽", s.x0 >= 0f)
                assertTrue("$where 오른쪽", s.x1 <= 100f)
                assertTrue("$where 위", s.y0 >= 0f)
                assertTrue("$where 아래", s.y1 <= 100f)
            }
            // 겹치면 이름 위에 번호가 찍힌다.
            val apart = name.x1 <= code.x0 || code.x1 <= name.x0 ||
                name.y1 <= code.y0 || code.y1 <= name.y0
            assertTrue("${t.id} 이름칸과 번호판이 겹친다", apart)
        }
    }

    /**
     * 이름칸이 너무 낮으면 **글자만 깨알같이 찍힌다.** 글자 크기를 칸 높이에서 구하기
     * 때문이다 (`drawSlotText`). 예전 도구가 상추에서 3.6% 짜리 칸을 뱉은 적이 있다.
     */
    @Test
    fun `이름칸이 충분히 높다`() {
        CARD_TEMPLATES.forEach {
            val h = it.name!!.let { s -> s.y1 - s.y0 }
            assertTrue("${it.id} 이름칸 높이 $h%", h >= 4.0f)
        }
    }

    /**
     * 칩을 까는 것이 **정확히 여덟 장**이다.
     *
     * 번호가 홀로그램 위에 있어 못 지우는 카드만 켠다. 피망·당근·단호박·가지는 저쪽이
     * 인쇄한 검은 상자가 이미 칩이라 끄고, 켜면 상자 안에 상자가 된다. 새 원화가
     * 들어올 때 조용히 어긋나는 것을 여기서 잡는다.
     */
    @Test
    fun `칩을 까는 카드가 여덟 장이다`() {
        val off = CARD_TEMPLATES.filterNot { it.codeChip }.map { it.id }.sorted()
        // 과일은 열세 장 다 켠다 — 번호가 홀로그램 바 위에 얹혀 있어 못 지운다.
        assertEquals(listOf("carrot", "danhobak", "eggplant", "pepper"), off)
        assertEquals(CARD_TEMPLATES.size - 4, CARD_TEMPLATES.count { it.codeChip })
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

    /**
     * **갈래(야채·과일)로는 여전히 안 가린다.** 무게는 곡·무대가 있느냐로만 갈린다
     * (사용자 결정 2026-09-04 는 그대로고, 그 위에 레어도가 얹힌 것이다).
     *
     * 같은 무게끼리는 고르게 나와야 한다 — 한 칸이라도 무게를 잘못 매기면 여기서 걸린다.
     */
    @Test
    fun `무게가 같은 카드끼리는 고르게 나온다`() {
        val random = Random(42)
        val rounds = 200_000
        val counts = mutableMapOf<String, Int>()
        repeat(rounds) {
            val id = drawTemplate(random = random).id
            counts[id] = (counts[id] ?: 0) + 1
        }

        assertEquals("스물다섯 종이 다 나와야 한다", CARD_TEMPLATES.size, counts.size)
        val total = CARD_TEMPLATES.sumOf { weightOf(it.id) }
        counts.forEach { (id, n) ->
            val expected = rounds.toDouble() * weightOf(id) / total
            val off = abs(n - expected) / rounds
            assertTrue("$id 가 $n 번 (기대 ${expected.toInt()}) — 0.5%p 를 벗어났다", off < 0.005)
        }
    }

    /**
     * 무게가 실제로 세 갈래인지 못 박는다.
     *
     * **[weightOf] 가 목록을 안 들고 곡·무대 표에 물어보기 때문에** 곡을 하나 붙이면
     * 그 카드는 그날로 레어가 된다. 반대로 표를 잘못 건드리면 조용히 균등으로 돌아간다.
     */
    @Test
    fun `곡과 무대가 있는 카드가 더 드물다`() {
        val stage = CARD_TEMPLATES.filter { it.id in IMMERSIVE_SCENES }
        val tuneOnly = CARD_TEMPLATES.filter { it.id in CARD_BGM && it.id !in IMMERSIVE_SCENES }
        val plain = CARD_TEMPLATES.filter { it.id !in CARD_BGM }

        assertEquals("무대 3장 + 곡만 8장 + 그냥 14장", 3, stage.size)
        assertEquals(8, tuneOnly.size)
        assertEquals(14, plain.size)

        stage.forEach { assertEquals(it.id, 1, weightOf(it.id)) }
        tuneOnly.forEach { assertEquals(it.id, 2, weightOf(it.id)) }
        plain.forEach { assertEquals(it.id, 4, weightOf(it.id)) }
    }

    /**
     * 꽝이 20% 다.
     *
     * **꽝이 0 이 되면 화면은 멀쩡해 보인다** — 그냥 늘 카드가 나올 뿐이라, 이 숫자를
     * 지키는 것은 테스트뿐이다.
     */
    @Test
    fun `한 판에 스무 번 중 네 번은 꽝이다`() {
        val random = Random(7)
        val rounds = 200_000
        val misses = (1..rounds).count { drawOutcome(random = random) is DrawOutcome.Miss }
        val off = abs(misses.toDouble() / rounds - MISS_PERCENT / 100.0)
        assertTrue("꽝이 $misses/$rounds — 0.5%p 를 벗어났다", off < 0.005)
    }

    /** 꽝이 아니면 반드시 카드가 나온다. 둘 사이에 빈 값이 없다. */
    @Test
    fun `꽝이 아닌 판은 언제나 카드다`() {
        val random = Random(11)
        repeat(5_000) {
            val outcome = drawOutcome(random = random)
            if (outcome is DrawOutcome.Got) {
                assertTrue(outcome.template.id, outcome.template in CARD_TEMPLATES)
            }
        }
    }

    /** 한 장짜리 목록을 줘도 죽지 않는다. 프리뷰·테스트가 그렇게 부른다. */
    @Test
    fun `목록이 한 장이면 그 한 장이 나온다`() {
        val one = listOf(CARD_TEMPLATES.first())
        repeat(20) { assertEquals(one.first().id, drawTemplate(one, Random(it)).id) }
    }
}
