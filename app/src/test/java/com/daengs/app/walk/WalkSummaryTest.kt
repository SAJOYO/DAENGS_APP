package com.daengs.app.walk

import org.junit.Assert.assertEquals
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

    private val session = RecordedSession(id = "s1", dogId = "dog-1", startedAtMillis = 1_000L)

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
        val fixes = listOf(
            fix(0, 0, 1_000L, 37.5000, 127.0000),
            fix(1, 0, 3_000L, 37.5010, 127.0000),
            fix(2, 0, 5_000L, 37.5020, 127.0000),
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
                fix(1, 0, 3_000L, 37.5010, 127.0000),
                // 여기서 일시정지 — 한참 뒤 먼 곳에서 다시 시작
                fix(2, 1, 900_000L, 37.6000, 127.1000),
                fix(3, 1, 902_000L, 37.6010, 127.1000),
            ),
        )
        assertEquals("세그먼트가 둘이어야 한다", 2, summary.segments.size)
        // 두 구간 사이 15분은 걸은 시간이 아니다. 각 구간 안의 2초씩만 센다.
        assertEquals(4_000L, summary.activeDurationMillis)
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
}
