package com.daengs.app.dogcard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 내보낸 파일의 이름.
 *
 * 눈으로는 못 잡는 것들이다 — 저장 앱마다 다르게 깨지는 글자, 같은 분에 두 장을
 * 저장했을 때의 충돌, 시간대.
 */
class CardExportNameTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(seoul).toInstant().toEpochMilli()

    @Test
    fun `모양이 정해져 있다`() {
        val name = cardFileName("cabbage", "3f9a12de-0000-4000-8000-000000000000", at(2026, 9, 2, 18, 30), seoul)
        assertEquals("daengs-cabbage-20260902-1830-3f9a12.png", name)
    }

    @Test
    fun `쓸 수 있는 글자만 남는다`() {
        // 아이 이름은 안 들어가지만 templateId 는 저쪽에서 온다 — 언젠가 이상한 글자가
        // 섞여 들어와도 파일 이름이 깨지면 안 된다.
        val name = cardFileName("Sweet Potato/네오", "abcdef", at(2026, 1, 1, 0, 0), seoul)
        assertTrue(name, Regex("^[a-z0-9.\\-]+$").matches(name))
        assertTrue(name, name.startsWith("daengs-sweet-potato-"))
        assertTrue(name, name.endsWith(".png"))
    }

    @Test
    fun `같은 분에 뽑은 두 장이 안 겹친다`() {
        val when1 = at(2026, 9, 2, 18, 30)
        val a = cardFileName("cabbage", "aaaaaa11", when1, seoul)
        val b = cardFileName("cabbage", "bbbbbb22", when1, seoul)
        assertNotEquals(a, b)
    }

    @Test
    fun `같은 카드는 몇 번을 저장해도 같은 이름이다`() {
        // 이름에 **저장한 시각이 아니라 뽑은 시각**이 든다. 두 번 저장했을 때 파일이
        // 둘로 갈라지는 것보다, 같은 이름이라 저장 앱이 물어보는 편이 낫다.
        val card = at(2026, 5, 4, 9, 5)
        assertEquals(
            cardFileName("tomato", "cccccc33", card, seoul),
            cardFileName("tomato", "cccccc33", card, seoul),
        )
    }

    @Test
    fun `시간대는 기기의 것이다`() {
        // 자정 직전에 뽑은 카드가 UTC 기준으로는 어제가 된다. 날짜가 하루 밀린
        // 파일 이름은 "언제 뽑았더라" 를 되짚을 때 바로 틀린 단서가 된다.
        val midnight = at(2026, 9, 2, 0, 30)
        assertTrue(cardFileName("carrot", "dddddd44", midnight, seoul).contains("20260902"))
        assertTrue(
            cardFileName("carrot", "dddddd44", midnight, ZoneId.of("UTC")).contains("20260901"),
        )
    }

    @Test
    fun `빈 id 여도 이름이 나온다`() {
        val name = cardFileName("", "", at(2026, 9, 2, 18, 30), seoul)
        assertEquals("daengs-card-20260902-1830-card.png", name)
    }
}
