package com.daengs.app.map.layout

import kotlin.math.*

/** Only real, contiguous route segments are obstacles. Never pass gap guide lines here. */
data class MarkerRouteEdge(val from: MarkerPoint, val to: MarkerPoint, val clearance: Double = 7.0) {
    fun intersects(box: MarkerRect): Boolean {
        val b = MarkerRect(box.left-clearance, box.top-clearance, box.right+clearance, box.bottom+clearance)
        if (max(from.x,to.x)<b.left || min(from.x,to.x)>b.right || max(from.y,to.y)<b.top || min(from.y,to.y)>b.bottom) return false
        var low=0.0; var high=1.0
        val dx=to.x-from.x; val dy=to.y-from.y
        for ((p,q) in listOf(-dx to from.x-b.left, dx to b.right-from.x, -dy to from.y-b.top, dy to b.bottom-from.y)) {
            if (abs(p)<1e-9) { if (q<0) return false }
            else { val t=q/p; if (p<0) low=max(low,t) else high=min(high,t); if (low>high) return false }
        }
        return true
    }
}

/**
 * Bounded local packing. Failed placements are explicit and must NOT be drawn by the caller.
 * Previous offsets are tried first; selection/color does not invalidate a valid placement.
 * Local backtracking moves up to two neighbours instead of accepting an overlapping fallback.
 */
fun placeDetachedMarkers(glyphs: List<MarkerGlyph>, viewport: MarkerRect,
    exclusions: List<MarkerRect> = emptyList(), route: List<MarkerRouteEdge> = emptyList(),
    previousOffsets: Map<String, MarkerPoint> = emptyMap(), maximumDisplacement: Double = 96.0,
): List<MarkerPlacement> {
    require(maximumDisplacement in 0.0..160.0)
    val ordered=glyphs.sortedWith(compareByDescending<MarkerGlyph> { previousOffsets.containsKey(it.group.key) }
        .thenByDescending { it.priority }.thenBy { it.group.anchor.y }.thenBy { it.group.anchor.x }.thenBy { it.group.key })
    val candidates=ordered.associate { glyph ->
        val a=glyph.group.anchor
        val nearby=route.filter { e ->
            val reach=maximumDisplacement+max(glyph.footprint.width,glyph.footprint.height)+e.clearance
            max(e.from.x,e.to.x)>=a.x-reach && min(e.from.x,e.to.x)<=a.x+reach &&
                max(e.from.y,e.to.y)>=a.y-reach && min(e.from.y,e.to.y)<=a.y+reach
        }
        val points=buildList {
            previousOffsets[glyph.group.key]?.let { add(a.copy(x=a.x+it.x,y=a.y+it.y)) }
            add(a)
            for (r in 16..maximumDisplacement.toInt() step 8) for (i in 0..15) {
                val angle=i*PI/8; add(a.copy(x=a.x+cos(angle)*r,y=a.y+sin(angle)*r))
            }
            val f=glyph.footprint
            if (viewport.right-viewport.left>=f.width && viewport.bottom-viewport.top>=f.height) add(a.copy(
                x=a.x.coerceIn(viewport.left+f.width*f.anchorX,viewport.right-f.width*(1-f.anchorX)),
                y=a.y.coerceIn(viewport.top+f.height*f.anchorY,viewport.bottom-f.height*(1-f.anchorY))))
        }.distinct().filter { p ->
            val box=glyph.footprint.at(p)
            p.distance(a)<=maximumDisplacement+.001 && viewport.contains(box) &&
                exclusions.none { it.intersects(box,4.0) } && nearby.none { it.intersects(box) }
        }
        glyph.group.key to points
    }
    val placed=linkedMapOf<String, MarkerPlacement>()
    var attempts=0
    fun insert(glyph: MarkerGlyph, depth: Int, protected: Set<String>): Boolean {
        val key=glyph.group.key
        for (p in candidates.getValue(key)) {
            if (++attempts>30_000) return false
            val box=glyph.footprint.at(p)
            val conflicts=placed.values.filter { it.bounds.intersects(box,4.0) }
            if (conflicts.isEmpty()) { placed[key]=MarkerPlacement(glyph,p); return true }
            if (depth<=0 || conflicts.size>2 || conflicts.any { it.glyph.group.key in protected }) continue
            val backup=LinkedHashMap(placed)
            conflicts.forEach { placed.remove(it.glyph.group.key) }
            placed[key]=MarkerPlacement(glyph,p)
            if (conflicts.all { insert(it.glyph,depth-1,protected+key) }) return true
            placed.clear(); placed.putAll(backup)
        }
        return false
    }
    ordered.forEach { insert(it,2,emptySet()) }
    return glyphs.map { placed[it.group.key] ?: MarkerPlacement(it,it.group.anchor,crowded=true) }
}
