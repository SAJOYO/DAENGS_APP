package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkSessionRouteTest {
    @Test
    fun `출발과 도착은 첫 세그먼트의 첫 점과 마지막 세그먼트의 마지막 점이다`() {
        val route = summary(
            listOf(
                listOf(sample(1_000L, 37.5000), sample(3_000L, 37.5010)),
                listOf(sample(900_000L, 37.6000), sample(902_000L, 37.6010)),
            ),
        ).toSessionRoute()

        assertEquals(1_000L, route.start?.capturedAtMillis)
        assertEquals(902_000L, route.end?.capturedAtMillis)
        assertEquals(0, route.start?.segmentIndex)
        assertEquals(1, route.end?.segmentIndex)
    }

    @Test
    fun `끊긴 구간 사이의 거리와 시간은 누적하지 않는다`() {
        val route = summary(
            listOf(
                listOf(sample(1_000L, 37.5000), sample(3_000L, 37.5010)),
                listOf(sample(900_000L, 37.6000), sample(902_000L, 37.6010)),
            ),
        ).toSessionRoute()

        assertEquals(4_000L, route.end?.activeElapsedMillis)
        assertTrue(route.end!!.cumulativeDistanceMeters < 300.0)
        assertNull(route.segments[1].points[0].derivedSpeedMetersPerSecond)
    }

    @Test
    fun `저장된 활동 시간축이 있으면 화면 필터와 무관하게 그 값을 쓴다`() {
        val route = summary(
            segments = listOf(listOf(sample(1_000L, 37.5000), sample(8_000L, 37.5100))),
            activeElapsed = mapOf(1_000L to 0L, 8_000L to 7_000L),
        ).toSessionRoute()

        assertEquals(7_000L, route.end?.activeElapsedMillis)
    }

    @Test
    fun `지도에서 누른 자리는 허용 반경 안의 가장 가까운 기록점만 고른다`() {
        val route = summary(
            listOf(listOf(sample(1_000L, 37.5000), sample(3_000L, 37.5010))),
        ).toSessionRoute()

        assertEquals(1_000L, route.nearestPointTo(GeoPoint(37.50005, 127.0), 20.0)?.capturedAtMillis)
        assertNull(route.nearestPointTo(GeoPoint(37.5100, 127.0), 20.0))
    }

    @Test
    fun `GPS 점이 멀어도 그 사이에 그린 선을 누르면 구간을 고른다`() {
        val route = summary(
            listOf(listOf(sample(1_000L, 37.5000), sample(3_000L, 37.5010))),
        ).toSessionRoute()

        // 양 끝에서 약 55m라 꼭짓점만 재면 20m 반경 밖이지만, 실제 경로선 위다.
        val selected = route.nearestPointTo(GeoPoint(37.5005, 127.0), 20.0)

        assertEquals(3_000L, selected?.capturedAtMillis)
    }

    @Test
    fun `출발과 도착이 가까우면 겹친 핀 하나로 표시할 수 있다`() {
        val route = summary(
            listOf(listOf(sample(1_000L, 37.5000), sample(3_000L, 37.50005))),
        ).toSessionRoute()

        assertTrue(route.endpointsAreNear(12.0))
    }

    @Test
    fun `경로 점이 없으면 출발과 도착도 없다`() {
        val route = summary(emptyList()).toSessionRoute()
        assertNull(route.start)
        assertNull(route.end)
        assertTrue(route.bounds.isEmpty())
    }

    private fun sample(atMillis: Long, lat: Double) = LocationSample(
        point = GeoPoint(lat, 127.0),
        capturedAtMillis = atMillis,
        accuracyMeters = 5f,
    )

    private fun summary(
        segments: List<List<LocationSample>>,
        activeElapsed: Map<Long, Long> = emptyMap(),
    ) = WalkSummary(
        sessionId = "walk-1",
        dogIds = emptyList(),
        startedAtMillis = 500L,
        endedAtMillis = 903_000L,
        weather = null,
        distanceMeters = 0.0,
        activeDurationMillis = 0L,
        segments = segments,
        anchor = segments.firstOrNull()?.firstOrNull()?.point,
        activeElapsedAtMillis = activeElapsed,
    )
}
