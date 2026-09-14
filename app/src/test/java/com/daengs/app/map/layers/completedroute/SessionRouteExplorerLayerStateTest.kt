package com.daengs.app.map.layers.completedroute

import com.daengs.app.location.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class SessionRouteExplorerLayerStateTest {
    @Test fun `unresolved scene has no overview arrows while an explicit overview keeps them`() {
        val overview = listOf(listOf(GeoPoint(37.5, 127.0), GeoPoint(37.501, 127.0)))
        assertEquals(overview, SessionRouteExplorerLayerState().directionPaths(overview))
        assertTrue(SessionRouteExplorerLayerState(useOverviewDirections = false).directionPaths(overview).isEmpty())
        val returning = overview.map { it.asReversed() }
        assertEquals(returning, SessionRouteExplorerLayerState(returning, useOverviewDirections = false).directionPaths(overview))
    }
}
