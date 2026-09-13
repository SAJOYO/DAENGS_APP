package com.daengs.app.map.layout

import kotlin.math.*

/** Projected screen dp. Record identity and provider rendering stay outside this policy. */
data class MarkerPoint(val id: String, val x: Double, val y: Double) {
    fun distance(other: MarkerPoint) = hypot(x - other.x, y - other.y)
}
data class MarkerGroup(val points: List<MarkerPoint>, val anchor: MarkerPoint) {
    val key get() = points.map { it.id }.sorted().joinToString("|") { "${it.length}:$it" }
}
data class MarkerRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun contains(other: MarkerRect) = other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
    fun intersects(other: MarkerRect, gap: Double = 0.0) =
        left < other.right + gap && right + gap > other.left && top < other.bottom + gap && bottom + gap > other.top
}
data class MarkerFootprint(val width: Double, val height: Double, val anchorX: Double = .5, val anchorY: Double = .5) {
    init { require(width > 0 && height > 0 && width.isFinite() && height.isFinite()) }
    fun at(p: MarkerPoint) = MarkerRect(p.x-width*anchorX, p.y-height*anchorY,
        p.x+width*(1-anchorX), p.y+height*(1-anchorY))
}
data class MarkerGlyph(val group: MarkerGroup, val footprint: MarkerFootprint, val priority: Int = 0)
data class MarkerPlacement(val glyph: MarkerGlyph, val point: MarkerPoint, val crowded: Boolean = false) {
    val bounds get() = glyph.footprint.at(point)
}
data class MarkerLayoutPolicy(val maximumDiameter: Double = 44.0, val maximumDisplacement: Double = 48.0, val gap: Double = 4.0)

/** Complete-link bound prevents A-near-B-near-C from absorbing an entire street. */
fun clusterMapMarkers(points: List<MarkerPoint>, maximumDiameter: Double = 44.0): List<MarkerGroup> {
    require(maximumDiameter > 0 && maximumDiameter.isFinite())
    val groups = mutableListOf<MutableList<MarkerPoint>>()
    val buckets = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    fun cell(p: MarkerPoint) = floor(p.x / maximumDiameter).toInt() to floor(p.y / maximumDiameter).toInt()
    points.filter { it.x.isFinite() && it.y.isFinite() }.sortedWith(compareBy<MarkerPoint> { it.y }.thenBy { it.x }.thenBy { it.id }).forEach { point ->
        val (x, y) = cell(point)
        val candidates = (-1..1).flatMap { dx -> (-1..1).flatMap { dy -> buckets[x + dx to y + dy].orEmpty() } }
        val nearest = candidates.filter { index -> groups[index].all { it.distance(point) <= maximumDiameter } }
            .minByOrNull { index -> groups[index].first().distance(point) }
        if (nearest == null) {
            buckets.getOrPut(x to y) { mutableListOf() }.add(groups.size)
            groups.add(mutableListOf(point))
        } else groups[nearest].add(point)
    }
    return groups.map { group ->
        val x = group.sumOf { it.x } / group.size; val y = group.sumOf { it.y } / group.size
        MarkerGroup(group.toList(), group.minBy { hypot(it.x - x, it.y - y) })
    }
}

/** Selection wins. Preserve original anchors and all members, even when the viewport is physically full.
 * Crowded is an explicit bounded fallback, never permission to silently drop or merge records.
 */
fun placeMapMarkers(glyphs: List<MarkerGlyph>, viewport: MarkerRect? = null,
    exclusions: List<MarkerRect> = emptyList(), policy: MarkerLayoutPolicy = MarkerLayoutPolicy()): List<MarkerPlacement> {
    require(policy.maximumDisplacement >= 0 && policy.maximumDisplacement.isFinite() && policy.gap >= 0)
    val placed = mutableListOf<MarkerPlacement>()
    val output = mutableMapOf<String, MarkerPlacement>()
    glyphs.sortedWith(compareByDescending<MarkerGlyph> { it.priority }.thenBy { it.group.anchor.y }
        .thenBy { it.group.anchor.x }.thenBy { it.group.key }).forEach { glyph ->
        val anchor = glyph.group.anchor
        val candidates = buildList {
            add(anchor)
            viewport?.let { v ->
                val f = glyph.footprint
                if (v.right-v.left >= f.width && v.bottom-v.top >= f.height) add(anchor.copy(
                    x = anchor.x.coerceIn(v.left+f.width*f.anchorX, v.right-f.width*(1-f.anchorX)),
                    y = anchor.y.coerceIn(v.top+f.height*f.anchorY, v.bottom-f.height*(1-f.anchorY))))
            }
            val f = glyph.footprint
            (placed.map { it.bounds } + exclusions).forEach { box ->
                val xs = listOf(anchor.x, box.left-policy.gap-f.width*(1-f.anchorX), box.right+policy.gap+f.width*f.anchorX)
                val ys = listOf(anchor.y, box.top-policy.gap-f.height*(1-f.anchorY), box.bottom+policy.gap+f.height*f.anchorY)
                for (x in xs) for (y in ys) add(anchor.copy(x=x,y=y))
            }
            for (radius in 8..policy.maximumDisplacement.toInt() step 8) for (step in 0..15) {
                val angle = step*PI/8
                add(anchor.copy(x = anchor.x+cos(angle)*radius, y = anchor.y+sin(angle)*radius))
            }
        }.filter { it.distance(anchor) <= policy.maximumDisplacement+.001 }.sortedBy { it.distance(anchor) }
        fun allowed(p: MarkerPoint): Boolean {
            val box = glyph.footprint.at(p)
            return (viewport == null || viewport.contains(box)) && exclusions.none { it.intersects(box, policy.gap) }
        }
        val free = candidates.firstOrNull { p -> allowed(p) && placed.none { it.bounds.intersects(glyph.footprint.at(p), policy.gap) } }
        val point = free ?: candidates.firstOrNull(::allowed) ?: anchor
        val placement = MarkerPlacement(glyph, point, crowded = free == null)
        placed += placement; output[glyph.group.key] = placement
    }
    return glyphs.map { output.getValue(it.group.key) }
}
