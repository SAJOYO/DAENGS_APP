package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import kotlin.math.cos
import kotlin.math.hypot

/** 저장된 한 산책을 지도에서 다시 읽을 때 쓰는 점. 원본에 없는 값은 파생값임을 이름으로 드러낸다. */
data class WalkRoutePoint(
    val point: GeoPoint,
    val capturedAtMillis: Long,
    val accuracyMeters: Float?,
    /** 일시정지로 끊긴 시간은 뺀, 세션 첫 원본 fix부터의 활동 시간. */
    val activeElapsedMillis: Long,
    /** 세그먼트 사이를 직선으로 잇지 않고 더한 누적 거리. */
    val cumulativeDistanceMeters: Double,
    /** 같은 세그먼트의 직전 화면용 점과 좌표·시각 차이로 계산한 값. Android speed 원본이 아니다. */
    val derivedSpeedMetersPerSecond: Double?,
    val segmentIndex: Int,
    val pointIndex: Int,
)

data class WalkRouteSegment(
    val index: Int,
    val points: List<WalkRoutePoint>,
)

/**
 * 완료 화면이 소비할 한 산책의 경로 읽기 모델.
 *
 * [WalkSummary.segments]의 메타데이터를 버리지 않는다. 지도 공급자용 좌표 목록과 화면용
 * 상세값을 이 단계에서 갈라 두면, 완료 화면이 Room 행이나 NAVER SDK에 직접 의존하지 않는다.
 */
data class WalkSessionRoute(
    val segments: List<WalkRouteSegment>,
) {
    val points: List<WalkRoutePoint> get() = segments.flatMap(WalkRouteSegment::points)
    val start: WalkRoutePoint? get() = segments.firstNotNullOfOrNull { it.points.firstOrNull() }
    val end: WalkRoutePoint? get() = segments.asReversed().firstNotNullOfOrNull { it.points.lastOrNull() }
    val bounds: List<GeoPoint> get() = points.map(WalkRoutePoint::point)

    fun endpointsAreNear(maxDistanceMeters: Double): Boolean {
        val startPoint = start?.point ?: return false
        val endPoint = end?.point ?: return false
        return startPoint.distanceTo(endPoint) <= maxDistanceMeters
    }

    /**
     * 누른 지도 좌표와 가장 가까운 경로 기록점을 찾는다.
     *
     * 꼭짓점뿐 아니라 실제로 그린 선분까지의 거리를 잰다. GPS 간격이 70m보다 벌어지면
     * 선 한가운데를 눌러도 양쪽 점은 35m 밖일 수 있기 때문이다. 선분이 선택되면 그
     * 구간의 속도와 누적값을 가진 뒤쪽 기록점을 돌려준다.
     */
    fun nearestPointTo(target: GeoPoint, maxDistanceMeters: Double): WalkRoutePoint? {
        require(maxDistanceMeters >= 0.0) { "maxDistanceMeters must not be negative" }
        val nearestVertex = points.minByOrNull { it.point.distanceTo(target) }
        if (nearestVertex != null && nearestVertex.point.distanceTo(target) <= maxDistanceMeters) {
            return nearestVertex
        }
        return segments.asSequence()
            .flatMap { segment -> segment.points.zipWithNext().asSequence() }
            .map { (before, after) ->
                RouteSelectionCandidate(
                    point = after,
                    distanceMeters = target.distanceToSegment(before.point, after.point),
                )
            }
            .minByOrNull(RouteSelectionCandidate::distanceMeters)
            ?.takeIf { it.distanceMeters <= maxDistanceMeters }
            ?.point
    }
}

private data class RouteSelectionCandidate(
    val point: WalkRoutePoint,
    val distanceMeters: Double,
)

/** 짧은 산책 선분을 target 기준의 평면 미터 좌표로 옮겨 점-선분 최단거리를 잰다. */
private fun GeoPoint.distanceToSegment(start: GeoPoint, end: GeoPoint): Double {
    val referenceLatitude = Math.toRadians(latitude)
    fun GeoPoint.localX(): Double =
        Math.toRadians(longitude - this@distanceToSegment.longitude) *
            cos(referenceLatitude) * EARTH_RADIUS_METERS
    fun GeoPoint.localY(): Double =
        Math.toRadians(this.latitude - this@distanceToSegment.latitude) * EARTH_RADIUS_METERS

    val startX = start.localX()
    val startY = start.localY()
    val deltaX = end.localX() - startX
    val deltaY = end.localY() - startY
    val lengthSquared = deltaX * deltaX + deltaY * deltaY
    if (lengthSquared == 0.0) return hypot(startX, startY)
    val ratio = (-(startX * deltaX + startY * deltaY) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(startX + ratio * deltaX, startY + ratio * deltaY)
}

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** 저장된 화면용 세그먼트에서 누적 거리·추정 속도를 복원하고 세션의 단일 시간축을 붙인다. */
fun WalkSummary.toSessionRoute(): WalkSessionRoute {
    var cumulativeDistance = 0.0
    var activeElapsed = 0L
    val routeSegments = segments.mapIndexed { segmentIndex, samples ->
        var previousCapturedAt: Long? = null
        var previousPoint: GeoPoint? = null
        val points = samples.mapIndexed { pointIndex, sample ->
            val deltaDistance = previousPoint?.distanceTo(sample.point) ?: 0.0
            val deltaMillis = previousCapturedAt
                ?.let { (sample.capturedAtMillis - it).coerceAtLeast(0L) }
                ?: 0L
            cumulativeDistance += deltaDistance
            activeElapsed = activeElapsedAtMillis[sample.capturedAtMillis]
                ?: (activeElapsed + deltaMillis)
            val derivedSpeed = if (previousPoint != null && deltaMillis > 0L) {
                deltaDistance / (deltaMillis / 1_000.0)
            } else {
                null
            }
            WalkRoutePoint(
                point = sample.point,
                capturedAtMillis = sample.capturedAtMillis,
                accuracyMeters = sample.accuracyMeters,
                activeElapsedMillis = activeElapsed,
                cumulativeDistanceMeters = cumulativeDistance,
                derivedSpeedMetersPerSecond = derivedSpeed,
                segmentIndex = segmentIndex,
                pointIndex = pointIndex,
            ).also {
                previousCapturedAt = sample.capturedAtMillis
                previousPoint = sample.point
            }
        }
        WalkRouteSegment(index = segmentIndex, points = points)
    }
    return WalkSessionRoute(routeSegments)
}
