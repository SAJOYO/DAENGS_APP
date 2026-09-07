package com.daengs.app.map.layers.stays

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.style.WalkSpeedPoint
import com.daengs.app.walk.StayStamp
import com.daengs.app.walk.StayStampPolicy
import com.daengs.app.walk.distanceTo

data class StayStampMarkerState(val id: Int, val point: GeoPoint)

/**
 * Choose the latest actual route vertex in the confirmed observation window.
 * Later fixes cannot shift it, and no off-route centroid or cross-gap projection
 * is manufactured. If no drawn segment supplies a vertex, defer the marker.
 */
fun anchorStayStamps(stamps: List<StayStamp>, paths: List<List<WalkSpeedPoint>>): List<StayStampMarkerState> {
    val vertices = paths.flatMap { path ->
        path.zipWithNext().filter { (a, b) -> a.point.distanceTo(b.point) >= 0.01 }
            .flatMap { (a, b) -> listOf(a, b) }
    }.sortedBy { it.capturedAtMillis }
    val radius = StayStampPolicy().enterMeters
    return stamps.mapNotNull { stamp ->
        vertices.lastOrNull { vertex ->
            vertex.capturedAtMillis in stamp.startedAtMillis..stamp.confirmedAtMillis &&
                stamp.origin.distanceTo(vertex.point) <= radius
        }?.let { StayStampMarkerState(stamp.id, it.point) }
    }
}
