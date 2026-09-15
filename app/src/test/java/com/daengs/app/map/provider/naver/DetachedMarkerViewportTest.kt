package com.daengs.app.map.provider.naver

import androidx.compose.ui.unit.IntSize
import com.daengs.app.map.layout.*
import com.daengs.app.map.shell.MapVisibilityQuery
import org.junit.Assert.*
import org.junit.Test

class DetachedMarkerViewportTest {
    @Test fun `records header and drawer constrain the full pin bounds at device density`() {
        val viewport = markerViewport(IntSize(720,1280), 2f, 300, 400)
        assertEquals(MarkerRect(4.0,154.0,356.0,436.0), viewport)
        listOf(MarkerPoint("top",180.0,150.0), MarkerPoint("bottom",180.0,440.0)).forEach { point ->
            val placed = placeDetachedMarkers(listOf(MarkerGlyph(MarkerGroup(listOf(point),point),
                MarkerFootprint(44.0,44.0))),viewport).single()
            assertFalse(placed.crowded)
            assertTrue(placed.bounds.top >= viewport.top)
            assertTrue(placed.bounds.bottom <= viewport.bottom)
        }
    }

    @Test fun `offset map settings are excluded at their actual displayed position`() {
        val size = IntSize(720,1280)
        val query = MapVisibilityQuery("r", emptyList(), 600, 200, 100, 100, 120)
        val controls = markerExclusions(size, 2f, query)
        assertEquals(listOf(MarkerRect(0.0,0.0,100.0,50.0),MarkerRect(310.0,60.0,360.0,110.0)), controls)
        val point = MarkerPoint("scene", 325.0,90.0)
        val placement = placeDetachedMarkers(listOf(MarkerGlyph(MarkerGroup(listOf(point),point),MarkerFootprint(40.0,40.0))),
            markerViewport(size,2f,0,query.bottomOcclusionPx),controls).single()
        assertFalse(placement.crowded)
        assertTrue(controls.none { it.intersects(placement.bounds,4.0) })
        assertTrue(placement.bounds.bottom <= 336.0)
    }
}
