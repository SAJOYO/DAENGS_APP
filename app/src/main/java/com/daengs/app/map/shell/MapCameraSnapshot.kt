package com.daengs.app.map.shell

import com.daengs.app.location.GeoPoint

/** A view's camera, kept separately from its search conditions and selected walk population. */
data class MapCameraSnapshot(
    val target: GeoPoint,
    val zoom: Double,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0,
)
