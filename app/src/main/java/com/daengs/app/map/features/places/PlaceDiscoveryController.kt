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
            mutableState.value = PlaceDiscoveryState(
                requestedKinds = kinds,
                origin = origin,
                originMode = originMode,
                preferParking = preferParking,
                nameQuery = nameQuery.trim(),
                radiusMeters = radiusMeters,
                search = PlaceSearchState.Failed(PlaceFailure.UnsupportedLocation),
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
        val request = requests.first()
        lastRequest = requests
        lastOriginMode = originMode
        val generation = ++requestGeneration
        searchJob?.cancel()
        mutableState.value = PlaceDiscoveryState(
            requestedKinds = requests.flatMap { it.kinds },
            origin = request.origin,
            originMode = originMode,
            preferParking = request.preferParking,
            nameQuery = request.nameQuery,
            radiusMeters = request.radiusMeters,
            search = PlaceSearchState.Loading,
        )
        searchJob = scope.launch {
            runCatching { com.daengs.app.place.searchPlaceBatches(repository, requests) }
                .onSuccess { response ->
                    if (generation != requestGeneration) return@onSuccess
                    val resultState = if (response.groups.all { it.results.isEmpty() }) {
                        PlaceSearchState.Empty(response)
                    } else {
                        PlaceSearchState.Content(response)
                    }
                    mutableState.update {
                        it.copy(
                            selectedPlaceKey = if (requests.size > 1) response.overviewHits(request.preferParking).firstOrNull()?.place?.key else response.firstPlaceKey(),
                            search = resultState,
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
