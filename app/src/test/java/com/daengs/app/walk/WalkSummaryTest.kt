package com.daengs.app.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저장된 원본으로 목록에 쓸 숫자를 낸다.
 *
 * **요약을 따로 저장하지 않는 것이 이 계산의 전제다.** 그래서 여기서 나오는 거리는
 * 산책 중 화면에 보이던 값과 같아야 하고, 그 보장은 같은 [TrailRecorder] 를 다시
 * 돌리는 데서 온다.
 */
class WalkSummaryTest {

    private val session = RecordedSession(id = "s1", dogIds = listOf("dog-1"), startedAtMillis = 1_000L)

    private fun fix(seq: Int, chain: Int, at: Long, lat: Double, lng: Double) = RecordedFix(
        clientSeq = seq,
        chainIndex = chain,
        atMillis = at,
        lat = lat,
        lng = lng,
        accuracyM = 5f,
        isMock = false,
    )

    /** 같은 좌표를 화면에 넣었을 때와 같은 거리가 나와야 한다. 규칙이 두 벌이면 갈라진다. */
    @Test
    fun `거리가 화면에서 보던 값과 같다`() {
        // 0.001도 = 약 111m 다. **걷는 속도(1.4 m/s)로 80초씩** 두면 실제 산책이 된다 —
        // 예전에는 2초 간격이라 55 m/s 였고, 속도 문턱([WalkPace])이 생기면서 그런
        // 좌표는 아예 안 담긴다. 거리를 비교하려면 담기는 좌표여야 한다.
        val fixes = listOf(
            fix(0, 0, 1_000L, 37.5000, 127.0000),
            fix(1, 0, 81_000L, 37.5010, 127.0000),
            fix(2, 0, 161_000L, 37.5020, 127.0000),
        )
        val recorder = TrailRecorder()
        recorder.start()
        fixes.forEach { f ->
            recorder.add(
                com.daengs.app.location.LocationSample(
                    point = com.daengs.app.location.GeoPoint(f.lat, f.lng),
                    capturedAtMillis = f.atMillis,
                    accuracyMeters = f.accuracyM,
                ),
            )
        }
        assertEquals(
            recorder.snapshot().distanceMeters,
            summarize(session, fixes).distanceMeters,
            0.001,
        )
    }

    /**
     * 일시정지 앞뒤는 **한 선이 아니다.**
     *
     * 이어 붙이면 멈춰 있던 동안 이동한 것으로 세어 걷지 않은 거리가 더해지고,
     * 지도에도 안 걸은 직선이 그려진다.
     */
    @Test
    fun `끊긴 구간을 이어 붙이지 않는다`() {
        val summary = summarize(
            session,
            listOf(
                fix(0, 0, 1_000L, 37.5000, 127.0000),
                fix(1, 0, 81_000L, 37.5010, 127.0000),
                // 여기서 일시정지 — 한참 뒤 먼 곳에서 다시 시작
                fix(2, 1, 900_000L, 37.6000, 127.1000),
                fix(3, 1, 980_000L, 37.6010, 127.1000),
            ),
        )
        assertEquals("세그먼트가 둘이어야 한다", 2, summary.segments.size)
        // 두 구간 사이 13분은 걸은 시간이 아니다. 각 구간 안의 80초씩만 센다.
        assertEquals(160_000L, summary.activeDurationMillis)
    }

    @Test
    fun `GPS 점프로 화면 선만 끊겨도 세션 활동 시간축은 끊지 않는다`() {
        val summary = summarize(
            session,
            listOf(
                fix(0, 0, 1_000L, 37.5000, 127.0000),
                fix(1, 0, 81_000L, 37.5010, 127.0000),
                // 1km 를 13분에 걸었다 — 걷는 속도지만 점 사이가 200m 를 넘어서
                // 화면 선은 끊긴다 (터널·신호 끊김 뒤에 다시 잡히는 모양이다).
                fix(2, 0, 881_000L, 37.5100, 127.0000),
            ),
        )

        assertEquals(2, summary.segments.size)
        assertEquals(880_000L, summary.activeDurationMillis)
        assertEquals(summary.activeDurationMillis, summary.toSessionRoute().end?.activeElapsedMillis)
    }

    @Test
    fun `목록은 오천 점으로 제한하고 한 산책 상세만 실제 출발점을 보존한다`() {
        val fixes = (0..5_000).map { index ->
            fix(
                seq = index,
                chain = 0,
                at = 1_000L + index * 1_000L,
                lat = 37.5 + index * 0.00004,
                lng = 127.0,
            )
        }

        val listSummary = summarize(session, fixes)
        val detailSummary = summarize(session, fixes, maxRouteSamples = Int.MAX_VALUE)

        assertEquals(WALK_SUMMARY_ROUTE_SAMPLE_LIMIT, listSummary.segments.flatten().size)
        assertEquals(5_001, detailSummary.segments.flatten().size)
        assertEquals(1_000L, detailSummary.toSessionRoute().start?.capturedAtMillis)
    }

    @Test
    fun `좌표가 하나면 거리도 시간도 0 이다`() {
        val summary = summarize(session, listOf(fix(0, 0, 1_000L, 37.5, 127.0)))
        assertEquals(0.0, summary.distanceMeters, 0.001)
        assertEquals(0L, summary.activeDurationMillis)
        assertTrue("선을 그릴 수 없다", !summary.hasRoute)
    }

    /** 날씨를 못 받았으면 null 그대로 온다 — 화면이 날씨 줄을 뺄 수 있어야 한다. */
    @Test
    fun `날씨가 없으면 없는 채로 온다`() {
        assertEquals(null, summarize(session, emptyList()).weather)
        val withWeather = session.copy(
            weather = RecordedWeather(weatherCode = 61, isDay = true, temperatureC = 18.5f),
        )
        assertEquals(61, summarize(withWeather, emptyList()).weather?.weatherCode)
    }

    /**
     * 그릴 선이 없어도 **어디였는지는 안다.**
     *
     * 필터가 다 버렸거나 좌표가 한 점뿐이면 세그먼트가 비는데, 그때 지도를 보낼 자리가
     * 없으면 네이버 기본 카메라(서울시청)가 나온다 — 강남에서 한 산책이 시청이 된다.
     */
    @Test
    fun `경로가 없어도 자리는 남는다`() {
        val summary = summarize(session, listOf(fix(0, 0, 1_000L, 37.4979, 127.0276)))
        assertTrue("선은 못 그린다", !summary.hasRoute)
        assertEquals(37.4979, summary.anchor?.latitude ?: 0.0, 1e-6)
    }

    @Test
    fun `좌표가 아예 없으면 자리도 없다`() {
        assertEquals(null, summarize(session, emptyList()).anchor)
    }

    // -- 산책으로 칠 만한가 ------------------------------------------------

    /** 문을 눌렀다 닫은 것이 "1회" 로 잡히면 홈의 오늘 요약이 거짓이 된다. */
    @Test
    fun `너무 짧으면 산책이 아니다`() {
        assertFalse(walk(distanceMeters = 12.0, activeMillis = 40_000L).countsAsWalk)
    }

    /** **둘 다** 넘어야 한다. 50m 를 걸었어도 10초 만에 끝났으면 걸은 것이 아니다. */
    @Test
    fun `거리만 넘고 시간이 모자라면 아니다`() {
        assertFalse(walk(distanceMeters = 500.0, activeMillis = 20_000L).countsAsWalk)
    }

    /** 1분을 서 있었어도 제자리면 산책이 아니다. */
    @Test
    fun `시간만 넘고 거리가 모자라면 아니다`() {
        assertFalse(walk(distanceMeters = 8.0, activeMillis = 600_000L).countsAsWalk)
    }

    /** 경계는 **인정한다** — 딱 50m 걷고 "왜 기록이 없지" 가 되면 안 된다. */
    @Test
    fun `딱 기준만큼이면 산책이다`() {
        assertTrue(walk(distanceMeters = 50.0, activeMillis = 60_000L).countsAsWalk)
    }

    // -- 오늘 합산 ---------------------------------------------------------

    @Test
    fun `오늘 걸은 것만 합산한다`() {
        val totals = listOf(
            walk(startedAt = TODAY_MORNING, distanceMeters = 1_200.0, activeMillis = 900_000L),
            walk(startedAt = TODAY_EVENING, distanceMeters = 800.0, activeMillis = 600_000L),
            // 어제 것은 안 센다.
            walk(startedAt = DAY_START - 1L, distanceMeters = 5_000.0, activeMillis = 3_600_000L),
        ).totalsFor(DAY_START, DAY_END)

        assertEquals(2, totals.count)
        assertEquals(1_500_000L, totals.activeDurationMillis)
        assertEquals(2_000.0, totals.distanceMeters, 0.001)
    }

    /** 자정을 넘겨 걸은 산책은 **나선 날**의 것이다. */
    @Test
    fun `시작한 날로 센다`() {
        val overnight = walk(
            startedAt = DAY_END - 60_000L,
            distanceMeters = 900.0,
            activeMillis = 1_800_000L,
        )
        assertEquals(1, listOf(overnight).totalsFor(DAY_START, DAY_END).count)
    }

    /** 너무 짧은 것은 횟수에도 안 들어간다. */
    @Test
    fun `짧은 산책은 오늘 합산에서 빠진다`() {
        val totals = listOf(
            walk(startedAt = TODAY_MORNING, distanceMeters = 10.0, activeMillis = 5_000L),
        ).totalsFor(DAY_START, DAY_END)

        assertEquals(0, totals.count)
        assertEquals(0.0, totals.distanceMeters, 0.001)
    }

    @Test
    fun `기록이 없으면 0 이다`() {
        val totals = emptyList<WalkSummary>().totalsFor(DAY_START, DAY_END)
        assertEquals(WalkDayTotals.EMPTY, totals)
    }

    private fun walk(
        startedAt: Long = TODAY_MORNING,
        distanceMeters: Double,
        activeMillis: Long,
    ) = WalkSummary(
        sessionId = "s",
        dogIds = emptyList(),
        startedAtMillis = startedAt,
        endedAtMillis = startedAt + activeMillis,
        weather = null,
        distanceMeters = distanceMeters,
        activeDurationMillis = activeMillis,
        segments = emptyList(),
        anchor = null,
    )

    private companion object {
        const val DAY_START = 1_756_566_000_000L
        const val DAY_END = DAY_START + 86_400_000L
        const val TODAY_MORNING = DAY_START + 32_400_000L
        const val TODAY_EVENING = DAY_START + 68_400_000L
    }
}
