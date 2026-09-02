package com.daengs.app.gait

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 보행 모델의 규칙.
 *
 * **여기서 잡는 것은 판정 문구가 새지 않는지다.** `CONTEXT.md` 8절이 "이상 있음/없음"
 * 을 금지하는데, 그건 문서에만 적혀 있으면 다음 사람이 [GaitVerdict] 에 갈래를 하나
 * 더 넣는 것으로 조용히 깨진다. 갈래 수와 문장을 테스트가 붙들고 있으면 그때 빨간
 * 줄이 뜬다.
 *
 * 색·여백·그림이 예쁜지는 여기서 못 잡는다. 그건 실기기와 `@Preview` 다.
 *
 * Robolectric 을 쓰는 이유는 [PreparedVideo] 가 `android.net.Uri` 를 들기 때문이다.
 * 순수 JVM 에서는 그 클래스가 "not mocked" 껍데기라 [Uri.EMPTY] 를 읽는 순간 던진다
 * (`WalkTrackingTest` 와 같은 사정).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GaitModelsTest {

    // ── 진행 단계 ────────────────────────────────────────────────────────────

    @Test
    fun `시작하면 첫 줄만 진행 중이고 나머지는 대기다`() {
        val p = GaitProgress.START
        assertEquals(GaitStageState.Running, p.stateOf(GaitStage.Video))
        assertEquals(GaitStageState.Waiting, p.stateOf(GaitStage.Joints))
        assertEquals(GaitStageState.Waiting, p.stateOf(GaitStage.Summary))
        assertFalse(p.finished)
    }

    @Test
    fun `앞 단계는 완료 뒤 단계는 대기로 갈린다`() {
        val p = GaitProgress(2)
        assertEquals(GaitStageState.Done, p.stateOf(GaitStage.Video))
        assertEquals(GaitStageState.Done, p.stateOf(GaitStage.Joints))
        assertEquals(GaitStageState.Running, p.stateOf(GaitStage.Walk))
        assertEquals(GaitStageState.Waiting, p.stateOf(GaitStage.Summary))
    }

    @Test
    fun `끝까지 가면 전부 완료이고 더 넘어가지 않는다`() {
        var p = GaitProgress.START
        repeat(GaitStage.entries.size + 3) { p = p.next() }
        assertEquals(GaitStage.entries.size, p.done)
        assertTrue(p.finished)
        GaitStage.entries.forEach { assertEquals(GaitStageState.Done, p.stateOf(it)) }
    }

    // ── 비교 판정 ────────────────────────────────────────────────────────────

    private fun record(id: String, comparable: Boolean = true, seconds: Int? = 12) =
        GaitRecord(id, LocalDate.of(2026, 8, 31), seconds = seconds, comparable = comparable)

    @Test
    fun `지표가 전부 유사하면 뚜렷한 차이가 없다고 말한다`() {
        val c = GaitComparison.of(
            record("a"),
            record("b"),
            listOf(GaitMetric("걸음 리듬", GaitDelta.Similar), GaitMetric("보폭 크기", GaitDelta.Similar)),
        )
        assertEquals(GaitVerdict.NoClearDifference, c.verdict)
    }

    @Test
    fun `한 줄이라도 다르면 일부 차이가 관찰된다고 말한다`() {
        val c = GaitComparison.of(
            record("a"),
            record("b"),
            listOf(GaitMetric("걸음 리듬", GaitDelta.Similar), GaitMetric("좌우 균형", GaitDelta.Slight)),
        )
        assertEquals(GaitVerdict.SomeDifference, c.verdict)
    }

    @Test
    fun `지표가 비면 부족하다고 말한다`() {
        assertEquals(GaitVerdict.NotEnough, GaitComparison.of(record("a"), record("b"), emptyList()).verdict)
    }

    @Test
    fun `한쪽이 비교 불가면 표 전체가 측정 부족이 되고 판정도 부족이다`() {
        val c = GaitComparison.of(
            record("a"),
            record("b", comparable = false),
            listOf(GaitMetric("걸음 리듬", GaitDelta.Similar), GaitMetric("좌우 균형", GaitDelta.Slight)),
        )
        assertTrue(c.metrics.all { it.delta == GaitDelta.Unknown })
        assertEquals(GaitVerdict.NotEnough, c.verdict)
    }

    /**
     * 갈래가 셋을 넘으면 안 된다.
     *
     * 넷째 갈래를 넣는 순간 그건 "좋아졌다" 나 "나빠졌다" 일 수밖에 없다 — 차이의
     * 유무에는 셋 말고 더 나눌 것이 없기 때문이다.
     */
    @Test
    fun `판정 갈래는 셋뿐이고 어느 문장에도 상태를 평가하는 말이 없다`() {
        assertEquals(3, GaitVerdict.entries.size)
        val banned = listOf("정상", "비정상", "점수", "위험", "호전", "악화", "진단", "이상")
        GaitVerdict.entries.forEach { verdict ->
            banned.forEach { word ->
                assertFalse("${verdict.name} 에 '$word' 가 들어갔다", verdict.sentence.contains(word))
            }
        }
    }

    @Test
    fun `배지 문구도 상태를 평가하지 않는다`() {
        val banned = listOf("정상", "비정상", "건강", "위험", "이상")
        listOf(record("a"), record("b", comparable = false)).forEach { r ->
            banned.forEach { assertFalse(r.badgeLabel.contains(it)) }
        }
    }

    // ── 같은 조합은 늘 같은 결과 ─────────────────────────────────────────────

    @Test
    fun `같은 두 기록을 몇 번 비교해도 같은 표가 나온다`() {
        val a = record("sample-0810")
        val b = record("sample-0730")
        val first = GaitSampleRecords.metricsFor(a, b)
        repeat(5) { assertEquals(first, GaitSampleRecords.metricsFor(a, b)) }
    }

    // ── 라벨 ─────────────────────────────────────────────────────────────────

    @Test
    fun `날짜와 길이 라벨`() {
        val r = GaitRecord("x", LocalDate.of(2026, 8, 31), seconds = 12)
        assertEquals("08.31", r.dateLabel)
        assertEquals("12초", r.lengthLabel)
        assertEquals("00:12", r.clockLabel)
        assertEquals("01:15", r.copy(seconds = 75).clockLabel)
    }

    // ── 홀더 ─────────────────────────────────────────────────────────────────

    @Test
    fun `분석이 끝나면 목록 맨 앞에 얹힌다`() = runTest {
        val holder = GaitHolder(
            analyzer = MockGaitAnalyzer(stepMillis = 0L, today = { LocalDate.of(2026, 8, 31) }),
            initial = GaitSampleRecords.of(LocalDate.of(2026, 8, 31)),
        )
        val before = holder.records.size
        val seen = mutableListOf<Int>()

        val made = holder.analyze(PreparedVideo(Uri.EMPTY, seconds = 24, thumbnail = null)) {
            seen += it.done
        }

        requireNotNull(made)
        assertEquals(before + 1, holder.records.size)
        assertEquals(made.id, holder.records.first().id)
        assertEquals(24, made.seconds)
        assertTrue(made.comparable)
        // 0(시작)부터 4(완료)까지 빠짐없이 올라온다.
        assertEquals(listOf(0, 1, 2, 3, 4), seen)
    }

    @Test
    fun `권장 길이보다 짧게 찍히면 비교 대상으로 세우지 않는다`() = runTest {
        val holder = GaitHolder(MockGaitAnalyzer(stepMillis = 0L), initial = emptyList())
        val made = holder.analyze(PreparedVideo(Uri.EMPTY, seconds = 4, thumbnail = null)) {}
        assertFalse(requireNotNull(made).comparable)
    }

    @Test
    fun `비교 불가 기록만 남아 있으면 비교할 것이 없다고 본다`() {
        val holder = GaitHolder(
            initial = listOf(record("only", comparable = false), record("me")),
        )
        assertFalse(holder.hasComparable("me"))
        assertTrue(holder.hasComparable("only"))
    }

    @Test
    fun `지우면 목록에서 빠지고 다시 찾아지지 않는다`() = runTest {
        val holder = GaitHolder(initial = listOf(record("a"), record("b")))
        holder.remove("a")
        assertEquals(null, holder.find("a"))
        assertEquals(1, holder.records.size)
    }

    @Test
    fun `서버 목록 한 줄은 길이를 모른 채로 옮겨진다`() {
        val summary = GaitSummary.parse(
            JSONObject(
                """
                {"record_id":"5389c92e-7f4b-41d8-a3c6-e0192b7d4f8a","status":"DONE",
                 "captured_at":"2026-08-31","comparable":true,"has_overlay":true,
                 "gait_filter_version":"v5-x"}
                """.trimIndent(),
            ),
        )
        val record = summary.toRecord()

        assertEquals("5389c92e-7f4b-41d8-a3c6-e0192b7d4f8a", record.id)
        assertEquals(LocalDate.of(2026, 8, 31), record.date)
        assertTrue(record.comparable)
        // 저쪽 목록에 길이가 없다. 0 초라고 단언하지 않는다.
        assertEquals(null, record.seconds)
        assertEquals("길이 미상", record.lengthLabel)
        assertEquals(null, record.clockLabel)
    }

    @Test
    fun `분석 응답의 quality_status 가 비교 가능 여부를 정한다`() {
        // 새 계약(D-043): `quality_status` 가 위로 올라왔고, 사유는 `quality` 안에 있다.
        val ok = GaitAnalyzed.parse(
            JSONObject(
                """
                {"record_id":"a1","status":"DONE","captured_at":"2026-08-31",
                 "quality_status":"ok","quality_tier":"low"}
                """.trimIndent(),
            ),
        )
        val bad = GaitAnalyzed.parse(
            JSONObject(
                """
                {"record_id":"a2","status":"DONE","quality_status":"unavailable",
                 "quality":{"reason":"프레임을 읽지 못했습니다",
                            "recommendation":"밝은 곳에서 다시 찍어 주세요"}}
                """.trimIndent(),
            ),
        )

        assertTrue(ok.qualityOk)
        assertEquals("low", ok.qualityTier)
        assertFalse(bad.qualityOk)
        assertEquals("프레임을 읽지 못했습니다", bad.reason)
        assertEquals("밝은 곳에서 다시 찍어 주세요", bad.recommendation)
        assertEquals(null, bad.date)
    }

    @Test
    fun `업로드 티켓의 주소와 헤더를 해석하지 않고 그대로 옮긴다`() {
        // 지금은 임시 bridge 주소가 오지만 곧 GCS Signed URL 이 온다. 앱이 뜯어보지
        // 않아야 저장소가 바뀌어도 앱이 안 바뀐다.
        val ticket = GaitTicket.parse(
            JSONObject(
                """
                {"record_id":"r1","status":"PENDING",
                 "upload_url":"https://storage.example/put?sig=abc",
                 "upload_headers":{"Content-Type":"video/quicktime"},
                 "expires_in_seconds":900}
                """.trimIndent(),
            ),
        )

        assertEquals("https://storage.example/put?sig=abc", ticket.uploadUrl)
        assertEquals(mapOf("Content-Type" to "video/quicktime"), ticket.uploadHeaders)
        assertEquals("PENDING", ticket.status)
        assertEquals(900, ticket.expiresInSeconds)
    }

    @Test
    fun `끝난 상태만 폴링을 멈춘다`() {
        // 폴링이 여기서 끝을 판단한다. PROCESSING 을 끝으로 보면 결과 없는 카드가 뜬다.
        assertTrue(GaitStatus.settled("DONE"))
        assertTrue(GaitStatus.settled("FAILED"))
        assertFalse(GaitStatus.settled("PENDING"))
        assertFalse(GaitStatus.settled("UPLOADED"))
        assertFalse(GaitStatus.settled("PROCESSING"))
    }

    @Test
    fun `실패 사유는 받아 두되 화면 문장으로 쓰지 않는다`() {
        // 운영 진단용이라 내부 경로가 들어 있을 수 있다. 모델은 들고만 있고,
        // 사용자에게 보여 줄 말은 HttpGaitAnalyzer 가 따로 고른다.
        val failed = GaitAnalyzed.parse(
            JSONObject(
                """
                {"record_id":"a3","status":"FAILED",
                 "failure_reason":"Traceback ... /app/src/daengs_gait/pipeline.py"}
                """.trimIndent(),
            ),
        )

        assertEquals("FAILED", failed.status)
        assertTrue(failed.settled)
        assertFalse(failed.qualityOk)
        assertTrue(failed.failureReason!!.contains("pipeline.py"))
    }

    @Test
    fun `서버 문장이 있으면 앱 문장을 이긴다`() {
        val recent = record("a")
        val past = record("b")
        val server = GaitComparison.of(
            recent, past,
            listOf(GaitMetric("Hock", GaitDelta.Similar)),
            serverMessage = "뚜렷한 차이는 관찰되지 않았습니다 (서버)",
        )
        val local = GaitComparison.of(recent, past, listOf(GaitMetric("Hock", GaitDelta.Similar)))

        assertEquals("뚜렷한 차이는 관찰되지 않았습니다 (서버)", server.sentence)
        assertEquals(GaitVerdict.NoClearDifference.sentence, local.sentence)
    }
    // -- 상세 요약 문장 -------------------------------------------------------
    //
    // 여기가 한 번 뚫린 자리다. comparable 의 뜻이 "10초 넘나" 에서 서버의
    // quality.status 로 바뀌었는데 문장만 옛 뜻에 남아, **1분짜리 영상에도
    // "10초보다 짧게 찍혀서" 가 떴다.** 빌드도 테스트도 그대로 통과했다.

    @Test
    fun `길이가 넉넉한데 비교 불가면 짧다는 말을 하지 않는다`() {
        val lines = record("a", comparable = false, seconds = 62)
            .copy(qualityReason = "걷는 구간이 충분히 잡히지 않았어요.")
            .summaryLines()

        assertTrue("서버 사유가 그대로 나와야 한다", lines.any { it.contains("걷는 구간") })
        assertFalse("1분짜리에 짧다고 말하면 안 된다: $lines", lines.any { it.contains("짧") })
    }

    @Test
    fun `서버가 준 사유와 권고를 그대로 옮긴다`() {
        val lines = record("a", comparable = false)
            .copy(
                qualityReason = "걷는 구간이 충분히 잡히지 않았어요.",
                qualityAdvice = "쉬지 않고 걷는 장면으로 다시 찍어 주세요.",
            )
            .summaryLines()

        assertTrue(lines.contains("걷는 구간이 충분히 잡히지 않았어요."))
        assertTrue(lines.contains("쉬지 않고 걷는 장면으로 다시 찍어 주세요."))
    }

    @Test
    fun `사유가 없으면 원인을 짚지 않고 사실만 말한다`() {
        val lines = record("a", comparable = false, seconds = 62).summaryLines()

        assertTrue(lines.any { it.contains("관절 지표를 뽑지 못했어요") })
        assertFalse("원인을 지어내면 안 된다: $lines", lines.any { it.contains("짧") })
    }

    @Test
    fun `앱이 직접 잰 길이가 권장보다 짧을 때만 길이 이야기를 한다`() {
        val short = GaitRecord.RECOMMENDED_SECONDS - 1
        val long = GaitRecord.RECOMMENDED_SECONDS + 1
        assertTrue(record("a", seconds = short).summaryLines().any { it.contains("걷는 모습이") })
        assertFalse(record("b", seconds = long).summaryLines().any { it.contains("걷는 모습이") })
        // 서버 목록에서 온 기록은 길이를 모른다 — 모르면 아무 말도 안 한다.
        assertFalse(record("c", seconds = null).summaryLines().any { it.contains("걷는 모습이") })
    }

    @Test
    fun `길이를 모르면 0초라고 단언하지 않는다`() {
        val r = record("a", seconds = null)
        assertEquals("길이 미상", r.lengthLabel)
        assertEquals(null, r.clockLabel)
    }
    // -- 영상 자리 비율 -------------------------------------------------------
    //
    // 촬영 가이드는 세로 프레임인데 결과·상세 화면 상자가 가로(16:10) 로 박혀
    // 있었다. 세로 영상이 좌우로 텅 빈 채 눕고, Crop 이 위아래를 잘라 **머리가
    // 날아갔다.** 서버가 자른 줄 알았지만 자른 것은 앱이었다.

    @Test
    fun `비율을 모르면 세로로 친다`() {
        assertEquals(GaitRecord.PORTRAIT_ASPECT, record("a").displayAspect, 0.001f)
    }

    @Test
    fun `아는 비율은 그대로 쓴다`() {
        val r = record("a").copy(aspect = 3f / 4f)
        assertEquals(0.75f, r.displayAspect, 0.001f)
    }

    @Test
    fun `너무 길쭉하거나 납작한 것은 잘라 담는다`() {
        assertEquals(GaitRecord.MIN_ASPECT, record("a").copy(aspect = 0.2f).displayAspect, 0.001f)
        assertEquals(GaitRecord.MAX_ASPECT, record("b").copy(aspect = 5f).displayAspect, 0.001f)
    }

    @Test
    fun `세로 영상이 가로로 눕지 않는다`() {
        // 9:16 로 찍힌 것이 1 보다 커지면(가로가 되면) 화면이 눕힌 것이다.
        assertTrue(record("a").copy(aspect = 9f / 16f).displayAspect < 1f)
        assertTrue(record("b").displayAspect < 1f)
    }
    // -- 권장 길이의 근거 -----------------------------------------------------
    //
    // 예전 값 10 은 근거 없이 정한 숫자였고, 저쪽 기준(§21 유효 80프레임 = 5fps
    // 기준 16초치)보다도 짧아서 **안내를 지켜도 떨어지는 영상**이 나왔다.

    @Test
    fun `걷는 모습 기준은 유효 프레임에서 끌어낸다`() {
        assertEquals(80, GaitRecord.MIN_VALID_FRAMES)
        assertEquals(5, GaitRecord.ANALYSIS_FPS)
        assertEquals(16, GaitRecord.MIN_WALKING_SECONDS)
    }

    @Test
    fun `권장 촬영 길이는 걷는 모습 기준보다 넉넉해야 한다`() {
        // 걷다 서는 구간이 늘 섞이므로, 기준과 같으면 지켜도 모자란다.
        assertTrue(
            "권장(${GaitRecord.RECOMMENDED_SECONDS})이 기준(${GaitRecord.MIN_WALKING_SECONDS})보다 커야 한다",
            GaitRecord.RECOMMENDED_SECONDS > GaitRecord.MIN_WALKING_SECONDS,
        )
    }
}
