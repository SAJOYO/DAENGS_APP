package com.daengs.app.map.layout

import org.junit.Assert.*
import org.junit.Test
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.moments.*

class MapMarkerLayoutTest {
    @Test fun `focused scene wins placement over inspected siblings after a group splits`() {
        val sibling = MomentMarkerState("sibling", GeoPoint(37.5,127.0), "1",
            diaryPin = DiaryPinAppearance(1, inspected = true))
        val focused = sibling.copy(id = "focused", selected = true, diaryPin = DiaryPinAppearance(2, inspected = true))
        val siblingPriority = momentGroupPriority(listOf(sibling))
        val focusedPriority = momentGroupPriority(listOf(focused))
        assertTrue(focusedPriority > siblingPriority)
        assertTrue(siblingPriority > momentGroupPriority(listOf(sibling.copy(diaryPin = DiaryPinAppearance(1)))))
        val input = listOf(glyph("sibling",100.0,100.0,72.0,46.0,siblingPriority),
            glyph("focused",145.0,100.0,72.0,46.0,focusedPriority))
        val result = placeMapMarkers(input)
        assertEquals(input[1].group.anchor, result[1].point)
        assertFalse(result[0].bounds.intersects(result[1].bounds, 4.0))
    }
    private fun glyph(id: String, x: Double, y: Double, width: Double, height: Double, priority: Int = 0): MarkerGlyph {
        val p = MarkerPoint(id,x,y)
        return MarkerGlyph(MarkerGroup(listOf(p),p),MarkerFootprint(width,height),priority)
    }
    @Test fun `wide art and selected art use actual rectangles and priority independent of input order`() {
        val input = listOf(glyph("a",100.0,100.0,104.0,40.0), glyph("b",145.0,100.0,48.0,46.0,1))
        val result = placeMapMarkers(input)
        assertEquals(input[1].group.anchor, result[1].point)
        assertFalse(result[0].bounds.intersects(result[1].bounds,4.0))
        assertTrue(result.none { it.crowded })
        assertEquals(result, placeMapMarkers(input.reversed()).reversed())
        result.forEach { assertTrue(it.point.distance(it.glyph.group.anchor) <= 48.001) }
    }
    @Test fun `edges panels and controls are excluded using measured anchor footprint`() {
        val viewport = MarkerRect(0.0,0.0,300.0,240.0)
        val control = MarkerRect(240.0,0.0,300.0,60.0)
        val tail = MarkerPoint("tail",12.0,24.0)
        val input = listOf(MarkerGlyph(MarkerGroup(listOf(tail),tail),MarkerFootprint(60.0,46.0,.5,1.0)),
            glyph("bottom",120.0,230.0,44.0,40.0),glyph("control",245.0,54.0,40.0,40.0))
        val result = placeMapMarkers(input,viewport,listOf(control))
        result.forEach { assertTrue(viewport.contains(it.bounds)); assertFalse(control.intersects(it.bounds,4.0)); assertFalse(it.crowded) }
        assertEquals(tail, result[0].glyph.group.anchor)
    }
    @Test fun `insufficient space is explicit preserves identities and never moves beyond policy`() {
        val input = (1..8).map { glyph("$it",20.0,20.0,40.0,40.0) }
        val result = placeMapMarkers(input,MarkerRect(0.0,0.0,40.0,40.0))
        assertEquals(input.map { it.group.key }, result.map { it.glyph.group.key })
        assertEquals(7,result.count { it.crowded })
        assertTrue(result.all { it.point.distance(it.glyph.group.anchor) <= 48 })
    }
    @Test fun `projection zoom splits nearby positions without losing same coordinate members`() {
        val points = (1..80).map { MarkerPoint("$it",0.0,if (it<=20) 0.0 else (it-20)*10.0) }
        val groups = clusterMapMarkers(points)
        assertEquals(points.map { it.id }.toSet(),groups.flatMap { it.points }.map { it.id }.toSet())
        groups.forEach { g -> assertTrue(g.points.all { a -> g.points.all { a.distance(it)<=44 } }) }
        val zoomed = clusterMapMarkers(points.map { it.copy(y=it.y*8) })
        assertEquals(20,zoomed.single { it.points.any { p -> p.id=="1" } }.points.size)
        assertTrue(zoomed.size>groups.size)
    }
}
