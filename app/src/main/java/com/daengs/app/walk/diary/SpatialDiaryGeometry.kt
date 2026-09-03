package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class SpatialDiaryCellId(val q: Int, val r: Int)

data class SpatialDiaryCellPolygon(
    val id: SpatialDiaryCellId,
    val center: GeoPoint,
    val boundary: List<GeoPoint>,
    val value: Double,
    val numerator: Double,
)

/** Dev의 `daengs_walk.hex_grid`와 같은 Web Mercator axial hex-v1 계산. */
object SpatialDiaryHexGrid {
    fun cellFor(point: GeoPoint, radiusU: Double): SpatialDiaryCellId {
        requireRadius(radiusU)
        val (x, y) = mercator(point)
        return roundAxial(
            q = (sqrt(3.0) / 3.0 * x - y / 3.0) / radiusU,
            r = (2.0 / 3.0 * y) / radiusU,
        )
    }

    fun center(cell: SpatialDiaryCellId, radiusU: Double): GeoPoint {
        requireRadius(radiusU)
        val (x, y) = centerMetres(cell, radiusU)
        return inverseMercator(x, y)
    }

    /** 서버와 같은 순서인 30°, -30° … -270°의 여섯 꼭짓점. */
    fun boundary(cell: SpatialDiaryCellId, radiusU: Double): List<GeoPoint> {
        requireRadius(radiusU)
        val (centerX, centerY) = centerMetres(cell, radiusU)
        return (0 until HEX_CORNERS).map { index ->
            val angle = Math.toRadians(30.0 - 60.0 * index)
            inverseMercator(
                x = centerX + radiusU * cos(angle),
                y = centerY + radiusU * sin(angle),
            )
        }
    }

    private fun mercator(point: GeoPoint): Pair<Double, Double> {
        val latitude = point.latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        return EARTH_RADIUS_METERS * Math.toRadians(point.longitude) to
            EARTH_RADIUS_METERS * ln(tan(PI / 4.0 + Math.toRadians(latitude) / 2.0))
    }

    private fun inverseMercator(x: Double, y: Double) = GeoPoint(
        latitude = Math.toDegrees(2.0 * atan(exp(y / EARTH_RADIUS_METERS)) - PI / 2.0),
        longitude = Math.toDegrees(x / EARTH_RADIUS_METERS),
    )

    private fun centerMetres(cell: SpatialDiaryCellId, radiusU: Double) =
        radiusU * sqrt(3.0) * (cell.q + cell.r / 2.0) to radiusU * 1.5 * cell.r

    private fun roundAxial(q: Double, r: Double): SpatialDiaryCellId {
        val x = q
        val z = r
        val y = -x - z
        var roundedX = round(x).toInt()
        val roundedY = round(y).toInt()
        var roundedZ = round(z).toInt()
        val deltaX = kotlin.math.abs(roundedX - x)
        val deltaY = kotlin.math.abs(roundedY - y)
        val deltaZ = kotlin.math.abs(roundedZ - z)
        if (deltaX > deltaY && deltaX > deltaZ) {
            roundedX = -roundedY - roundedZ
        } else if (deltaY <= deltaZ) {
            // y가 가장 멀면 x/z는 이미 맞다. 그 외에는 z를 x/y에 맞춘다.
            roundedZ = -roundedX - roundedY
        }
        return SpatialDiaryCellId(roundedX, roundedZ)
    }

    private fun requireRadius(radiusU: Double) {
        require(radiusU.isFinite() && radiusU > 0) {
            "hex-v1 반지름은 유한한 양수여야 해요."
        }
    }

    private const val EARTH_RADIUS_METERS = 6_378_137.0
    private const val MAX_MERCATOR_LATITUDE = 85.0
    private const val HEX_CORNERS = 6
}

/** 지도 공급자와 무관한 GeoPoint 다각형으로 바꿔 다음 레이어 PR에 넘긴다. */
fun SpatialDiaryView.cellPolygons(): List<SpatialDiaryCellPolygon> = field.cells.map { cell ->
    val id = SpatialDiaryCellId(cell.q, cell.r)
    SpatialDiaryCellPolygon(
        id = id,
        center = SpatialDiaryHexGrid.center(id, projection.radiusU),
        boundary = SpatialDiaryHexGrid.boundary(id, projection.radiusU),
        value = cell.value,
        numerator = cell.numerator,
    )
}
