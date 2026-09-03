package com.daengs.app.dogcard

import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 개발자 패널이 지정해서 만드는 카드를 잡는다.
 *
 * **핵심은 이 카드가 하루 세 번을 안 먹는 것이다.** 먹으면 정작 뽑기 연출을 못 본다 —
 * 뽑기 팝업은 진짜 뽑기 흐름에서만 뜨는데, 무대를 세워 보려고 열두 장을 만들면 그날은
 * 뽑기가 잠긴다. `seedCards` 가 시드를 지난 날짜로 두는 것과 같은 이유이고,
 * 화면으로는 안 보이는 종류의 고장이라 여기서 잡는다.
 */
class DevCardsTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(seoul).toInstant().toEpochMilli()

    @Test
    fun `열두 장을 만들어도 오늘 몫은 그대로다`() {
        val now = at(2026, 9, 2, 10, 0)
        val made = CARD_TEMPLATES.map { devCardTime(now) }
        assertEquals(12, made.size)
        assertEquals(3, drawsLeft(made, now, seoul))
    }

    /**
     * **자정 직후가 위험한 자리다.** "몇 시간 빼기" 로 밀면 00:30 에 만든 카드가 아직
     * 오늘 안에 남아서 그날 몫을 깎는다. 하루를 통째로 미는 이유다.
     */
    @Test
    fun `자정 직후에 만들어도 오늘 몫은 그대로다`() {
        val justAfterMidnight = at(2026, 9, 2, 0, 30)
        val made = List(3) { devCardTime(justAfterMidnight) }
        assertEquals(3, drawsLeft(made, justAfterMidnight, seoul))
    }

    /** 뽑은 카드와 섞여도 진짜 뽑기만 센다. */
    @Test
    fun `지정해 만든 카드는 뽑은 카드 옆에서도 안 세어진다`() {
        val now = at(2026, 9, 2, 10, 0)
        val drawn = listOf(at(2026, 9, 2, 9, 0))
        assertEquals(2, drawsLeft(drawn + List(12) { devCardTime(now) }, now, seoul))
    }

    @Test
    fun `만드는 시각이 지금보다 앞선다`() {
        val now = at(2026, 9, 2, 10, 0)
        assertTrue(devCardTime(now) < now)
    }
}
