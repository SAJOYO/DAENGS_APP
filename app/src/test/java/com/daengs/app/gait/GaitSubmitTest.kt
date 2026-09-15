package com.daengs.app.gait

import android.app.Application
import android.net.Uri
import com.daengs.app.gait.work.GaitAnalysisWorker
import com.daengs.app.gait.work.GaitWatchTags
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
     * `runAttemptCount` 를 보는 쪽이 없으면 영영 돈다. 한 번의 실행 안에서 묻는 시간과
     * 간격은 `GaitWatchTest` 가 잡는다.
     */
    @Test
    fun `실행 횟수 상한이 있다`() {
        assertTrue("상한이 있어야 한다", GaitAnalysisWorker.MAX_ATTEMPTS in 1..10)
    }

    /**
     * **첫 지연을 두지 않는다.** 제출 직후 앱이 아직 앞에 있을 때 시작해야 시스템이
     * 미루지 않는다 — 20초 지연을 두었을 때 실기기에서 background 알림이 몇 분씩 늦었다.
     */
    @Test
    fun `제출 직후 바로 시작한다`() {
        val request = GaitAnalysisWorker.request("rec-1", "pet-1")
        assertEquals(0L, request.workSpec.initialDelay)
    }

    @Test
    fun `지켜볼 기록과 강아지를 실어 보낸다`() {
        val input = GaitAnalysisWorker.request("rec-1", "pet-1").workSpec.input
        assertEquals("rec-1", input.getString(GaitAnalysisWorker.KEY_RECORD_ID))
        assertEquals("pet-1", input.getString(GaitAnalysisWorker.KEY_PET_ID))
    }

    /** WorkManager 가 허용하는 최소 backoff 가 10초다. 그보다 작게 적으면 조용히 올려 버린다. */
    @Test
    fun `backoff 는 WorkManager 최소치 아래로 내려가지 않는다`() {
        assertTrue(GaitAnalysisWorker.BACKOFF_SECONDS >= 10)
    }

    /**
     * 챗에 다시 들어왔을 때 진행 중인 카드를 되살리는 근거다. `WorkInfo` 는 입력 데이터를
     * 안 주고 tag 만 주므로, 강아지와 기록이 tag 에 없으면 되살릴 수 없다.
     *
     * `WorkRequest.tags` 는 라이브러리 내부용(`@RestrictTo`)이라 WorkManager 를 올릴 때
     * 깨질 수 있다. 등록 결과를 읽는 공개 API 가 없어서 이렇게 본다.
     */
    @Test
    fun `작업에 강아지와 기록 tag 를 단다`() {
        val tags = GaitAnalysisWorker.request("rec-1", "pet-1").tags

        assertTrue(GaitWatchTags.record("rec-1") in tags)
        assertTrue(GaitWatchTags.pet("pet-1") in tags)
        assertEquals("rec-1", GaitWatchTags.recordIdOf(tags))
    }

    @Test
    fun `강아지를 모르면 강아지 tag 는 달지 않는다`() {
        val tags = GaitAnalysisWorker.request("rec-1", petId = null).tags

        assertTrue(GaitWatchTags.record("rec-1") in tags)
        assertFalse(tags.any { it.startsWith(GaitWatchTags.pet("")) })
    }


    // -- 알림으로 돌아왔을 때 붙일 것 (#220) ----------------------------------
    //
    // 챗을 나갔다 오면 대화는 서버 이력에서 다시 그려지는데 거기에 보행 카드가 없다.
    // 그래서 무엇을 다시 붙일지를 이 목록이 들고 있는다.

    @Test
    fun `같은 기록을 두 번 기억하지 않는다`() {
        val done = GaitCompletions()
        done.remember("pet-1", "rec-1")
        done.remember("pet-1", "rec-1")

        assertEquals("알림을 두 번 눌러도 카드는 하나다", 1, done.forPet("pet-1").size)
    }

    @Test
    fun `대표가 다르면 붙이지 않는다`() {
        val done = GaitCompletions()
        done.remember("pet-1", "rec-1")

        assertTrue("남의 아이 대화에 붙으면 안 된다", done.forPet("pet-2").isEmpty())
        assertEquals(1, done.forPet("pet-1").size)
    }

    @Test
    fun `어느 아이인지 모르는 것은 지금 대표에게 붙인다`() {
        // 옛 알림이나 petId 가 없던 경로. 버리는 것보다 보여 주는 편이 낫다 —
        // 기다리던 결과가 아무 데도 안 뜨는 것이 제일 나쁘다.
        val done = GaitCompletions()
        done.remember(null, "rec-1")

        assertEquals(1, done.forPet("pet-1").size)
        assertEquals(1, done.forPet(null).size)
    }

    @Test
    fun `붙이고 나면 지워져서 다시 붙지 않는다`() {
        val done = GaitCompletions()
        done.remember("pet-1", "rec-1")
        done.consume("rec-1")

        assertTrue("챗에 들어갈 때마다 또 붙으면 안 된다", done.forPet("pet-1").isEmpty())
    }

    @Test
    fun `여럿이 끝나 있으면 다 붙인다`() {
        val done = GaitCompletions()
        done.remember("pet-1", "rec-1")
        done.remember("pet-1", "rec-2")

        assertEquals(2, done.forPet("pet-1").size)
        done.consume("rec-1")
        assertEquals(listOf("rec-2"), done.forPet("pet-1").map { it.recordId })
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
