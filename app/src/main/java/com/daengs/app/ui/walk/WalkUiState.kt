package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.map.features.territory.TerritoryBoardState
import com.daengs.app.map.features.territory.TerritoryGameState
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.pet.Pet
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.WalkMoment
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkRoutePoint
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.WalkTrackingState

enum class WalkLocationOwner {
    NONE,
    SCREEN,
    TRACKING_SERVICE,
}

data class WalkLocationUiState(
    val permissionGranted: Boolean = false,
    val precisePermission: Boolean = false,
    val currentPosition: GeoPoint? = null,
    val sample: LocationSample? = null,
    val followDevice: Boolean = true,
    val errorMessage: String? = null,
    val locating: Boolean = false,
    val centerOn: GeoPoint? = null,
    val centerZoom: Double? = null,
    val owner: WalkLocationOwner = WalkLocationOwner.NONE,
)

data class WalkSelectionState(
    val pets: List<Pet> = emptyList(),
    val selectedDogIds: Set<String> = emptySet(),
)

data class WalkMapUiState(
    val purpose: MapPurpose = MapPurpose.WALK,
    val selectedMomentId: String? = null,
    val frameSelectedTerritory: Boolean = false,
    val selectedRoutePointKey: String? = null,
    val selectedRouteSessionId: String? = null,
)

data class WalkCompletionUiState(
    val detail: WalkSessionDetail? = null,
    val resultExpanded: Boolean = true,
)

data class WalkUiState(
    val tracking: WalkTrackingState = WalkTrackingState(),
    val location: WalkLocationUiState = WalkLocationUiState(),
    val selection: WalkSelectionState = WalkSelectionState(),
    val map: WalkMapUiState = WalkMapUiState(),
    val territory: TerritoryBoardState = TerritoryBoardState(),
    val territoryGame: TerritoryGameState = TerritoryGameState(),
    val completion: WalkCompletionUiState = WalkCompletionUiState(),
    val momentNotice: String? = null,
    val diaryPhotos: List<com.daengs.app.walk.WalkPhoto> = emptyList(),
    val nearbyTerritory: com.daengs.app.map.features.territory.TerritoryNearbyState = com.daengs.app.map.features.territory.TerritoryNearbyState(),
)

val WalkUiState.trackingActive: Boolean
    get() = tracking.trail.state != TrackingState.OFF

val WalkUiState.completedSummary: WalkSummary?
    get() = completion.detail?.summary

val WalkUiState.displayedMoments: List<WalkMoment>
    get() = completion.detail?.moments ?: tracking.momentGroups

val WalkUiState.selectedRoutePoint: WalkRoutePoint?
    get() = completion.detail?.route?.points?.firstOrNull {
        map.selectedRouteSessionId == tracking.completedSessionId &&
            it.routePointKey == map.selectedRoutePointKey
    }

/** 화면에서 발생하는 산책 기능 입력을 하나의 닫힌 계약으로 둔다. */
sealed interface WalkAction {
    data object Back : WalkAction
    data object Home : WalkAction
    data object StartRequested : WalkAction
    data object StartConfirmed : WalkAction
    data object Pause : WalkAction
    data object Resume : WalkAction
    data object Stop : WalkAction
    data object Locate : WalkAction
    data object OpenAppSettings : WalkAction
    data object ClearTerritory : WalkAction
    data object OpenEntries : WalkAction

    /**
     * 산책 일기 **목록**으로 나간다.
     *
     * [OpenEntries] 와 다른 자리다 — 저쪽은 지금 걷는 산책 한 건에 남긴 것이고,
     * 이쪽은 지난 산책들의 목록이다. 걷는 중인 산책은 끝나야 목록에 들어가므로
     * 둘을 하나로 합칠 수 없다.
     */
    data object OpenDiaryList : WalkAction
    data object PhotographWalk : WalkAction
    data class SelectClaimingPet(val siteId: String, val petId: String) : WalkAction
    data object RetryTerritory : WalkAction
    data object RefreshClaimAccess : WalkAction
    data class MarkTerritory(val siteId: String) : WalkAction
    data class PhotographTerritory(val siteId: String) : WalkAction
    data object ClearRoutePoint : WalkAction
    data object ReviewMap : WalkAction
    data object ShowResult : WalkAction
    data object CloseResult : WalkAction
    data class ToggleDog(val id: String) : WalkAction
    data class ChangeMapPurpose(val purpose: MapPurpose) : WalkAction
    data class RequestOrientation(val orientation: WalkOrientation) : WalkAction
    data class AddMoment(val type: WalkMomentType) : WalkAction
    data class SelectMoment(val id: String) : WalkAction
    data class SelectTerritorySite(val id: String) : WalkAction
    data class SelectRouteEndpoint(val id: String) : WalkAction
    data class CameraSettled(val point: GeoPoint) : WalkAction
    data object CameraMoved : WalkAction
    data class MapTapped(val point: GeoPoint) : WalkAction
}

/** Android 경계에서만 수행해야 하는 일은 일회성 effect로 Route에 전달한다. */
sealed interface WalkEffect {
    data object NavigateHome : WalkEffect
    data object RequestNotificationPermission : WalkEffect
    data object OpenAppSettings : WalkEffect
    data class ChangeOrientation(val orientation: WalkOrientation) : WalkEffect
    data class CaptureTerritory(val target: com.daengs.app.map.features.territory.TerritoryCaptureTarget) : WalkEffect
}

internal fun WalkLocationUiState.accept(sample: LocationSample): WalkLocationUiState =
    copy(currentPosition = sample.point, sample = sample)
