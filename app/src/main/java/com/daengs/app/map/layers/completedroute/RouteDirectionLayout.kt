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
internal data class RouteDirectionArrow(val anchor: RouteDirectionAnchor, val center: RouteScreenPoint, val angle: Float) {
    val id get() = "${anchor.edgeId}:${anchor.fraction}"
}

/** Screen-space policy: there is deliberately no zoom threshold. Numbers are dp tuning values. */
internal fun placeRouteDirections(
    edges: List<RouteScreenEdge>, visible: RouteScreenRect, exclusions: List<RouteScreenRect>,
    density: Double, previous: List<RouteDirectionArrow> = emptyList(),
    distinguishPasses: Boolean = false,
    collisionEdges: List<RouteScreenEdge> = edges,
): List<RouteDirectionArrow> {
    if (!density.isFinite() || density <= 0 ||
        !listOf(visible.left, visible.top, visible.right, visible.bottom).all { it.isFinite() } ||
        visible.right <= visible.left || visible.bottom <= visible.top) return emptyList()
    val policy = RouteDirectionPolicy
    val chains = directionChains(edges)
    val valid = chains.flatMap { it.edges }
    val routeObstacles = (collisionEdges + edges).filter {
        it.a.valid && it.b.valid && it.length.isFinite() && it.length > .000001
    }
    val spacing = policy.spacingDp * density
    val radius = policy.iconSizeDp / 2.0 * density
    val offset = policy.offsetDp * density
    // Revalidate original anchors in the new projection before considering fresh candidates.
    val retained = previous.take(policy.maxArrows).mapNotNull { arrow ->
        chains.firstNotNullOfOrNull { chain ->
            chain.distance(arrow.anchor)?.let { chain.candidate(it, density, arrow.anchor) }
        }
    }
    val attempts = retained + freshDirectionCandidates(chains, visible, density)
    val arrows = mutableListOf<RouteDirectionArrow>()
    for (candidate in attempts) {
        val anchor = candidate.point
        val dx = candidate.dx; val dy = candidate.dy
        if (!visible.contains(anchor, radius)) continue
        if (!distinguishPasses && valid.any { other ->
            if (other.id == candidate.anchor.edgeId || other.distance(anchor) > policy.ambiguityDistanceDp * density) false
            else {
                val dot = (dx * (other.b.x - other.a.x) + dy * (other.b.y - other.a.y)) / other.length
                dot < policy.ambiguityDirectionCosine
            }
        }) continue
        // One lane on the right of travel. A blocked candidate is omitted, never moved farther away.
        val center = RouteScreenPoint(anchor.x - dy * offset, anchor.y + dx * offset)
        if (!visible.contains(center, radius) || exclusions.any { it.near(center, radius) } ||
            arrows.any { it.center.distance(center) < spacing } ||
            routeObstacles.any { it.distance(center) < max(radius + policy.routeClearanceDp * density,
                offset - policy.routeClearanceDp * density) }) continue
        val angle = (Math.toDegrees(atan2(dy, dx)) + 450) % 360
        arrows += RouteDirectionArrow(candidate.anchor, center, angle.toFloat())
        if (arrows.size == policy.maxArrows) break
    }
    return arrows
}
