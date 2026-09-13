package com.daengs.app.map.features.records

import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.daengs.app.map.shell.MapScene
import com.daengs.app.map.shell.WalkLayerPresentation
import com.daengs.app.map.shell.WalkLayerStack
import com.daengs.app.map.style.speedScaleStops

data class WalkRecordsRenderPlan(
    val scene: MapScene,
    val traceLegend: List<TraceLegendItem>,
    val routeLegend: List<Pair<Float, Int>>,
)

/** Records' only scene assembly point: raster pigments and route style must already be explicit. */
fun composeWalkRecordsMapScene(
    policy: WalkRecordsDisplayPolicy,
    tiles: List<TraceRasterTile> = emptyList(),
    route: CompletedRouteLayerState = CompletedRouteLayerState(),
    moments: List<MomentMarkerState> = emptyList(),
): WalkRecordsRenderPlan {
    require(tiles.all { it.rgb != null }) { "모아보기 흔적의 표시 색이 준비되지 않았어요." }
    return WalkRecordsRenderPlan(
        MapScene(traceTiles = tiles, completedRoute = route, moments = moments, allowRegionalOverview = true,
            walkPresentation = WalkLayerPresentation(WalkLayerStack.RECORDS, policy.route)),
        policy.trace.density.legend(), policy.route.speedPolicy.speedScaleStops(policy.route.themeId),
    )
}
