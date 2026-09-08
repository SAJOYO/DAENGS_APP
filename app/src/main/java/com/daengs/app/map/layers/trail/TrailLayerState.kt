package com.daengs.app.map.layers.trail

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.TrailSnapshot

/**
 * 지도 공급자와 무관하게 표현한 산책 동선.
 *
 * 각 경로는 서로 이어 그리면 안 되는 별도 세그먼트다. 일시정지나 비현실적인 GPS 점프
 * 전후를 한 선으로 합치지 않도록 중첩 목록 구조를 유지한다.
 */
data class TrailLayerState(
    val paths: List<List<GeoPoint>> = emptyList(),
    val speedPaths: List<List<com.daengs.app.map.style.WalkSpeedPoint>> = emptyList(),
    val startPoint: GeoPoint? = null,
)

/** 좌표와 속도 표시용 시각을 넘긴다. 기록 원본은 수정하지 않는다. */
fun TrailSnapshot.toTrailLayerState(): TrailLayerState =
    TrailLayerState(
        startPoint = startSample?.point ?: segments.firstOrNull()?.firstOrNull()?.point,
        paths = segments.map { segment -> segment.map { it.point } },
        speedPaths = segments.map { segment -> segment.map {
            com.daengs.app.map.style.WalkSpeedPoint(it.point, it.capturedAtMillis)
        } },
    )
