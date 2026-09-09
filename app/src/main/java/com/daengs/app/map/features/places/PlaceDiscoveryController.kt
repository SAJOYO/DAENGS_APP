package com.daengs.app.map.features.places

import com.daengs.app.location.GeoPoint
import com.daengs.app.place.DogSearchContext
import com.daengs.app.place.PlaceFailure
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceSearchRequest
import com.daengs.app.place.PlaceSearchResponse
import com.daengs.app.place.PlaceSearchRepository
import com.daengs.app.place.supportsParkingPreference
import com.daengs.app.place.toPlaceFailure
import com.daengs.app.place.userMessage
import com.daengs.app.place.overviewHits
import com.daengs.app.place.PlaceFilterCriteria
import com.daengs.app.place.PlaceFilterRequest
import com.daengs.app.place.PlaceFilterResponse
import com.daengs.app.place.PlaceFilterCapabilities
import com.daengs.app.place.PlaceApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 결과가 어느 지점 기준인지. 화면 문구와 "종류만 바꾸기"가 같은 사실을 봐야 한다. */
enum class PlaceOriginMode { DEVICE, PINNED }

sealed interface PlaceSearchState {
    data object Idle : PlaceSearchState

    data object Loading : PlaceSearchState

    data class Content(val response: PlaceSearchResponse) : PlaceSearchState

    data class Empty(val response: PlaceSearchResponse) : PlaceSearchState

    data class Failed(val failure: PlaceFailure) : PlaceSearchState
}

data class PlaceDiscoveryState(
    val requestedKinds: List<PlaceKind> = emptyList(),
    val origin: GeoPoint? = null,
    val originMode: PlaceOriginMode = PlaceOriginMode.DEVICE,
    val preferParking: Boolean = false,
    val selectedPlaceKey: PlaceKey? = null,
    val search: PlaceSearchState = PlaceSearchState.Idle,
    val nameQuery: String = "",
    val radiusMeters: Int = 3_000,
    val filters: PlaceFilterCriteria? = null,
    val filterResponse: PlaceFilterResponse? = null,
    val filterEditLoading: Boolean = false,
    val filterError: String? = null,
    val filterCapabilities: PlaceFilterCapabilities? = null,
    val filterCapabilitiesLoading: Boolean = false,
) {
    val response: PlaceSearchResponse?
        get() = when (val current = search) {
            is PlaceSearchState.Content -> current.response
            is PlaceSearchState.Empty -> current.response
            else -> null
        }

    val loading: Boolean get() = search is PlaceSearchState.Loading

    val error: String?
        get() = (search as? PlaceSearchState.Failed)?.failure?.userMessage()
}

/**
 * 장소 검색 요청의 생애를 맡는다.
 *
 * 요청 만들기 · 재시도 기억 · 늦게 온 응답 버리기를 여기 모아 둬서, `MapViewModel` 이
 * 남의 기능 사정까지 떠안지 않게 한다.
 */
class PlaceDiscoveryController(
    private val repository: PlaceSearchRepository,
    // identity 가 아니라 값이다 — 서버는 dog_id 를 받지 않는다 (결정 #73).
    private var dogContext: DogSearchContext?,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(PlaceDiscoveryState())
    val state: StateFlow<PlaceDiscoveryState> = mutableState.asStateFlow()

    private var lastRequest: List<PlaceSearchRequest>? = null
    private var lastOriginMode = PlaceOriginMode.DEVICE
    private var requestGeneration = 0L
    private var searchJob: Job? = null
    private var dogs: List<com.daengs.app.place.PlaceDogSnapshot> = emptyList()

    /** Capture the exact manual search generation; no selection, location or dog defaults invented. */
    fun captureFilterBase(): PlaceFilterRequest? {
        val current = mutableState.value
        if (current.loading || current.filterEditLoading || current.filterCapabilities == null || current.origin == null || !PlaceSearchArea.contains(current.origin)) return null
        val requests = lastRequest ?: return null
        val kinds = requests.flatMap { it.kinds }
        if (kinds.size !in 1..6) return null
        val request = requests.first().copy(kinds = kinds, dogs = dogs, dogSize = null, dogWeightKg = null, dogAgeYears = null)
        val criteria = current.filters ?: PlaceFilterCriteria(kinds, preferences = if (request.preferParking) listOf(
            com.daengs.app.place.PlaceFilterPreference(com.daengs.app.place.PlaceFilterAtom("manual-parking-preference", "operations.parking", "eq", kotlinx.serialization.json.JsonPrimitive(true)), kinds.filter { it.supportsParkingPreference() })
        ) else emptyList())
        return PlaceFilterRequest(request, criteria, requestGeneration)
    }

    fun matchesFilterBase(base: PlaceFilterRequest): Boolean = captureFilterBase()?.let {
        it.revision == base.revision && com.daengs.app.place.sameFilterJson(it.state, base.state)
    } == true

    /** Adopt the already executed shared-engine result, never issue a second legacy search. */
    fun acceptFilterEdit(base: PlaceFilterRequest, result: PlaceFilterResponse): Boolean {
        if (!matchesFilterBase(base) || result.request.revision != base.revision + 1) return false
        requestGeneration++
        searchJob?.cancel()
        val next = result.request.request
        lastRequest = listOf(next)
        val response = result.results()
        mutableState.update { it.copy(filters = result.request.criteria, requestedKinds = next.kinds,
            nameQuery = next.nameQuery, preferParking = next.preferParking, filterResponse = result,
            filterError = null, selectedPlaceKey = response.firstPlaceKey(), search = response.searchState()) }
        return true
    }

    fun loadFilterCapabilities() {
        if (mutableState.value.filterCapabilities != null || mutableState.value.filterCapabilitiesLoading) return
        mutableState.update { it.copy(filterCapabilitiesLoading = true, filterError = null) }
        scope.launch {
            runCatching { repository.filterCapabilities() }
                .onSuccess { value -> mutableState.update { it.copy(filterCapabilities = value, filterCapabilitiesLoading = false) } }
                .onFailure { error -> mutableState.update { it.copy(filterCapabilitiesLoading = false, filterError = filterMessage(error)) } }
        }
    }

    /** A rejected edit leaves both the applied tree and its results untouched. */
    fun applyFilters(criteria: PlaceFilterCriteria?) {
        val previous = mutableState.value
        val request = lastRequest?.firstOrNull()
        if (criteria == null) {
            cancel()
            mutableState.update { it.copy(filters = null, filterResponse = null, filterError = null, preferParking = false) }
            lastRequest?.let { submit(it.map { request -> request.copy(preferParking = false) }, lastOriginMode) }
            return
        }
        if (request == null || previous.origin == null || !PlaceSearchArea.contains(previous.origin)) {
            mutableState.update { it.copy(filterError = "위치를 확인하고 검색한 뒤 조건을 적용해 주세요.") }; return
        }
        if (previous.filterCapabilities == null || criteria.kinds.any { it !in previous.filterCapabilities.kinds }) {
            mutableState.update { it.copy(filterError = "서버의 지원 조건을 먼저 확인해 주세요.") }; return
        }
        criteria.validationMessage()?.let { message -> mutableState.update { it.copy(filterError = message) }; return }
        val next = request.copy(kinds = criteria.kinds, preferParking = criteria.preferences.isNotEmpty(),
            dogs = dogs, dogSize = null, dogWeightKg = null, dogAgeYears = null)
        val generation = ++requestGeneration
        searchJob?.cancel()
        mutableState.update { it.copy(filterEditLoading = true, filterError = null) }
        searchJob = scope.launch {
            runCatching { repository.searchFiltered(PlaceFilterRequest(next, criteria, generation)) }
                .onSuccess { result ->
                    if (generation != requestGeneration) return@onSuccess
                    lastRequest = listOf(next)
                    val response = result.results()
                    mutableState.update { it.copy(requestedKinds = criteria.kinds, filters = criteria,
                        preferParking = next.preferParking, filterResponse = result, filterEditLoading = false,
                        selectedPlaceKey = response.firstPlaceKey(), search = response.searchState()) }
                }.onFailure { error ->
                    if (generation == requestGeneration) mutableState.update { it.copy(filterEditLoading = false, filterError = filterMessage(error)) }
                }
        }
    }

    fun cancelFilterEdit() {
        if (mutableState.value.filterEditLoading) cancel()
        mutableState.update { it.copy(filterError = null) }
    }

    fun updateDogs(value: List<com.daengs.app.place.PlaceDogSnapshot>) {
        dogs = value.toList()
        dogContext = null
        lastRequest = lastRequest?.map { it.copy(dogs = dogs, dogSize = null, dogWeightKg = null, dogAgeYears = null) }
        lastRequest?.let { submit(it, lastOriginMode) }
    }

    fun updateDogContext(value: DogSearchContext?) {
        dogContext = value
    }

    fun search(
        origin: GeoPoint,
        kinds: List<PlaceKind>,
        preferParking: Boolean = false,
        originMode: PlaceOriginMode = PlaceOriginMode.DEVICE,
        nameQuery: String = "",
        radiusMeters: Int = 3_000,
    ) {
        if (!PlaceSearchArea.contains(origin)) {
            cancel()
            lastRequest = null
            mutableState.value = mutableState.value.copy(
                requestedKinds = kinds,
                origin = origin,
                originMode = originMode,
                preferParking = preferParking,
                nameQuery = nameQuery.trim(),
                radiusMeters = radiusMeters,
                search = PlaceSearchState.Failed(PlaceFailure.UnsupportedLocation),
                filterResponse = null,
            )
            return
        }
        require(kinds.isNotEmpty() && kinds.distinct().size == kinds.size)
        submit(
            kinds.chunked(6).map { batch -> PlaceSearchRequest(
                origin = origin,
                kinds = batch,
                radiusMeters = radiusMeters,
                nameQuery = nameQuery.trim(),
                limitPerKind = PLACE_RESULT_LIMIT,
                dogs = dogs,
                dogSize = dogContext?.size,
                dogWeightKg = dogContext?.weightKg,
                dogAgeYears = dogContext?.ageYears,
                // 주차 사실 계약이 없는 kind만 요청했다면 선호를 들고 가지 않는다. 화면에서
                // 칩이 사라진 뒤에도 이전 선택이 몰래 따라붙던 자리다.
                preferParking = preferParking && kinds.any(PlaceKind::supportsParkingPreference),
            ) },
            originMode,
        )
    }

    fun retry() {
        val request = lastRequest
        if (request == null) {
            mutableState.update {
                it.copy(search = PlaceSearchState.Failed(PlaceFailure.NothingToRetry))
            }
            return
        }
        submit(request, lastOriginMode)
    }

    fun select(key: PlaceKey) {
        mutableState.update { it.copy(selectedPlaceKey = key) }
    }

    fun cancel() {
        requestGeneration++
        searchJob?.cancel()
        searchJob = null
        mutableState.update { it.copy(filterEditLoading = false) }
        if (mutableState.value.search is PlaceSearchState.Loading) {
            mutableState.update { it.copy(search = PlaceSearchState.Idle) }
        }
    }

    fun clear() {
        cancel()
        lastRequest = null
        mutableState.value = PlaceDiscoveryState()
    }

    private fun submit(requests: List<PlaceSearchRequest>, originMode: PlaceOriginMode) {
        val criteria = mutableState.value.filters
        val effectiveRequests = if (criteria == null) requests else listOf(requests.first().copy(
            kinds = criteria.kinds, preferParking = criteria.preferences.isNotEmpty(),
            dogSize = null, dogWeightKg = null, dogAgeYears = null,
        ))
        val request = effectiveRequests.first()
        lastRequest = effectiveRequests
        lastOriginMode = originMode
        val generation = ++requestGeneration
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(
            requestedKinds = effectiveRequests.flatMap { it.kinds },
            origin = request.origin,
            originMode = originMode,
            preferParking = request.preferParking,
            nameQuery = request.nameQuery,
            radiusMeters = request.radiusMeters,
            search = PlaceSearchState.Loading,
            selectedPlaceKey = null,
            filterResponse = null,
            filterEditLoading = false,
            filterError = null,
        )
        searchJob = scope.launch {
            runCatching {
                val filtered = criteria?.let { repository.searchFiltered(PlaceFilterRequest(request, it, generation)) }
                (filtered?.results() ?: com.daengs.app.place.searchPlaceBatches(repository, effectiveRequests)) to filtered
            }.onSuccess { (response, filtered) ->
                    if (generation != requestGeneration) return@onSuccess
                    val resultState = if (response.groups.all { it.results.isEmpty() }) {
                        PlaceSearchState.Empty(response)
                    } else {
                        PlaceSearchState.Content(response)
                    }
                    mutableState.update {
                        it.copy(
                            selectedPlaceKey = if (filtered == null && effectiveRequests.sumOf { it.kinds.size } > 1) response.overviewHits(request.preferParking).firstOrNull()?.place?.key else response.firstPlaceKey(),
                            search = resultState,
                            filterResponse = filtered,
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == requestGeneration) {
                        mutableState.update {
                            it.copy(
                                selectedPlaceKey = null,
                                search = PlaceSearchState.Failed(error.toPlaceFailure()),
                            )
                        }
                    }
                }
        }
    }
}

private fun PlaceSearchResponse.searchState(): PlaceSearchState =
    if (groups.all { it.results.isEmpty() }) PlaceSearchState.Empty(this) else PlaceSearchState.Content(this)

private fun filterMessage(error: Throwable): String = when {
    error is PlaceApiException && error.status == 422 -> "함께 만족할 수 없는 조건이나 지원하지 않는 조합이 있어요. 조건을 수정해 주세요. 이전 적용 조건은 유지됩니다."
    error is PlaceApiException && error.status == 404 -> "이 서버는 아직 조건 검색을 지원하지 않아요."
    else -> error.toPlaceFailure().userMessage()
}

private fun PlaceSearchResponse.firstPlaceKey(): PlaceKey? = groups.asSequence()
    .flatMap { it.results.asSequence() }
    .firstOrNull()
    ?.place
    ?.key

object PlaceSearchArea {
    fun contains(point: GeoPoint): Boolean =
        point.latitude in 32.0..40.0 && point.longitude in 123.0..133.0
}

const val PLACE_RESULT_LIMIT = 50
