package com.daengs.app.gait.work

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.daengs.app.DaengsApp
import com.daengs.app.gait.GaitApi
import com.daengs.app.gait.GaitStatus
import com.daengs.app.notify.postAnalysisNotice
import java.util.concurrent.TimeUnit

/**
 * 분석이 끝났는지를 **앱 밖에서** 지켜보는 자리 (#220).
 *
 * ### 앱이 분석하는 게 아니다
 *
 * 분석은 저쪽 `gait-worker` 가 앱과 무관하게 한다. 여기서 하는 일은 **상태를 물어보고
 * 끝났으면 알려 주는 것**뿐이다. 그래서 영상 바이트도, 모델도, 배터리 많이 먹는 것도
 * 여기 없다 — 짧은 GET 요청이 전부다.
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
 * ### 한 번 시작하면 그 실행 안에서 끝까지 묻는다
 *
 * 처음에는 20초 뒤 한 번 묻고 아직이면 `Result.retry()` 로 끝내는 식이었다. 그러면
 * **앱이 background 에 있을 때 다음 실행이 몇 분씩 밀렸다** — 실기기(Galaxy S26)에서
 * 13초 영상도 3분 넘게 알림이 없다가, 앱을 다시 열자마자 결과와 알림이 같이 왔다.
 * 이유와 지금 방식은 [watchUntilSettled] 에 적었다. 요약하면:
 *
 *  - **첫 지연을 두지 않는다.** 제출 직후는 사용자가 아직 챗 화면에 있을 때라, 이때
 *    시작해야 시스템이 미루지 않는다
 *  - 실행 하나 안에서 [POLL_INTERVAL_MILLIS] 마다 묻고, [POLL_BUDGET_MILLIS] 를 넘기면
 *    그때만 `Result.retry()` 한다
 *  - `retry()` 는 [MAX_ATTEMPTS] 번까지다. 거기 닿으면 **조용히 손을 든다**: 알림을
 *    띄우지 않는다. 서버에서는 끝났을 수도 있고, 그때 "실패했어요" 라고 하면 거짓말이
 *    된다. 목록에는 결과가 있다.
 *
 * ### 로그
 *
 * [LOG_TAG] 로 시작 · 조회마다 상태 · 끝남 · 알림 호출을 남긴다. 앱이 앞(`fg`)인지
 * 뒤(`bg(중요도)`)인지를 같이 적어, 실기기에서 background 간격이 지켜지는지 본다.
 * **토큰은 남기지 않는다.** 기록 id 도 앞 8자만 쓴다.
 */
class GaitAnalysisWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DaengsApp ?: return Result.failure()
        val recordId = inputData.getString(KEY_RECORD_ID) ?: return Result.failure()
        // 알림이 **어느 강아지의 기록인지** 같이 나른다. 알림으로 챗에 돌아왔을 때
        // 대표가 바뀌어 있으면 엉뚱한 아이의 대화에 카드가 붙는다.
        val petId = inputData.getString(KEY_PET_ID)
        if (!GaitApi.configured) return finished(null)

        val startedAt = SystemClock.elapsedRealtime()
        val trace = Trace(recordId.take(8), startedAt)
        trace.log("start attempt=${runAttemptCount + 1}/$MAX_ATTEMPTS")

        val outcome = watchUntilSettled(
            budgetMillis = POLL_BUDGET_MILLIS,
            intervalMillis = POLL_INTERVAL_MILLIS,
            elapsedMillis = { SystemClock.elapsedRealtime() - startedAt },
        ) { attempt ->
            // **매번 새로 받는다.** access token 이 5분이라 한 번의 지켜보기 안에서도
            // 만료될 수 있다 ([com.daengs.app.gait.HttpGaitAnalyzer] 폴링과 같은 사정).
            val session = app.sessionProvider.freshSession()
            val step = when {
                session != null -> GaitApi.record(session.accessToken, recordId).fold(
                    onSuccess = { GaitCheck.Status(it.status) },
                    onFailure = { GaitCheck.Missed },
                )
                // 로그아웃했다. 기다릴 이유가 없다.
                app.tokenStore.load() == null -> GaitCheck.SignedOut
                // 토큰을 못 살렸을 뿐이다. 다음 차례에 다시 해 본다.
                else -> GaitCheck.Missed
            }
            trace.log("check #$attempt ${step.label()}")
            step
        }

        return when (outcome) {
            is GaitWatchOutcome.Settled -> {
                trace.log("settled ${outcome.status}")
                // **실패도 알린다.** 기다리던 사람에게 아무 말도 안 하면 계속 기다린다.
                // 다만 저쪽 failure_reason 은 안 띄운다 — 운영 진단용이라 내부 경로가
                // 들어 있을 수 있다.
                val posted = if (outcome.status == GaitStatus.DONE) {
                    notify(app, DONE_TITLE, DONE_TEXT, recordId, petId)
                } else {
                    notify(app, FAILED_TITLE, FAILED_TEXT, recordId, petId)
                }
                trace.log(if (posted) "notify posted" else "notify skipped (disabled or failed)")
                finished(outcome.status)
            }
            GaitWatchOutcome.SignedOut -> {
                trace.log("signed out")
                finished(null)
            }
            GaitWatchOutcome.OutOfTime ->
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                    // 위 머리말의 "거짓말이 된다" 참고.
                    trace.log("out of time, giving up")
                    finished(null)
                } else {
                    trace.log("out of time, retry")
                    Result.retry()
                }
        }
    }

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
     * 완료 알림. 실제로 띄웠으면 `true`.
     *
     * **id 를 `recordId` 로 잡는다.** 같은 기록의 알림이 두 번 뜨지 않게 하려는 것이다 —
     * Worker 가 어떤 이유로 다시 돌아도 같은 자리에 덮어쓴다.
     */
    private fun notify(
        context: Context,
        title: String,
        text: String,
        recordId: String,
        petId: String?,
    ): Boolean = postAnalysisNotice(
        context = context,
        id = recordId.hashCode(),
        title = title,
        text = text,
        // 챗으로 데려가고, 어느 기록이 끝났는지도 같이 알린다. 화면을 나갔던 사람은
        // 대화가 서버 이력에서 다시 그려지는데 거기에는 보행 카드가 없어서, 이 둘이
        // 없으면 결과를 다시 붙일 근거가 없다.
        extras = mapOf(EXTRA_OPEN_GAIT_RECORD to recordId, EXTRA_OPEN_GAIT_PET to petId),
    )

    /** 한 번의 실행에 대한 타이밍 로그. 시작한 뒤 흐른 ms 와 앱이 앞/뒤인지를 붙인다. */
    private class Trace(private val shortId: String, private val startedAt: Long) {
        fun log(message: String) {
            Log.i(LOG_TAG, "[$shortId] +${SystemClock.elapsedRealtime() - startedAt}ms ${appState()} $message")
        }

        /**
         * 앱이 앞에 있나. `fg` 가 아니면 중요도 숫자를 같이 적는다 — background 에서
         * 잡이 도는 동안의 값이 기기마다 달라서, 추측하지 않고 그대로 본다.
         */
        private fun appState(): String {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return if (info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                "fg"
            } else {
                "bg(${info.importance})"
            }
        }
    }

    companion object {
        const val KEY_RECORD_ID = "record_id"

        /** 어느 강아지의 기록인가. 알림으로 챗에 돌아왔을 때 대표가 맞는지 본다. */
        const val KEY_PET_ID = "pet_id"

        /** 알림이 `MainActivity` 에 실어 보내는 것. 챗으로 가서 이 기록을 붙인다. */
        const val EXTRA_OPEN_GAIT_RECORD = "com.daengs.app.gait.OPEN_RECORD"
        const val EXTRA_OPEN_GAIT_PET = "com.daengs.app.gait.OPEN_PET"

        /** 출력 데이터의 열쇠. 값은 [GaitStatus] 의 것이거나, 모르는 채 끝났으면 없다. */
        const val KEY_STATUS = "status"

        /** logcat 태그. `adb logcat -s GaitWatch` 로 본다. */
        const val LOG_TAG = "GaitWatch"

        /**
         * 조회 간격. 옛 foreground 폴링(`HttpGaitAnalyzer.POLL_INTERVAL_MS`)과 같은 5초다.
         * 요청 하나가 짧은 GET 이고, 13초 영상도 수십 초면 끝나서 이보다 드물면 늦게 안다.
         */
        const val POLL_INTERVAL_MILLIS = 5_000L

        /**
         * 한 번의 실행에서 묻는 시간 상한. Worker 실행 한계(10분)에서 **2분을 남긴다** —
         * 상한 직전에 시작한 조회 하나가 연결 15초 + 읽기 30초까지 걸릴 수 있고, 토큰
         * 갱신과 알림까지 끝내야 한다.
         */
        const val POLL_BUDGET_MILLIS = 8 * 60_000L

        /**
         * 실행 횟수 상한. 한 번이 8분이라 세 번이면 **24분**을 지켜본다 — 예전 상한(약
         * 20분)과 같은 뜻이다. 그래도 끝은 있다.
         */
        const val MAX_ATTEMPTS = 3

        /** 상한을 넘겨 `retry()` 할 때만 쓴다. WorkManager 최소 backoff 가 10초다. */
        const val BACKOFF_SECONDS = 10L

        private const val DONE_TITLE = "보행 분석이 완료되었어요."
        private const val DONE_TEXT = "결과를 확인해 보세요."
        private const val FAILED_TITLE = "보행 분석을 마치지 못했어요."
        private const val FAILED_TEXT = "잠시 뒤에 다시 시도해 주세요."

        /** 한 기록에 하나. 같은 영상으로 Worker 가 둘 생기지 않게 하는 열쇠다. */
        fun workName(recordId: String): String = "gait:$recordId"

        /**
         * **첫 지연을 두지 않는다.** 머리말의 "한 번 시작하면" 참고 — 제출 직후 앱이 아직
         * 앞에 있을 때 시작해야 한다.
         */
        fun request(recordId: String, petId: String?): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<GaitAnalysisWorker>()
                .setInputData(workDataOf(KEY_RECORD_ID to recordId, KEY_PET_ID to petId))
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                // 챗에 다시 들어왔을 때 진행 중인 카드를 되살리려고 단다 ([pendingGaitRecords]).
                // `WorkInfo` 는 입력 데이터를 안 주고 tag 만 준다.
                .addTag(GaitWatchTags.record(recordId))
                .apply { if (petId != null) addTag(GaitWatchTags.pet(petId)) }
                .build()
    }
}

/** 로그에 남길 한 단어. 상태 값은 서버 것 그대로다. */
private fun GaitCheck.label(): String = when (this) {
    is GaitCheck.Status -> value
    GaitCheck.Missed -> "missed"
    GaitCheck.SignedOut -> "signed-out"
}

/**
 * 기록 하나의 완료를 지켜보게 한다.
 *
 * **[ExistingWorkPolicy.KEEP] 이다.** 같은 기록으로 두 번 부르면(화면이 다시 조합되거나
 * 사용자가 되돌아왔거나) 이미 도는 것을 그대로 둔다. `REPLACE` 로 두면 지켜보던 것이
 * 취소되고 처음부터 다시 세어, 완료를 아는 시점이 오히려 늦어진다.
 */
fun scheduleGaitAnalysisWatch(context: Context, recordId: String, petId: String?) {
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        GaitAnalysisWorker.workName(recordId),
        ExistingWorkPolicy.KEEP,
        GaitAnalysisWorker.request(recordId, petId),
    )
}
