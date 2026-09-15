package com.daengs.app.map.layers.completedroute

import kotlin.math.*

/** Source address survives projection changes; screen coordinates are always recomputed. */
internal data class RouteDirectionAnchor(val edgeId: String, val fraction: Double)
internal data class DirectionCandidate(
    val anchor: RouteDirectionAnchor, val point: RouteScreenPoint,
    val dx: Double, val dy: Double, val straightness: Double,
)

internal class DirectionChain(val edges: List<RouteScreenEdge>) {
    private val ends = DoubleArray(edges.size)
    init { edges.forEachIndexed { i, e -> ends[i] = e.length + if (i == 0) 0.0 else ends[i - 1] } }
    val length get() = ends.last()
    private fun start(i: Int) = if (i == 0) 0.0 else ends[i - 1]
    private fun index(s: Double): Int {
        val found = ends.binarySearch(s)
        return (if (found >= 0) found else -found - 1).coerceIn(edges.indices)
    }
    private fun point(s: Double): RouteScreenPoint {
        val i = index(s)
        return edges[i].point(((s - start(i)) / edges[i].length).coerceIn(0.0, 1.0))
    }
    fun distance(anchor: RouteDirectionAnchor): Double? {
        val i = edges.indexOfFirst { it.id == anchor.edgeId }
        return if (i < 0 || !anchor.fraction.isFinite() || anchor.fraction !in 0.0..1.0) null
            else start(i) + edges[i].length * anchor.fraction
    }
    fun candidate(s: Double, density: Double, retained: RouteDirectionAnchor? = null): DirectionCandidate? {
        val half = RouteDirectionPolicy.tangentHalfWindowDp * density
        if (s < half || s > length - half) return null
        val a = point(s - half); val b = point(s + half)
        val chord = a.distance(b)
        val straightness = chord / (2 * half)
        if (straightness < RouteDirectionPolicy.minimumStraightness) return null
        val guide = RouteScreenEdge("tangent", a, b)
        if ((index(s - half)..index(s + half)).any { i ->
            ends[i] < s + half && guide.distance(edges[i].b) > RouteDirectionPolicy.maxBendDp * density
        }) return null
        val i = index(s)
        val anchor = retained ?: RouteDirectionAnchor(edges[i].id, (s - start(i)) / edges[i].length)
        return DirectionCandidate(anchor, point(s), (b.x - a.x) / chord, (b.y - a.y) / chord, straightness)
    }
    /** Clip before sampling so an offscreen kilometre cannot consume the candidate budget. */
    fun visibleSpans(rect: RouteScreenRect): List<ClosedFloatingPointRange<Double>> {
        val spans = mutableListOf<ClosedFloatingPointRange<Double>>()
        edges.forEachIndexed { i, edge ->
            val clipped = clipDirectionEdge(edge, rect) ?: return@forEachIndexed
            val from = start(i) + clipped.start * edge.length
            val to = start(i) + clipped.endInclusive * edge.length
            if (spans.isNotEmpty() && abs(spans.last().endInclusive - from) < .01) {
                spans[spans.lastIndex] = spans.last().start..to
            } else spans += from..to
        }
        return spans
    }
}

internal fun directionChains(edges: List<RouteScreenEdge>): List<DirectionChain> {
    val chains = mutableListOf<DirectionChain>()
    var run = mutableListOf<RouteScreenEdge>()
    fun flush() { if (run.isNotEmpty()) chains += DirectionChain(run); run = mutableListOf() }
    for (edge in edges) {
        if (!edge.a.valid || !edge.b.valid || !edge.length.isFinite()) { flush(); continue }
        if (run.isNotEmpty() && (run.last().id.substringBefore(":") != edge.id.substringBefore(":") ||
            run.last().b.distance(edge.a) > .01)) flush()
        // Keep subpixel observations; zooming out must not erase an otherwise long chain.
        if (edge.length > .000001) run += edge
    }
    flush()
    return chains
}

internal fun freshDirectionCandidates(
    chains: List<DirectionChain>, visible: RouteScreenRect, density: Double,
): List<DirectionCandidate> {
    val spans = chains.flatMap { chain -> chain.visibleSpans(visible).map { chain to it } }
    val length = spans.sumOf { (_, span) -> span.endInclusive - span.start }
    val step = max(RouteDirectionPolicy.sampleStepDp * density, length / RouteDirectionPolicy.maxCandidates)
    val samples = spans.flatMap { (chain, span) ->
        val count = max(1, ceil((span.endInclusive - span.start) / step).toInt())
        (0 until count).map { i -> chain to (span.start + (i + .5) * (span.endInclusive - span.start) / count) }
    }
    val stride = max(1, ceil(samples.size.toDouble() / RouteDirectionPolicy.maxCandidates).toInt())
    return samples.filterIndexed { i, _ -> i % stride == 0 }
        .mapNotNull { (chain, s) -> chain.candidate(s, density) }
        .sortedByDescending { it.straightness }
}

private fun clipDirectionEdge(edge: RouteScreenEdge, rect: RouteScreenRect): ClosedFloatingPointRange<Double>? {
    var from = 0.0; var to = 1.0
    val dx = edge.b.x - edge.a.x; val dy = edge.b.y - edge.a.y
    for ((p, q) in listOf(-dx to edge.a.x - rect.left, dx to rect.right - edge.a.x,
        -dy to edge.a.y - rect.top, dy to rect.bottom - edge.a.y)) {
        if (abs(p) < 1e-12) { if (q < 0) return null }
        else if (p < 0) from = max(from, q / p) else to = min(to, q / p)
        if (from > to) return null
    }
    return from..to
}
