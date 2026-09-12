package com.daengs.app.map.layers.completedroute

import kotlin.math.*

internal data class RouteScreenPoint(val x: Double, val y: Double) {
    val valid get() = x.isFinite() && y.isFinite()
    fun distance(other: RouteScreenPoint) = hypot(x - other.x, y - other.y)
}
internal data class RouteScreenEdge(val id: String, val a: RouteScreenPoint, val b: RouteScreenPoint) {
    val length get() = a.distance(b)
    fun point(t: Double) = RouteScreenPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    fun distance(p: RouteScreenPoint): Double {
        val dx = b.x - a.x; val dy = b.y - a.y
        val t = if (length < .01) 0.0 else ((p.x - a.x) * dx + (p.y - a.y) * dy) / (length * length)
        return p.distance(point(t.coerceIn(0.0, 1.0)))
    }
}
internal data class RouteScreenRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun contains(p: RouteScreenPoint, margin: Double = 0.0) =
        p.x >= left + margin && p.x <= right - margin && p.y >= top + margin && p.y <= bottom - margin
    fun near(p: RouteScreenPoint, radius: Double) =
        p.x >= left - radius && p.x <= right + radius && p.y >= top - radius && p.y <= bottom + radius
}
internal data class RouteDirectionArrow(val id: String, val center: RouteScreenPoint, val angle: Float, val side: Int)

/** Screen-space policy: there is deliberately no zoom threshold. Numbers are dp tuning values. */
internal fun placeRouteDirections(
    edges: List<RouteScreenEdge>, visible: RouteScreenRect, exclusions: List<RouteScreenRect>,
    density: Double, previousSides: Map<String, Int> = emptyMap(),
    distinguishPasses: Boolean = false,
    collisionEdges: List<RouteScreenEdge> = edges,
): List<RouteDirectionArrow> {
    if (density <= 0 || visible.right <= visible.left || visible.bottom <= visible.top) return emptyList()
    val valid = edges.filter { it.a.valid && it.b.valid && it.length > 1.0 }
    val routeObstacles = collisionEdges.filter { it.a.valid && it.b.valid && it.length > 1.0 }
    val spacing = 110 * density
    val radius = 12 * density
    val offset = 23 * density
    val guides = mutableListOf<RouteScreenEdge>()
    var run = mutableListOf<RouteScreenEdge>()
    fun flush() {
        if (run.isNotEmpty()) guides += RouteScreenEdge(run.first().id, run.first().a, run.last().b)
        run = mutableListOf()
    }
    for (edge in valid) {
        if (run.isNotEmpty()) {
            val merged = RouteScreenEdge(run.first().id, run.first().a, edge.b)
            if (run.first().id.substringBefore(":") != edge.id.substringBefore(":") ||
                run.last().b.distance(edge.a) > .01 ||
                run.any { merged.distance(it.b) > 3 * density }) flush()
        }
        run += edge
        if (run.first().a.distance(edge.b) >= 40 * density || run.size >= 80) flush()
    }
    flush()
    val candidates = guides.flatMap { edge ->
        // Stable fractions keep a candidate's side across small camera changes.
        listOf(.25, .5, .75).mapNotNull { t ->
            val anchor = edge.point(t)
            if (edge.length < 20 * density || !visible.contains(anchor, radius)) null else edge to t
        }
    }
    if (candidates.isEmpty()) return emptyList()
    // Bound expensive collision checks and spread attempts over the entire visible route.
    val stride = max(1, ceil(candidates.size / 160.0).toInt())
    val attempts = candidates.filterIndexed { i, _ -> i % stride == 0 }
    val arrows = mutableListOf<RouteDirectionArrow>()
    for ((edge, t) in attempts) {
        val anchor = edge.point(t)
        val dx = (edge.b.x - edge.a.x) / edge.length
        val dy = (edge.b.y - edge.a.y) / edge.length
        if (!distinguishPasses && valid.any { other ->
            if (other.id == edge.id || other.distance(anchor) > 7 * density) false
            else {
                val dot = (dx * (other.b.x - other.a.x) + dy * (other.b.y - other.a.y)) / other.length
                dot < .7 // Crossing or opposite passages cannot be represented by one direction.
            }
        }) continue
        val id = edge.id + ":" + t
        val preferred = previousSides[id] ?: 1
        placement@ for (distance in listOf(offset, 48 * density)) for (side in listOf(preferred, -preferred)) {
            // Numbered scene pins can occupy the entire short visible passage. Try a wider lane
            // after both normal sides fail, while keeping all marker/route/viewport exclusions.
            val center = RouteScreenPoint(anchor.x - dy * distance * side, anchor.y + dx * distance * side)
            if (!visible.contains(center, radius) || exclusions.any { it.near(center, radius) } ||
                arrows.any { it.center.distance(center) < spacing } ||
                routeObstacles.any { it.distance(center) < max(radius + 4 * density, edge.distance(center) - 3 * density) }) continue
            val angle = (Math.toDegrees(atan2(dy, dx)) + 450) % 360
            arrows += RouteDirectionArrow(id, center, angle.toFloat(), side)
            break@placement
        }
        if (arrows.size == 6) break
    }
    return arrows
}
