package com.daengs.app.map.shell

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.places.PlaceMarkerState

/** Probe: trail/territory 필드는 walk 축이라 절개 (원본 geo MapScene 참고). */
data class MapScene(
    val currentPosition: GeoPoint? = null,
    val places: List<PlaceMarkerState> = emptyList(),
)
