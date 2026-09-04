package com.daengs.app.map.shell

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.completedroute.RouteEndpointKind
import com.daengs.app.map.layers.completedroute.RouteEndpointMarkerState
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.layers.places.PlaceMarkerState
import com.daengs.app.map.layers.territory.TerritorySiteMarkerState
import com.daengs.app.map.layers.trail.TrailLayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScenePolicyTest {
    private val point = GeoPoint(37.5, 127.0)
    private val sources = MapSceneSources(
        currentPosition = point,
        places = listOf(PlaceMarkerState("place", point, "시설")),
        territorySites = listOf(TerritorySiteMarkerState("site", point)),
        moments = listOf(MomentMarkerState("moment", point, "순간")),
        trail = TrailLayerState(paths = listOf(listOf(point, GeoPoint(37.5001, 127.0)))),
        completedRoute = CompletedRouteLayerState(
            start = RouteEndpointMarkerState("start", point, "출발", RouteEndpointKind.START),
        ),
    )

    @Test
    fun `territory receives only neutral sites and an active walk trail`() {
        val scene = composeMapScene(MapPurpose.TERRITORY, sources, walkActive = true)

        assertEquals(point, scene.currentPosition)
        assertEquals(listOf("site"), scene.territorySites.map { it.id })
        assertTrue(scene.places.isEmpty())
        assertTrue(scene.moments.isEmpty())
        assertNull(scene.completedRoute.start)
        assertTrue(scene.trail.paths.isNotEmpty())
        assertEquals(BaseMapStyle.TERRITORY_FOCUSED, scene.baseMapStyle)
    }

    @Test
    fun `territory hides an idle walk trail`() {
        val scene = composeMapScene(MapPurpose.TERRITORY, sources, walkActive = false)

        assertTrue(scene.trail.paths.isEmpty())
    }

    @Test
    fun `place search cannot receive walk or territory layers`() {
        val scene = composeMapScene(MapPurpose.PLACE_SEARCH, sources, walkActive = true)

        assertEquals(listOf("place"), scene.places.map { it.id })
        assertTrue(scene.territorySites.isEmpty())
        assertTrue(scene.moments.isEmpty())
        assertTrue(scene.trail.paths.isEmpty())
        assertEquals(BaseMapStyle.SEARCH_DETAIL, scene.baseMapStyle)
    }

    @Test
    fun `walk cannot receive facility or territory markers`() {
        val scene = composeMapScene(MapPurpose.WALK, sources, walkActive = true)

        assertTrue(scene.places.isEmpty())
        assertTrue(scene.territorySites.isEmpty())
        assertTrue(scene.moments.isNotEmpty())
        assertTrue(scene.trail.paths.isNotEmpty())
    }
}
