package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.map.layers.completedroute.RouteEndpointKind
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.toSessionRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkCompletedRoutePresentationTest {
    @Test
    fun `출발과 도착이 떨어져 있으면 서로 다른 핀을 만든다`() {
        val route = route(listOf(listOf(sample(1_000L, 37.5), sample(3_000L, 37.501))))
        val layer = route.toCompletedRouteLayerState(formatTime = { it.toString() })

        assertEquals(ROUTE_START_ID, layer.start?.id)
        assertEquals(RouteEndpointKind.START, layer.start?.kind)
        assertEquals(ROUTE_END_ID, layer.end?.id)
        assertEquals(RouteEndpointKind.END, layer.end?.kind)
    }

    @Test
    fun `출발과 도착이 가까우면 출발도착 핀 하나로 합친다`() {
        val route = route(listOf(listOf(sample(1_000L, 37.5), sample(3_000L, 37.50005))))
        val layer = route.toCompletedRouteLayerState(formatTime = { it.toString() })

        assertEquals(ROUTE_START_END_ID, layer.start?.id)
        assertEquals(RouteEndpointKind.START_END, layer.start?.kind)
        assertNull(layer.end)
    }

    @Test
    fun `세그먼트 사이 양쪽 점을 끊김 표시로 넘긴다`() {
        val route = route(
            listOf(
                listOf(sample(1_000L, 37.5), sample(3_000L, 37.501)),
                listOf(sample(5_000L, 37.6), sample(7_000L, 37.601)),
            ),
        )

        val layer = route.toCompletedRouteLayerState(formatTime = { it.toString() })

        assertEquals(listOf(GeoPoint(37.501, 127.0), GeoPoint(37.6, 127.0)), layer.gapEndpoints)
    }

    @Test
    fun `고른 경로점은 지도 선택점과 출발 핀 선택 상태에 함께 반영된다`() {
        val route = route(listOf(listOf(sample(1_000L, 37.5), sample(3_000L, 37.501))))
        val layer = route.toCompletedRouteLayerState(
            selectedPoint = route.start,
            formatTime = { it.toString() },
        )

        assertTrue(layer.start?.selected == true)
        assertEquals(route.start?.point, layer.selectedPoint)
    }

    private fun route(segments: List<List<LocationSample>>) = WalkSummary(
        sessionId = "walk-1",
        dogIds = emptyList(),
        startedAtMillis = 0L,
        endedAtMillis = 10_000L,
        weather = null,
        distanceMeters = 0.0,
        activeDurationMillis = 0L,
        segments = segments,
        anchor = segments.firstOrNull()?.firstOrNull()?.point,
    ).toSessionRoute()

    private fun sample(atMillis: Long, lat: Double) = LocationSample(
        point = GeoPoint(lat, 127.0),
        capturedAtMillis = atMillis,
        accuracyMeters = 5f,
    )
}
