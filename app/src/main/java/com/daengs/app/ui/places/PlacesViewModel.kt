package com.daengs.app.ui.places

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daengs.app.BuildConfig
import com.daengs.app.journey.HttpJourneyRepository
import com.daengs.app.journey.JourneyApi
import com.daengs.app.journey.JourneyRepository
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.map.features.journey.PlaceJourneyState
import com.daengs.app.map.features.places.PlaceDiscoveryState
import com.daengs.app.map.features.places.PlaceSearchArea
import com.daengs.app.place.DogSearchContext
import com.daengs.app.place.PlaceApi
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceRepository
import com.daengs.app.place.PlaceResult
import com.daengs.app.place.PlaceSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlacesUiState(
    val location: PlaceLocationState = PlaceLocationState.PermissionRequired,
    val discovery: PlaceDiscoveryState = PlaceDiscoveryState(),
    val journey: PlaceJourneyState = PlaceJourneyState(),
)

/** 화면에서 발생할 수 있는 시설 기능 입력을 하나의 닫힌 계약으로 둔다. */
sealed interface PlacesAction {
    data class Locate(val kind: PlaceKind, val preferParking: Boolean) : PlacesAction

    data class Search(val kind: PlaceKind, val preferParking: Boolean) : PlacesAction

    data class SearchAt(
        val point: GeoPoint,
        val kind: PlaceKind,
        val preferParking: Boolean,
    ) : PlacesAction

    data object RetrySearch : PlacesAction

    data class Select(val key: PlaceKey) : PlacesAction

    data class LoadJourney(val place: PlaceResult) : PlacesAction

    data object RetryJourney : PlacesAction
}

/** 시설 화면 입력을 위치 생애와 탐색 세션 경계에 연결하고 화면 상태를 합성한다. */
class PlacesViewModel(
    placeRepository: PlaceSearchRepository,
    journeyRepository: JourneyRepository,
    locationSource: LocationSource,
    externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val runtimeScope = externalScope ?: viewModelScope
    private val location = PlaceLocationCoordinator(
        source = locationSource,
        scope = runtimeScope,
    )
    private val session = PlaceSessionCoordinator(
        placeRepository = placeRepository,
        journeyRepository = journeyRepository,
        scope = runtimeScope,
    )

    val state: StateFlow<PlacesUiState> = combine(
        location.state,
        session.state,
    ) { location, session ->
        PlacesUiState(location, session.discovery, session.journey)
    }.stateIn(
        scope = runtimeScope,
        started = SharingStarted.Eagerly,
        initialValue = PlacesUiState(),
    )

    init {
        runtimeScope.launch {
            location.updates.collect(::acceptLocationUpdate)
        }
    }

    fun activate(permissionGranted: Boolean) {
        location.activate(permissionGranted)
        if (permissionGranted) {
            startDefaultSearchIfNeeded()
        } else {
            location.cancelLocate()
            session.clear()
        }
    }

    fun deactivate() {
        location.deactivate()
        session.deactivate()
    }

    fun updatePermission(granted: Boolean, permanentlyDenied: Boolean) {
        if (!granted) {
            location.cancelLocate()
            session.clear()
        }
        location.updatePermission(granted, permanentlyDenied)
        if (granted && location.isActive) startDefaultSearchIfNeeded()
    }

    fun updateDogContext(context: DogSearchContext?) {
        session.updateDogContext(context)
    }

    fun onAction(action: PlacesAction) {
        when (action) {
            is PlacesAction.Locate -> locateAndSearch(action.kind, action.preferParking)
            is PlacesAction.Search -> searchAtCurrentOrigin(action.kind, action.preferParking)
            is PlacesAction.SearchAt -> searchAt(
                action.point,
                action.kind,
                action.preferParking,
            )
            PlacesAction.RetrySearch -> retrySearch()
            is PlacesAction.Select -> selectPlace(action.key)
            is PlacesAction.LoadJourney -> loadJourney(action.place)
            PlacesAction.RetryJourney -> retryJourney()
        }
    }

    fun locateAndSearch(kind: PlaceKind, preferParking: Boolean) {
        if (location.state.value is PlaceLocationState.PermissionRequired ||
            location.state.value is PlaceLocationState.PermissionPermanentlyDenied
        ) {
            return
        }
        locate(session.requestDeviceSearch(kind, preferParking))
    }

    fun searchAtCurrentOrigin(kind: PlaceKind, preferParking: Boolean) {
        session.searchAtCurrentOrigin(
            kind = kind,
            preferParking = preferParking,
            devicePosition = location.state.value.devicePosition,
        )?.let(::locate)
    }

    fun searchAt(point: GeoPoint, kind: PlaceKind, preferParking: Boolean) {
        session.searchAt(point, kind, preferParking)
    }

    fun retrySearch() {
        session.retrySearch()
    }

    fun selectPlace(key: PlaceKey) {
        session.selectPlace(key)
    }

    fun loadJourney(place: PlaceResult) {
        session.loadJourney(location.state.value.devicePosition, place)
    }

    fun retryJourney() {
        session.retryJourney()
    }

    private fun locate(request: PendingDevicePlaceSearch) {
        location.locate { sample -> session.resolveDeviceSearch(request, sample.point) }
    }

    private fun startDefaultSearchIfNeeded() {
        session.startDefaultSearchIfNeeded(location.state.value.devicePosition)?.let(::locate)
    }

    private fun acceptLocationUpdate(sample: LocationSample) {
        if (PlaceSearchArea.contains(sample.point)) return
        location.cancelLocate()
        session.replaceUnsupportedDeviceOrigin(sample.point)
    }

    override fun onCleared() {
        deactivate()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(PlacesViewModel::class.java))
                    return PlacesViewModel(
                        placeRepository = PlaceRepository(
                            PlaceApi(baseUrl = { BuildConfig.API_BASE_URL }),
                        ),
                        journeyRepository = HttpJourneyRepository(
                            JourneyApi(baseUrl = { BuildConfig.API_BASE_URL }),
                        ),
                        locationSource = FusedLocationSource(context.applicationContext),
                    ) as T
                }
            }
    }
}
