package com.daengs.app.ui.places

import com.daengs.app.journey.JourneyRepository
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.journey.PlaceJourneyController
import com.daengs.app.map.features.journey.PlaceJourneyState
import com.daengs.app.map.features.places.DEFAULT_PLACE_KIND
import com.daengs.app.map.features.places.PlaceDiscoveryController
import com.daengs.app.map.features.places.PlaceDiscoveryState
import com.daengs.app.map.features.places.PlaceOriginMode
import com.daengs.app.map.features.places.PlaceSearchState
import com.daengs.app.place.DogSearchContext
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceResult
import com.daengs.app.place.PlaceSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal sealed interface PlaceSearchOrigin {
    data object CurrentDevice : PlaceSearchOrigin

    data class DeviceSnapshot(val point: GeoPoint) : PlaceSearchOrigin

    data class PinnedMap(val point: GeoPoint) : PlaceSearchOrigin
}

internal data class PlaceSearchIntent(
    val origin: PlaceSearchOrigin,
    val kinds: List<PlaceKind>,
    val preferParking: Boolean,
)

/** 위치 확인이 끝나기 전에도 어느 사용자 검색 명령이 최신인지 식별한다. */
internal data class PendingDevicePlaceSearch(
    val generation: Long,
    val intent: PlaceSearchIntent,
)

internal data class PlaceSessionState(
    val latestIntent: PlaceSearchIntent? = null,
    val discovery: PlaceDiscoveryState = PlaceDiscoveryState(),
    val journey: PlaceJourneyState = PlaceJourneyState(),
)

/**
 * 시설 탐색 한 세션의 검색 의도·결과·선택·Journey 생애를 맡는다.
 *
 * 위치 권한과 측위 자체는 알지 못한다. 장치 좌표가 필요한 검색은 토큰을 반환하고, 호출자가
 * 나중에 좌표를 공급한다. 그 사이 더 최신 검색이 시작됐으면 늦은 좌표는 이 경계에서 버린다.
 */
internal class PlaceSessionCoordinator(
    placeRepository: PlaceSearchRepository,
    journeyRepository: JourneyRepository,
    scope: CoroutineScope,
) {
    private val discovery = PlaceDiscoveryController(
        repository = placeRepository,
        dogContext = null,
        scope = scope,
    )
    private val journey = PlaceJourneyController(
        repository = journeyRepository,
        scope = scope,
    )
    private val latestIntent = MutableStateFlow<PlaceSearchIntent?>(null)
    private var intentGeneration = 0L

    val state: StateFlow<PlaceSessionState> = combine(
        latestIntent,
        discovery.state,
        journey.state,
        ::PlaceSessionState,
    ).stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = PlaceSessionState(),
    )

    fun updateDogContext(context: DogSearchContext?) {
        discovery.updateDogContext(context)
    }

    fun startDefaultSearchIfNeeded(devicePosition: GeoPoint?): PendingDevicePlaceSearch? {
        if (discovery.state.value.search !is PlaceSearchState.Idle) return null
        return if (devicePosition == null) {
            requestDeviceSearch(DEFAULT_PLACE_KIND, preferParking = false)
        } else {
            startResolvedSearch(
                PlaceSearchIntent(
                    origin = PlaceSearchOrigin.DeviceSnapshot(devicePosition),
                    kinds = listOf(DEFAULT_PLACE_KIND),
                    preferParking = false,
                ),
            )
            null
        }
    }

    fun requestDeviceSearch(
        kind: PlaceKind,
        preferParking: Boolean,
    ): PendingDevicePlaceSearch {
        val intent = PlaceSearchIntent(
            origin = PlaceSearchOrigin.CurrentDevice,
            kinds = listOf(kind),
            preferParking = preferParking,
        )
        latestIntent.value = intent
        return PendingDevicePlaceSearch(
            generation = ++intentGeneration,
            intent = intent,
        )
    }

    fun resolveDeviceSearch(request: PendingDevicePlaceSearch, point: GeoPoint) {
        if (request.generation != intentGeneration) return
        val resolved = request.intent.copy(origin = PlaceSearchOrigin.DeviceSnapshot(point))
        latestIntent.value = resolved
        submitResolvedSearch(resolved)
    }

    fun searchAtCurrentOrigin(
        kind: PlaceKind,
        preferParking: Boolean,
        devicePosition: GeoPoint?,
    ): PendingDevicePlaceSearch? {
        val current = discovery.state.value
        val pinned = current.origin?.takeIf { current.originMode == PlaceOriginMode.PINNED }
        return when {
            pinned != null -> {
                searchAt(pinned, kind, preferParking)
                null
            }
            devicePosition != null -> {
                startResolvedSearch(
                    PlaceSearchIntent(
                        origin = PlaceSearchOrigin.DeviceSnapshot(devicePosition),
                        kinds = listOf(kind),
                        preferParking = preferParking,
                    ),
                )
                null
            }
            else -> requestDeviceSearch(kind, preferParking)
        }
    }

    fun searchAt(point: GeoPoint, kind: PlaceKind, preferParking: Boolean) {
        startResolvedSearch(
            PlaceSearchIntent(
                origin = PlaceSearchOrigin.PinnedMap(point),
                kinds = listOf(kind),
                preferParking = preferParking,
            ),
        )
    }

    fun retrySearch() {
        invalidatePendingDeviceSearch()
        journey.clear()
        discovery.retry()
    }

    fun replaceUnsupportedDeviceOrigin(point: GeoPoint) {
        val current = discovery.state.value
        val pending = latestIntent.value?.takeIf { it.origin == PlaceSearchOrigin.CurrentDevice }
        if (pending == null && current.originMode != PlaceOriginMode.DEVICE) return
        startResolvedSearch(
            pending?.copy(origin = PlaceSearchOrigin.DeviceSnapshot(point)) ?: PlaceSearchIntent(
                origin = PlaceSearchOrigin.DeviceSnapshot(point),
                kinds = current.requestedKinds.ifEmpty { listOf(DEFAULT_PLACE_KIND) },
                preferParking = current.preferParking,
            ),
        )
    }

    fun selectPlace(key: PlaceKey) {
        if (journey.state.value.destinationKey?.let { it != key } == true) journey.clear()
        discovery.select(key)
    }

    fun loadJourney(origin: GeoPoint?, place: PlaceResult) {
        selectPlace(place.key)
        if (origin == null) {
            journey.reject(place.key, "현재 위치를 확인한 뒤 길찾기를 다시 눌러주세요.")
        } else {
            journey.load(origin, place)
        }
    }

    fun retryJourney() {
        journey.retry()
    }

    fun deactivate() {
        invalidatePendingDeviceSearch()
        discovery.cancel()
        journey.clear()
    }

    fun clear() {
        invalidatePendingDeviceSearch()
        latestIntent.value = null
        discovery.clear()
        journey.clear()
    }

    private fun startResolvedSearch(intent: PlaceSearchIntent) {
        intentGeneration++
        latestIntent.value = intent
        submitResolvedSearch(intent)
    }

    private fun submitResolvedSearch(intent: PlaceSearchIntent) {
        val (point, mode) = when (val origin = intent.origin) {
            is PlaceSearchOrigin.DeviceSnapshot -> origin.point to PlaceOriginMode.DEVICE
            is PlaceSearchOrigin.PinnedMap -> origin.point to PlaceOriginMode.PINNED
            PlaceSearchOrigin.CurrentDevice -> error("장치 위치를 확인한 뒤 검색해야 합니다.")
        }
        journey.clear()
        discovery.search(point, intent.kinds, intent.preferParking, mode)
    }

    private fun invalidatePendingDeviceSearch() {
        intentGeneration++
        if (latestIntent.value?.origin == PlaceSearchOrigin.CurrentDevice) {
            latestIntent.value = currentResolvedIntent()
        }
    }

    private fun currentResolvedIntent(): PlaceSearchIntent? {
        val current = discovery.state.value
        val point = current.origin ?: return null
        if (current.requestedKinds.isEmpty()) return null
        val origin = when (current.originMode) {
            PlaceOriginMode.DEVICE -> PlaceSearchOrigin.DeviceSnapshot(point)
            PlaceOriginMode.PINNED -> PlaceSearchOrigin.PinnedMap(point)
        }
        return PlaceSearchIntent(origin, current.requestedKinds, current.preferParking)
    }
}
