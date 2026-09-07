package com.daengs.app.map.layers.stays

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.style.WalkSpeedPoint
import com.daengs.app.map.layers.trail.TrailLayerState
import com.daengs.app.map.shell.*
import com.daengs.app.walk.StayStamp
import org.junit.Assert.*
import org.junit.Test

class StayStampMarkerTest {
    private val origin = GeoPoint(37.5, 127.0)
    private val stamp = StayStamp(1, origin, 0, 30_000)
    private fun vertex(t: Long, offset: Double = 0.00001) = WalkSpeedPoint(origin.copy(latitude = origin.latitude + offset), t)

    @Test fun `latest vertex from own window stays fixed when future route grows`() {
        val path = listOf(vertex(0, 0.0), vertex(20_000), vertex(30_000, .00002))
        val expected = anchorStayStamps(listOf(stamp), listOf(path))
        assertEquals(path.last().point, expected.single().point)
        assertEquals(expected, anchorStayStamps(listOf(stamp), listOf(path + vertex(80_000, .00003))))
    }

    @Test fun `missing off-route single-point and wrong-time geometry defer stamp`() {
        for (path in listOf(emptyList(), listOf(vertex(0)), listOf(vertex(0, .01), vertex(30_000, .02)),
            listOf(vertex(40_000), vertex(60_000, .00002)))) {
            assertTrue(anchorStayStamps(listOf(stamp), listOf(path)).isEmpty())
        }
    }

    @Test fun `only visible walk geometry can supply stamps and owner moments stay separate`() {
        val path = listOf(vertex(0, 0.0), vertex(30_000))
        val sources = MapSceneSources(stayStamps = listOf(stamp), trail = TrailLayerState(speedPaths = listOf(path)))
        assertEquals(1, composeMapScene(MapPurpose.WALK, sources).stayStamps.size)
        assertTrue(composeMapScene(MapPurpose.WALK, sources).moments.isEmpty())
        assertTrue(composeMapScene(MapPurpose.PLACE_SEARCH, sources, true).stayStamps.isEmpty())
        assertTrue(composeMapScene(MapPurpose.TERRITORY, sources, false).stayStamps.isEmpty())
        assertEquals(1, composeMapScene(MapPurpose.TERRITORY, sources, true).stayStamps.size)
    }
}
