package com.daengs.app.dogcard.photo

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.daengs.app.DaengsApp
import com.daengs.app.notify.postResultNotice
import kotlinx.coroutines.delay

/**
 * 포토 카드가 완성됐는지를 **앱 밖에서** 지켜보는 자리.
 *
 * ### 왜 필요한가
 *
 * 서버가 그림을 그리는 데 30~60초가 걸린다(만들기 화면도 "1분쯤 걸려요" 라고 말한다).
 * 그 사이 폴링은 `MainActivity` 의 `repeatOnLifecycle(STARTED)` 안에서 도는데, **앱을
 * 내리면 같이 멈춘다.** 그래서 만들기 화면의 「다 되면 알려 주세요」 버튼이 사실은
 * 화면만 닫았다 — **알려 줄 사람이 없었다.** 이 Worker 가 그 약속을 지킨다.
 *
 * 보행 분석([com.daengs.app.gait.work.GaitAnalysisWorker])과 같은 모양이고 같은 이유다.
 * 다른 점은 둘뿐이다:
 *
 *  - **조회를 더 뜸하게 한다** ([POLL_INTERVAL_MILLIS]). 화면이 떠 있으면 거기서 5초마다
 *    이미 묻고 있어서, 여기까지 5초면 서버에 두 배로 묻는다. 이쪽의 일은 애니메이션이
 *    아니라 알림 하나라 15초면 충분하다
 *  - **실패도 알린다.** 기다리던 사람에게 아무 말도 안 하면 계속 기다린다. 다만 서버의
 *    `error_code` 는 안 띄운다 — 운영 진단용 값이다
 *
 * ⚠️ **앱이 앞에 있어도 띄운다.** 만들기 화면을 보고 있으면 뒤집기 애니메이션과 알림이
 * 같이 온다. 보행 분석도 그렇게 돈다 — 앞/뒤를 따져 가리면, 화면은 떠 있지만 다른 탭을
 * 보고 있던 사람이 완성을 모르고 지나간다.
 */
class PhotoCardWatcher(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DaengsApp ?: return Result.failure()
        val cardId = inputData.getString(KEY_CARD_ID) ?: return Result.failure()
        if (!HttpPhotoCardRemote.configured) return Result.success()

        var waited = 0L
        while (waited < POLL_BUDGET_MILLIS) {
            // **매번 새로 받는다.** access token 이 5분이라 한 번의 지켜보기 안에서도
            // 만료된다 (보행 분석 Worker 와 같은 사정).
            val session = app.sessionProvider.freshSession()
            if (session == null) {
                // 로그아웃했으면 기다릴 이유가 없다. 토큰을 못 살린 것뿐이면 다시 해 본다.
                if (app.tokenStore.load() == null) return Result.success()
            } else {
                val card = HttpPhotoCardRemote.get(session.accessToken, cardId).getOrNull()?.card
                when (card?.status) {
                    PhotoCardStatus.Ready -> {
                        notify(READY_TITLE, READY_TEXT, cardId)
                        return Result.success()
                    }
                    PhotoCardStatus.Failed -> {
                        notify(FAILED_TITLE, FAILED_TEXT, cardId)
                        return Result.success()
                    }
                    // 아직 그리는 중이거나(Generating) 조회가 실패했다. 다음 차례에 다시.
                    else -> Unit
                }
            }
            delay(POLL_INTERVAL_MILLIS)
            waited += POLL_INTERVAL_MILLIS
        }
        // 상한에 닿았다. **조용히 손을 든다** — 서버에서는 끝났을 수도 있어서 여기서
        // "실패했어요" 라고 하면 거짓말이 된다. 도감 목록에는 결과가 있다.
        return Result.success()
    }

    private fun notify(title: String, text: String, cardId: String) {
        postResultNotice(
            context = applicationContext,
            id = photoCardNoticeId(cardId),
            title = title,
            text = text,
            // 도감으로 데려간다. 완성된 카드를 뒤집는 자리가 거기다 (`PhotoRevealLog`).
            extras = mapOf(EXTRA_OPEN_PHOTO_CARD to cardId),
        )
    }

    companion object {
        const val KEY_CARD_ID = "photo_card_id"

        /**
         * 조회 간격. 화면이 떠 있으면 거기서 5초마다 이미 묻는다 — 위 머리말 참고.
         */
        const val POLL_INTERVAL_MILLIS = 15_000L

        /**
         * 한 번의 실행에서 묻는 시간 상한. 서버가 30~60초라고 하지만 밀릴 수 있어서
         * 넉넉히 둔다. Worker 실행 한계(10분)에서 2분을 남긴다.
         */
        const val POLL_BUDGET_MILLIS = 8 * 60_000L

        private const val READY_TITLE = "포토 카드가 완성됐어요."
        private const val READY_TEXT = "도감에서 뒤집어 보세요."
        private const val FAILED_TITLE = "포토 카드를 만들지 못했어요."
        private const val FAILED_TEXT = "사진을 바꿔 다시 해 보세요."
    }
}

/** 알림이 `MainActivity` 에 실어 보내는 것. 도감으로 간다. */
const val EXTRA_OPEN_PHOTO_CARD = "com.daengs.app.dogcard.OPEN_PHOTO_CARD"

/**
 * 알림 자리.
 *
 * **접두사를 붙여 다른 알림과 가른다.** 보행 기록은 `recordId.hashCode()`, 산책 일기는
 * `"walk-diary:$sessionId"` 를 쓴다 — 셋 다 서버가 준 문자열 id 라 날것으로 해싱하면
 * 값이 겹칠 수 있고, 겹치면 한쪽이 다른 쪽을 덮어써서 **알림이 조용히 사라진다.**
 */
fun photoCardNoticeId(cardId: String): Int = "photo-card:$cardId".hashCode()

/**
 * 카드 하나를 지켜보게 한다. 만들기를 시작한 직후에 부른다.
 *
 * **[ExistingWorkPolicy.KEEP] 이다.** 같은 카드로 두 번 부르면(화면이 다시 조합되거나
 * 사용자가 되돌아왔거나) 이미 도는 것을 그대로 둔다 — `REPLACE` 면 지켜보던 것이
 * 취소되고 처음부터 세어, 완성을 아는 시점이 오히려 늦어진다.
 */
fun schedulePhotoCardWatch(context: Context, cardId: String) {
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        "photo-card:$cardId",
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<PhotoCardWatcher>()
            .setInputData(workDataOf(PhotoCardWatcher.KEY_CARD_ID to cardId))
            .build(),
    )
}
