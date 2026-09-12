package com.daengs.app.map.layers.completedroute

import com.daengs.app.location.GeoPoint

/** Opt-in saved-session overlays; live walking and multi-session records never enable these implicitly. */
data class SessionRouteExplorerLayerState(
    val highlightPaths: List<List<GeoPoint>> = emptyList(),
    val cursor: GeoPoint? = null,
)
