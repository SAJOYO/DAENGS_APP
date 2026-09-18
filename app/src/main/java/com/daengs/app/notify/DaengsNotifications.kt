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
 * 「분석 결과」 채널.
 *
 * **기다리던 것이 준비됐다고 한 번 알리는** 알림들이 여기 있다 — 보행 분석 완료와 산책
 * 일기 장면. 산책 기록(`walk_tracking`)과는 다른 채널이다: 저건 산책 내내 떠 있는 진행
 * 알림이라 소리가 없어야 하고, 이건 한 번 부르는 것이다. `WalkTrackingService` 의 채널
 * 주석에 있는 그 규칙이다 — **한 채널에 묶으면 사용자가 둘 중 하나만 끄지 못한다.**
 */
const val ANALYSIS_CHANNEL_ID = "analysis_result"

/**
 * 옛 채널 id.
 *
 * 보행 분석만 있을 때는 `gait_analysis` 였다. 산책 일기 장면이 **같은 성격으로 같은
 * 채널에 들어오면서** 그 이름이 하는 일과 어긋났고, 이 저장소는 그것을 안 둔다
 * (`BottomTab.Storage` 주석: *"이름표와 하는 일이 어긋난 채로 두지 않는다"*).
 *
 * **아직 출시 전이라 지워도 잃을 사용자 설정이 없다.** 출시 뒤라면 이렇게 못 한다 —
 * 채널을 지우면 사용자가 거기에 해 둔 설정(소리·중요도)이 같이 없어진다.
 */
const val LEGACY_GAIT_CHANNEL_ID = "gait_analysis"

/**
 * 「분석 결과」 알림을 띄운다. 실제로 띄웠으면 `true`.
 *
 * @param id 알림 자리. 같은 것에 대한 알림은 같은 자리에 덮어쓴다.
 * @param extras `MainActivity` 가 읽을 것. 눌렀을 때 **어디로 갈지**를 이것으로 정한다.
 */
fun postAnalysisNotice(
    context: Context,
    id: Int,
    title: String,
    text: String,
    extras: Map<String, String?> = emptyMap(),
): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    ensureAnalysisChannel(context)

    val open = PendingIntent.getActivity(
        context,
        id,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { extras.forEach { (key, value) -> putExtra(key, value) } },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(context, ANALYSIS_CHANNEL_ID)
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

/** 채널을 만들고 **옛 채널을 지운다.** 지우는 이유는 [LEGACY_GAIT_CHANNEL_ID] 참고. */
fun ensureAnalysisChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
        NotificationChannel(
            ANALYSIS_CHANNEL_ID,
            "분석 결과",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "보행 분석과 산책 일기 장면이 준비되면 알려 드립니다." },
    )
    manager.deleteNotificationChannel(LEGACY_GAIT_CHANNEL_ID)
}

/**
 * 안드로이드의 **이 앱 알림 설정**을 연다. 종 아이콘이 여기로 간다.
 *
 * 앱 안에 스위치 화면을 따로 두지 않는다. 지금 채널이 [ANALYSIS_CHANNEL_ID] 하나인데,
 * 안드로이드 설정이 이미 그 채널을 이름까지 보여 주며 켜고 끄게 한다. 앱에 스위치를 또
 * 두면 **둘이 어긋난 상태**(앱은 켬, 시스템은 끔)를 만들 수 있다 — 그러면 사용자는
 * 켜져 있는 스위치를 보면서 알림을 못 받는다.
 *
 * 채널이 여럿이 되면 그때 앱 화면이 값을 한다 (`docs/home-ux-and-notifications.md` 6절).
 */
fun notificationSettingsIntent(context: Context): Intent =
    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
