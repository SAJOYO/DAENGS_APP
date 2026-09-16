package com.daengs.app.map.layers.completedroute

import com.daengs.app.map.shell.MapScene

internal const val LIVE_ROUTE_START_ID = "walk-live-start"

/** Completed endpoints replace the live start; recording never guesses an arrival. */
internal fun MapScene.routeEndpointStamps(): List<RouteEndpointMarkerState> {
    if (detachedDiaryPins) {
        val base = listOfNotNull(completedRoute.start, completedRoute.end)
        return base.groupBy { it.point }.values.map { group ->
            val kind = if (group.size > 1) RouteEndpointKind.START_END else group.single().kind
            group.first().copy(kind = kind, label = when (kind) {
                RouteEndpointKind.START -> "산책 시작"
                RouteEndpointKind.END -> "산책 끝"
                RouteEndpointKind.START_END -> "산책 시작·끝"
            }, compact = true, abovePoint = true)
        }
    }
    sessionExplorer?.recordContext?.let { return it.endpoints }
    val completed = listOfNotNull(completedRoute.start, completedRoute.end)
    if (completed.isNotEmpty()) return completed
    return listOfNotNull(trail.startPoint?.let {
        RouteEndpointMarkerState(LIVE_ROUTE_START_ID, it, "출발", RouteEndpointKind.START)
    })
}
