package com.daengs.app.map.layers.completedroute

import org.junit.Assert.*
import org.junit.Test

class RouteDirectionLayoutTest {
    private val viewport = RouteScreenRect(0.0, 0.0, 400.0, 300.0)
    private fun edge(id: String, ax: Double, ay: Double, bx: Double, by: Double) =
        RouteScreenEdge(id, RouteScreenPoint(ax, ay), RouteScreenPoint(bx, by))

    @Test fun `short passage beside numbered pins can use a wider lane without crossing another path`() {
        val source = listOf(edge("observed:8-9", 200.0, 40.0, 200.0, 260.0))
        val pins = listOf(RouteScreenRect(172.0, 0.0, 228.0, 300.0))
        val arrows = placeRouteDirections(source, viewport, pins, 1.0)
        assertTrue(arrows.isNotEmpty())
        assertTrue(arrows.all { kotlin.math.abs(it.center.x - 200) == 48.0 })
        val sidesBlocked = source + listOf(edge("left", 160.0, 0.0, 160.0, 300.0), edge("right", 240.0, 0.0, 240.0, 300.0))
        assertTrue(placeRouteDirections(source, viewport, pins, 1.0, collisionEdges = sidesBlocked).isEmpty())
    }

    @Test fun `overview already shows arrows outside a dense GPS path`() {
        val edges = (0 until 60).map { edge("0:$it", 40.0 + it * 5, 140.0, 45.0 + it * 5, 140.0) }
        val arrows = placeRouteDirections(edges, viewport, emptyList(), 1.0)
        assertTrue(arrows.size in 2..6)
        assertTrue(arrows.all { kotlin.math.abs(it.center.y - 140) >= 20 })
        assertTrue(arrows.all { kotlin.math.abs(it.angle - 90) < .1 })
    }
    @Test fun `opposite passages cannot collapse into one misleading arrow`() {
        val edges = listOf(edge("0:0", 30.0, 140.0, 350.0, 140.0),
            edge("1:0", 350.0, 140.0, 30.0, 140.0))
        assertTrue(placeRouteDirections(edges, viewport, emptyList(), 1.0).isEmpty())
        assertTrue(placeRouteDirections(edges.take(1), viewport, emptyList(), 1.0, distinguishPasses = true).isNotEmpty())
    }
    @Test fun `collision chooses the other side and remembers it`() {
        val edges = listOf(edge("0:0", 40.0, 140.0, 360.0, 140.0))
        val blocked = listOf(RouteScreenRect(0.0, 145.0, 400.0, 200.0))
        val first = placeRouteDirections(edges, viewport, blocked, 1.0)
        assertTrue(first.isNotEmpty())
        assertTrue(first.all { it.side == -1 })
        val next = placeRouteDirections(edges, viewport, emptyList(), 1.0, first.associate { it.id to it.side })
        assertEquals(first, next)
    }
    @Test fun `fully occluded route and invalid projected points stay hidden`() {
        assertTrue(placeRouteDirections(listOf(edge("0", 40.0, 140.0, 360.0, 140.0)),
            viewport, listOf(viewport), 1.0).isEmpty())
        assertTrue(placeRouteDirections(listOf(edge("invalid", Double.NaN, 0.0, 100.0, 140.0)),
            viewport, emptyList(), 1.0).isEmpty())
    }
    @Test fun `density scaling preserves placement and rotation follows screen tangent`() {
        val original = listOf(edge("0", 40.0, 140.0, 360.0, 140.0))
        val scaled = listOf(edge("0", 80.0, 280.0, 720.0, 280.0))
        val a = placeRouteDirections(original, viewport, emptyList(), 1.0)
        val b = placeRouteDirections(scaled, RouteScreenRect(0.0, 0.0, 800.0, 600.0), emptyList(), 2.0)
        assertEquals(a.size, b.size)
        assertEquals(a.first().center.x * 2, b.first().center.x, .01)
        val rotated = placeRouteDirections(listOf(edge("0", 140.0, 30.0, 140.0, 270.0)),
            viewport, emptyList(), 1.0)
        assertEquals(180f, rotated.first().angle, .01f)
        val northwest = placeRouteDirections(listOf(edge("0", 300.0, 260.0, 60.0, 20.0)),
            viewport, emptyList(), 1.0)
        assertEquals(315f, northwest.first().angle, .01f)
    }
    @Test fun `selected pass arrows still avoid unselected route geometry`() {
        val selected = listOf(edge("0", 40.0, 140.0, 360.0, 140.0))
        val obstacles = selected + edge("1", 40.0, 163.0, 360.0, 163.0)
        val arrows = placeRouteDirections(selected, viewport, emptyList(), 1.0,
            distinguishPasses = true, collisionEdges = obstacles)
        assertTrue(arrows.isNotEmpty())
        assertTrue(arrows.all { it.center.y < 140.0 })
    }
}
