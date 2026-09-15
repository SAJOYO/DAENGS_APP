package com.daengs.app.gait.work

import com.daengs.app.gait.GaitStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 기록 상태를 **한 번** 물어본 결과. */
internal sealed interface GaitCheck {
    /** 서버가 준 상태 ([GaitStatus] 의 값). */
    data class Status(val value: String) : GaitCheck

    /** 이번에는 못 물었다 — 연결 실패 · 토큰 갱신 실패. 다음 차례에 다시 묻는다. */
    data object Missed : GaitCheck

    /** 로그아웃됐다. 더 지켜볼 이유가 없다. */
    data object SignedOut : GaitCheck
}

/** [watchUntilSettled] 가 끝난 이유. */
internal sealed interface GaitWatchOutcome {
    /** 서버가 끝을 냈다 — [GaitStatus.DONE] 또는 [GaitStatus.FAILED]. */
    data class Settled(val status: String) : GaitWatchOutcome

    data object SignedOut : GaitWatchOutcome

    /** 정해 둔 시간 안에 안 끝났다. 서버에서는 계속 도는 중일 수 있다. */
    data object OutOfTime : GaitWatchOutcome
}

/**
 * 분석이 끝날 때까지 **한 자리에서** [intervalMillis] 마다 묻는다.
 *
 * ### 왜 한 번의 실행 안에서 도나
 *
 * 예전에는 한 번 묻고 아직이면 `Result.retry()` 로 끝낸 뒤 다음 실행을 WorkManager 에
 * 맡겼다. 그 "다음 실행" 은 앱 프로세스 안 타이머와 JobScheduler 둘에 걸리는데, 앞엣것은
 * **앱이 살아 돌 때만** 제시간에 울리고 뒤엣것은 마감이 없어 시스템이 미룰 수 있다. 그래서
 * 앱이 앞에 있으면 10초 간격이 지켜지고, 홈으로 나가면 몇 분씩 밀렸다가 앱을 다시 열어야
 * 완료를 알았다 (실기기 Galaxy S26 에서 13초 영상도 3분 넘게 알림이 안 왔다).
 *
 * 실행 중인 잡은 시스템이 멈춰 세우지 않으므로, 한 번 시작된 실행 안에서 계속 물으면
 * 앱이 뒤로 가도 간격이 지켜진다.
 *
 * ### 시간 상한
 *
 * 다음 조회까지 기다리면 [budgetMillis] 를 넘기게 되는 순간 [GaitWatchOutcome.OutOfTime]
 * 으로 끝낸다. **상한을 넘겨서 새 조회를 시작하지 않는다** — Worker 실행에는 시스템
 * 한계(10분)가 있고, 거기 닿아 끊기면 알림을 띄울 기회도 같이 사라진다.
 *
 * 조회 중에 던진 예외는 [GaitCheck.Missed] 로 친다 (취소는 그대로 올린다). 연결이 한 번
 * 끊겼다고 지켜보기를 그만두면, 끝난 분석을 모르는 채 남는다.
 *
 * @param elapsedMillis 이 지켜보기를 시작한 뒤 흐른 시간. 테스트가 가상 시계를 넣는다
 * @param check 한 번 묻는다. 인자는 1부터 세는 조회 차례 (로그용)
 */
internal suspend fun watchUntilSettled(
    budgetMillis: Long,
    intervalMillis: Long,
    elapsedMillis: () -> Long,
    check: suspend (attempt: Int) -> GaitCheck,
): GaitWatchOutcome {
    var attempt = 0
    while (true) {
        attempt++
        val step = try {
            check(attempt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            GaitCheck.Missed
        }

        when (step) {
            is GaitCheck.Status ->
                if (GaitStatus.settled(step.value)) return GaitWatchOutcome.Settled(step.value)
            GaitCheck.Missed -> Unit
            GaitCheck.SignedOut -> return GaitWatchOutcome.SignedOut
        }

        if (elapsedMillis() + intervalMillis > budgetMillis) return GaitWatchOutcome.OutOfTime
        delay(intervalMillis)
    }
}
