package com.daengs.app.map.shell

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.CompletedRouteLayerState
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.layers.places.PlaceMarkerState
import com.daengs.app.map.layers.territory.TerritorySiteMarkerState
import com.daengs.app.map.layers.trail.TrailLayerState

/** 같은 지도 표면을 쓰는 화면의 제품 목적. 데이터 원천이나 SDK 모드가 아니다. */
enum class MapPurpose {
    PLACE_SEARCH,
    WALK,
    TERRITORY,
}

/** 지도 공급자가 목적에 맞게 번역할 바탕 지도 밀도. 기능 레이어의 의미는 갖지 않는다. */
enum class BaseMapStyle {
    SEARCH_DETAIL,
    WALK_CONTEXT,
    TERRITORY_FOCUSED,
}

data class MapSceneSources(
    val currentPosition: GeoPoint? = null,
    val places: List<PlaceMarkerState> = emptyList(),
    val territorySites: List<TerritorySiteMarkerState> = emptyList(),
    val moments: List<MomentMarkerState> = emptyList(),
    val trail: TrailLayerState = TrailLayerState(),
    val completedRoute: CompletedRouteLayerState = CompletedRouteLayerState(),
)

data class MapDisplayPolicy(
    val baseMapStyle: BaseMapStyle,
    val showPlaces: Boolean,
    val showTerritorySites: Boolean,
    val showMoments: Boolean,
    val showLiveTrail: Boolean,
    val showCompletedRoute: Boolean,
)

/**
 * 어떤 화면에서 무엇을 보여 줄지는 여기서 한 번만 정한다.
 *
 * 공급자에서 가리는 방식은 이미 받아 둔 시설 데이터가 화면 전환 뒤 다시 보일 수 있다.
 * 그래서 금지된 레이어는 [MapScene]을 만들 때 비워 Naver SDK까지 도달하지 못하게 한다.
 */
fun composeMapScene(
    purpose: MapPurpose,
    sources: MapSceneSources,
    walkActive: Boolean = false,
): MapScene {
    val policy = mapDisplayPolicy(purpose, walkActive)
    return MapScene(
        baseMapStyle = policy.baseMapStyle,
        currentPosition = sources.currentPosition,
        places = sources.places.takeIf { policy.showPlaces }.orEmpty(),
        territorySites = sources.territorySites.takeIf { policy.showTerritorySites }.orEmpty(),
        moments = sources.moments.takeIf { policy.showMoments }.orEmpty(),
        trail = sources.trail.takeIf { policy.showLiveTrail } ?: TrailLayerState(),
        completedRoute = sources.completedRoute.takeIf { policy.showCompletedRoute }
            ?: CompletedRouteLayerState(),
    )
}

fun mapDisplayPolicy(purpose: MapPurpose, walkActive: Boolean): MapDisplayPolicy = when (purpose) {
    MapPurpose.PLACE_SEARCH -> MapDisplayPolicy(
        baseMapStyle = BaseMapStyle.SEARCH_DETAIL,
        showPlaces = true,
        showTerritorySites = false,
        showMoments = false,
        showLiveTrail = false,
        showCompletedRoute = false,
    )
    MapPurpose.WALK -> MapDisplayPolicy(
        baseMapStyle = BaseMapStyle.WALK_CONTEXT,
        showPlaces = false,
        showTerritorySites = false,
        showMoments = true,
        showLiveTrail = true,
        showCompletedRoute = true,
    )
    MapPurpose.TERRITORY -> MapDisplayPolicy(
        baseMapStyle = BaseMapStyle.WALK_CONTEXT,
        showPlaces = false,
        showTerritorySites = true,
        showMoments = true,
        showLiveTrail = walkActive,
        showCompletedRoute = false,
    )
}
