package com.daengs.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.daengs.app.MainActivity
import com.daengs.app.R
import com.daengs.app.ui.theme.DaengsColors

/**
 * 알림 하나를 띄우는 공용 자리.
 *
 * `GaitAnalysisWorker.notify()` 가 혼자 들고 있던 것을 빼냈다. 그 함수에는 **실기기에서
 * 한 번씩 데인 뒤에 생긴 규칙들**이 모여 있어서, 두 번째 알림을 붙이는 사람이 그것을
 * 다시 겪게 둘 이유가 없다:
 *
 *  - **권한을 먼저 본다.** 꺼져 있으면 `notify` 가 조용히 버려지는데, 그러면 "띄웠다"는
 *    로그만 남고 아무것도 안 뜬 것을 나중에 따라가게 된다
 *  - **`CLEAR_TOP` 을 쓰지 않는다.** 그걸 주면 액티비티가 다시 만들어지면서 보던 화면이
 *    홈으로 초기화된다 — 챗에서 기다리던 사람이 알림을 눌렀는데 챗이 사라졌다
 *    (에뮬레이터에서 실제로 그랬다). `SINGLE_TOP` 은 앱을 **앞으로 데려오기만** 한다
 *  - **id 를 부르는 쪽이 정한다.** 같은 것에 대한 알림이 두 번 뜨지 않게, 기록마다 한
 *    자리를 잡아 덮어쓴다
 *  - ⛔ **색은 앱 테마에서만 가져온다** (`docs/design-locks.md` 0절)
 */

/**
 * 「완성 알림」 채널.
 *
 * **기다리던 것이 준비됐다고 한 번 알리는** 알림들이 여기 있다 — 보행 분석 · 산책 일기
 * 장면 · 포토 카드. 셋 다 서버가 분 단위로 만드는 것이라 사용자가 기다리다 앱을 나간다.
 *
 * 산책 기록(`walk_tracking`)과는 다른 채널이다: 저건 산책 내내 떠 있는 진행 알림이라
 * 소리가 없어야 하고, 이건 한 번 부르는 것이다. `WalkTrackingService` 의 채널 주석에
 * 있는 그 규칙이다 — **한 채널에 묶으면 사용자가 둘 중 하나만 끄지 못한다.**
 */
const val RESULT_CHANNEL_ID = "result_ready"

/**
 * 지워야 할 옛 채널 id 들.
 *
 * 이름이 두 번 좁았다. 보행 분석만 있을 때는 `gait_analysis` 였고, 산책 일기가 들어오며
 * `analysis_result` 가 됐다. 그런데 포토 카드는 **분석이 아니라 생성**이라 그 이름도
 * 좁아졌다 — 이 저장소는 이름표와 하는 일이 어긋난 채로 두지 않는다
 * (`BottomTab.Storage` 주석).
 *
 * ⚠️ **지우면 사용자가 그 채널에 해 둔 설정이 없어진다.** 소리·중요도·켬/끔은 앱이 아니라
 * 사용자 것이어서, **id 가 바뀌면 새 채널이고 기본값에서 다시 시작한다.** 껐던 사람은
 * 다시 켜진 상태가 된다 — "안 온다" 가 아니라 **"끈 것 같은데 또 온다"** 로 나타난다.
 *
 * 그래도 지우는 이유는 **지금이 비공개 테스트라서**다 (2026-09-19 기준 versionCode 10,
 * 테스터 12명). 영향을 받으려면 ① 보행 분석을 돌려 채널이 만들어졌고 ② 안드로이드 알림
 * 설정에서 그 채널을 직접 손댔어야 한다. **정식 출시 뒤라면 이 판단이 달라진다** — 그때는
 * 옛 채널을 남기고 새 것만 더하거나, id 를 그대로 두고 이름만 바꾼다(같은 id 로
 * `createNotificationChannel` 을 다시 부르면 이름과 설명은 갱신된다).
 *
 * 되돌릴 길도 **같은 id 뿐이다.** 지운 채널을 같은 id 로 다시 만들면 안드로이드가 옛
 * 설정을 되살려 주는데, 우리는 id 를 바꿨으니 그 길이 없다.
 */
val LEGACY_CHANNEL_IDS = listOf("gait_analysis", "analysis_result")

/**
 * 「산책 알림」 채널.
 *
 * **성격이 다르니 채널을 가른다.** 분석 결과는 사용자가 기다리던 것이고, 이건 **앱이 먼저
 * 말을 거는 것**이다. 부탁하지 않은 말은 끄고 싶을 수 있고, 그때 분석 결과까지 같이
 * 꺼지면 안 된다 — 한 채널에 묶으면 사용자가 둘 중 하나만 끄지 못한다.
 */
const val WALK_REMINDER_CHANNEL_ID = "walk_reminder"

/**
 * 「완성 알림」을 띄운다. 실제로 띄웠으면 `true`.
 *
 * @param id 알림 자리. 같은 것에 대한 알림은 같은 자리에 덮어쓴다.
 * @param extras `MainActivity` 가 읽을 것. 눌렀을 때 **어디로 갈지**를 이것으로 정한다.
 */
fun postResultNotice(
    context: Context,
    id: Int,
    title: String,
    text: String,
    extras: Map<String, String?> = emptyMap(),
): Boolean = postDaengsNotice(context, RESULT_CHANNEL_ID, id, title, text, extras)

/**
 * 채널을 골라 알림을 띄운다. 실제로 **시스템 알림을** 띄웠으면 `true`.
 *
 * ⚠️ **돌려주는 값이 `false` 여도 사라진 것은 아니다.** 앱의 알림함([NoticeInbox])에는
 * 먼저 적는다 — 권한을 안 줬거나 채널을 껐어도 종을 누르면 볼 수 있다. 그 순서가
 * 뒤집히면 "알림을 껐으니 아무것도 없다" 가 되는데, 사용자가 끈 것은 *시스템이 부르지
 * 마라* 였고 *앱에서도 숨겨라* 가 아니다.
 */
fun postDaengsNotice(
    context: Context,
    channelId: String,
    id: Int,
    title: String,
    text: String,
    extras: Map<String, String?> = emptyMap(),
): Boolean {
    NoticeInbox.add(
        context,
        DaengsNotice(
            id = id,
            channelId = channelId,
            title = title,
            text = text,
            atMillis = System.currentTimeMillis(),
            // null 인 값은 안 나른다. 들고 가도 갈 곳을 못 정한다.
            extras = extras.filterValues { it != null }.mapValues { it.value!! },
        ),
    )
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    ensureDaengsChannels(context)

    val open = PendingIntent.getActivity(
        context,
        id,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { extras.forEach { (key, value) -> putExtra(key, value) } },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_walk_notification)
        .setContentTitle(title)
        .setContentText(text)
        // ⛔ 색은 앱 테마에서만 가져온다 (`docs/design-locks.md` 0절).
        .setColor(DaengsColors.BrandPrimary.toArgb())
        .setContentIntent(open)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()

    return runCatching {
        NotificationManagerCompat.from(context).notify(id, notification)
    }.isSuccess
}

/** 채널을 만들고 **옛 채널을 지운다.** 지우는 이유는 [LEGACY_CHANNEL_IDS] 참고. */
fun ensureDaengsChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
        NotificationChannel(
            RESULT_CHANNEL_ID,
            "완성 알림",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "보행 분석·산책 일기·포토 카드가 준비되면 알려 드립니다." },
    )
    manager.createNotificationChannel(
        NotificationChannel(
            WALK_REMINDER_CHANNEL_ID,
            "산책 알림",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "평소 나가는 시각이 지났는데 아직 안 나갔으면 알려 드립니다." },
    )
    LEGACY_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
}

/**
 * 안드로이드의 **이 앱 알림 설정**을 연다. 종 아이콘이 여기로 간다.
 *
 * 앱 안에 스위치 화면을 따로 두지 않는다. 안드로이드 설정이 이미 채널을 **이름까지
 * 보여 주며** 켜고 끄게 한다 (실기기에서 「분석 결과」 · 「산책 알림」 · 「산책 기록」으로
 * 뜨는 것을 확인했다). 앱에 스위치를 또 두면 **둘이 어긋난 상태**(앱은 켬, 시스템은 끔)를
 * 만들 수 있고, 그러면 사용자는 켜져 있는 스위치를 보면서 알림을 못 받는다.
 *
 * 앱 화면이 값을 하는 것은 **시스템에 없는 것**을 둘 때다 — 알림함이나, 채널 하나 안에서
 * 더 잘게 고르는 것 (`docs/home-ux-and-notifications.md` 6절).
 */
fun notificationSettingsIntent(context: Context): Intent =
    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
