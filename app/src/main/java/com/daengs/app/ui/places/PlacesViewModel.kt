package com.daengs.app.ui.places

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daengs.app.BuildConfig
import com.daengs.app.journey.HttpJourneyRepository
import com.daengs.app.journey.JourneyApi
import com.daengs.app.journey.JourneyRepository
import com.daengs.app.location.FeedStatus
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.location.LocationSource
import com.daengs.app.location.LocationTracker
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val locationSource: LocationSource,
    externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val runtimeScope = externalScope ?: viewModelScope
    private val locationTracker = LocationTracker(runtimeScope)
    private val mutableLocation = MutableStateFlow<PlaceLocationState>(
        PlaceLocationState.PermissionRequired,
    )
    private var active = false
    private var locateJob: Job? = null

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
        mutableLocation,
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
            locationTracker.updates.collect(::acceptLocationUpdate)
        }
        runtimeScope.launch {
            locationTracker.status.collect { status ->
                if (status is FeedStatus.Failed) {
                    mutableLocation.value = PlaceLocationState.Failed(
                        failure = PlaceLocationFailure.UPDATE_FAILED,
                        lastKnown = mutableLocation.value.devicePosition,
                    )
                }
            }
        }
    }

    fun activate(permissionGranted: Boolean) {
        active = true
        updatePermission(permissionGranted, permanentlyDenied = false)
    }

    fun deactivate() {
        active = false
        locateJob?.cancel()
        locateJob = null
        locationTracker.stop()
        discovery.cancel()
    }

    fun updatePermission(granted: Boolean, permanentlyDenied: Boolean) {
        if (!granted) {
            locateJob?.cancel()
            locateJob = null
            locationTracker.stop()
            journey.clear()
            discovery.clear()
            mutableLocation.value = if (permanentlyDenied) {
                PlaceLocationState.PermissionPermanentlyDenied
            } else {
                PlaceLocationState.PermissionRequired
            }
            return
        }

        if (mutableLocation.value is PlaceLocationState.PermissionRequired ||
            mutableLocation.value is PlaceLocationState.PermissionPermanentlyDenied
        ) {
            mutableLocation.value = PlaceLocationState.Locating(lastKnown = null)
        }
        if (!active) return
        locationTracker.start(locationSource)
        if (discovery.state.value.search is PlaceSearchState.Idle) {
            val known = mutableLocation.value.devicePosition
            if (known == null) {
                locateAndSearch(DEFAULT_PLACE_KIND, preferParking = false)
            } else {
                beginSearch(known, DEFAULT_PLACE_KIND, preferParking = false)
            }
        }
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
        if (mutableLocation.value is PlaceLocationState.PermissionRequired ||
            mutableLocation.value is PlaceLocationState.PermissionPermanentlyDenied
        ) {
            return
        }
        locateJob?.cancel()
        val lastKnown = mutableLocation.value.devicePosition
        mutableLocation.value = PlaceLocationState.Locating(lastKnown)
        locateJob = runtimeScope.launch {
            val sample = try {
                locationSource.currentLocation()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                mutableLocation.value = PlaceLocationState.Failed(
                    PlaceLocationFailure.UNAVAILABLE,
                    lastKnown,
                )
                return@launch
            }

            when {
                sample.isMock -> mutableLocation.value = PlaceLocationState.Failed(
                    PlaceLocationFailure.MOCK_LOCATION,
                    lastKnown,
                )
                !PlaceSearchArea.contains(sample.point) -> {
                    mutableLocation.value = PlaceLocationState.Unsupported(sample.point)
                    beginSearch(sample.point, kind, preferParking)
                }
                else -> {
                    mutableLocation.value = PlaceLocationState.Ready(sample.point)
                    beginSearch(sample.point, kind, preferParking)
                }
            }
        }
    }

    fun searchAtCurrentOrigin(kind: PlaceKind, preferParking: Boolean) {
        val discoveryState = discovery.state.value
        val pinned = discoveryState.origin?.takeIf {
            discoveryState.originMode == PlaceOriginMode.PINNED
        }
        when {
            pinned != null -> beginSearch(pinned, kind, preferParking, PlaceOriginMode.PINNED)
            mutableLocation.value.devicePosition != null -> beginSearch(
                mutableLocation.value.devicePosition!!,
                kind,
                preferParking,
            )
            else -> locateAndSearch(kind, preferParking)
        }
    }

    fun searchAt(point: GeoPoint, kind: PlaceKind, preferParking: Boolean) {
        beginSearch(point, kind, preferParking, PlaceOriginMode.PINNED)
    }

    fun retrySearch() {
        discovery.retry()
    }

    fun selectPlace(key: PlaceKey) {
        discovery.select(key)
    }

    fun loadJourney(place: PlaceResult) {
        val origin = mutableLocation.value.devicePosition
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

    private fun acceptLocationUpdate(sample: LocationSample) {
        if (sample.isMock) {
            mutableLocation.value = PlaceLocationState.Failed(
                PlaceLocationFailure.MOCK_LOCATION,
                mutableLocation.value.devicePosition,
            )
            return
        }
        if (PlaceSearchArea.contains(sample.point)) {
            mutableLocation.value = PlaceLocationState.Ready(sample.point)
            return
        }

        mutableLocation.value = PlaceLocationState.Unsupported(sample.point)
        val currentSearch = discovery.state.value
        if (currentSearch.originMode == PlaceOriginMode.DEVICE) {
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
