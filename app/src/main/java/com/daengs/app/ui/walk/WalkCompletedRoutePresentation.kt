package com.daengs.app.ui.walk

import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.completedroute.RouteEndpointKind
import com.daengs.app.map.layers.completedroute.RouteEndpointMarkerState
import com.daengs.app.walk.WalkRoutePoint
import com.daengs.app.walk.WalkSessionRoute

internal const val ROUTE_START_ID = "walk-route-start"
internal const val ROUTE_END_ID = "walk-route-end"
internal const val ROUTE_START_END_ID = "walk-route-start-end"

/** 완료 직후와 지난 산책이 같은 출발·도착·구간 경계 표현을 쓰게 하는 화면 모델 변환. */
internal fun WalkSessionRoute.toCompletedRouteLayerState(
    selectedPoint: WalkRoutePoint? = null,
): CompletedRouteLayerState {
    val endpointsNear = endpointsAreNear(ROUTE_ENDPOINT_MERGE_METERS)
    return CompletedRouteLayerState(
        paths = segments.map { segment -> segment.points.map { it.point } },
        speedPaths = segments.map { segment -> segment.points.map {
            com.daengs.app.map.style.WalkSpeedPoint(it.point, it.capturedAtMillis)
        } },
        start = if (endpointsNear) {
            val startPoint = start
            val endPoint = end
            if (startPoint != null && endPoint != null) {
                RouteEndpointMarkerState(
                    id = ROUTE_START_END_ID,
                    point = endPoint.point,
                    label = "출발 · 도착",
                    kind = RouteEndpointKind.START_END,
                    selected = selectedPoint == startPoint || selectedPoint == endPoint,
                )
            } else {
                null
            }
        } else start?.let { point ->
            RouteEndpointMarkerState(
                id = ROUTE_START_ID,
                point = point.point,
                label = "출발",
                kind = RouteEndpointKind.START,
                selected = selectedPoint == point,
            )
        },
        end = if (endpointsNear) null else end?.let { point ->
            RouteEndpointMarkerState(
                id = ROUTE_END_ID,
                point = point.point,
                label = "도착",
                kind = RouteEndpointKind.END,
                selected = selectedPoint == point,
            )
        },
        gapEndpoints = segments.zipWithNext().flatMap { (before, after) ->
            listOfNotNull(before.points.lastOrNull()?.point, after.points.firstOrNull()?.point)
        },
        selectedPoint = selectedPoint?.point,
    )
}

private const val ROUTE_ENDPOINT_MERGE_METERS = 12.0
