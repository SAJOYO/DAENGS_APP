package com.daengs.app.map.shell

import com.daengs.app.location.GeoPoint

data class MapLocationTarget(val id: String, val points: List<GeoPoint>)
data class MapVisibilityQuery(val revisionKey: String, val targets: List<MapLocationTarget>,
    val bottomOcclusionPx: Int, val topLeftCoverWidthPx: Int = 0, val topLeftCoverHeightPx: Int = 0,
    val topRightCoverPx: Int = 0, val topRightCoverTopPx: Int = 0)
/** null visibleIds means the map is moving or its size/projection is not ready. */
data class MapVisibilityResult(val query: MapVisibilityQuery, val visibleIds: Set<String>?, val unplacedIds: Set<String> = emptySet())

data class MapScreenPoint(val x: Float, val y: Float)
data class MapScreenSize(val width: Float, val height: Float)

/** Screen projection handles bearing and tilt. Sheet occlusion is independent of camera padding. */
fun visibleMapLocationIds(query: MapVisibilityQuery, width: Int, height: Int, density: Float,
    badgeSize: (GeoPoint) -> MapScreenSize = { MapScreenSize(32 * density, 37 * density) },
    project: (GeoPoint) -> MapScreenPoint): Set<String>? {
    val bottom = height - query.bottomOcclusionPx
    if (width <= 0 || bottom <= 0 || density <= 0) return null
    fun visible(point: GeoPoint): Boolean {
        val p = project(point)
        if (!p.x.isFinite() || !p.y.isFinite()) return false
        // A diary ordinal sits above its anchor. Count it if any of the badge is exposed.
        val badge = badgeSize(point)
        val left = maxOf(0f, p.x - badge.width / 2); val right = minOf(width.toFloat(), p.x + badge.width / 2)
        val top = maxOf(0f, p.y - badge.height); val end = minOf(bottom.toFloat(), p.y)
        if (left >= right || top >= end) return false
        if (right <= query.topLeftCoverWidthPx && end <= query.topLeftCoverHeightPx) return false
        if (left >= width - query.topRightCoverPx && top >= query.topRightCoverTopPx &&
            end <= query.topRightCoverTopPx + query.topRightCoverPx) return false
        return true
    }
    return query.targets.filter { target -> target.points.any(::visible) }.mapTo(linkedSetOf()) { it.id }
}
