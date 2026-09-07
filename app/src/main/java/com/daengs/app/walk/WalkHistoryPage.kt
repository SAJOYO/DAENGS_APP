package com.daengs.app.walk

import com.daengs.app.location.LocationSample
import kotlin.math.cos
import kotlin.math.hypot

data class WalkHistoryCursor(val startedAtMillis: Long, val sessionId: String) {
    fun encode(): String = "$startedAtMillis|$sessionId"
    companion object {
        fun decode(value: String?): WalkHistoryCursor? = value?.takeIf { it.isNotEmpty() }?.let {
            WalkHistoryCursor(it.substringBefore('|').toLong(), it.substringAfter('|'))
        }
    }
}

data class WalkHistoryPage(val walks: List<WalkSummary>, val next: WalkHistoryCursor?)

/** Simplify only a display copy, preserving each segment's first/last point and full-walk bounds.
 * Iterative RDP avoids recursion on long recordings. No gap is ever joined. */
fun WalkSummary.forHistoryThumbnail(): WalkSummary {
    val all = segments.flatten()
    if (all.isEmpty()) return copy(activeElapsedAtMillis = emptyMap())
    val cosLat = cos(Math.toRadians(all.first().point.latitude))
    val minLat = all.minOf { it.point.latitude }
    val minLng = all.minOf { it.point.longitude }
    fun LocationSample.x() = (point.longitude - minLng) * cosLat
    fun LocationSample.y() = point.latitude - minLat
    val epsilon = maxOf(all.maxOf { it.x() }, all.maxOf { it.y() }) / 160.0
    val paths = segments.map { path ->
        if (path.size < 3) path else {
            val keep = BooleanArray(path.size)
            // Preserve extrema too: the shape's scale must not change after simplification.
            val anchors = listOf(0, path.lastIndex, path.indices.minBy { path[it].x() },
                path.indices.maxBy { path[it].x() }, path.indices.minBy { path[it].y() },
                path.indices.maxBy { path[it].y() }).distinct().sorted()
            anchors.forEach { keep[it] = true }
            val pending = ArrayDeque<Pair<Int, Int>>()
            anchors.zipWithNext().forEach { pending.add(it) }
            while (pending.isNotEmpty()) {
                val (a, b) = pending.removeLast()
                val dx = path[b].x() - path[a].x(); val dy = path[b].y() - path[a].y()
                val length = dx * dx + dy * dy
                var furthest = -1; var maximum = epsilon
                for (i in a + 1 until b) {
                    val x = path[i].x() - path[a].x(); val y = path[i].y() - path[a].y()
                    val t = if (length == 0.0) 0.0 else ((x * dx + y * dy) / length).coerceIn(0.0, 1.0)
                    val distance = hypot(x - t * dx, y - t * dy)
                    if (distance > maximum) { maximum = distance; furthest = i }
                }
                if (furthest >= 0) { keep[furthest] = true; pending.add(a to furthest); pending.add(furthest to b) }
            }
            path.filterIndexed { index, _ -> keep[index] }
        }
    }
    return copy(segments = paths, activeElapsedAtMillis = emptyMap())
}
