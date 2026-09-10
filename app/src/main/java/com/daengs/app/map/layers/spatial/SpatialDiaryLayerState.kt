package com.daengs.app.map.layers.spatial

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.diary.BehaviorWalkGroup
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.diary.SpatialDiaryHexGrid
import com.daengs.app.walk.diary.WalkBehaviorComparison

data class SpatialDiaryPaintCell(val boundary: List<GeoPoint>, val alpha: Float)

/** Fixed share-to-opacity curve for both groups and all behavior selections. No per-map maxima. */
internal fun spatialDiaryAlpha(value: Double): Float {
    require(value.isFinite() && value >= 0)
    return (0.72 * value / (value + 0.001)).toFloat()
}

fun BehaviorWalkGroup.paintCells(radiusU: Double): List<SpatialDiaryPaintCell> = field.cells.map {
    SpatialDiaryPaintCell(SpatialDiaryHexGrid.boundary(SpatialDiaryCellId(it.q, it.r), radiusU), spatialDiaryAlpha(it.value))
}

/** Fit once to the baseline, even when the selected subset has no located entries. */
fun WalkBehaviorComparison.mapBounds(): List<GeoPoint> = baseline.field.cells.flatMap {
    SpatialDiaryHexGrid.boundary(SpatialDiaryCellId(it.q, it.r), projection.radiusU)
}
