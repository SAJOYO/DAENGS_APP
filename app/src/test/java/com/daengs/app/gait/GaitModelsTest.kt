package com.daengs.app.gait

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.test.runTest
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

    private fun record(id: String, comparable: Boolean = true, seconds: Int = 12) =
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

        val made = holder.analyze(PreparedVideo(Uri.EMPTY, seconds = 14, thumbnail = null)) {
            seen += it.done
        }

        requireNotNull(made)
        assertEquals(before + 1, holder.records.size)
        assertEquals(made.id, holder.records.first().id)
        assertEquals(14, made.seconds)
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
    fun `지우면 목록에서 빠지고 다시 찾아지지 않는다`() {
        val holder = GaitHolder(initial = listOf(record("a"), record("b")))
        holder.remove("a")
        assertEquals(null, holder.find("a"))
        assertEquals(1, holder.records.size)
    }
}
