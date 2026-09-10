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

    /** 왼쪽 셋 · 오른쪽 셋을 선언 순서대로 채운다. */
    private fun joints(vararg changes: GaitJointChange) =
        GaitJoint.entries.mapIndexed { index, joint -> GaitJointState(joint, changes[index]) }

    private val allSimilar = joints(
        GaitJointChange.None, GaitJointChange.None, GaitJointChange.None,
        GaitJointChange.None, GaitJointChange.None, GaitJointChange.None,
    )

    @Test
    fun `관절이 전부 비슷하면 뚜렷한 차이가 없다고 말한다`() {
        val c = GaitComparison.of(record("a"), record("b"), allSimilar)
        assertEquals(GaitVerdict.NoClearDifference, c.verdict)
    }

    @Test
    fun `한 다리에서 하나만 달라지면 아직 기준 미충족이다`() {
        """셋 중 둘이 기준이다. 하나로 "변화가 있다" 고 하면 촬영 흔들림 하나에도 문구가 바뀐다."""
        val c = GaitComparison.of(
            record("a"),
            record("b"),
            joints(
                GaitJointChange.Both, GaitJointChange.None, GaitJointChange.None,
                GaitJointChange.None, GaitJointChange.None, GaitJointChange.None,
            ),
        )
        assertEquals(GaitVerdict.NoClearDifference, c.verdict)
    }

    @Test
    fun `한 다리에서 셋 중 둘이 달라지면 한쪽 다리로 말한다`() {
        val c = GaitComparison.of(
            record("a"),
            record("b"),
            joints(
                GaitJointChange.Horizontal, GaitJointChange.Vertical, GaitJointChange.None,
                GaitJointChange.None, GaitJointChange.None, GaitJointChange.None,
            ),
        )
        assertEquals(GaitVerdict.OneSide, c.verdict)
    }

    @Test
    fun `양쪽 다 셋 중 둘이면 양쪽 다리로 말한다`() {
        val c = GaitComparison.of(
            record("a"),
            record("b"),
            joints(
                GaitJointChange.Horizontal, GaitJointChange.Both, GaitJointChange.None,
                GaitJointChange.Vertical, GaitJointChange.Vertical, GaitJointChange.None,
            ),
        )
        assertEquals(GaitVerdict.BothSides, c.verdict)
    }

    @Test
    fun `측정 부족을 변화 없음으로 세지 않는다`() {
        """**제일 나쁜 오답이다.** 못 잰 것을 "비슷해요" 로 옮기면 없는 안심을 준다.
        오른쪽에서 잰 관절이 하나뿐이라 "전반적으로 비슷" 이라고 말할 근거가 없다."""
        val c = GaitComparison.of(
            record("a"),
            record("b"),
            joints(
                GaitJointChange.None, GaitJointChange.None, GaitJointChange.None,
                GaitJointChange.None, GaitJointChange.Unknown, GaitJointChange.Unknown,
            ),
        )
        assertEquals(GaitVerdict.NotEnough, c.verdict)
    }

    @Test
    fun `변화가 기준을 채웠으면 못 잰 관절이 있어도 그대로 말한다`() {
        """잡힌 변화는 빠진 데이터로 흐려지지 않는다 — 반대쪽 다리가 통째로 비어도
        이쪽에서 둘이 달라진 것은 달라진 것이다."""
        val c = GaitComparison.of(
            record("a"),
            record("b"),
            joints(
                GaitJointChange.Horizontal, GaitJointChange.Vertical, GaitJointChange.Unknown,
                GaitJointChange.Unknown, GaitJointChange.Unknown, GaitJointChange.Unknown,
            ),
        )
        assertEquals(GaitVerdict.OneSide, c.verdict)
    }

    @Test
    fun `관절이 비면 부족하다고 말한다`() {
        assertEquals(GaitVerdict.NotEnough, GaitComparison.of(record("a"), record("b"), emptyList()).verdict)
    }

    @Test
    fun `한쪽이 비교 불가면 표 전체가 측정 부족이 되고 판정도 부족이다`() {
        val c = GaitComparison.of(
            record("a"),
            record("b", comparable = false),
            joints(
                GaitJointChange.None, GaitJointChange.Horizontal, GaitJointChange.None,
                GaitJointChange.None, GaitJointChange.None, GaitJointChange.None,
            ),
        )
        assertTrue(c.joints.all { it.change == GaitJointChange.Unknown })
        assertEquals(GaitVerdict.NotEnough, c.verdict)
    }

    // ── 비교가 얼마나 믿을 만한가 ────────────────────────────────────────────
    //
    // 저쪽 `reliability_note` 를 그대로 띄우던 자리다. 그 문장에는 record UUID 와
    // "§21 기준 80프레임 미만" 이 들어 있어 화면에 개발자 말이 새고 있었다.

    private fun withTier(id: String, tier: GaitQualityTier?) =
        record(id, comparable = tier != null).copy(qualityTier = tier)

    private fun pair(recent: GaitQualityTier?, past: GaitQualityTier?) =
        GaitComparison.of(withTier("recent", recent), withTier("past", past), allSimilar)
            .reliabilitySentence

    @Test
    fun `믿을 만한 정도는 어느 쪽 기록이 모자랐는지까지 말한다`() {
        assertEquals(
            "두 기록 모두 비교하기에 충분한 보행 장면이 확인됐어요.",
            pair(GaitQualityTier.Good, GaitQualityTier.Good),
        )
        assertEquals(
            "최근 기록의 보행 장면이 적어 결과는 참고용으로 봐주세요.",
            pair(GaitQualityTier.Low, GaitQualityTier.Good),
        )
        assertEquals(
            "비교 기록의 보행 장면이 적어 결과는 참고용으로 봐주세요.",
            pair(GaitQualityTier.Good, GaitQualityTier.Low),
        )
        assertEquals(
            "두 기록 모두 보행 장면이 적어 결과는 참고용으로 봐주세요.",
            pair(GaitQualityTier.Low, GaitQualityTier.Low),
        )
    }

    @Test
    fun `모자람의 기준은 저쪽과 같게 good 이 아닌 것이다`() {
        """저쪽은 `quality_tier != "good"` 일 때 주의를 붙였다. 여기서 기준을 느슨하게 잡으면
        저쪽이 참고용이라고 본 비교에 앱이 "충분" 도장을 찍게 된다."""
        assertTrue(pair(GaitQualityTier.Ok, GaitQualityTier.Good).startsWith("최근 기록"))
        // 등급을 모르는 기록(표본·옛 기록)도 "충분" 으로 올려 말하지 않는다.
        assertTrue(pair(null, GaitQualityTier.Good).startsWith("최근 기록"))
    }

    @Test
    fun `믿을 만한 정도 문장에 내부 표기가 새지 않는다`() {
        val banned = listOf("§", "프레임", "record", "id", "quality", "tier", "-")
        listOf(
            GaitQualityTier.Good to GaitQualityTier.Good,
            GaitQualityTier.Low to GaitQualityTier.Good,
            GaitQualityTier.Good to GaitQualityTier.Low,
            GaitQualityTier.Low to GaitQualityTier.Low,
        ).forEach { (a, b) ->
            val line = pair(a, b)
            banned.forEach { assertFalse("'$it' 가 들어갔다: $line", line.contains(it, ignoreCase = true)) }
            assertFalse("숫자가 새면 안 된다: $line", line.any { ch -> ch.isDigit() })
        }
    }

    // ── 축 두 개를 한 줄로 합치는 규칙 ───────────────────────────────────────

    @Test
    fun `두 축의 조합이 네 갈래로 접힌다`() {
        val of = GaitJointChange::of
        assertEquals(GaitJointChange.None, of(GaitAxis.Similar, GaitAxis.Similar))
        assertEquals(GaitJointChange.Horizontal, of(GaitAxis.Changed, GaitAxis.Similar))
        assertEquals(GaitJointChange.Vertical, of(GaitAxis.Similar, GaitAxis.Changed))
        assertEquals(GaitJointChange.Both, of(GaitAxis.Changed, GaitAxis.Changed))
    }

    @Test
    fun `한 축을 못 재면 비슷함으로 접지 않는다`() {
        """못 잰 축을 "비슷함" 쪽으로 세면 관절 하나가 통째로 초록이 된다."""
        assertEquals(GaitJointChange.Unknown, GaitJointChange.of(GaitAxis.Unknown, GaitAxis.Similar))
        assertEquals(GaitJointChange.Unknown, GaitJointChange.of(GaitAxis.Unknown, GaitAxis.Unknown))
        // 반대로 잡힌 변화는 감추지 않는다.
        assertEquals(GaitJointChange.Vertical, GaitJointChange.of(GaitAxis.Unknown, GaitAxis.Changed))
    }

    /**
     * 갈래가 셋을 넘으면 안 된다.
     *
     * 넷째 갈래를 넣는 순간 그건 "좋아졌다" 나 "나빠졌다" 일 수밖에 없다 — 차이의
     * 유무에는 셋 말고 더 나눌 것이 없기 때문이다.
     */
    @Test
    fun `판정 갈래는 넷뿐이고 어느 문장에도 상태를 평가하는 말이 없다`() {
        // 갈래가 늘면 그건 "좋아졌다" 나 "나빠졌다" 일 수밖에 없다 — 차이의 유무와
        // 어느 다리인가 말고 더 나눌 것이 없다.
        assertEquals(4, GaitVerdict.entries.size)
        val banned = listOf("정상", "비정상", "점수", "위험", "호전", "악화", "진단", "이상")
        GaitVerdict.entries.forEach { verdict ->
            banned.forEach { word ->
                assertFalse("${verdict.name} 제목에 '$word' 가 들어갔다", verdict.title.contains(word))
                assertFalse("${verdict.name} 보조문구에 '$word' 가 들어갔다", verdict.detail.contains(word))
            }
        }
    }

    @Test
    fun `관절 상태 문구에도 상태를 평가하는 말이 없다`() {
        """색이 초록·주황·빨강으로 갈리는 자리라 문구가 더 조심스러워야 한다."""
        val banned = listOf("정상", "비정상", "위험", "이상", "악화", "심함")
        GaitJointChange.entries.forEach { change ->
            banned.forEach { assertFalse("${change.name} 에 '$it' 이 들어갔다", change.label.contains(it)) }
        }
    }

    @Test
    fun `관절 여섯 개의 순서와 이름이 고정이다`() {
        """왼쪽 위에서 아래, 그다음 오른쪽. 서버 key 는 화면에 안 나간다."""
        assertEquals(6, GaitJoint.entries.size)
        assertEquals(
            listOf("고관절", "무릎", "뒷발", "고관절", "무릎", "뒷발"),
            GaitJoint.entries.map { it.label },
        )
        assertEquals(
            listOf("L_Hip", "L_Knee", "L_B_Paw", "R_Hip", "R_Knee", "R_B_Paw"),
            GaitJoint.entries.map { it.key },
        )
        assertEquals(3, GaitJoint.entries.count { it.leg == GaitLeg.Left })
        assertEquals(3, GaitJoint.entries.count { it.leg == GaitLeg.Right })
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
        val first = GaitSampleRecords.jointStatesFor(a, b)
        repeat(5) { assertEquals(first, GaitSampleRecords.jointStatesFor(a, b)) }
        // 표본도 서버와 같은 모양이어야 화면이 표본에서만 맞는 일이 없다.
        assertEquals(GaitJoint.entries.toList(), first.map { it.joint })
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
    fun `비교의 최근과 비교 자리는 넘긴 순서가 아니라 날짜가 정한다`() = runTest {
        """B 진입은 사용자가 고른 순서대로 넘긴다. 그 순서를 라벨에 그대로 쓰면 09.02 가
        "최근 기록" 으로 붙는다 — 에뮬레이터에서 실제로 그랬다."""
        val older = GaitRecord("old", LocalDate.of(2026, 9, 2))
        val newer = GaitRecord("new", LocalDate.of(2026, 9, 7))
        val holder = GaitHolder(initial = listOf(newer, older))

        val backwards = holder.compare(recentId = "old", pastId = "new")!!
        assertEquals("new", backwards.recent.id)
        assertEquals("old", backwards.past.id)

        // 날짜가 같으면 넘긴 순서를 그대로 둔다 — 뒤집을 근거가 없다.
        val sameDay = GaitHolder(initial = listOf(record("a"), record("b")))
            .compare(recentId = "b", pastId = "a")!!
        assertEquals("b", sameDay.recent.id)
        assertEquals("a", sameDay.past.id)
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

    /**
     * **실제 서버가 준 응답 그대로다** (2026-09-02, `daengback` 에서 받아 옮김).
     *
     * 방금 만든 기록은 **거의 모든 값이 `null`** 인데, 앱은 confirm 직후 바로 이걸
     * 폴링한다. 여기서 파서가 터지면 분석이 시작도 못 하고 죽는다 — 손으로 지어낸
     * JSON 으로는 이 조합을 놓치기 쉬워서 실제 응답을 박아 둔다.
     */
    @Test
    fun `갓 만든 기록은 거의 전부 null 인데 그대로 읽힌다`() {
        val analyzed = GaitAnalyzed.parse(
            JSONObject(
                """
                {"record_id":"0eb458e4-6d57-4a9e-b945-f1873a5dae3b",
                 "pet_id":"9061a4ed-b7bd-44ce-8dc3-b8eed7456ef1","status":"PENDING",
                 "quality_status":null,"quality_tier":null,"gait_filter_version":null,
                 "captured_at":null,"source_file":"IMG_8631.mov","note":null,
                 "created_at":"2026-09-02T04:49:03.055693Z","comparable":false,
                 "has_overlay":false,"quality":null,"summary_for_ui":null,
                 "video_meta":null,"failure_reason":null}
                """.trimIndent(),
            ),
        )

        assertEquals("PENDING", analyzed.status)
        // 아직 끝나지 않았다 — 폴링이 계속돼야 한다.
        assertFalse(analyzed.settled)
        assertFalse(analyzed.qualityOk)
        assertEquals(null, analyzed.date)
        assertEquals(null, analyzed.reason)
        assertEquals(null, analyzed.qualityTier)
        assertEquals(null, analyzed.failureReason)
    }

    /** 목록도 같은 상태를 담아 온다. **아직 안 끝난 기록이 목록에 섞인다.** */
    @Test
    fun `목록에 아직 분석 중인 기록이 섞여도 비교 대상으로 세우지 않는다`() {
        val page = GaitPage.parse(
            JSONObject(
                """
                {"records":[{"record_id":"0eb458e4-6d57-4a9e-b945-f1873a5dae3b",
                 "pet_id":"9061a4ed-b7bd-44ce-8dc3-b8eed7456ef1","status":"PENDING",
                 "quality_status":null,"quality_tier":null,"gait_filter_version":null,
                 "captured_at":null,"source_file":"IMG_8631.mov","note":null,
                 "created_at":"2026-09-02T04:49:03.055693Z","comparable":false,
                 "has_overlay":false}],"next_cursor":null}
                """.trimIndent(),
            ),
        )

        assertEquals(1, page.records.size)
        assertEquals(null, page.nextCursor)
        // 서버가 이미 false 로 준다 — 앱이 따로 판단하지 않는다.
        assertFalse(page.records[0].toRecord().comparable)
    }

    /** 티켓도 실제 응답 그대로. 임시 bridge 주소지만 앱은 해석하지 않는다. */
    @Test
    fun `실제 서버가 준 티켓을 그대로 읽는다`() {
        val ticket = GaitTicket.parse(
            JSONObject(
                """
                {"record_id":"0eb458e4-6d57-4a9e-b945-f1873a5dae3b","status":"PENDING",
                 "upload_url":"http://daengback.weareithero.cloud/app/gait/_bridge/upload/gait/9061a4ed-b7bd-44ce-8dc3-b8eed7456ef1/original/cbb2cc7ed79a4bccbd5431234c59ada1.mov",
                 "upload_headers":{"Content-Type":"video/quicktime"},
                 "expires_in_seconds":900}
                """.trimIndent(),
            ),
        )

        assertTrue(ticket.uploadUrl.endsWith(".mov"))
        assertEquals("video/quicktime", ticket.uploadHeaders["Content-Type"])
        assertEquals(900, ticket.expiresInSeconds)
    }

    // ── 비교 응답: 관절 하나가 한 줄 ─────────────────────────────────────────
    @Test
    fun `관절 하나가 축을 합쳐 한 줄이 된다`() {
        """서버는 관절마다 comparison_note{x,y} 를 준다. 합치되 **어느 축이 움직였는지는
        문구에 남는다** — 그게 예전에 축을 나눠 뒀던 이유였다."""
        val compared = GaitCompared.parse(
            JSONObject(
                """
                {"status":"ok","message_for_ui":"일부 움직임 지표에서 차이가 관찰됩니다",
                 "joint_movement_range_comparison":{
                   "L_Hip":{"record_a":{"x_range":10.0},"record_b":{"x_range":20.0},
                            "comparison_note":{"x":"차이 관찰됨","y":"비슷함"}},
                   "R_Knee":{"comparison_note":{"x":"차이 관찰됨","y":"차이 관찰됨"}}},
                 "reliability_note":"유효 프레임 수가 적어 참고용입니다",
                 "version_warning":"두 기록의 분석 버전이 다릅니다"}
                """.trimIndent(),
            ),
        )
        val states = compared.toJointStates().associate { it.joint to it.change }

        // 서버가 둘만 줘도 여섯 줄을 채운다 — 빠진 것은 측정 부족이다.
        assertEquals(6, states.size)
        assertEquals(GaitJointChange.Horizontal, states[GaitJoint.LeftHip])
        assertEquals(GaitJointChange.Both, states[GaitJoint.RightKnee])
        assertEquals(GaitJointChange.Unknown, states[GaitJoint.LeftKnee])
        assertEquals("두 기록의 분석 버전이 다릅니다", compared.versionWarning)
        assertEquals("유효 프레임 수가 적어 참고용입니다", compared.reliabilityNote)
    }

    @Test
    fun `모르는 판정 문자열은 비슷함으로 떨어뜨리지 않는다`() {
        """"차이 관찰됨" 을 놓쳐 "비슷함" 이 되면 **없는 안심**을 준다."""
        val compared = GaitCompared.parse(
            JSONObject(
                """{"status":"ok","joint_movement_range_comparison":{
                     "L_Knee":{"comparison_note":{"x":"???","y":null}}}}"""
            ),
        )
        val states = compared.toJointStates().associate { it.joint to it.change }
        assertEquals(GaitJointChange.Unknown, states[GaitJoint.LeftKnee])
    }

    @Test
    fun `서버 key 는 화면 문구에 새지 않는다`() {
        """`L_B_Paw (좌우)` 를 그대로 띄우던 것이 이번 개편의 출발점이었다."""
        val compared = GaitCompared.parse(
            JSONObject(
                """{"status":"ok","joint_movement_range_comparison":{
                     "L_B_Paw":{"comparison_note":{"x":"비슷함","y":"비슷함"}}}}"""
            ),
        )
        compared.toJointStates().forEach { state ->
            listOf("L_", "R_", "Paw", "Hip", "Knee").forEach { key ->
                assertFalse(state.joint.label.contains(key))
                assertFalse(state.change.label.contains(key))
            }
        }
    }

    @Test
    fun `비교 불가면 표를 비운다`() {
        val compared = GaitCompared.parse(
            JSONObject("""{"status":"unavailable","reason":"분석 가능 상태가 아닙니다"}"""),
        )
        assertFalse(compared.available)
        assertTrue(compared.toJointStates().isEmpty())
    }

    @Test
    fun `지난 기록의 길이는 상세의 샘플 프레임 수로 셈한다`() {
        """서버 응답에 길이가 없다 — `video_meta` 는 해상도·원본 fps 뿐이다. 5fps 로 훑은
        샘플 수가 곧 길이라 그걸로 채운다. 비교 화면에서 옆 기록만 "길이 미상" 이던 것."""
        val done = GaitAnalyzed.parse(
            JSONObject(
                """{"record_id":"r","status":"DONE","quality_status":"ok","quality_tier":"good",
                    "quality":{"n_frames_sampled":119,"n_frames_gait_usable":105}}"""
            ),
        )
        assertEquals(119, done.sampledFrames)
        assertEquals(24, done.approxSeconds)   // 119 / 5 = 23.8 → 24

        // 아직 분석 중이면 quality 가 없다 — 0초가 아니라 모른다.
        val pending = GaitAnalyzed.parse(JSONObject("""{"record_id":"r","status":"PROCESSING"}"""))
        assertEquals(null, pending.sampledFrames)
        assertEquals(null, pending.approxSeconds)

        // 샘플이 0 이면 길이를 말하지 않는다.
        val empty = GaitAnalyzed.parse(
            JSONObject("""{"record_id":"r","status":"DONE","quality":{"n_frames_sampled":0}}"""),
        )
        assertEquals(null, empty.approxSeconds)
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
    fun `제목은 서버 한 줄이 아니라 관절 결과에서 나온다`() {
        """**D-058 이 여기서 뒤집혔다.** 예전에는 서버 `message_for_ui` 가 앱 문장을
        이겼다. 그 한 줄은 관절을 통틀어 "일부 지표에서 차이가 관찰됩니다" 뿐이라
        어느 다리인지를 말하지 못한다 — 사용자가 알고 싶은 것이 그거라서 제목을
        관절 결과에서 짓는다. 계산은 그대로 저쪽 것이다.

        서버가 "차이가 관찰됩니다" 라고 해도, 한 다리에서 둘을 못 채웠으면 이 화면은
        기준 미충족으로 말한다 — 표와 제목이 어긋나면 안 되기 때문이다."""
        val compared = GaitCompared.parse(
            JSONObject(
                """
                {"status":"ok",
                 "message_for_ui":"이전 기록과 비교해 일부 움직임 지표에서 차이가 관찰됩니다.",
                 "joint_movement_range_comparison":{
                   "L_Hip":{"comparison_note":{"x":"차이 관찰됨","y":"비슷함"}},
                   "L_Knee":{"comparison_note":{"x":"비슷함","y":"비슷함"}},
                   "L_B_Paw":{"comparison_note":{"x":"비슷함","y":"비슷함"}},
                   "R_Hip":{"comparison_note":{"x":"비슷함","y":"비슷함"}},
                   "R_Knee":{"comparison_note":{"x":"비슷함","y":"비슷함"}},
                   "R_B_Paw":{"comparison_note":{"x":"비슷함","y":"비슷함"}}},
                 "reliability_note":"유효 프레임 수가 적어 참고용입니다",
                 "version_warning":"두 기록의 분석 버전이 다릅니다"}
                """.trimIndent(),
            ),
        )
        val c = GaitComparison.of(
            record("a"),
            record("b"),
            compared.toJointStates(),
            compared.versionWarning,
            compared.reliabilityNote,
        )

        // 셋 중 하나뿐이라 기준 미충족. 서버 한 줄에 끌려가지 않는다.
        assertEquals(GaitVerdict.NoClearDifference, c.verdict)
        assertEquals("뚜렷한 차이는 관찰되지 않았어요", c.verdict.title)

        // **저쪽 주의 문장은 그대로 옮긴다.** 어느 기록이 왜 참고용인지는 서버만 안다.
        assertEquals("유효 프레임 수가 적어 참고용입니다", c.reliabilityNote)
        assertEquals("두 기록의 분석 버전이 다릅니다", c.versionWarning)
    }
    // -- 상세 요약 문장 -------------------------------------------------------
    //
    // 최대 셋: 날짜·길이 / 등급 한 줄 / 권고 또는 재생 안내. 숫자·모델명·플래그·진단
    // 표현은 안 나간다. 여기가 한 번 뚫린 자리라(옛 문장이 뜻이 바뀐 뒤에도 남았다)
    // 문장을 테스트가 붙든다.

    private fun tiered(tier: GaitQualityTier?, comparable: Boolean = tier != null) =
        record("a", comparable = comparable, seconds = 24).copy(qualityTier = tier)

    @Test
    fun `요약 첫 줄은 날짜와 길이고 길이를 모르면 날짜만이다`() {
        assertEquals("08.31 · 24초", tiered(GaitQualityTier.Good).summaryLines().first())
        assertEquals("08.31", record("a", seconds = null).summaryLines().first())
        assertFalse(record("a", seconds = null).summaryLines().any { it.contains("미상") })
    }

    @Test
    fun `등급마다 정해진 한 줄이 둘째 줄이다`() {
        assertEquals("관절 움직임이 충분히 확인된 영상이에요.", tiered(GaitQualityTier.Good).summaryLines()[1])
        assertEquals("관절 움직임을 확인할 수 있는 영상이에요.", tiered(GaitQualityTier.Ok).summaryLines()[1])
        assertEquals("확인된 보행 장면이 적어 결과는 참고용으로 봐주세요.", tiered(GaitQualityTier.Low).summaryLines()[1])
        assertEquals("분석 가능한 보행 장면이 충분하지 않았어요.", tiered(null, comparable = false).summaryLines()[1])
        // 등급 없이 comparable 인 옛 기록·표본은 "충분히" 로 올려 말하지 않는다.
        assertEquals("관절 움직임을 확인할 수 있는 영상이에요.", tiered(null, comparable = true).summaryLines()[1])
    }

    @Test
    fun `분석 불가면 셋째 줄은 서버 권고가 먼저고 없을 때만 재생 안내다`() {
        val advised = tiered(null, comparable = false)
            .copy(qualityAdvice = "쉬지 않고 걷는 장면으로 다시 찍어 주세요.", overlay = Uri.parse("http://x/o.mp4"))
        assertEquals("쉬지 않고 걷는 장면으로 다시 찍어 주세요.", advised.summaryLines()[2])

        val noAdvice = tiered(null, comparable = false).copy(overlay = Uri.parse("http://x/o.mp4"))
        assertEquals("분석 영상에서 관절 위치를 직접 확인할 수 있어요.", noAdvice.summaryLines()[2])

        // 분석이 된 기록은 권고가 있어도 재생 안내다 — 권고는 분석 불가의 행동 지침이다.
        val good = tiered(GaitQualityTier.Good).copy(qualityAdvice = "무시돼야 함", overlay = Uri.parse("http://x/o.mp4"))
        assertEquals("분석 영상에서 관절 위치를 직접 확인할 수 있어요.", good.summaryLines()[2])
    }

    @Test
    fun `오버레이가 없으면 지원되지 않는다고 하고 아직 못 받았으면 말을 아낀다`() {
        assertEquals("이 기록은 분석 영상 재생이 지원되지 않아요.", tiered(GaitQualityTier.Ok).summaryLines()[2])
        // 저쪽에 있다는데 주소를 아직 못 받았다 — 잠깐 "지원 안 됨" 이라고 했다가 바뀌면 안 된다.
        val pending = tiered(GaitQualityTier.Ok).copy(hasOverlay = true)
        assertEquals(2, pending.summaryLines().size)
    }

    @Test
    fun `요약은 셋을 넘지 않고 숫자나 평가하는 말이 없다`() {
        val samples = listOf(
            tiered(GaitQualityTier.Good).copy(overlay = Uri.parse("http://x/o.mp4")),
            tiered(GaitQualityTier.Low),
            tiered(null, comparable = false).copy(qualityAdvice = "다시 찍어 주세요."),
        )
        val banned = listOf("프레임", "conf", "keypoint", "검출률", "표본", "정상", "이상", "진단", "질환", "위험")
        samples.forEach { r ->
            val lines = r.summaryLines()
            assertTrue("셋을 넘었다: $lines", lines.size <= 3)
            lines.drop(1).forEach { line ->
                banned.forEach { assertFalse("'$it' 가 들어갔다: $line", line.contains(it)) }
                assertFalse("숫자가 새면 안 된다: $line", line.any { ch -> ch.isDigit() })
            }
        }
    }

    @Test
    fun `등급은 status 가 ok 일 때만 읽는다`() {
        assertEquals(GaitQualityTier.Good, GaitQualityTier.of("ok", "good"))
        assertEquals(GaitQualityTier.Low, GaitQualityTier.of("ok", "low"))
        // unavailable 이면 tier 가 딸려 와도 없는 것이다.
        assertEquals(null, GaitQualityTier.of("unavailable", "good"))
        assertEquals(null, GaitQualityTier.of(null, "good"))
        assertEquals(null, GaitQualityTier.of("ok", "???"))
    }

    @Test
    fun `길이를 모르면 0초라고 단언하지 않는다`() {
        val r = record("a", seconds = null)
        assertEquals("길이 미상", r.lengthLabel)
        assertEquals(null, r.clockLabel)
    }

    // -- 기록 제목 -------------------------------------------------------------
    //
    // 제목과 날짜는 다른 필드다. 제목을 고쳐도 날짜는 그대로여야 하고, 날짜는 파일이
    // 아니라 **기록을 만든 날**이다. 서버에 수정 API 가 없어 고친 제목은 로컬에만 남는다.

    @Test
    fun `제목이 없으면 기본값을 그리고 저장하지는 않는다`() {
        assertEquals("보행 기록", record("a").displayTitle)
        assertEquals("보행 기록", record("a").copy(title = "  ").displayTitle)
        assertEquals("저녁 산책", record("a").copy(title = "저녁 산책").displayTitle)
        assertEquals(null, record("a").title)
    }

    @Test
    fun `서버 note 가 제목이 된다`() {
        val summary = GaitSummary.parse(
            JSONObject(
                """{"record_id":"r","status":"DONE","captured_at":"2026-09-07","comparable":true,
                    "has_overlay":true,"quality_status":"ok","quality_tier":"good","note":"저녁 산책"}"""
            ),
        )
        val r = summary.toRecord()
        assertEquals("저녁 산책", r.title)
        assertEquals(GaitQualityTier.Good, r.qualityTier)
        assertTrue(r.hasOverlay)
        assertEquals(LocalDate.of(2026, 9, 7), r.date)
    }

    @Test
    fun `목록에서 작성자와 현재 사용자의 권한을 읽는다`() {
        val summary = GaitSummary.parse(
            JSONObject(
                """{"record_id":"r","status":"DONE","created_by":{"app_user_id":"u1","nickname":"키키"},
                    "can_confirm":true,"can_delete":false}""",
            ),
        )
        assertEquals("u1", summary.createdBy?.appUserId)
        assertEquals("키키", summary.createdBy?.displayName)
        assertEquals(true, summary.canConfirm)
        assertEquals(false, summary.canDelete)
        val record = summary.toRecord()
        assertEquals("u1", record.createdBy?.appUserId)
        assertEquals(true, record.canConfirm)
        assertEquals(false, record.canDelete)
    }

    @Test
    fun `옛 보행 응답에는 권한 정보가 없어도 읽는다`() {
        val summary = GaitSummary.parse(JSONObject("""{"record_id":"r","status":"DONE"}"""))
        assertEquals(null, summary.createdBy)
        assertEquals(null, summary.canConfirm)
        assertEquals(null, summary.canDelete)
    }

    @Test
    fun `삭제 권한이 없는 보행 기록은 홀더에서도 지우지 않는다`() = runTest {
        val protected = record("shared").copy(canDelete = false)
        val holder = GaitHolder(initial = listOf(protected))

        holder.remove(protected.id)

        assertEquals(listOf(protected), holder.records)
        assertEquals("대표 보호자만 이 보행 기록을 지울 수 있어요.", holder.error)
    }

    @Test
    fun `제목은 다듬고 스물 자에서 자르며 비면 없는 것이다`() {
        assertEquals("저녁 산책", GaitTitleStore.normalize("  저녁 산책  "))
        assertEquals(null, GaitTitleStore.normalize("   "))
        assertEquals(null, GaitTitleStore.normalize(null))
        assertEquals(20, GaitTitleStore.normalize("가".repeat(30))!!.length)
    }

    @Test
    fun `제목을 고쳐도 날짜는 그대로다`() = runTest {
        val holder = GaitHolder(initial = listOf(record("a"), record("b")))
        val before = holder.find("a")!!.date
        holder.rename("a", "저녁 산책")
        assertEquals("저녁 산책", holder.find("a")!!.title)
        assertEquals(before, holder.find("a")!!.date)
        // 비우면 기본값으로 돌아간다.
        holder.rename("a", "")
        assertEquals(null, holder.find("a")!!.title)
        assertEquals(before, holder.find("a")!!.date)
        // 옆 기록은 건드리지 않는다.
        assertEquals(null, holder.find("b")!!.title)
    }

    @Test
    fun `기록 날짜는 영상 파일이 아니라 기록을 만든 날이다`() = runTest {
        val created = LocalDate.of(2026, 9, 7)
        val holder = GaitHolder(
            analyzer = MockGaitAnalyzer(stepMillis = 0L, today = { created }),
            initial = emptyList(),
        )
        val made = holder.analyze(PreparedVideo(Uri.EMPTY, seconds = 24, thumbnail = null), "저녁 산책") {}
        assertEquals(created, requireNotNull(made).date)
        assertEquals("저녁 산책", made.title)
    }

    @Test
    fun `로컬 제목 저장소는 넣고 읽고 지운다`() {
        val store = GaitTitleStore(androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>())
        store.set("r1", "  아침 산책 ")
        assertEquals("아침 산책", store.get("r1"))
        store.set("r1", "")
        assertEquals(null, store.get("r1"))
        store.set("r2", "x")
        store.remove("r2")
        assertEquals(null, store.get("r2"))
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
