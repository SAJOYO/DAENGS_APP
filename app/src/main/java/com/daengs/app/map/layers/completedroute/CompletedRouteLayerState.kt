package com.daengs.app.map.layers.completedroute

import com.daengs.app.location.GeoPoint

/** 출발·도착은 같은 모양의 일반 핀으로 합치지 않는다. 색뿐 아니라 안쪽 기호도 달라야 한다. */
enum class RouteEndpointKind { START, END, START_END }

data class RouteEndpointMarkerState(
    val id: String,
    val point: GeoPoint,
    val label: String,
    val kind: RouteEndpointKind,
    val selected: Boolean = false,
)

/**
 * 완료한 한 산책만을 그리는 지도 공급자 독립 상태.
 *
 * 실시간 [com.daengs.app.map.layers.trail.TrailLayerState]와 분리해 완료 화면의 출발·도착·
 * 끊긴 구간·선택점을 라이브 기록 상태에 섞지 않는다.
 */
data class CompletedRouteLayerState(
    val paths: List<List<GeoPoint>> = emptyList(),
    val start: RouteEndpointMarkerState? = null,
    val end: RouteEndpointMarkerState? = null,
    /** 내부 세그먼트 경계의 양쪽 점. 원인을 단정하지 않고 선이 끊겼다는 사실만 표시한다. */
    val gapEndpoints: List<GeoPoint> = emptyList(),
    val selectedPoint: GeoPoint? = null,
)
