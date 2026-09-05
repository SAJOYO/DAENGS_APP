package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.layers.territory.TerritorySiteMarkerState
import com.daengs.app.map.layers.territory.TerritoryMarkerOccupancy
import com.daengs.app.territory.ClaimAccess
import com.daengs.app.territory.ClaimCertification
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
    val gameSites = territoryGame.sites.associateBy { it.site.id }
    val fitBounds = (route?.bounds.orEmpty().ifEmpty {
        listOfNotNull(summary?.anchor)
    } + diaryPhotos.map { it.point }).takeIf { summary != null && it.isNotEmpty() }

    return WalkMapPresentation(
        scene = composeMapScene(
            purpose = map.purpose,
            sources = MapSceneSources(
                currentPosition = location.currentPosition.takeIf {
                    summary == null && location.permissionGranted
                },
                territorySites = territory.sites.map { site ->
                    val gameSite = gameSites[site.id]
                    val target = if (territoryGame.enabled) site.id == territoryGame.targetId
                        else site.id == territory.selectedSiteId
                    TerritorySiteMarkerState(
                        id = site.id,
                        point = site.point,
                        selected = target,
                        occupancy = when (gameSite?.claim?.occupancy?.certification) {
                            null -> TerritoryMarkerOccupancy.NEUTRAL
                            ClaimCertification.UNVERIFIED -> TerritoryMarkerOccupancy.UNVERIFIED
                            ClaimCertification.VERIFIED -> TerritoryMarkerOccupancy.VERIFIED
                        },
                        label = gameSite?.occupancyLabel ?: "미점유",
                        ready = gameSite?.interaction?.access == ClaimAccess.READY,
                        radiusMeters = territoryGame.radiusMeters.takeIf { territoryGame.enabled && target && territoryGame.phase == com.daengs.app.map.features.territory.TerritoryWalkPhase.WALKING },
                    )
                },
                moments = displayedMoments.map { moment ->
                    MomentMarkerState(
                        id = moment.id,
                        point = moment.point,
                        label = moment.markerLabel,
                        selected = moment.id == map.selectedMomentId,
                    )
                } + diaryPhotos.photoMarkers(),
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
        fitBounds = fitBounds ?: territoryGame.target?.takeIf {
            map.purpose == com.daengs.app.map.shell.MapPurpose.TERRITORY && map.frameSelectedTerritory
        }?.let { listOfNotNull(it.site.point, location.currentPosition) },
    )
}

internal fun List<com.daengs.app.walk.WalkPhoto>.photoMarkers(): List<MomentMarkerState> = map {
    MomentMarkerState("photo-${it.id}", it.point, "사진", photoFile = it.file)
}
