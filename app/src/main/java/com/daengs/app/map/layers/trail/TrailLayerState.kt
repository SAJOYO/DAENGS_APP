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
)

/** 위치 표본의 기록 메타데이터를 제외하고 지도에 필요한 좌표만 넘긴다. */
fun TrailSnapshot.toTrailLayerState(): TrailLayerState =
    TrailLayerState(paths = segments.map { segment -> segment.map { it.point } })
