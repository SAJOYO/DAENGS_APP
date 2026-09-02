package com.daengs.app.map.shell

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.places.PlaceMarkerState
import com.daengs.app.map.layers.trail.TrailLayerState

/** 지도 표면이 한 프레임에 그릴 공급자 독립 상태. 비어 있는 레이어는 기존 화면에 영향을 주지 않는다. */
data class MapScene(
    val currentPosition: GeoPoint? = null,
    val places: List<PlaceMarkerState> = emptyList(),
    val trail: TrailLayerState = TrailLayerState(),
)
