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
import com.daengs.app.place.PlaceCategorySelection
import com.daengs.app.place.FacilityRepository
import com.daengs.app.place.FacilityQuery
import com.daengs.app.place.FacilityChoice
import com.daengs.app.place.FacilityResponse
import com.daengs.app.place.FacilityAction
import com.daengs.app.place.FacilityException
import com.daengs.app.place.FacilityApi
import com.daengs.app.place.AuthenticatedFacilityRepository
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceRepository
import com.daengs.app.place.PlaceResult
import com.daengs.app.place.PlaceSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlacesUiState(
    val location: PlaceLocationState = PlaceLocationState.PermissionRequired,
    val discovery: PlaceDiscoveryState = PlaceDiscoveryState(),
    val journey: PlaceJourneyState = PlaceJourneyState(),
    val profiles: PlaceProfiles = PlaceProfiles(),
    val waitingForSearchLocation: Boolean = false,
    val facility: FacilityUiState = FacilityUiState(),
)

/** 선택 범위를 HTTP의 업종 목록으로 전달한다. 기존 단일 업종 화면은 보조 생성자를 쓴다. */
sealed interface PlacesAction {
    data class SetAiMode(val enabled: Boolean) : PlacesAction
    data class Discover(val query: String) : PlacesAction
    data class ChooseAi(val choice: FacilityChoice) : PlacesAction
    data object RetryAi : PlacesAction
    data class ToggleDog(val id: String) : PlacesAction
    data class Locate(val category: PlaceCategorySelection, val preferParking: Boolean, val nameQuery: String? = null) : PlacesAction {
        constructor(kind: PlaceKind?, preferParking: Boolean, nameQuery: String? = null) :
            this(PlaceCategorySelection.fromKind(kind), preferParking, nameQuery)
    }

    data class Search(val category: PlaceCategorySelection, val preferParking: Boolean, val nameQuery: String? = null) : PlacesAction {
        constructor(kind: PlaceKind?, preferParking: Boolean, nameQuery: String? = null) :
            this(PlaceCategorySelection.fromKind(kind), preferParking, nameQuery)
    }

    data class SearchAt(
        val point: GeoPoint,
        val category: PlaceCategorySelection,
        val preferParking: Boolean,
        val nameQuery: String? = null,
    ) : PlacesAction {
        constructor(point: GeoPoint, kind: PlaceKind?, preferParking: Boolean, nameQuery: String? = null) :
            this(point, PlaceCategorySelection.fromKind(kind), preferParking, nameQuery)
    }

    data class SetRadius(val meters: Int) : PlacesAction

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
    facilityRepository: FacilityRepository? = null,
) : ViewModel() {
    private val runtimeScope = externalScope ?: viewModelScope
    private val facility = FacilitySearchCoordinator(facilityRepository ?: object : FacilityRepository {
        override suspend fun discover(owner: String, query: FacilityQuery): FacilityResponse = throw FacilityException(0)
        override suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction): FacilityResponse = throw FacilityException(0)
    }, runtimeScope)
    private val profiles = MutableStateFlow(PlaceProfiles())
    private var appliedDogs = emptyList<com.daengs.app.place.PlaceDogSnapshot>()
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
        profiles,
        facility.state,
    ) { location, session, profiles, facility ->
        val pending = session.latestIntent?.takeIf { it.origin == PlaceSearchOrigin.CurrentDevice }
        val discovery = pending?.let {
            session.discovery.copy(requestedKinds = it.kinds, nameQuery = it.nameQuery,
                preferParking = it.preferParking, radiusMeters = it.radiusMeters,
                search = com.daengs.app.map.features.places.PlaceSearchState.Loading,
                selectedPlaceKey = null)
        } ?: session.discovery
        PlacesUiState(location, discovery, session.journey, profiles, waitingForSearchLocation = pending != null, facility = facility)
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
        facility.enable(false)
        location.deactivate()
        session.deactivate()
    }

    fun updatePermission(granted: Boolean, permanentlyDenied: Boolean) {
        if (!granted) {
            facility.invalidate()
            location.cancelLocate()
            session.clear()
        }
        location.updatePermission(granted, permanentlyDenied)
        if (granted && location.isActive) startDefaultSearchIfNeeded()
    }

    fun updateDogContext(context: DogSearchContext?) {
        session.updateDogContext(context)
    }

    fun updateProfiles(owner: String?, pets: List<com.daengs.app.pet.Pet>?, busy: Boolean, error: String?) {
        applyProfiles(profiles.value.receive(owner, pets, busy, error))
    }

    private fun applyProfiles(value: PlaceProfiles) {
        if (value.ownerId != profiles.value.ownerId || value.snapshots() != profiles.value.snapshots()) {
            facility.invalidate(if (facility.state.value.enabled) "반려견 정보가 바뀌었어요. 조건을 확인하고 다시 검색해 주세요." else null)
        }
        profiles.value = value
        val snapshots = value.snapshots()
        if (snapshots != appliedDogs) {
            appliedDogs = snapshots
            session.updateDogs(snapshots)
        }
    }

    fun onAction(action: PlacesAction) {
        if (action is PlacesAction.Search || action is PlacesAction.SearchAt || action is PlacesAction.Locate || action is PlacesAction.SetRadius) {
            facility.invalidate(if (facility.state.value.enabled) "검색 위치나 조건이 바뀌었어요. 문장으로 다시 검색해 주세요." else null)
        }
        when (action) {
            is PlacesAction.SetAiMode -> facility.enable(action.enabled)
            is PlacesAction.Discover -> discover(action.query)
            is PlacesAction.ChooseAi -> facility.choose(action.choice)
            PlacesAction.RetryAi -> facility.retry()
            is PlacesAction.ToggleDog -> applyProfiles(profiles.value.toggle(action.id))
            is PlacesAction.Locate -> locateAndSearch(action.category.kinds, action.preferParking, action.nameQuery)
            is PlacesAction.Search -> searchAtCurrentOrigin(action.category.kinds, action.preferParking, action.nameQuery)
            is PlacesAction.SearchAt -> searchAt(
                action.point,
                action.category.kinds,
                action.preferParking,
                action.nameQuery,
            )
            is PlacesAction.SetRadius -> session.radius(action.meters)?.let(::locate)
            PlacesAction.RetrySearch -> retrySearch()
            is PlacesAction.Select -> selectPlace(action.key)
            is PlacesAction.LoadJourney -> loadJourney(action.place)
            PlacesAction.RetryJourney -> retryJourney()
        }
    }

    fun locateAndSearch(kind: PlaceKind?, preferParking: Boolean, nameQuery: String? = null) =
        locateAndSearch(PlaceCategorySelection.fromKind(kind).kinds, preferParking, nameQuery)

    private fun locateAndSearch(kinds: List<PlaceKind>, preferParking: Boolean, nameQuery: String? = null) {
        if (location.state.value is PlaceLocationState.PermissionRequired ||
            location.state.value is PlaceLocationState.PermissionPermanentlyDenied
        ) {
            return
        }
        locate(session.requestDeviceSearch(kinds, preferParking, nameQuery))
    }

    fun searchAtCurrentOrigin(kind: PlaceKind?, preferParking: Boolean, nameQuery: String? = null) =
        searchAtCurrentOrigin(PlaceCategorySelection.fromKind(kind).kinds, preferParking, nameQuery)

    private fun searchAtCurrentOrigin(kinds: List<PlaceKind>, preferParking: Boolean, nameQuery: String? = null) {
        session.searchAtCurrentOrigin(
            kinds = kinds,
            preferParking = preferParking,
            devicePosition = location.state.value.devicePosition,
            nameQuery = nameQuery,
        )?.let(::locate)
    }

    fun searchAt(point: GeoPoint, kind: PlaceKind?, preferParking: Boolean, nameQuery: String? = null) =
        searchAt(point, PlaceCategorySelection.fromKind(kind).kinds, preferParking, nameQuery)

    private fun searchAt(point: GeoPoint, kinds: List<PlaceKind>, preferParking: Boolean, nameQuery: String? = null) {
        session.searchAt(point, kinds, preferParking, nameQuery)
    }

    private fun discover(text: String) {
        val query = text.trim()
        if (profiles.value.ownerId == null) {
            facility.reject("AI 조건 검색은 로그인 후 사용할 수 있어요."); return
        }
        if (query.isEmpty() || query.codePointCount(0, query.length) > 1000) {
            facility.reject("원하는 장소와 조건을 1~1,000자로 입력해 주세요."); return
        }
        if (profiles.value.selectedIds.isNotEmpty() && !profiles.value.ready) {
            facility.reject("선택한 반려견 정보를 새로고침한 뒤 검색해 주세요."); return
        }
        val current = state.value
        val origin = current.discovery.origin
        if (origin == null || current.waitingForSearchLocation || !PlaceSearchArea.contains(origin)) {
            facility.reject("위치를 확인하거나 지도를 움직여 이 지역 검색을 누른 뒤 다시 시도해 주세요."); return
        }
        val kinds = current.discovery.requestedKinds
        facility.search(profiles.value.ownerId, FacilityQuery(query, origin, current.discovery.radiusMeters,
            if (kinds.toSet() == PlaceKind.entries.toSet()) emptyList() else kinds,
            current.discovery.preferParking, profiles.value.snapshots()))
    }

    fun retrySearch() {
        session.retrySearch()?.let(::locate)
    }

    fun selectPlace(key: PlaceKey) {
        facility.select(key)
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
                        facilityRepository = (context.applicationContext as com.daengs.app.DaengsApp).let { app ->
                            AuthenticatedFacilityRepository(FacilityApi { BuildConfig.API_BASE_URL },
                                app.sessionProvider::freshSession, app.tokenStore::load)
                        },
                    ) as T
                }
            }
    }
}
