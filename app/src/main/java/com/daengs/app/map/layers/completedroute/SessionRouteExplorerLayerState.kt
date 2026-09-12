package com.daengs.app.map.layers.completedroute

import com.daengs.app.location.GeoPoint

/** Opt-in saved-session overlays; live walking and multi-session records never enable these implicitly. */
data class SessionRouteExplorerLayerState(
    val highlightPaths: List<List<GeoPoint>> = emptyList(),
    val cursor: GeoPoint? = null,
    /** An unresolved scene/passage has no direction to assert; empty emphasis is not overview. */
    val useOverviewDirections: Boolean = true,
    val observedParts: List<RouteRenderPart> = emptyList(),
) {
    val emphasisPaths get() = highlightPaths + observedParts.filter { it.selected }.map { it.path }
    val observedDirectionEdges get() = observedParts.filter { it.selected || useOverviewDirections && !it.selected }
        .flatMap { it.directions }.distinctBy { it.id }
}

internal fun SessionRouteExplorerLayerState.directionPaths(overview: List<List<GeoPoint>>): List<List<GeoPoint>> =
    highlightPaths.ifEmpty { if (useOverviewDirections) overview else emptyList() }
