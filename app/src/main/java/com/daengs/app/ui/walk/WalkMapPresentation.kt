package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.layers.territory.TerritorySiteMarkerState
import com.daengs.app.map.layers.trail.TrailLayerState
import com.daengs.app.map.layers.trail.toTrailLayerState
import com.daengs.app.map.shell.MapScene
import com.daengs.app.map.shell.MapSceneSources
import com.daengs.app.map.shell.composeMapScene

internal data class WalkMapPresentation(
    val scene: MapScene,
    val fitBounds: List<GeoPoint>?,
)

/** Walk 상태를 지도 공급자와 무관한 scene으로 투영한다. */
internal fun WalkUiState.toMapPresentation(
    formatTime: (Long) -> String,
): WalkMapPresentation {
    val route = completion.detail?.route
    val summary = completedSummary
    val fitBounds = route?.bounds.orEmpty().ifEmpty {
        listOfNotNull(summary?.anchor)
    }.takeIf { summary != null && it.isNotEmpty() }

    return WalkMapPresentation(
        scene = composeMapScene(
            purpose = map.purpose,
            sources = MapSceneSources(
                currentPosition = location.currentPosition.takeIf {
                    summary == null && location.permissionGranted
                },
                territorySites = territory.sites.map { site ->
                    TerritorySiteMarkerState(
                        id = site.id,
                        point = site.point,
                        selected = site.id == territory.selectedSiteId,
                    )
                },
                moments = displayedMoments.map { moment ->
                    MomentMarkerState(
                        id = moment.id,
                        point = moment.point,
                        label = moment.markerLabel,
                        selected = moment.id == map.selectedMomentId,
                    )
                },
                trail = if (completion.detail == null) {
                    tracking.trail.toTrailLayerState()
                } else {
                    TrailLayerState()
                },
                completedRoute = route?.toCompletedRouteLayerState(
                    selectedPoint = selectedRoutePoint,
                    formatTime = formatTime,
                ) ?: CompletedRouteLayerState(),
            ),
            walkActive = trackingActive,
        ),
        fitBounds = fitBounds,
    )
}
