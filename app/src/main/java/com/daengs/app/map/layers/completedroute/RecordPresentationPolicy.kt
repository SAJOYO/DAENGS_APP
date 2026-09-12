package com.daengs.app.map.layers.completedroute

import com.daengs.app.location.GeoPoint

enum class RecordRouteRole { OBSERVED_EXCLUDED, OBSERVED_UNRESOLVED }
data class RecordDirectionEdge(val id: String, val from: GeoPoint, val to: GeoPoint)
/** Geometry and direction carry original source identity, scoped to the prepared read. */
data class RouteRenderPart(val id: String, val role: RecordRouteRole, val path: List<GeoPoint>,
    val selected: Boolean, val directions: List<RecordDirectionEdge>)

data class RecordRouteStroke(val widthDp: Float, val railWidthDp: Float, val color: Int, val centerColor: Int)

/** The renderer does not decide whether an observation counts as walking. Selection keeps the rails. */
object RecordPresentationPolicy {
    const val VERSION = "record-route-style-v1"
    fun stroke(role: RecordRouteRole, selected: Boolean) = RecordRouteStroke(
        widthDp = if (selected) 4f else 3f, railWidthDp = if (selected) 2.5f else 1.5f,
        color = when (role) {
            RecordRouteRole.OBSERVED_EXCLUDED -> if (selected) 0xff25596a.toInt() else 0xff57808b.toInt()
            RecordRouteRole.OBSERVED_UNRESOLVED -> if (selected) 0xff786038.toInt() else 0xff9b8259.toInt()
        }, centerColor = 0xfffdf4f0.toInt())
    fun label(role: RecordRouteRole) = when (role) {
        RecordRouteRole.OBSERVED_EXCLUDED -> "보행거리 제외"
        RecordRouteRole.OBSERVED_UNRESOLVED -> "보행 판정 미확정"
    }
}
