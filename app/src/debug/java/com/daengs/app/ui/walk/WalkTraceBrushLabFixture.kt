package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.diary.SpatialDiaryHexGrid
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot

/** Invented paths near Seoul Forest, for rendering review only. Never written to the walk store. */
internal object WalkTraceBrushLabFixture {
    private const val RADIUS_U = 8.0
    private val firstLoop = listOf(
        Offset(0.0, 0.0), Offset(0.0, 80.0), Offset(50.0, 160.0),
        Offset(120.0, 180.0), Offset(180.0, 140.0), Offset(210.0, 50.0),
        Offset(170.0, -20.0), Offset(80.0, -35.0), Offset(0.0, 0.0),
    )
    // This walk goes back over its opening segment. Set membership keeps it one painted walk.
    private val firstRetrace = listOf(Offset(0.0, 0.0), Offset(0.0, 80.0), Offset(0.0, 0.0))
    private val secondLoop = listOf(
        Offset(0.0, 0.0), Offset(0.0, 80.0), Offset(50.0, 160.0),
        Offset(120.0, 180.0), Offset(100.0, 260.0), Offset(-20.0, 290.0),
        Offset(-90.0, 230.0), Offset(-100.0, 100.0), Offset(-60.0, 40.0),
        Offset(0.0, 0.0),
    )
    // Separate single-cell support. A display renderer must not bridge this gap to the loop.
    private val isolated = Offset(280.0, -10.0)

    val bounds: List<GeoPoint> = (firstLoop + secondLoop + isolated).map(Offset::toPoint)

    /** Thumbnail paths use the same invented geometry; the isolated point is never joined. */
    fun routes(): List<List<List<GeoPoint>>> = listOf(
        listOf(firstLoop.map(Offset::toPoint), firstRetrace.map(Offset::toPoint), listOf(isolated.toPoint())),
        listOf(secondLoop.map(Offset::toPoint)),
    )

    fun sheets(): List<WalkTraceSheet> {
        val firstCells = cells(firstLoop) + cells(firstRetrace) +
            SpatialDiaryHexGrid.cellFor(isolated.toPoint(), RADIUS_U)
        return listOf(
            WalkTraceSheet("sample-walk-1", RADIUS_U, firstCells),
            WalkTraceSheet("sample-walk-2", RADIUS_U, cells(secondLoop)),
        )
    }

    private fun cells(path: List<Offset>): Set<SpatialDiaryCellId> = buildSet {
        path.zipWithNext().forEach { (start, end) ->
            val steps = ceil(hypot(end.x - start.x, end.y - start.y) / 2.0).toInt().coerceAtLeast(1)
            for (step in 0..steps) {
                val fraction = step.toDouble() / steps
                val point = Offset(
                    start.x + (end.x - start.x) * fraction,
                    start.y + (end.y - start.y) * fraction,
                ).toPoint()
                add(SpatialDiaryHexGrid.cellFor(point, RADIUS_U))
            }
        }
    }

    private data class Offset(val x: Double, val y: Double) {
        fun toPoint() = GeoPoint(
            latitude = ORIGIN_LATITUDE + y / METRES_PER_DEGREE,
            longitude = ORIGIN_LONGITUDE + x / (METRES_PER_DEGREE * cos(Math.toRadians(ORIGIN_LATITUDE))),
        )
    }

    private const val ORIGIN_LATITUDE = 37.5444
    private const val ORIGIN_LONGITUDE = 127.0389
    private const val METRES_PER_DEGREE = 111_320.0
}
