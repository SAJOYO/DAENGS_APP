package com.daengs.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 「오늘 아직 안 나갔어요」의 규칙.
 *
 * **안 부르는 쪽을 더 많이 잰다.** 이 알림은 틀리면 바로 잔소리가 되고, 잔소리는 알림
 * 전체를 끄게 만든다.
 */
class WalkReminderRuleTest {

    private fun times(vararg hhmm: String) = hhmm.map(LocalTime::parse)

    @Test
    fun `평소 시각은 산책 시작 시각의 중간값이다`() {
        assertEquals(
            LocalTime.of(19, 0),
            habitualWalkTime(times("18:30", "19:00", "19:30", "19:00", "18:40")),
        )
    }

    /**
     * **평균이 아니라 중간값이라야 하는 이유.** 새벽 산책 한 번이 평균은 크게 당기지만
     * 중간값은 한 표로 센다.
     */
    @Test
    fun `어쩌다 한 번 새벽에 나간 산책에 끌려가지 않는다`() {
        val starts = times("03:00", "19:00", "19:10", "19:20", "19:30")
        assertEquals(LocalTime.of(19, 10), habitualWalkTime(starts))
    }

    @Test
    fun `짝수 개면 가운데 둘의 평균이다`() {
        assertEquals(LocalTime.of(19, 15), habitualWalkTime(times("19:00", "19:10", "19:20", "19:40")))
    }

    /** 4주에 네 번(주 1회)을 못 채우면 「평소」라는 것이 없다. */
    @Test
    fun `습관이 안 보이면 평소 시각이 없다`() {
        assertNull(habitualWalkTime(times("19:00", "19:10", "19:20")))
        assertNull(habitualWalkTime(emptyList()))
    }

    @Test
    fun `부를 시각은 평소 시각에 한 시간을 더한 것이다`() {
        assertEquals(
            LocalTime.of(20, 0),
            walkReminderTime(times("19:00", "19:00", "19:00", "19:00")),
        )
    }

    /**
     * ⚠️ **밤늦게 나가는 집은 아예 안 부른다.** 여유를 더하면 통금을 넘는다 — 자는
     * 사람을 깨우는 것보다 안 부르는 쪽이 낫다.
     */
    @Test
    fun `통금을 넘기면 오늘은 안 부른다`() {
        assertNull(walkReminderTime(times("20:30", "20:30", "20:30", "20:30")))
        assertNull(walkReminderTime(times("23:30", "23:30", "23:30", "23:30")))
    }

    @Test
    fun `평소 시각이 지나고 오늘 안 나갔으면 부른다`() {
        val at = LocalTime.of(20, 0)
        assertTrue(shouldRemindNow(LocalDateTime.parse("2026-09-18T20:05"), at, walkedToday = false))
    }

    @Test
    fun `이미 나갔으면 안 부른다`() {
        val at = LocalTime.of(20, 0)
        assertFalse(shouldRemindNow(LocalDateTime.parse("2026-09-18T20:05"), at, walkedToday = true))
    }

    @Test
    fun `평소 시각이 아직 안 지났으면 안 부른다`() {
        val at = LocalTime.of(20, 0)
        assertFalse(shouldRemindNow(LocalDateTime.parse("2026-09-18T19:30"), at, walkedToday = false))
    }

    @Test
    fun `밤이 되면 안 부른다`() {
        val at = LocalTime.of(20, 0)
        assertFalse(shouldRemindNow(LocalDateTime.parse("2026-09-18T21:30"), at, walkedToday = false))
    }

    @Test
    fun `부를 시각이 없으면 언제든 안 부른다`() {
        assertFalse(shouldRemindNow(LocalDateTime.parse("2026-09-18T20:05"), null, walkedToday = false))
    }

    @Test
    fun `부를 시각이 남았으면 오늘 그 시각에 깨어난다`() {
        assertEquals(
            LocalDateTime.parse("2026-09-18T20:00"),
            nextWalkReminderCheck(LocalDateTime.parse("2026-09-18T09:00"), LocalTime.of(20, 0)),
        )
    }

    @Test
    fun `이미 지났으면 내일 그 시각에 깨어난다`() {
        assertEquals(
            LocalDateTime.parse("2026-09-19T20:00"),
            nextWalkReminderCheck(LocalDateTime.parse("2026-09-18T20:30"), LocalTime.of(20, 0)),
        )
    }

    /** 습관이 없어도 손을 놓지 않는다 — 산책이 쌓이면 생길 수 있다. */
    @Test
    fun `부를 시각이 없으면 내일 다시 계산한다`() {
        assertEquals(
            LocalDateTime.parse("2026-09-19T09:00"),
            nextWalkReminderCheck(LocalDateTime.parse("2026-09-18T09:00"), null),
        )
    }

    @Test
    fun `4주 밖의 산책은 평소 시각에 안 든다`() {
        val today = LocalDate.parse("2026-09-18")
        val starts = listOf(
            LocalDateTime.parse("2026-09-18T19:00"),
            LocalDateTime.parse("2026-08-22T07:00"),
            LocalDateTime.parse("2026-08-21T07:00"),
        )
        assertEquals(
            listOf(LocalDateTime.parse("2026-09-18T19:00"), LocalDateTime.parse("2026-08-22T07:00")),
            withinWalkReminderWindow(starts, today),
        )
    }
}
