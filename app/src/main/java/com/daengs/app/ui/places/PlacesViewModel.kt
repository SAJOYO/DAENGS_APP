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
import com.daengs.app.map.features.journey.PlaceJourneyController
import com.daengs.app.map.features.journey.PlaceJourneyState
import com.daengs.app.map.features.places.DEFAULT_PLACE_KIND
import com.daengs.app.map.features.places.PlaceDiscoveryController
import com.daengs.app.map.features.places.PlaceDiscoveryState
import com.daengs.app.map.features.places.PlaceOriginMode
import com.daengs.app.map.features.places.PlaceSearchArea
import com.daengs.app.map.features.places.PlaceSearchState
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

/** 시설 화면의 위치·검색·길찾기 생애를 화면 합성과 분리해 소유한다. */
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
    // 위치 관측은 최신 상태로 받아도, 그 관측에 매달린 옛 검색 명령은 실행하면 안 된다.
    // coordinator의 측위 세대와 별도로 두어 두 생애를 섞지 않는다.
    private var locationSearchGeneration = 0L

    private val discovery = PlaceDiscoveryController(
        repository = placeRepository,
        dogContext = null,
        scope = runtimeScope,
    )
    private val journey = PlaceJourneyController(
        repository = journeyRepository,
        scope = runtimeScope,
    )

    val state: StateFlow<PlacesUiState> = combine(
        location.state,
        discovery.state,
        journey.state,
    ) { location, discovery, journey ->
        PlacesUiState(location, discovery, journey)
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
            invalidatePendingLocationSearch(cancelLocation = true)
            journey.clear()
            discovery.clear()
        }
    }

    fun deactivate() {
        invalidatePendingLocationSearch(cancelLocation = true)
        location.deactivate()
        discovery.cancel()
    }

    fun updatePermission(granted: Boolean, permanentlyDenied: Boolean) {
        if (!granted) {
            invalidatePendingLocationSearch(cancelLocation = true)
            journey.clear()
            discovery.clear()
        }
        location.updatePermission(granted, permanentlyDenied)
        if (granted && location.isActive) startDefaultSearchIfNeeded()
    }

    fun updateDogContext(context: DogSearchContext?) {
        discovery.updateDogContext(context)
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
        val generation = ++locationSearchGeneration
        location.locate { sample ->
            if (generation == locationSearchGeneration) {
                beginSearch(sample.point, kind, preferParking)
            }
        }
    }

    fun searchAtCurrentOrigin(kind: PlaceKind, preferParking: Boolean) {
        val discoveryState = discovery.state.value
        val pinned = discoveryState.origin?.takeIf {
            discoveryState.originMode == PlaceOriginMode.PINNED
        }
        when {
            pinned != null -> startSearch(pinned, kind, preferParking, PlaceOriginMode.PINNED)
            location.state.value.devicePosition != null -> startSearch(
                location.state.value.devicePosition!!,
                kind,
                preferParking,
            )
            else -> locateAndSearch(kind, preferParking)
        }
    }

    fun searchAt(point: GeoPoint, kind: PlaceKind, preferParking: Boolean) {
        startSearch(point, kind, preferParking, PlaceOriginMode.PINNED)
    }

    fun retrySearch() {
        invalidatePendingLocationSearch()
        discovery.retry()
    }

    fun selectPlace(key: PlaceKey) {
        discovery.select(key)
    }

    fun loadJourney(place: PlaceResult) {
        val origin = location.state.value.devicePosition
        if (origin == null) {
            journey.reject(place.key, "현재 위치를 확인한 뒤 길찾기를 다시 눌러주세요.")
        } else {
            journey.load(origin, place)
        }
    }

    fun retryJourney() {
        journey.retry()
    }

    private fun beginSearch(
        origin: GeoPoint,
        kind: PlaceKind,
        preferParking: Boolean,
        originMode: PlaceOriginMode = PlaceOriginMode.DEVICE,
    ) {
        journey.clear()
        discovery.search(origin, listOf(kind), preferParking, originMode)
    }

    private fun startSearch(
        origin: GeoPoint,
        kind: PlaceKind,
        preferParking: Boolean,
        originMode: PlaceOriginMode = PlaceOriginMode.DEVICE,
    ) {
        invalidatePendingLocationSearch()
        beginSearch(origin, kind, preferParking, originMode)
    }

    private fun startDefaultSearchIfNeeded() {
        if (discovery.state.value.search !is PlaceSearchState.Idle) return
        val known = location.state.value.devicePosition
        if (known == null) {
            locateAndSearch(DEFAULT_PLACE_KIND, preferParking = false)
        } else {
            startSearch(known, DEFAULT_PLACE_KIND, preferParking = false)
        }
    }

    private fun invalidatePendingLocationSearch(cancelLocation: Boolean = false) {
        locationSearchGeneration++
        if (cancelLocation) location.cancelLocate()
    }

    private fun acceptLocationUpdate(sample: LocationSample) {
        if (PlaceSearchArea.contains(sample.point)) return
        val currentSearch = discovery.state.value
        if (currentSearch.originMode == PlaceOriginMode.DEVICE) {
            invalidatePendingLocationSearch(cancelLocation = true)
            journey.clear()
            discovery.search(
                origin = sample.point,
                kinds = currentSearch.requestedKinds.ifEmpty { listOf(DEFAULT_PLACE_KIND) },
                preferParking = currentSearch.preferParking,
                originMode = PlaceOriginMode.DEVICE,
            )
        }
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
