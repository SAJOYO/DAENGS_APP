package com.daengs.app.map.layout

import org.junit.Assert.*
import org.junit.Test

class DetachedMarkerLayoutTest {
    private val viewport=MarkerRect(0.0,0.0,360.0,400.0)
    private fun glyph(id:String,x:Double,y:Double,width:Double=36.0,height:Double=36.0):MarkerGlyph {
        val point=MarkerPoint(id,x,y)
        return MarkerGlyph(MarkerGroup(listOf(point),point),MarkerFootprint(width,height))
    }
    @Test fun `wide actions and scenes avoid each other route controls and fixed endpoints`() {
        val path=listOf(MarkerRouteEdge(MarkerPoint("",180.0,20.0),MarkerPoint("",180.0,380.0)))
        val controls=listOf(MarkerRect(0.0,0.0,130.0,60.0),MarkerRect(148.0,330.0,210.0,360.0))
        val input=listOf(glyph("scene",180.0,150.0),glyph("action",190.0,158.0,108.0,44.0),glyph("scene2",180.0,330.0))
        val result=placeDetachedMarkers(input,viewport,controls,path)
        assertTrue(result.none { it.crowded })
        result.forEach { a->
            assertTrue(viewport.contains(a.bounds)); assertTrue(path.none { it.intersects(a.bounds) })
            assertTrue(controls.none { it.intersects(a.bounds,4.0) })
            assertTrue(result.filter { it!=a }.none { it.bounds.intersects(a.bounds,4.0) })
            assertTrue(a.point.distance(a.glyph.group.anchor)<=96.001)
        }
        assertEquals(input.map { it.group.anchor },result.map { it.glyph.group.anchor })
    }
    @Test fun `no space returns undrawn identities instead of overlapping success`() {
        val input=(1..12).map { glyph("$it",20.0,20.0) }
        val result=placeDetachedMarkers(input,MarkerRect(0.0,0.0,40.0,40.0))
        assertEquals(1,result.count { !it.crowded })
        assertEquals(input.map { it.group.key },result.map { it.glyph.group.key })
    }
    @Test fun `existing placements survive selection and input reordering`() {
        val input=listOf(glyph("a",100.0,150.0),glyph("b",110.0,155.0))
        val first=placeDetachedMarkers(input,viewport)
        val offsets=first.associate { it.glyph.group.key to it.point.copy(x=it.point.x-it.glyph.group.anchor.x,y=it.point.y-it.glyph.group.anchor.y) }
        val changed=placeDetachedMarkers(input.reversed().map { it.copy(priority=2) },viewport,previousOffsets=offsets)
        assertEquals(first.map { it.point },changed.reversed().map { it.point })
    }
    @Test fun `route fully covering an area yields no false free slot`() {
        val path=(0..400 step 10).map { y->MarkerRouteEdge(MarkerPoint("",0.0,y.toDouble()),MarkerPoint("",360.0,y.toDouble())) }
        assertTrue(placeDetachedMarkers(listOf(glyph("s",180.0,200.0)),viewport,route=path).single().crowded)
    }
    @Test fun `diagonal route collision checks segments rather than entire bounding box`() {
        val edge=MarkerRouteEdge(MarkerPoint("",0.0,0.0),MarkerPoint("",300.0,300.0))
        assertFalse(edge.intersects(MarkerRect(20.0,220.0,60.0,260.0)))
        assertTrue(edge.intersects(MarkerRect(100.0,100.0,130.0,130.0)))
        assertTrue(edge.intersects(MarkerRect(100.0,105.0,110.0,115.0)))
    }
    @Test fun `dense mixed footprints never report overlaps as placed`() {
        val input=(0..35).map { i->glyph("$i",140.0+(i%6)*15,140.0+(i/6)*15,if(i%3==0) 76.0 else 36.0,40.0) }
        val result=placeDetachedMarkers(input,viewport).filterNot { it.crowded }
        result.forEachIndexed { i,a->result.drop(i+1).forEach { b->assertFalse(a.bounds.intersects(b.bounds,4.0)) } }
        assertTrue(result.size>5)
    }
}
