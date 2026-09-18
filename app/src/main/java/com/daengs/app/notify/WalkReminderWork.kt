package com.daengs.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daengs.app.DaengsApp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 「오늘 아직 안 나갔어요」를 부르는 자리 (후보 2번).
 *
 * 규칙은 [WalkReminderRule] 에 있고 여기는 **기록을 읽어 그 규칙에 넣는 일**만 한다.
 *
 * ### 왜 작업이 둘인가
 *
 * 부를 시각이 집집마다 달라서, 「매일 9시에 도는 작업」 하나로는 안 된다. 그래서 둘로
 * 나눴다:
 *
 *  - [WalkReminderPlanWorker] — 하루 두 번 깨어나 **언제 부를지 계산**하고 아래를 그 시각에
 *    예약한다. 몇 시에 깨어나든 상관없다(계산만 한다)
 *  - [WalkReminderWorker] — 예약된 그 시각에 깨어나 **한 번 더 확인하고** 부른다
 *
 * 하나로 합쳐 스스로 다시 예약하게 하면, 같은 고유 이름을 `REPLACE` 로 다시 넣는 순간
 * **돌고 있는 자기 자신이 취소된다.** 이름을 둘로 가르면 그 문제가 없다.
 *
 * ### 정확한 시각은 보장되지 않는다
 *
 * `AlarmManager.setExactAndAllowWhileIdle` 은 Android 12+ 에서 `SCHEDULE_EXACT_ALARM`
 * 권한이고 **Play 정책이 용도를 제한한다** — 알람·캘린더 앱이 아니면 거절될 수 있다.
 * `WorkManager` 로 가고 **늦을 수 있는 것을 받아들인다.** 이 알림은 분 단위 정확도가
 * 필요 없고, 너무 늦으면 [WALK_REMINDER_CURFEW] 가 막는다.
 */
class WalkReminderPlanWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DaengsApp ?: return Result.failure()
        val starts = walkStarts(app) ?: return Result.success()
        val now = LocalDateTime.now()
        val at = nextWalkReminderCheck(
            now = now,
            reminderTime = walkReminderTime(
                withinWalkReminderWindow(starts, now.toLocalDate()).map(LocalDateTime::toLocalTime),
            ),
        )
        scheduleWalkReminderCheck(applicationContext, ChronoUnit.MILLIS.between(now, at))
        return Result.success()
    }
}

/** 예약된 시각에 깨어나 한 번 더 확인하고 부른다. */
class WalkReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DaengsApp ?: return Result.failure()
        val starts = walkStarts(app) ?: return Result.success()
        val now = LocalDateTime.now()
        val window = withinWalkReminderWindow(starts, now.toLocalDate())
        // **예약한 뒤에 나갔을 수 있다.** 여기서 다시 보는 이유가 그것이다 — 예약은 몇
        // 시간 전에 걸렸고, 그 사이에 산책이 끝났으면 이 알림은 거짓말이 된다.
        val walkedToday = window.any { it.toLocalDate() == now.toLocalDate() }
        val at = walkReminderTime(window.map(LocalDateTime::toLocalTime))

        if (shouldRemindNow(now, at, walkedToday)) {
            postDaengsNotice(
                context = applicationContext,
                channelId = WALK_REMINDER_CHANNEL_ID,
                id = WALK_REMINDER_NOTICE_ID,
                title = WALK_REMINDER_TITLE,
                text = WALK_REMINDER_TEXT,
                extras = mapOf(EXTRA_OPEN_WALK to "1"),
            )
        }
        // 다음 차례를 여기서도 걸어 둔다. 계획 작업만 믿으면 그것이 밀린 날은 하루 빈다.
        scheduleWalkReminderCheck(
            applicationContext,
            ChronoUnit.MILLIS.between(now, nextWalkReminderCheck(now, at)),
        )
        return Result.success()
    }
}

/**
 * 산책 시작 시각들. 읽을 수 없으면 `null`.
 *
 * ⚠️ **로그인하지 않았으면 안 읽는다.** 세션 행의 `ownerId` 는 로그인 전에 빈 문자열이라
 * (`WalkSessionRow` 기본값), 빈 소유자로 거르면 **다른 사람이 이 기기에서 걸은 산책과
 * 섞인다.** 기기를 물려받았거나 계정을 바꾼 경우다.
 *
 * 너무 짧아 산책으로 안 치는 세션은 여기서 걸러 낼 것이 없다 — 산책이 끝날 때
 * `WalkHistory.keepIfWalk` 가 이미 지운다(50m · 60초 미만, 기록을 남긴 것은 제외).
 */
private suspend fun walkStarts(app: DaengsApp): List<LocalDateTime>? {
    val owner = app.tokenStore.load()?.appUserId?.takeIf { it.isNotBlank() } ?: return null
    val zone = ZoneId.systemDefault()
    return app.walkEntryDao.finishedSessions()
        .filter { it.ownerId == owner }
        .map { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startedAtMillis), zone) }
}

/**
 * 계획 작업을 걸어 둔다. `DaengsApp.onCreate` 에서 부른다.
 *
 * **[ExistingPeriodicWorkPolicy.KEEP] 이다.** 앱을 열 때마다 다시 넣으면 주기가 매번
 * 처음부터 세어져서, 자주 여는 사람에게는 영원히 안 돌 수 있다.
 */
fun scheduleWalkReminderPlan(context: Context) {
    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
        PLAN_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<WalkReminderPlanWorker>(PLAN_PERIOD_HOURS, TimeUnit.HOURS).build(),
    )
}

/**
 * 부를 시각에 확인 작업을 예약한다.
 *
 * **[ExistingWorkPolicy.REPLACE] 다.** 계획이 다시 서면(산책이 쌓여 평소 시각이 옮겨졌다)
 * 옛 예약은 틀린 시각이다. 돌고 있는 자신을 취소하는 문제는 없다 — 이 이름으로 도는 것은
 * [WalkReminderWorker] 이고, 그것이 자기 일을 **다 끝낸 뒤에** 이 함수를 부른다.
 */
internal fun scheduleWalkReminderCheck(context: Context, delayMillis: Long) {
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        CHECK_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<WalkReminderWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build(),
    )
}

/** 한 자리만 쓴다. 어제 안 누른 알림 위에 오늘 것이 덮인다 — 쌓아 둘 말이 아니다. */
internal const val WALK_REMINDER_NOTICE_ID = 0x7DA1

/** 알림이 `MainActivity` 에 실어 보내는 것. 산책 화면으로 데려간다. */
const val EXTRA_OPEN_WALK = "com.daengs.app.walk.OPEN_WALK"

private const val PLAN_WORK_NAME = "walk-reminder-plan"
private const val CHECK_WORK_NAME = "walk-reminder-check"

/**
 * 계획 작업의 주기. **하루 두 번이다.**
 *
 * 하루 한 번이면 그 한 번이 밀린 날(기기가 절전에 들어가 있었다) 그날의 예약이 아예
 * 없어진다. 계산만 하는 짧은 작업이라 두 번이 비싸지 않다.
 */
private const val PLAN_PERIOD_HOURS = 12L
