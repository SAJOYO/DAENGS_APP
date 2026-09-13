package com.daengs.app.walk.records

import com.daengs.app.location.GeoPoint
import com.daengs.app.ui.theme.WalkTraceShadow
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.diary.SpatialDiaryHexGrid
import java.util.Collections
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Evidence belongs to one original cell; neighbouring cells' walks are never unioned into a hit. */
data class WalkTraceOverlapHit(val point: GeoPoint, val cell: SpatialDiaryCellId, val walkIds: Set<String>)

/** Full-query evidence, independent of display alpha, smoothing, visibility and the current page. */
internal class WalkTraceOverlap private constructor(
    val unavailableReason: String?,
    val radiusU: Double,
    private val cells: Map<SpatialDiaryCellId, Set<String>>,
) {
    /** Hiding reduces ink strength, while threshold and hit evidence remain full-query based. */
    fun cellOpacities(hiddenIds: Set<String>, checkCancelled: () -> Unit): Map<SpatialDiaryCellId, Float> =
        cells.mapValues { (_, walkIds) ->
            checkCancelled()
            WalkTraceShadow.alphaForWalkCount(walkIds.count { it !in hiddenIds })
        }

    // Prepared on Dispatchers.Default with the index, never rescanned on camera recomposition.
    private val eligibleByMinimum = listOf(2, 3, 5).associateWith { minimum ->
        val selectedCells = cells.filterValues { it.size >= minimum }.keys.toSet()
        val selectedIds = selectedCells.asSequence().flatMap { cells.getValue(it) }.toSet()
        Eligibility(Collections.unmodifiableSet(selectedCells), Collections.unmodifiableSet(selectedIds))
    }

    fun hasOverlap(minimumWalks: Int): Boolean {
        requireMinimum(minimumWalks)
        return unavailableReason == null && eligibleByMinimum.getValue(minimumWalks).cells.isNotEmpty()
    }

    fun walkIds(minimumWalks: Int): Set<String> {
        requireMinimum(minimumWalks)
        return eligibleByMinimum.getValue(minimumWalks).walkIds
    }

    fun sheet(minimumWalks: Int, hiddenIds: Set<String> = emptySet()): WalkTraceSheet {
        requireMinimum(minimumWalks)
        require(unavailableReason == null) { unavailableReason.orEmpty() }
        val eligible = eligibleByMinimum.getValue(minimumWalks).cells
        // Threshold membership stays full-query based. Only display eligibility removes cells
        // whose contributors are all hidden, so another walk's neighbouring blur cannot leak in.
        val visibleCells = if (hiddenIds.isEmpty()) eligible else eligible.filterTo(linkedSetOf()) { cell ->
            cells.getValue(cell).any { it !in hiddenIds }
        }
        return WalkTraceSheet("overlap-$minimumWalks", radiusU,
            visibleCells)
    }

    fun hit(point: GeoPoint, minimumWalks: Int, snapRadiusU: Double, hiddenIds: Set<String>): WalkTraceOverlapHit? {
        requireMinimum(minimumWalks)
        require(snapRadiusU.isFinite() && snapRadiusU in 0.0..12.0)
        if (unavailableReason != null || !point.latitude.isFinite() || point.latitude !in -85.0..85.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0) return null
        val at = SpatialDiaryHexGrid.cellFor(point, radiusU)
        fun eligible(cell: SpatialDiaryCellId) = cells[cell]?.takeIf { ids ->
            ids.size >= minimumWalks && ids.any { it !in hiddenIds }
        }
        eligible(at)?.let { return result(at, it) }
        if (snapRadiusU == 0.0) return null
        val x = EARTH_RADIUS * Math.toRadians(point.longitude)
        val y = EARTH_RADIUS * ln(tan(PI / 4 + Math.toRadians(point.latitude) / 2))
        // Both candidate centre and the rounded cell centre are at most one radius from their
        // cell. Axial q/r components are bounded by 2*distance/(3*radius). No full-index scan.
        val reach = ceil(2 * (snapRadiusU + 2 * radiusU) / (3 * radiusU)).toInt()
        var nearest: SpatialDiaryCellId? = null
        var nearestDistance = snapRadiusU * snapRadiusU
        for (dq in -reach..reach) for (dr in -reach..reach) {
            val cell = SpatialDiaryCellId(at.q + dq, at.r + dr)
            if (eligible(cell) == null) continue
            val distance = distanceSquaredToHex(x, y, cell)
            if (distance <= nearestDistance && (nearest == null || distance < nearestDistance)) {
                nearest = cell
                nearestDistance = distance
            }
        }
        return nearest?.let { result(it, cells.getValue(it)) }
    }

    private fun result(cell: SpatialDiaryCellId, ids: Set<String>) =
        WalkTraceOverlapHit(SpatialDiaryHexGrid.center(cell, radiusU), cell, ids.toSet())

    private data class Eligibility(val cells: Set<SpatialDiaryCellId>, val walkIds: Set<String>)

    private fun distanceSquaredToHex(x: Double, y: Double, cell: SpatialDiaryCellId): Double {
        val dx = x - radiusU * sqrt(3.0) * (cell.q + cell.r / 2.0)
        val dy = y - radiusU * 1.5 * cell.r
        if (abs(dx) <= sqrt(3.0) * radiusU / 2 && abs(dy) + abs(dx) / sqrt(3.0) <= radiusU) return 0.0
        var nearest = Double.POSITIVE_INFINITY
        for (index in CORNERS.indices) {
            val a = CORNERS[index]
            val b = CORNERS[(index + 1) % CORNERS.size]
            val ax = a.first * radiusU
            val ay = a.second * radiusU
            val vx = (b.first - a.first) * radiusU
            val vy = (b.second - a.second) * radiusU
            val t = (((dx - ax) * vx + (dy - ay) * vy) / (vx * vx + vy * vy)).coerceIn(0.0, 1.0)
            val ex = dx - ax - t * vx
            val ey = dy - ay - t * vy
            nearest = minOf(nearest, ex * ex + ey * ey)
        }
        return nearest
    }

    companion object {
        fun empty() = WalkTraceOverlap(null, 8.0, emptyMap())

        fun create(sheets: List<WalkTraceSheet>, checkCancelled: () -> Unit): WalkTraceOverlap {
            val radii = sheets.map { it.radiusU }.distinct()
            if (radii.size > 1) return unavailable("공간 격자 크기가 다른 산책이 있어 겹친 곳을 비교할 수 없어요.")
            // Different analyses may share a paint policy, but different policies must never
            // silently become comparable just because their cells have the same radius.
            // Existing synthetic sheets are comparable only to other synthetic sheets.
            val policies = sheets.map { sheet -> sheet.provenance?.let {
                listOf(it.paintFingerprint, it.paintVersion, it.gridVersion,
                    it.profileFingerprint, it.sampleStepMeters)
            } }.distinct()
            if (policies.size > 1) return unavailable("흔적을 만든 기준이 다른 산책이 있어 겹친 곳을 비교할 수 없어요.")
            val radius = radii.singleOrNull() ?: return empty()
            // prepareWalkRecordsTraces already enforces the default brush's minimum 2u pixel size.
            if (radius < 2.0) return unavailable("이 공간 격자는 겹침 조회를 지원하지 않아요.")
            val members = mutableMapOf<SpatialDiaryCellId, MutableSet<String>>()
            var references = 0
            sheets.forEach { sheet ->
                checkCancelled()
                sheet.cells.forEach { cell ->
                    checkCancelled()
                    references++
                    if (references > 100_000 || (cell !in members && members.size >= 25_000)) {
                        return unavailable("겹침을 조회할 공간 정보가 너무 많아요. 기간이나 조건을 좁혀 주세요.")
                    }
                    members.getOrPut(cell) { linkedSetOf() }.add(sheet.walkId)
                }
            }
            val overlaps = members.filterValues { it.size >= 2 }
            if (overlaps.size > 5_000) return unavailable("겹친 구역이 너무 넓어요. 기간이나 조건을 좁혀 주세요.")
            return WalkTraceOverlap(null, radius, members.mapValues { it.value.toSet() })
        }

        private fun unavailable(reason: String) = WalkTraceOverlap(reason, 8.0, emptyMap())
        private fun requireMinimum(minimumWalks: Int) = require(minimumWalks in setOf(2, 3, 5))
        private const val EARTH_RADIUS = 6_378_137.0
        private val CORNERS = (0..5).map { index ->
            val radians = Math.toRadians(30.0 - 60.0 * index)
            cos(radians) to sin(radians)
        }
    }
}
