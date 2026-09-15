package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import kotlin.math.*

internal data class RoutePass(
    val id: String, val startedAtMillis: Long, val endedAtMillis: Long,
    val path: List<GeoPoint>, val reverse: Boolean,
)
internal data class RoutePassages(val anchor: GeoPoint, val passes: List<RoutePass>, val uncertain: Boolean = false)
internal data class RouteReplayFrame(val point: GeoPoint?, val recordedAtMillis: Long?, val inGap: Boolean,
    /** Speed of the same proven edge used for replay, never affected by the viewing multiplier. */
    val derivedSpeedMetersPerSecond: Double? = null)

private data class XY(val x: Double, val y: Double) {
    operator fun minus(p: XY) = XY(x - p.x, y - p.y)
    fun dot(p: XY) = x * p.x + y * p.y
    val length get() = hypot(x, y)
}
private data class IndexedEdge(
    val ordinal: Int, val before: WalkRoutePoint, val after: WalkRoutePoint, val a: XY, val b: XY,
) {
    fun distance(p: XY): Double {
        val delta = b - a
        val t = ((p - a).dot(delta) / delta.dot(delta)).coerceIn(0.0, 1.0)
        return (p - XY(a.x + delta.x * t, a.y + delta.y * t)).length
    }
}

/** Built off the UI thread. The spatial grid answers a local selection, not all-pairs overlap. */
internal class RouteExplorerIndex(val route: WalkSessionRoute) {
    private val points = route.points
    private val timedPoints = buildList<WalkRoutePoint> {
        for (point in points) if (point.activeElapsedMillis >= 0 &&
            (lastOrNull()?.activeElapsedMillis ?: 0) <= point.activeElapsedMillis) add(point)
    }
    private val origin = points.firstOrNull()?.point ?: GeoPoint(0.0, 0.0)
    private val longitudeScale = 111_195.0 * cos(Math.toRadians(origin.latitude))
    private fun xy(p: GeoPoint) = XY((p.longitude - origin.longitude) * longitudeScale,
        (p.latitude - origin.latitude) * 111_195.0)
    private fun cell(value: Double) = floor(value / RouteIndexGrid.CELL_SIZE_METERS).toInt()
    private val edges = route.segments.flatMap { it.points.zipWithNext() }.mapIndexedNotNull { ordinal, (a, b) ->
        val start = xy(a.point); val end = xy(b.point)
        if ((end - start).length !in RoutePassageCriteria.MIN_EDGE_LENGTH_METERS..RoutePassageCriteria.MAX_EDGE_LENGTH_METERS ||
            b.capturedAtMillis - a.capturedAtMillis !in 1L..RoutePassageCriteria.MAX_SAMPLE_GAP_MILLIS ||
            listOf(a.accuracyMeters, b.accuracyMeters).any {
                it == null || !it.isFinite() || it <= 0 || it > RoutePassageCriteria.MAX_ACCURACY_METERS
            }) null
        else IndexedEdge(ordinal, a, b, start, end)
    }
    private val grid = buildMap<Pair<Int, Int>, MutableList<IndexedEdge>> {
        for (edge in edges) {
            for (x in cell(min(edge.a.x, edge.b.x))..cell(max(edge.a.x, edge.b.x)))
                for (y in cell(min(edge.a.y, edge.b.y))..cell(max(edge.a.y, edge.b.y)))
                    getOrPut(x to y) { mutableListOf() }.add(edge)
        }
    }
    private fun nearby(p: XY, meters: Double): List<IndexedEdge> = buildSet {
        for (x in cell(p.x - meters)..cell(p.x + meters))
            for (y in cell(p.y - meters)..cell(p.y + meters)) addAll(grid[x to y].orEmpty())
    }.toList()

    fun passagesAt(point: GeoPoint): RoutePassages {
        val tap = xy(point)
        val seed = nearby(tap, RouteTapCriteria.MAX_EDGE_DISTANCE_METERS).minByOrNull { it.distance(tap) }
            ?.takeIf { it.distance(tap) <= RouteTapCriteria.MAX_EDGE_DISTANCE_METERS }
            ?: return RoutePassages(point, emptyList(), uncertain = true)
        val delta = seed.b - seed.a
        val direction = XY(delta.x / delta.length, delta.y / delta.length)
        val normal = XY(-direction.y, direction.x)
        val ratio = ((tap - seed.a).dot(delta) / delta.dot(delta)).coerceIn(0.0, 1.0)
        val center = XY(seed.a.x + delta.x * ratio, seed.a.y + delta.y * ratio)
        val anchor = GeoPoint(origin.latitude + center.y / 111_195, origin.longitude + center.x / longitudeScale)
        data class Piece(val edge: IndexedEdge, val start: Double, val end: Double, val side: Double,
            val sign: Int)
        val pieces = nearby(center, RoutePassageCriteria.CANDIDATE_SEARCH_METERS).sortedBy { it.ordinal }.mapNotNull { edge ->
            val vector = edge.b - edge.a
            val alignment = vector.dot(direction) / vector.length
            if (abs(alignment) < RoutePassageCriteria.MIN_DIRECTION_ALIGNMENT) return@mapNotNull null
            val a = edge.a - center; val b = edge.b - center
            val ax = a.dot(direction); val bx = b.dot(direction)
            val ay = a.dot(normal); val by = b.dot(normal)
            // The corridor's longitudinal and lateral criteria have different meanings.
            var low = 0.0; var high = 1.0
            fun clip(start: Double, end: Double, limit: Double): Boolean {
                val change = end - start
                if (abs(change) < CLIP_PARALLEL_EPSILON_METERS) return abs(start) <= limit
                val p = (-limit - start) / change; val q = (limit - start) / change
                low = max(low, min(p, q)); high = min(high, max(p, q))
                return low <= high
            }
            if (!clip(ax, bx, RoutePassageCriteria.CORRIDOR_HALF_LENGTH_METERS) ||
                !clip(ay, by, RoutePassageCriteria.CORRIDOR_HALF_WIDTH_METERS)) null
            else Piece(edge, ax + (bx - ax) * low, ax + (bx - ax) * high,
                (ay + (by - ay) * low + ay + (by - ay) * high) / 2, if (alignment > 0) 1 else -1)
        }
        val runs = mutableListOf<List<Piece>>()
        var run = mutableListOf<Piece>()
        for (piece in pieces) {
            val last = run.lastOrNull()
            if (last != null && (piece.edge.ordinal != last.edge.ordinal + 1 ||
                    piece.edge.before.segmentIndex != last.edge.before.segmentIndex || piece.sign != last.sign)) {
                runs += run; run = mutableListOf()
            }
            run += piece
        }
        if (run.isNotEmpty()) runs += run
        val traversals = runs.filter {
            val a = it.first().start; val b = it.last().end
            (a <= -RoutePassageCriteria.MIN_CENTER_CROSSING_METERS && b >= RoutePassageCriteria.MIN_CENTER_CROSSING_METERS ||
                a >= RoutePassageCriteria.MIN_CENTER_CROSSING_METERS && b <= -RoutePassageCriteria.MIN_CENTER_CROSSING_METERS) &&
                abs(b - a) >= RoutePassageCriteria.MIN_TRAVERSAL_SPAN_METERS
        }
        // Parallel lanes or inconsistent GPS offsets cannot support a definite count.
        val offsets = traversals.map { it.map(Piece::side).average() }
        if (offsets.isNotEmpty() && offsets.max() - offsets.min() > RoutePassageCriteria.MAX_LATERAL_SPREAD_METERS)
            return RoutePassages(anchor, emptyList(), uncertain = true)
        return RoutePassages(anchor, traversals.map { passage ->
            val first = passage.first().edge; val last = passage.last().edge
            RoutePass(first.before.segmentIndex.toString() + ":" + first.ordinal + ":" + last.ordinal,
                first.before.capturedAtMillis, last.after.capturedAtMillis,
                listOf(first.before.point) + passage.map { it.edge.after.point }, passage.first().sign < 0)
        })
    }

    /** Binary search on the recorded active-time axis. Never bridge a segment or missing sample. */
    fun frameAt(elapsed: Long): RouteReplayFrame {
        val points = timedPoints
        if (points.isEmpty()) return RouteReplayFrame(null, null, true)
        var low = 0; var high = points.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (points[mid].activeElapsedMillis <= elapsed) low = mid + 1 else high = mid
        }
        val before = points.getOrNull(low - 1) ?: return RouteReplayFrame(null, null, true)
        if (before.activeElapsedMillis == elapsed) return RouteReplayFrame(before.point, before.capturedAtMillis, false,
            before.derivedSpeedMetersPerSecond)
        val after = points.getOrNull(low) ?: return RouteReplayFrame(null, null, true)
        val delta = after.activeElapsedMillis - before.activeElapsedMillis
        if (before.segmentIndex != after.segmentIndex || after.pointIndex != before.pointIndex + 1 ||
            delta !in 1L..RouteReplayCriteria.MAX_INTERPOLATION_GAP_MILLIS ||
            after.capturedAtMillis - before.capturedAtMillis !in 1L..RouteReplayCriteria.MAX_INTERPOLATION_GAP_MILLIS)
            return RouteReplayFrame(null, null, true)
        val fraction = (elapsed - before.activeElapsedMillis).toDouble() / delta
        return RouteReplayFrame(GeoPoint(
            before.point.latitude + (after.point.latitude - before.point.latitude) * fraction,
            before.point.longitude + (after.point.longitude - before.point.longitude) * fraction),
            before.capturedAtMillis + ((after.capturedAtMillis - before.capturedAtMillis) * fraction).toLong(), false,
            after.derivedSpeedMetersPerSecond)
    }
    val durationMillis: Long = timedPoints.lastOrNull()?.activeElapsedMillis ?: 0
}

/** Numerical tolerance for clipping a nearly parallel edge, not a GPS acceptance threshold. */
private const val CLIP_PARALLEL_EPSILON_METERS = .0001

internal enum class RoutePlaybackSpeed(val multiplier: Int) {
    ONE(1), TWO(2), FOUR(4), EIGHT(8), SIXTEEN(16),
}

/** Advance only the viewing clock; clamp before multiplying to avoid wrapping past the end. */
internal fun advanceRoutePlayback(at: Long, elapsed: Long, duration: Long,
    speed: RoutePlaybackSpeed = RoutePlaybackSpeed.ONE): Long {
    val end = duration.coerceAtLeast(0)
    val position = at.coerceIn(0, end)
    val remaining = end - position
    val delta = elapsed.coerceAtLeast(0)
    return if (delta > remaining / speed.multiplier) end else position + delta * speed.multiplier
}
