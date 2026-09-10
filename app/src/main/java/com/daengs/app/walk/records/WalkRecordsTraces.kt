package com.daengs.app.walk.records

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.traces.TraceBrush
import com.daengs.app.map.layers.traces.TraceBrushPolicy
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.daengs.app.map.layers.traces.WalkTraceMask
import com.daengs.app.walk.diary.SpatialDiaryHexGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One prepared full selection. Visibility changes reuse its private masks and fixed bounds. */
class PreparedWalkRecordsTraces internal constructor(
    private val masks: List<WalkTraceMask>,
    bounds: List<GeoPoint>,
    private val baseAlpha: Double,
    private val overlap: WalkTraceOverlap = WalkTraceOverlap.empty(),
) {
    val availableWalkIds: Set<String> = masks.map { it.walkId }.toSet()
    val bounds: List<GeoPoint> = bounds.toList()
    val overlapUnavailableReason: String? get() = overlap.unavailableReason
    private val eligibilityMutex = Mutex()
    private val eligibilityCache = linkedMapOf<EligibilityKey, WalkTraceMask>()

    fun hasOverlap(minimumWalks: Int): Boolean = overlap.hasOverlap(minimumWalks)
    fun overlapWalkIds(minimumWalks: Int): Set<String> = overlap.walkIds(minimumWalks)
    fun hitTestOverlap(
        point: GeoPoint, minimumWalks: Int, snapRadiusU: Double = 12.0,
        hiddenIds: Set<String> = emptySet(),
    ): WalkTraceOverlapHit? = overlap.hit(point, minimumWalks, snapRadiusU, hiddenIds)

    /** Hidden IDs affect display only. Unknown IDs and records without a mask have no effect. */
    suspend fun compose(
        hiddenIds: Set<String> = emptySet(), minimumOverlapWalks: Int? = null,
    ): List<TraceRasterTile> {
        val hidden = hiddenIds.toSet()
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            context.ensureActive()
            if (minimumOverlapWalks != null) {
                require(overlapUnavailableReason == null) { overlapUnavailableReason.orEmpty() }
                if (!hasOverlap(minimumOverlapWalks)) return@withContext emptyList()
            }
            val contributors = minimumOverlapWalks?.let(::overlapWalkIds)
            val visible = TraceBrush.compose(masks.filter {
                it.walkId !in hidden && (contributors == null || it.walkId in contributors)
            }, baseAlpha) {
                context.ensureActive()
            }
            if (minimumOverlapWalks == null || visible.isEmpty()) return@withContext visible
            val eligibility = eligibilityMask(minimumOverlapWalks, hidden).tiles.associateBy { it.tileX to it.tileY }
            visible.mapNotNull { tile ->
                context.ensureActive()
                val region = eligibility[tile.tileX to tile.tileY] ?: return@mapNotNull null
                val clipped = FloatArray(tile.alpha.size) { i ->
                    if (i % 4_096 == 0) context.ensureActive()
                    tile.alpha[i] * region.alpha[i]
                }
                if (clipped.any { it > 0f }) tile.copy(alpha = clipped) else null
            }
        }
    }

    private suspend fun eligibilityMask(minimumWalks: Int, hiddenIds: Set<String>): WalkTraceMask = eligibilityMutex.withLock {
        val key = EligibilityKey(minimumWalks, hiddenIds.intersect(overlapWalkIds(minimumWalks)))
        eligibilityCache[key]?.let { return@withLock it }
        val context = currentCoroutineContext()
        val mask = TraceBrush.mask(overlap.sheet(minimumWalks, key.hiddenIds), TraceBrushPolicy()) { context.ensureActive() }
        // Cache threshold/visibility states without retaining unbounded empty states or tiles.
        while (eligibilityCache.size >= 6 || eligibilityCache.values.sumOf { it.tiles.size } + mask.tiles.size > 256) {
            eligibilityCache.remove(eligibilityCache.keys.first())
        }
        eligibilityCache[key] = mask
        mask
    }

    private data class EligibilityKey(val minimumWalks: Int, val hiddenIds: Set<String>)
}

/** Focus the selected record's actual route without changing the full selection's bounds. */
fun walkRecordFocusBounds(record: WalkRecord): List<GeoPoint> {
    val bounds = TraceSelectionBounds()
    record.summary.segments.forEach { segment ->
        segment.forEach { sample -> bounds.include(sample.point) }
    }
    return bounds.points()
}

/** Creates each walk mask once; never filter the input down to the current list page. */
suspend fun prepareWalkRecordsTraces(selection: WalkRecordsSelection): PreparedWalkRecordsTraces =
    withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        context.ensureActive()
        val sheets = selection.records.mapNotNull { it.trace }.filter { it.cells.isNotEmpty() }
        require(sheets.size <= 400) { "한 번에 표시할 산책이 너무 많아요. 조건을 좁혀 주세요." }
        val masks = mutableListOf<WalkTraceMask>()
        val bounds = TraceSelectionBounds()
        var totalTiles = 0
        val policy = TraceBrushPolicy()
        sheets.forEach { sheet ->
            context.ensureActive()
            val mask = TraceBrush.mask(sheet, policy) { context.ensureActive() }
            totalTiles += mask.tiles.size
            require(totalTiles <= 1_024) { "선택한 산책의 지도 범위가 너무 넓어요. 조건을 좁혀 주세요." }
            if (mask.tiles.isNotEmpty()) masks += mask
            sheet.cells.forEach { cell ->
                context.ensureActive()
                SpatialDiaryHexGrid.boundary(cell, sheet.radiusU).forEach(bounds::include)
            }
        }
        // A walk without a trace can still have a real route to inspect. Include it before any
        // record is highlighted or hidden, so these actions never refit the whole map.
        selection.records.forEach { record ->
            context.ensureActive()
            record.summary.segments.forEach { segment ->
                segment.forEach { sample ->
                    context.ensureActive()
                    bounds.include(sample.point)
                }
            }
        }
        val overlap = WalkTraceOverlap.create(sheets) { context.ensureActive() }
        PreparedWalkRecordsTraces(masks.toList(), bounds.points(), policy.baseAlpha, overlap)
    }

private class TraceSelectionBounds {
    private var south = Double.POSITIVE_INFINITY
    private var west = Double.POSITIVE_INFINITY
    private var north = Double.NEGATIVE_INFINITY
    private var east = Double.NEGATIVE_INFINITY

    fun include(point: GeoPoint) {
        if (!point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0) return
        south = minOf(south, point.latitude)
        west = minOf(west, point.longitude)
        north = maxOf(north, point.latitude)
        east = maxOf(east, point.longitude)
    }

    fun points(): List<GeoPoint> {
        if (!south.isFinite()) return emptyList()
        val latitudeIsFlat = south == north
        val longitudeIsFlat = west == east
        // NAVER treats a one-axis-flat rectangle as empty. A genuine single point remains equal
        // at both corners so the provider's existing single-point camera handling still applies.
        return if (latitudeIsFlat && !longitudeIsFlat) {
            listOf(GeoPoint((south - MIN_AXIS_PADDING).coerceAtLeast(-90.0), west),
                GeoPoint((north + MIN_AXIS_PADDING).coerceAtMost(90.0), east))
        } else if (longitudeIsFlat && !latitudeIsFlat) {
            listOf(GeoPoint(south, (west - MIN_AXIS_PADDING).coerceAtLeast(-180.0)),
                GeoPoint(north, (east + MIN_AXIS_PADDING).coerceAtMost(180.0)))
        } else listOf(GeoPoint(south, west), GeoPoint(north, east))
    }

    private companion object { const val MIN_AXIS_PADDING = 0.00001 }
}
