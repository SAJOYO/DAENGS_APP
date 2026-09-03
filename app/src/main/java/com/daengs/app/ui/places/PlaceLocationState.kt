package com.daengs.app.ui.places

import com.daengs.app.location.GeoPoint

sealed interface PlaceLocationState {
    data object PermissionRequired : PlaceLocationState

    data object PermissionPermanentlyDenied : PlaceLocationState

    data class Locating(val lastKnown: GeoPoint?) : PlaceLocationState

    data class Ready(val point: GeoPoint) : PlaceLocationState

    data class Failed(
        val failure: PlaceLocationFailure,
        val lastKnown: GeoPoint?,
    ) : PlaceLocationState

    data class Unsupported(val point: GeoPoint) : PlaceLocationState
}

enum class PlaceLocationFailure {
    MOCK_LOCATION,
    UNAVAILABLE,
    UPDATE_FAILED,
}

val PlaceLocationState.currentPosition: GeoPoint?
    get() = when (this) {
        is PlaceLocationState.Locating -> lastKnown
        is PlaceLocationState.Ready -> point
        is PlaceLocationState.Failed -> lastKnown
        is PlaceLocationState.Unsupported -> point
        PlaceLocationState.PermissionRequired,
        PlaceLocationState.PermissionPermanentlyDenied,
        -> null
    }

val PlaceLocationState.devicePosition: GeoPoint?
    get() = when (this) {
        is PlaceLocationState.Locating -> lastKnown
        is PlaceLocationState.Ready -> point
        is PlaceLocationState.Failed -> lastKnown
        is PlaceLocationState.Unsupported,
        PlaceLocationState.PermissionRequired,
        PlaceLocationState.PermissionPermanentlyDenied,
        -> null
    }

val PlaceLocationState.locating: Boolean get() = this is PlaceLocationState.Locating

fun PlaceLocationState.userMessage(): String? = when (this) {
    PlaceLocationState.PermissionRequired -> "주변 시설을 찾으려면 위치 권한이 필요해요."
    PlaceLocationState.PermissionPermanentlyDenied ->
        "위치 권한이 꺼져 있어요. 설정에서 권한을 허용해주세요."
    is PlaceLocationState.Failed -> when (failure) {
        PlaceLocationFailure.MOCK_LOCATION -> "가상 위치로는 주변 장소를 검색할 수 없어요."
        PlaceLocationFailure.UNAVAILABLE -> "현재 위치를 확인하지 못했습니다."
        PlaceLocationFailure.UPDATE_FAILED -> "위치 업데이트를 이어가지 못했습니다."
    }
    is PlaceLocationState.Unsupported -> "현재는 대한민국 안의 시설만 검색할 수 있어요."
    is PlaceLocationState.Locating,
    is PlaceLocationState.Ready,
    -> null
}
