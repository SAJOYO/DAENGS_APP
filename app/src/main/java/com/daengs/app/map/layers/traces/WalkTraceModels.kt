package com.daengs.app.map.layers.traces

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.diary.SpatialDiaryCellId

const val DEFAULT_TRACE_BASE_ALPHA = 0.28

/** One walk's positive support. Time, peak and repeat-pass weights are deliberately absent. */
data class WalkTraceSheet(
    val walkId: String,
    val radiusU: Double = 8.0,
    val cells: Set<SpatialDiaryCellId>,
) {
    init {
        require(walkId.isNotBlank())
        require(radiusU.isFinite() && radiusU > 0 && radiusU <= 64)
    }
}

/** Distances are hex-v1 Web Mercator units, not latitude-corrected ground metres. */
data class TraceBrushPolicy(
    val pixelU: Double = 2.0,
    val tileSize: Int = 128,
    val sigmaU: Double = 5.0,
    val insetU: Double = 2.0,
    val baseAlpha: Double = DEFAULT_TRACE_BASE_ALPHA,
    val maxCells: Int = 5_000,
    val maxTiles: Int = 256,
    val maxRasterTiles: Int = 512,
) {
    init {
        require(pixelU.isFinite() && pixelU in 0.25..16.0)
        require(tileSize in 32..256)
        require(sigmaU.isFinite() && sigmaU in 0.0..32.0)
        require(insetU.isFinite() && insetU in 0.0..16.0)
        require(baseAlpha.isFinite() && baseAlpha in 0.0..1.0)
        require(maxCells in 1..5_000 && maxTiles in 1..256 && maxRasterTiles in 1..512)
        require((3 * sigmaU + insetU) / pixelU <= tileSize / 2.0) {
            "브러시 번짐이 타일 크기에 비해 너무 커요."
        }
    }
}

data class WalkTraceMask(val walkId: String, val tiles: List<TraceRasterTile>)

/** Fixed world grid: tileY grows northwards; alpha row 0 is the northernmost row. */
data class TraceRasterTile(
    val tileX: Int,
    val tileY: Int,
    val size: Int,
    val pixelU: Double,
    val alpha: FloatArray,
    val southWest: GeoPoint,
    val northEast: GeoPoint,
)
