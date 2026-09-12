package com.daengs.app.gait

import android.app.Application
import android.net.Uri
import com.daengs.app.gait.work.GaitAnalysisWorker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * 접수만 하고 빠지는 계약 (#220).
 *
 * **여기서 잡는 것은 "기다리지 않는다" 다.** 예전에는 [GaitAnalyzer.analyze] 가 `DONE`
 * 이 될 때까지 붙잡고 있어서, 앱을 나가면 결과를 못 받고 서버가 바쁘면 "닿지 못했어요"
 * 가 떴다. 그 구조로 되돌아가면 이 테스트들이 먼저 깨진다.
 *
 * Worker 자체의 상태 전이는 WorkManager 런타임이 필요해 여기서 다루지 않는다 — 대신
 * **되풀이 상한과 backoff 셈**이 맞는지를 잡는다. 그게 틀리면 완료를 늦게 알거나
 * 영영 안다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GaitSubmitTest {

    private fun video(seconds: Int = 24) =
        PreparedVideo(Uri.EMPTY, seconds = seconds, thumbnail = null)

    // -- 접수 계약 ------------------------------------------------------------

    @Test
    fun `submit 은 결과가 아니라 접수증을 준다`() = runTest {
        val got = MockGaitAnalyzer(stepMillis = 0L).submit(video()).getOrThrow()

        assertTrue("기록 id 가 있어야 나중에 찾는다", got.recordId.isNotBlank())
        assertNotNull(got.status)
    }

    @Test
    fun `제목은 접수증에 실려 온다`() = runTest {
        val got = MockGaitAnalyzer(stepMillis = 0L).submit(video(), "저녁 산책").getOrThrow()

        assertEquals("저녁 산책", got.title)
    }

    @Test
    fun `빈 제목은 정하지 않은 것으로 친다`() = runTest {
        val got = MockGaitAnalyzer(stepMillis = 0L).submit(video(), "   ").getOrThrow()

        assertNull("공백만 있는 제목은 기본값으로 떨어져야 한다", got.title)
    }

    /**
     * **접수는 기다리지 않는다.**
     *
     * `MockGaitAnalyzer` 는 [GaitAnalyzer.analyze] 에서 단계마다 [MockGaitAnalyzer] 의
     * `stepMillis` 만큼 쉰다. 그 값을 크게 두고 `submit` 이 그 시간을 안 쓰는지 본다 —
     * 폴링을 다시 넣으면 여기가 걸린다.
     */
    @Test
    fun `submit 은 분석 단계를 기다리지 않는다`() = runTest {
        val analyzer = MockGaitAnalyzer(stepMillis = 60_000L)

        val before = testScheduler.currentTime
        analyzer.submit(video()).getOrThrow()
        val waited = testScheduler.currentTime - before

        assertEquals("접수에는 기다림이 없어야 한다", 0L, waited)
    }

    @Test
    fun `analyze 는 여전히 끝까지 기다린다 — 테스트가 쓰는 계약이다`() = runTest {
        val analyzer = MockGaitAnalyzer(stepMillis = 1_000L)

        val before = testScheduler.currentTime
        val record = analyzer.analyze(video(), onStage = {}).getOrThrow()
        val waited = testScheduler.currentTime - before

        assertTrue("네 단계를 다 거쳐야 한다", waited >= 4_000L)
        assertTrue(record.id.isNotBlank())
    }

    // -- 홀더 ---------------------------------------------------------------

    @Test
    fun `홀더 submit 은 목록을 건드리지 않는다`() = runTest {
        // 결과가 아직 없으므로 얹을 것도 없다. 여기서 목록에 넣으면 품질도 오버레이도
        // 없는 껍데기가 "완료된 기록" 인 척하게 된다.
        val holder = GaitHolder(MockGaitAnalyzer(stepMillis = 0L), initial = emptyList())

        val got = holder.submit(video())

        assertNotNull(got)
        assertTrue("접수만으로 목록이 늘면 안 된다", holder.records.isEmpty())
    }

    @Test
    fun `접수가 실패하면 이유를 남기고 null 을 준다`() = runTest {
        val holder = GaitHolder(FailingAnalyzer("올리지 못했어요."), initial = emptyList())

        val got = holder.submit(video())

        assertNull(got)
        assertEquals("올리지 못했어요.", holder.error)
    }

    // -- Worker 의 셈 ---------------------------------------------------------

    @Test
    fun `한 기록에 이름이 하나다 — 중복 등록을 막는 열쇠`() {
        assertEquals("gait:abc", GaitAnalysisWorker.workName("abc"))
        assertFalse(
            "기록이 다르면 이름도 달라야 따로 돈다",
            GaitAnalysisWorker.workName("a") == GaitAnalysisWorker.workName("b"),
        )
    }

    /**
     * 되풀이가 끝이 있어야 한다. `Result.retry()` 는 스스로 안 멈춘다 —
     * `runAttemptCount` 를 보는 쪽이 없으면 영영 돈다.
     */
    @Test
    fun `되풀이 상한이 있고 그 안에 분석이 끝날 만큼은 기다린다`() {
        val n = GaitAnalysisWorker.MAX_ATTEMPTS
        assertTrue("상한이 있어야 한다", n in 1..100)

        // 선형 backoff 의 누적 대기: 10 + 20 + … + 10n
        val backoff = GaitAnalysisWorker.BACKOFF_SECONDS
        val total = GaitAnalysisWorker.INITIAL_DELAY_SECONDS + (1..n).sumOf { it * backoff }

        assertTrue(
            "실측 2분짜리 분석을 넉넉히 덮어야 한다 (지금 ${total}초)",
            total >= TimeUnit.MINUTES.toSeconds(10),
        )
    }

    /**
     * 첫 확인을 너무 일찍 하면 "아직" 이라는 답만 받고 시도만 하나 쓴다.
     * 너무 늦으면 짧은 영상이 끝났는데도 한참 모른다.
     */
    @Test
    fun `첫 확인은 곧바로도 한참 뒤도 아니다`() {
        val delay = GaitAnalysisWorker.INITIAL_DELAY_SECONDS
        assertTrue("너무 이르다 (${delay}초)", delay >= 10)
        assertTrue("너무 늦다 (${delay}초)", delay <= 60)
    }

    /** WorkManager 가 허용하는 최소 backoff 가 10초다. 그보다 작게 적으면 조용히 올려 버린다. */
    @Test
    fun `backoff 는 WorkManager 최소치 아래로 내려가지 않는다`() {
        assertTrue(GaitAnalysisWorker.BACKOFF_SECONDS >= 10)
    }

    private class FailingAnalyzer(private val why: String) : GaitAnalyzer {
        override suspend fun submit(video: PreparedVideo, title: String?) =
            Result.failure<GaitSubmission>(IllegalStateException(why))

        override suspend fun analyze(
            video: PreparedVideo,
            onStage: (GaitProgress) -> Unit,
            title: String?,
        ) = Result.failure<GaitRecord>(IllegalStateException(why))
    }
}
