package com.daengs.app.map.style

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.distanceTo
import kotlin.math.ceil

data class WalkSpeedPoint(val point: GeoPoint, val capturedAtMillis: Long)
data class WalkSpeedPart(val points: List<GeoPoint>, val color: Int)

/** Display-only. Never changes stored points, distance or the recording filter. */
fun paintWalkSpeedPath(path: List<WalkSpeedPoint>, policy: WalkStylePolicy, themeId: String): List<WalkSpeedPart> {
    val edges = path.zipWithNext()
    val speeds = edges.map { (a, b) ->
        val millis = b.capturedAtMillis - a.capturedAtMillis
        if (millis !in 500L..10_000L) null
        else (a.point.distanceTo(b.point) / (millis / 1000.0)).takeIf { it.isFinite() && it <= 7.0 }
    }
    return edges.flatMapIndexed { index, (a, b) ->
        val meters = a.point.distanceTo(b.point)
        if (meters < 0.01) return@flatMapIndexed emptyList()
        val current = speeds[index]?.coerceAtMost(policy.speedMax)
        if (current == null) return@flatMapIndexed listOf(WalkSpeedPart(listOf(a.point, b.point), policy.unknownColor))
        val start = speeds.getOrNull(index - 1)?.let { (it.coerceAtMost(policy.speedMax) + current) / 2 } ?: current
        val end = speeds.getOrNull(index + 1)?.let { (it.coerceAtMost(policy.speedMax) + current) / 2 } ?: current
        // Native multipart paths approximate the blend at <=1m spacing (max 32 parts/edge).
        // One native overlay per existing recording segment, not per small color part.
        val steps = ceil(meters).toInt().coerceIn(1, 32)
        fun point(f: Double) = GeoPoint(
            a.point.latitude + (b.point.latitude - a.point.latitude) * f,
            a.point.longitude + (b.point.longitude - a.point.longitude) * f,
        )
        List(steps) { i ->
            WalkSpeedPart(listOf(if (i == 0) a.point else point(i.toDouble() / steps),
                if (i == steps - 1) b.point else point((i + 1.0) / steps)),
                policy.color(start + (end - start) * (i + .5) / steps, themeId))
        }
    }
}
