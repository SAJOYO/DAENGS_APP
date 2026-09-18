package com.daengs.app.notify

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 「오늘 아직 안 나갔어요」를 **언제** 부를지 정하는 규칙 (후보 2번).
 *
 * ### 고정 시각이 아니다
 *
 * 사용자가 처음에 리마인더를 다 뺐다 — *"앱이 조르지 않는 쪽"* 이다. 이것만 다시 넣기로
 * 한 이유는 **부르는 시각을 앱이 정하지 않기 때문**이다: *"단순 매일 특정 시각 때 말고
 * 뭔가 기존 산책 기록들 보고"*. 저녁 7시에 나가는 집에 저녁 6시 알림은 잔소리지만,
 * 7시에 나가는 집에 8시 알림은 *"오늘 빠졌네"* 다.
 *
 * ### 안 부르는 조건이 부르는 조건보다 중요하다
 *
 * 이 알림은 **틀리면 바로 잔소리가 된다.** 그래서 네 가지 중 하나라도 걸리면 조용히
 * 지나간다:
 *
 *  - **습관이 안 보인다** — 최근 [WALK_REMINDER_WINDOW_DAYS] 일에 [WALK_REMINDER_MIN_WALKS]
 *    회 미만이면 「평소 시각」이라는 것이 없다. 없는 습관을 지어내 부르지 않는다
 *  - **이미 나갔다**
 *  - **평소 시각이 아직 안 지났다** — [WALK_REMINDER_GRACE_MINUTES] 분을 더 기다린다.
 *    평소 시각 정각에 부르면 준비하는 사람을 재촉하는 것이 된다
 *  - **밤이 됐다** — [WALK_REMINDER_CURFEW] 를 넘으면 오늘은 넘어간다. 늦은 알림은
 *    나가게 만들지 못하고 잠만 깨운다
 *
 * ### 요일을 구분하지 않는다
 *
 * 주말에 늦게 나가는 집이면 평일 시각으로 부르는 셈인데, 사용자가 그대로 가자고 했다.
 * 요일별로 가르면 4주에 요일당 네 번이라 [WALK_REMINDER_MIN_WALKS] 를 채우는 집이 거의
 * 없어진다 — 규칙이 정교해지는 대신 **아무한테도 안 뜨는** 알림이 된다.
 */

/** 평소 시각을 계산할 때 보는 기간. 4주다 — 요일이 네 번씩 들어온다. */
const val WALK_REMINDER_WINDOW_DAYS = 28L

/**
 * 습관이라고 볼 최소 횟수.
 *
 * 4주에 네 번, 곧 **주 1회**다. 이보다 드물면 산책 시각이 그때그때라 「평소」가 없다.
 */
const val WALK_REMINDER_MIN_WALKS = 4

/** 평소 시각에서 이만큼 기다린 뒤 부른다. */
const val WALK_REMINDER_GRACE_MINUTES = 60L

/** 이 시각을 넘으면 오늘은 안 부른다. */
val WALK_REMINDER_CURFEW: LocalTime = LocalTime.of(21, 0)

/**
 * 평소 나가는 시각. 습관이 안 보이면 `null`.
 *
 * **평균이 아니라 중간값이다.** 평균은 어쩌다 한 번 새벽에 나간 산책 하나에 끌려간다 —
 * 스무 번을 저녁 7시에 나갔어도 새벽 3시 한 번이 평균을 40분 당긴다. 중간값은 그 한 번을
 * 그냥 한 표로 센다.
 *
 * 짝수 개면 가운데 둘의 평균이다.
 */
fun habitualWalkTime(
    starts: List<LocalTime>,
    minWalks: Int = WALK_REMINDER_MIN_WALKS,
): LocalTime? {
    if (starts.size < minWalks) return null
    val minutes = starts.map { it.hour * 60 + it.minute }.sorted()
    val middle = minutes.size / 2
    val median = if (minutes.size % 2 == 1) {
        minutes[middle]
    } else {
        (minutes[middle - 1] + minutes[middle]) / 2
    }
    return LocalTime.of(median / 60, median % 60)
}

/**
 * 오늘 부를 시각. `null` 이면 오늘은 안 부른다.
 *
 * ⚠️ **밤늦게 나가는 집은 아예 안 부르게 된다.** 평소 23시면 여유를 더해 자정을 넘고,
 * [WALK_REMINDER_CURFEW] 에 걸려 `null` 이다. 그대로 둔다 — 자는 사람을 깨우는 것보다
 * 안 부르는 쪽이 낫고, 그 집은 어차피 늦게라도 나가는 집이다.
 */
fun walkReminderTime(
    starts: List<LocalTime>,
    graceMinutes: Long = WALK_REMINDER_GRACE_MINUTES,
    curfew: LocalTime = WALK_REMINDER_CURFEW,
): LocalTime? {
    val habitual = habitualWalkTime(starts) ?: return null
    val at = habitual.plusMinutes(graceMinutes)
    // 자정을 넘겼으면 더한 쪽이 오히려 이른 시각이 된다 — 그것도 밤이라 안 부른다.
    if (at.isBefore(habitual) || at.isAfter(curfew)) return null
    return at
}

/** 지금 불러야 하나. [reminderTime] 이 `null` 이면 언제든 아니다. */
fun shouldRemindNow(
    now: LocalDateTime,
    reminderTime: LocalTime?,
    walkedToday: Boolean,
    curfew: LocalTime = WALK_REMINDER_CURFEW,
): Boolean {
    if (walkedToday || reminderTime == null) return false
    val time = now.toLocalTime()
    return !time.isBefore(reminderTime) && !time.isAfter(curfew)
}

/**
 * 다음에 깨어날 시각.
 *
 * 오늘 부를 시각이 아직 안 지났으면 오늘, 지났으면 **내일 그 시각**이다. 부를 시각이
 * 없으면(습관이 안 보이거나 밤에만 나가는 집) 내일 이맘때 다시 계산하러 깨어난다 —
 * 산책이 쌓이면 습관이 생길 수 있어서 한 번 없다고 손을 놓지 않는다.
 */
fun nextWalkReminderCheck(now: LocalDateTime, reminderTime: LocalTime?): LocalDateTime {
    val time = reminderTime ?: return now.plusDays(1)
    val today = now.toLocalDate().atTime(time)
    return if (now.isBefore(today)) today else now.toLocalDate().plusDays(1).atTime(time)
}

/** [WALK_REMINDER_WINDOW_DAYS] 안에 든 산책만. 오늘 것도 센다. */
fun withinWalkReminderWindow(
    starts: List<LocalDateTime>,
    today: LocalDate,
    windowDays: Long = WALK_REMINDER_WINDOW_DAYS,
): List<LocalDateTime> {
    val from = today.minusDays(windowDays - 1)
    return starts.filter { !it.toLocalDate().isBefore(from) }
}

internal const val WALK_REMINDER_TITLE = "오늘 아직 안 나갔어요."
internal const val WALK_REMINDER_TEXT = "잠깐 한 바퀴 어때요?"
