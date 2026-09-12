package com.daengs.app.gait.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.daengs.app.DaengsApp
import com.daengs.app.MainActivity
import com.daengs.app.R
import com.daengs.app.gait.GaitApi
import com.daengs.app.gait.GaitStatus
import com.daengs.app.ui.theme.DaengsColors
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * 분석이 끝났는지를 **앱 밖에서** 지켜보는 자리 (#220).
 *
 * ### 앱이 분석하는 게 아니다
 *
 * 분석은 저쪽 `gait-worker` 가 앱과 무관하게 한다. 여기서 하는 일은 **상태를 물어보고
 * 끝났으면 알려 주는 것**뿐이다. 그래서 영상 바이트도, 모델도, 배터리 많이 먹는 것도
 * 여기 없다 — 요청 한 번이 전부다.
 *
 * ### 왜 화면에서 떼어냈나
 *
 * 예전에는 [com.daengs.app.gait.HttpGaitAnalyzer] 가 `DONE` 이 될 때까지 붙잡고
 * 기다렸다. 분석이 분 단위라(24초 영상에 약 2분 실측) 셋이 어긋났다:
 *
 *  - **앱을 나가면 결과를 못 받는다** — 폴링 코루틴이 같이 죽는데 서버는 끝낸다
 *  - **"보행 서버에 닿지 못했어요"** — 서버가 바빠 응답이 30초를 넘기면 그렇게 보였다.
 *    죽은 게 아니라 일하는 중이었다
 *  - **체감 지연** — 30초 영상이 "8분째" 처럼 보였다
 *
 * ### 되풀이는 [BackoffPolicy.LINEAR] 다
 *
 * 지수(기본값)로 두면 `10 → 20 → 40 → 80` 이라 누적 150초다. 분석이 120초쯤이니
 * **끝난 것을 30초 늦게 안다.** 선형이면 `10 → 20 → 30 → 40` 으로 같은 구간을 더
 * 촘촘히 훑는다. WorkManager 최소 backoff 가 10초라 그보다 잦게는 못 한다.
 *
 * ### 끝이 없는 되풀이는 두지 않는다
 *
 * `Result.retry()` 는 스스로 멈추지 않는다 — [runAttemptCount] 를 직접 봐야 한다.
 * [MAX_ATTEMPTS] 는 옛 foreground 폴링의 10분 상한과 같은 뜻이다. 거기 닿으면
 * **조용히 손을 든다**: 알림을 띄우지 않는다. 서버에서는 끝났을 수도 있고, 그때
 * "실패했어요" 라고 하면 거짓말이 된다. 목록에는 결과가 있다.
 */
class GaitAnalysisWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DaengsApp ?: return Result.failure()
        val recordId = inputData.getString(KEY_RECORD_ID) ?: return Result.failure()
        if (!GaitApi.configured) return finished(null)

        // **매번 새로 받는다.** access token 이 5분이라 2분짜리 분석 하나에도 중간에
        // 만료될 수 있다 ([com.daengs.app.gait.HttpGaitAnalyzer] 폴링과 같은 사정).
        val session = app.sessionProvider.freshSession()
            ?: return if (app.tokenStore.load() == null) {
                // 로그아웃했다. 기다릴 이유가 없다.
                finished(null)
            } else {
                // 토큰을 못 살렸을 뿐이다. 다음에 다시 해 본다.
                retryOrGiveUp()
            }

        return try {
            val record = GaitApi.record(session.accessToken, recordId).getOrNull()
                ?: return retryOrGiveUp()

            when {
                record.status == GaitStatus.DONE -> {
                    notify(app, DONE_TITLE, DONE_TEXT, recordId)
                    finished(GaitStatus.DONE)
                }
                // **실패도 알린다.** 기다리던 사람에게 아무 말도 안 하면 계속 기다린다.
                // 다만 저쪽 failure_reason 은 안 띄운다 — 운영 진단용이라 내부 경로가
                // 들어 있을 수 있다.
                record.status == GaitStatus.FAILED -> {
                    notify(app, FAILED_TITLE, FAILED_TEXT, recordId)
                    // 서버가 끝을 냈으니 우리 일도 끝이다. 되풀이할 것이 없다.
                    finished(GaitStatus.FAILED)
                }
                else -> retryOrGiveUp()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            retryOrGiveUp()
        }
    }

    /** 상한에 닿았으면 조용히 끝낸다. 위 머리말의 "거짓말이 된다" 참고. */
    private fun retryOrGiveUp(): Result =
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) finished(null) else Result.retry()

    /**
     * 끝났다고 알리면서 **무엇으로 끝났는지**를 같이 남긴다.
     *
     * `Result.success()` 만으로는 화면이 구분을 못 한다 — 분석이 `DONE` 이라 끝난 것과
     * 상한에 닿아 손을 든 것이 같은 값이기 때문이다. 앞은 결과 카드를 띄워야 하고
     * 뒤는 "아직" 이라고 둬야 해서, 여기서 갈라 준다. `null` 이면 모르는 채로 끝난 것.
     */
    private fun finished(status: String?): Result =
        Result.success(workDataOf(KEY_STATUS to status))

    /**
     * 완료 알림.
     *
     * **id 를 `recordId` 로 잡는다.** 같은 기록의 알림이 두 번 뜨지 않게 하려는 것이다 —
     * Worker 가 어떤 이유로 다시 돌아도 같은 자리에 덮어쓴다.
     */
    private fun notify(context: Context, title: String, text: String, recordId: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            recordId.hashCode(),
            // **CLEAR_TOP 을 쓰지 않는다.** 그걸 주면 액티비티가 다시 만들어지면서
            // 보던 화면이 홈으로 초기화된다 — 챗에서 기다리던 사람이 알림을 눌렀는데
            // 챗이 사라진다(에뮬레이터에서 실제로 그랬다). SINGLE_TOP 은 이미 떠 있는
            // 것을 그대로 살리므로, 알림은 **앱을 앞으로 데려오기만** 한다.
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walk_notification)
            .setContentTitle(title)
            .setContentText(text)
            // ⛔ 색은 앱 테마에서만 가져온다 (`docs/design-locks.md` 0절).
            .setColor(DaengsColors.BrandPrimary.toArgb())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(recordId.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        // 산책 기록(`walk_tracking`)과 **다른 채널이다.** 산책은 계속 떠 있는 진행 알림이라
        // 소리가 없어야 하지만, 보행 완료는 기다리던 사람에게 한 번 알리는 것이라 성격이
        // 다르다. 한 채널에 묶으면 사용자가 둘 중 하나만 끄지 못한다.
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "보행 분석 완료",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "보행 영상 분석이 끝나면 알려 드립니다." },
            )
    }

    companion object {
        const val KEY_RECORD_ID = "record_id"

        /** 출력 데이터의 열쇠. 값은 [GaitStatus] 의 것이거나, 모르는 채 끝났으면 없다. */
        const val KEY_STATUS = "status"

        /** 알림 채널. 산책(`walk_tracking`)과 가른 이유는 [ensureChannel] 참고. */
        const val CHANNEL_ID = "gait_analysis"

        /**
         * 되풀이 상한. 선형 10초 backoff 로 `10+20+…+150` ≈ **20분**이다.
         * 옛 foreground 폴링의 10분보다 넉넉한데, 앱이 잠든 동안 시스템이 미루는 몫을
         * 감안한 것이다. 그래도 끝은 있다.
         */
        const val MAX_ATTEMPTS = 15

        /** 첫 확인까지. 분석이 분 단위라 바로 물어봐야 "아직" 이라는 답만 받는다. */
        const val INITIAL_DELAY_SECONDS = 20L

        /** WorkManager 가 허용하는 최소 backoff 가 10초다. 그보다 잦게는 못 한다. */
        const val BACKOFF_SECONDS = 10L

        private const val DONE_TITLE = "보행 분석이 완료되었어요."
        private const val DONE_TEXT = "결과를 확인해 보세요."
        private const val FAILED_TITLE = "보행 분석을 마치지 못했어요."
        private const val FAILED_TEXT = "잠시 뒤에 다시 시도해 주세요."

        /** 한 기록에 하나. 같은 영상으로 Worker 가 둘 생기지 않게 하는 열쇠다. */
        fun workName(recordId: String): String = "gait:$recordId"

        fun request(recordId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<GaitAnalysisWorker>()
                .setInputData(workDataOf(KEY_RECORD_ID to recordId))
                .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
    }
}

/**
 * 기록 하나의 완료를 지켜보게 한다.
 *
 * **[ExistingWorkPolicy.KEEP] 이다.** 같은 기록으로 두 번 부르면(화면이 다시 조합되거나
 * 사용자가 되돌아왔거나) 이미 도는 것을 그대로 둔다. `REPLACE` 로 두면 지켜보던 것이
 * 취소되고 처음부터 다시 세어, 완료를 아는 시점이 오히려 늦어진다.
 */
fun scheduleGaitAnalysisWatch(context: Context, recordId: String) {
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        GaitAnalysisWorker.workName(recordId),
        ExistingWorkPolicy.KEEP,
        GaitAnalysisWorker.request(recordId),
    )
}
