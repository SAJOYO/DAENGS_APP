package com.daengs.app.map.layers.completedroute

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.trail.TrailLayerState
import com.daengs.app.map.shell.*
import org.junit.Assert.*
import org.junit.Test

class RouteEndpointStampsTest {
    private val point = GeoPoint(37.5, 127.0)

    @Test fun `recording has one fixed start and no speculative arrival`() {
        val scene = MapScene(trail = TrailLayerState(startPoint = point))
        assertEquals(RouteEndpointKind.START, scene.routeEndpointStamps().single().kind)
        assertEquals(point, scene.routeEndpointStamps().single().point)
        assertTrue(MapScene().routeEndpointStamps().isEmpty())
    }

    @Test fun `completion replaces live start without duplicating it`() {
        val start = RouteEndpointMarkerState("s", point, "출발", RouteEndpointKind.START)
        val end = RouteEndpointMarkerState("e", point.copy(latitude = 37.6), "도착", RouteEndpointKind.END)
        val scene = MapScene(trail = TrailLayerState(startPoint = point), completedRoute = CompletedRouteLayerState(start = start, end = end))
        assertEquals(listOf(start, end), scene.routeEndpointStamps())
    }

    @Test fun `search and inactive territory cannot leak the live start`() {
        val sources = MapSceneSources(trail = TrailLayerState(startPoint = point))
        assertTrue(composeMapScene(MapPurpose.PLACE_SEARCH, sources, true).routeEndpointStamps().isEmpty())
        assertTrue(composeMapScene(MapPurpose.TERRITORY, sources, false).routeEndpointStamps().isEmpty())
        assertEquals(1, composeMapScene(MapPurpose.TERRITORY, sources, true).routeEndpointStamps().size)
    }
}
