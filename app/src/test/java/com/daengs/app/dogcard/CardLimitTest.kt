package com.daengs.app.dogcard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 하루 세 번을 잡는다.
 *
 * **하루의 경계가 기기 시간대인지**가 핵심이다. UTC 로 세면 한국에서는 아침 9시에
 * 횟수가 차서, 자정에 찬다고 적어 둔 문구가 거짓말이 된다.
 */
class CardLimitTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(seoul).toInstant().toEpochMilli()

    @Test
    fun `한 장도 안 뽑았으면 세 번`() {
        assertEquals(3, drawsLeft(emptyList(), at(2026, 9, 2, 10, 0), seoul))
    }

    @Test
    fun `오늘 두 장 뽑았으면 한 번 남는다`() {
        val drawn = listOf(at(2026, 9, 2, 9, 0), at(2026, 9, 2, 9, 30))
        assertEquals(1, drawsLeft(drawn, at(2026, 9, 2, 10, 0), seoul))
    }

    @Test
    fun `세 장을 채우면 0 이고 더 뽑아도 음수가 안 된다`() {
        val three = List(3) { at(2026, 9, 2, 9, it) }
        assertEquals(0, drawsLeft(three, at(2026, 9, 2, 23, 59), seoul))
        val four = three + at(2026, 9, 2, 21, 0)
        assertEquals(0, drawsLeft(four, at(2026, 9, 2, 23, 59), seoul))
    }

    /** 어제 뽑은 것은 오늘 몫을 안 깎는다. */
    @Test
    fun `자정을 넘기면 다시 세 번`() {
        val yesterday = List(3) { at(2026, 9, 1, 22, it) }
        assertEquals(0, drawsLeft(yesterday, at(2026, 9, 1, 23, 59), seoul))
        assertEquals(3, drawsLeft(yesterday, at(2026, 9, 2, 0, 1), seoul))
    }

    /**
     * 한국 자정 직후는 UTC 로는 아직 어제 오후 3시다. **시간대를 안 보면 여기서 틀린다** —
     * 어제 뽑은 카드가 오늘 것으로 세어져서 새 하루인데도 횟수가 안 찬다.
     */
    @Test
    fun `UTC 가 아니라 기기 시간대로 센다`() {
        val lateLastNight = List(3) { at(2026, 9, 1, 23, it) }
        val justAfterMidnight = at(2026, 9, 2, 0, 10)
        assertEquals("서울 기준으로는 새 하루다", 3, drawsLeft(lateLastNight, justAfterMidnight, seoul))
        assertEquals("UTC 기준으로는 아직 같은 날이다", 0, drawsLeft(lateLastNight, justAfterMidnight, ZoneId.of("UTC")))
    }

    @Test
    fun `상한을 바꿔서 부를 수 있다`() {
        assertEquals(5, drawsLeft(emptyList(), at(2026, 9, 2, 10, 0), seoul, limit = 5))
    }
}
