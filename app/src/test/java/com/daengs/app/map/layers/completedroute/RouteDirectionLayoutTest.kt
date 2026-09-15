package com.daengs.app.map.layers.completedroute

import org.junit.Assert.*
import org.junit.Test

class RouteDirectionLayoutTest {
    private val viewport = RouteScreenRect(0.0, 0.0, 400.0, 300.0)
    private fun edge(id: String, ax: Double, ay: Double, bx: Double, by: Double) =
        RouteScreenEdge(id, RouteScreenPoint(ax, ay), RouteScreenPoint(bx, by))

    @Test fun `numbered pins omit a short passage instead of moving arrows to a wider lane`() {
        val source = listOf(edge("observed:8-9", 200.0, 40.0, 200.0, 260.0))
        val pins = listOf(RouteScreenRect(172.0, 0.0, 228.0, 300.0))
        val arrows = placeRouteDirections(source, viewport, pins, 1.0)
        assertTrue(arrows.isEmpty())
        val sidesBlocked = source + listOf(edge("left", 160.0, 0.0, 160.0, 300.0), edge("right", 240.0, 0.0, 240.0, 300.0))
        assertTrue(placeRouteDirections(source, viewport, pins, 1.0, collisionEdges = sidesBlocked).isEmpty())
    }

    @Test fun `overview already shows arrows outside a dense GPS path`() {
        val edges = (0 until 60).map { edge("0:$it", 40.0 + it * 5, 140.0, 45.0 + it * 5, 140.0) }
        val arrows = placeRouteDirections(edges, viewport, emptyList(), 1.0)
        assertTrue(arrows.size in 2..5)
        assertTrue(arrows.all { kotlin.math.abs(it.center.y - 156) < .01 })
        assertTrue(arrows.all { kotlin.math.abs(it.angle - 90) < .1 })
    }
    @Test fun `opposite passages cannot collapse into one misleading arrow`() {
        val edges = listOf(edge("0:0", 30.0, 140.0, 350.0, 140.0),
            edge("1:0", 350.0, 140.0, 30.0, 140.0))
        assertTrue(placeRouteDirections(edges, viewport, emptyList(), 1.0).isEmpty())
        assertTrue(placeRouteDirections(edges.take(1), viewport, emptyList(), 1.0, distinguishPasses = true).isNotEmpty())
    }
    @Test fun `blocked lane is omitted instead of switching sides`() {
        val edges = listOf(edge("0:0", 40.0, 140.0, 360.0, 140.0))
        val blocked = listOf(RouteScreenRect(0.0, 145.0, 400.0, 200.0))
        val first = placeRouteDirections(edges, viewport, blocked, 1.0)
        assertTrue(first.isEmpty())
        assertTrue(placeRouteDirections(edges, viewport, emptyList(), 1.0).isNotEmpty())
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
        assertTrue(arrows.isEmpty())
    }

    @Test fun `small pan and zoom retain legal source anchors but recalculate pixel locations`() {
        val edges = listOf(edge("0:0", 40.0, 140.0, 340.0, 140.0))
        val first = placeRouteDirections(edges, viewport, emptyList(), 1.0)
        assertTrue(first.size >= 2)
        val moved = listOf(edge("0:0", 43.0, 146.0, 349.0, 146.0))
        val next = placeRouteDirections(moved, viewport, emptyList(), 1.0, previous = first)
        assertEquals(first.map { it.anchor }, next.take(first.size).map { it.anchor })
        first.zip(next).forEach { (a, b) ->
            assertEquals(43 + a.anchor.fraction * 306, b.center.x, .001)
            assertEquals(162.0, b.center.y, .001)
        }
    }

    @Test fun `retained anchors must pass new pin and route collisions`() {
        val edges = listOf(edge("0:0", 40.0, 140.0, 360.0, 140.0))
        val first = placeRouteDirections(edges, viewport, emptyList(), 1.0)
        val p = first.first().center
        val pin = RouteScreenRect(p.x - 15, p.y - 15, p.x + 15, p.y + 15)
        val next = placeRouteDirections(edges, viewport, listOf(pin), 1.0, previous = first)
        assertTrue(next.none { pin.near(it.center, 9.0) })
        assertTrue(next.none { it.anchor == first.first().anchor })
        val blocked = listOf(edge("other:0", 0.0, 156.0, 400.0, 156.0))
        assertTrue(placeRouteDirections(edges, viewport, emptyList(), 1.0, first,
            collisionEdges = blocked).isEmpty())
    }

    @Test fun `sharp corner has no diagonal direction inferred across it`() {
        val edges = listOf(edge("0:0", 30.0, 150.0, 200.0, 150.0),
            edge("0:1", 200.0, 150.0, 200.0, 30.0))
        val candidates = freshDirectionCandidates(directionChains(edges), viewport, 1.0)
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { kotlin.math.abs(it.dx) < .001 || kotlin.math.abs(it.dy) < .001 })
    }

    @Test fun `subpixel GPS edges still make readable overview directions`() {
        val edges = (0 until 600).map { edge("0:$it", 40 + it * .5, 140.0, 40.5 + it * .5, 140.0) }
        assertTrue(placeRouteDirections(edges, viewport, emptyList(), 1.0).size >= 2)
    }

    @Test fun `offscreen route cannot exhaust visible candidate budget`() {
        val edges = listOf(edge("0:0", -100000.0, 140.0, 350.0, 140.0))
        val candidates = freshDirectionCandidates(directionChains(edges), viewport, 1.0)
        assertTrue(candidates.size <= RouteDirectionPolicy.maxCandidates)
        assertTrue(placeRouteDirections(edges, viewport, emptyList(), 1.0).size >= 2)
    }

    @Test fun `short disconnected or invalid spans cannot be bridged into a tangent`() {
        val broken = listOf(edge("0:0", 40.0, 140.0, 50.0, 140.0),
            edge("0:1", 55.0, 140.0, 65.0, 140.0))
        assertTrue(placeRouteDirections(broken, viewport, emptyList(), 1.0).isEmpty())
        val invalid = listOf(broken.first(), edge("0:1", Double.NaN, 0.0, 50.0, 140.0),
            edge("0:2", 50.0, 140.0, 60.0, 140.0))
        assertTrue(placeRouteDirections(invalid, viewport, emptyList(), 1.0).isEmpty())
        assertTrue(placeRouteDirections(broken, viewport, emptyList(), Double.NaN).isEmpty())
    }

    @Test fun `spacing and count remain bounded over long independent visible paths`() {
        val edges = (0..5).map { edge("$it:0", 30.0, 30.0 + it * 40, 370.0, 30.0 + it * 40) }
        val arrows = placeRouteDirections(edges, viewport, emptyList(), 1.0)
        assertEquals(5, arrows.size)
        arrows.forEachIndexed { i, a -> arrows.drop(i + 1).forEach { b ->
            assertTrue(a.center.distance(b.center) >= 100)
        } }
    }

    @Test fun `crossing excludes its ambiguous junction but preserves clear approaches`() {
        val edges = listOf(edge("0:0", 30.0, 150.0, 370.0, 150.0),
            edge("1:0", 200.0, 30.0, 200.0, 270.0))
        val arrows = placeRouteDirections(edges, viewport, emptyList(), 1.0)
        assertTrue(arrows.isNotEmpty())
        arrows.forEach { arrow ->
            val owner = edges.first { it.id == arrow.anchor.edgeId }
            assertTrue(owner.point(arrow.anchor.fraction).distance(RouteScreenPoint(200.0, 150.0)) > 7)
        }
    }
}
