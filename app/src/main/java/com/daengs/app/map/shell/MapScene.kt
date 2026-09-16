package com.daengs.app.map.shell

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.layers.places.PlaceMarkerState
import com.daengs.app.map.layers.territory.TerritorySiteMarkerState
import com.daengs.app.map.layers.trail.TrailLayerState
import com.daengs.app.map.layers.traces.TraceRasterTile

/** 지도 표면이 한 프레임에 그릴 공급자 독립 상태. 비어 있는 레이어는 기존 화면에 영향을 주지 않는다. */
data class MapScene(
    val baseMapStyle: BaseMapStyle = BaseMapStyle.WALK_CONTEXT,
    val currentPosition: GeoPoint? = null,
    val places: List<PlaceMarkerState> = emptyList(),
    val territorySites: List<TerritorySiteMarkerState> = emptyList(),
    val moments: List<MomentMarkerState> = emptyList(),
    val trail: TrailLayerState = TrailLayerState(),
    val completedRoute: CompletedRouteLayerState = CompletedRouteLayerState(),
    val stayStamps: List<com.daengs.app.map.layers.stays.StayStampMarkerState> = emptyList(),
    val travelHeading: com.daengs.app.location.TravelHeading? = null,
    /** 이미 산책별로 합성한 표시 전용 흔적. 통계 값이나 이동 경로를 대신하지 않는다. */
    val traceTiles: List<TraceRasterTile> = emptyList(),
    val spatialCells: List<com.daengs.app.map.layers.spatial.SpatialDiaryPaintCell> = emptyList(),
    val allowRegionalOverview: Boolean = false,
    val sessionExplorer: com.daengs.app.map.layers.completedroute.SessionRouteExplorerLayerState? = null,
    val walkPresentation: WalkLayerPresentation? = null,
    /** Completed diary only: jointly pack scene/action objects beside the real route. */
    val detachedDiaryPins: Boolean = false,
    /** Records reuse diary action packing without changing diary-specific endpoint semantics. */
    val detachedRecordPins: Boolean = false,
    /** Real route segments used only as marker obstacles, never drawn or joined across gaps. */
    val markerAvoidancePaths: List<List<GeoPoint>> = emptyList(),
)

/** Multiple historical walks may span cities; active and single-walk maps keep their local zoom. */
internal fun MapScene.minimumZoom(): Double = when {
    allowRegionalOverview -> 5.0
    baseMapStyle == BaseMapStyle.TERRITORY_FOCUSED -> 13.0
    else -> 11.0
}
