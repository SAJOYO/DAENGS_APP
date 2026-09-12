package com.daengs.app.map.layers.completedroute

import com.daengs.app.location.GeoPoint

/** Relation between two confirmed endpoints. Intentionally has no path, distance, time interpolation or direction. */
data class GapGuideCommand(val contextId: String, val beforePoint: GeoPoint, val afterPoint: GeoPoint)
data class RecordContextMarker(val contextId: String, val point: GeoPoint, val label: String, val selected: Boolean)
data class RecordContextLayerState(
    val markers: List<RecordContextMarker> = emptyList(),
    val selectedGapGuide: GapGuideCommand? = null,
    val endpoints: List<RouteEndpointMarkerState> = emptyList(),
)
