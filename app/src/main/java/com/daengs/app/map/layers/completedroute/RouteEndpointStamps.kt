package com.daengs.app.map.layers.completedroute

import com.daengs.app.map.shell.MapScene

internal const val LIVE_ROUTE_START_ID = "walk-live-start"

/** Completed endpoints replace the live start; recording never guesses an arrival. */
internal fun MapScene.routeEndpointStamps(): List<RouteEndpointMarkerState> {
    val completed = listOfNotNull(completedRoute.start, completedRoute.end)
    if (completed.isNotEmpty()) return completed
    return listOfNotNull(trail.startPoint?.let {
        RouteEndpointMarkerState(LIVE_ROUTE_START_ID, it, "출발", RouteEndpointKind.START)
    })
}
