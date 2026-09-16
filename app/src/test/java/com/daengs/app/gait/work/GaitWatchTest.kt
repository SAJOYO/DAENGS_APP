package com.daengs.app.gait.work

import com.daengs.app.gait.GaitStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 한 번의 실행 안에서 완료를 묻는 루프 ([watchUntilSettled]).
 *
 * **여기서 잡는 것은 "끝까지 묻되 상한은 넘지 않는다" 다.** 예전 구조(한 번 묻고
 * `retry()`)는 앱이 background 일 때 다음 실행이 밀려 완료 알림이 몇 분씩 늦었다. 루프가
 * 첫 "아직" 에 빠져나가거나, 연결 한 번 끊겼다고 그만두거나, 상한을 넘겨 새 조회를
 * 시작하면 여기가 깨진다.
 *
 * 시간은 코루틴 가상 시계로 잰다 — 실제로 5초씩 기다리지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GaitWatchTest {

    private val interval = 5_000L
    private val budget = 60_000L

    /** [answers] 를 차례로 돌려주고, 다 쓰면 마지막 것을 되풀이한다. */
    private suspend fun TestScope.watch(
        vararg answers: GaitCheck,
        calls: MutableList<Long> = mutableListOf(),
    ): GaitWatchOutcome = watchUntilSettled(
        budgetMillis = budget,
        intervalMillis = interval,
        elapsedMillis = { testScheduler.currentTime },
    ) { attempt ->
        calls += testScheduler.currentTime
        answers[minOf(attempt, answers.size) - 1]
    }

    @Test
    fun `DONE 을 보면 그 자리에서 끝낸다`() = runTest {
        val calls = mutableListOf<Long>()
        val got = watch(
            GaitCheck.Status(GaitStatus.PROCESSING),
            GaitCheck.Status(GaitStatus.PROCESSING),
            GaitCheck.Status(GaitStatus.DONE),
            calls = calls,
        )

        assertEquals(GaitWatchOutcome.Settled(GaitStatus.DONE), got)
        assertEquals("간격마다 한 번씩, 세 번 물었다", listOf(0L, 5_000L, 10_000L), calls)
    }

    @Test
    fun `FAILED 도 끝으로 친다`() = runTest {
        val got = watch(GaitCheck.Status(GaitStatus.FAILED))

        assertEquals(GaitWatchOutcome.Settled(GaitStatus.FAILED), got)
        assertEquals("첫 조회에서 바로 끝나면 기다리지 않는다", 0L, testScheduler.currentTime)
    }

    @Test
    fun `첫 조회는 기다리지 않는다`() = runTest {
        val calls = mutableListOf<Long>()
        watch(GaitCheck.Status(GaitStatus.DONE), calls = calls)

        assertEquals(listOf(0L), calls)
    }

    @Test
    fun `아직 올리는 중인 상태도 끝이 아니다`() = runTest {
        val got = watch(
            GaitCheck.Status(GaitStatus.PENDING),
            GaitCheck.Status(GaitStatus.UPLOADED),
            GaitCheck.Status(GaitStatus.DONE),
        )

        assertEquals(GaitWatchOutcome.Settled(GaitStatus.DONE), got)
    }

    @Test
    fun `못 물은 차례가 있어도 계속 묻는다`() = runTest {
        val got = watch(
            GaitCheck.Missed,
            GaitCheck.Missed,
            GaitCheck.Status(GaitStatus.DONE),
        )

        assertEquals("연결 한 번 끊겼다고 지켜보기를 그만두면 안 된다", GaitWatchOutcome.Settled(GaitStatus.DONE), got)
    }

    @Test
    fun `조회가 예외를 던져도 다음 차례에 다시 묻는다`() = runTest {
        var n = 0
        val got = watchUntilSettled(
            budgetMillis = budget,
            intervalMillis = interval,
            elapsedMillis = { testScheduler.currentTime },
        ) {
            n++
            if (n < 3) error("연결 끊김") else GaitCheck.Status(GaitStatus.DONE)
        }

        assertEquals(GaitWatchOutcome.Settled(GaitStatus.DONE), got)
        assertEquals(3, n)
    }

    @Test
    fun `로그아웃이면 바로 그만둔다`() = runTest {
        val got = watch(GaitCheck.SignedOut)

        assertEquals(GaitWatchOutcome.SignedOut, got)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `안 끝나면 상한에서 멈추고 상한을 넘겨 새로 묻지 않는다`() = runTest {
        val calls = mutableListOf<Long>()
        val got = watch(GaitCheck.Status(GaitStatus.PROCESSING), calls = calls)

        assertEquals(GaitWatchOutcome.OutOfTime, got)
        assertTrue("마지막 조회가 상한 안이어야 한다 (${calls.last()})", calls.last() <= budget)
        assertTrue("상한까지 기다리지 않고 다음 조회를 건너뛰었다", testScheduler.currentTime <= budget)
        // 0, 5, 10, …, 60초 — 60초 조회 뒤로는 다음 조회가 상한을 넘기므로 멈춘다.
        assertEquals(budget / interval + 1, calls.size.toLong())
    }

    // -- Worker 의 값 ---------------------------------------------------------

    /**
     * 한 번의 실행이 Worker 한계(10분)에 닿으면 알림을 띄우기 전에 끊길 수 있다.
     * 상한 직전에 시작한 조회 하나가 연결 15초 + 읽기 30초(`GaitApi`)까지 걸린다.
     */
    @Test
    fun `한 번의 실행은 Worker 한계 10분보다 넉넉히 짧다`() {
        val worstSingleCheck = 15_000L + 30_000L
        val margin = 10 * 60_000L - (GaitAnalysisWorker.POLL_BUDGET_MILLIS + worstSingleCheck)

        assertTrue("여유가 1분은 있어야 한다 (지금 ${margin}ms)", margin >= 60_000L)
    }

    @Test
    fun `조회 간격은 너무 잦지도 드물지도 않다`() {
        val every = GaitAnalysisWorker.POLL_INTERVAL_MILLIS
        assertTrue("너무 잦다 (${every}ms)", every >= 3_000L)
        assertTrue("짧은 영상이 끝난 걸 너무 늦게 안다 (${every}ms)", every <= 15_000L)
    }

    @Test
    fun `다 합쳐 예전만큼은 지켜본다`() {
        // 예전 구조의 상한이 약 20분이었다. 짧아지면 느린 분석을 모르는 채 손을 든다.
        val total = GaitAnalysisWorker.MAX_ATTEMPTS * GaitAnalysisWorker.POLL_BUDGET_MILLIS
        assertTrue("지금 ${total / 60_000}분", total >= 20 * 60_000L)
    }
}
