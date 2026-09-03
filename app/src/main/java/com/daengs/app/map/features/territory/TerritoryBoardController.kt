package com.daengs.app.map.features.territory

import com.daengs.app.location.GeoPoint
import com.daengs.app.territory.NearbyTerritorySitesRequest
import com.daengs.app.territory.TerritoryFailure
import com.daengs.app.territory.TerritorySite
import com.daengs.app.territory.TerritorySiteRepository
import com.daengs.app.territory.toTerritoryFailure
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerritoryBoardState(
    val sites: List<TerritorySite> = emptyList(),
    val loadedOrigin: GeoPoint? = null,
    val requestedOrigin: GeoPoint? = null,
    val selectedSiteId: String? = null,
    val truncated: Boolean = false,
    val loading: Boolean = false,
    val failure: TerritoryFailure? = null,
)

/** 점령 지도 한 장의 짧은 생애만 맡는다. 서버 결과를 Room에 영구 저장하지 않는다. */
class TerritoryBoardController(
    private val repository: TerritorySiteRepository,
    private val scope: CoroutineScope,
    private val cameraDebounceMillis: Long = TERRITORY_CAMERA_DEBOUNCE_MILLIS,
) {
    private val mutableState = MutableStateFlow(TerritoryBoardState())
    val state: StateFlow<TerritoryBoardState> = mutableState.asStateFlow()

    private var active = false
    private var requestGeneration = 0L
    private var requestJob: Job? = null
    private var cameraJob: Job? = null

    fun activate(origin: GeoPoint?) {
        active = true
        origin?.let(::refreshIfNeeded)
    }

    fun deactivate() {
        active = false
        requestGeneration++
        requestJob?.cancel()
        cameraJob?.cancel()
        requestJob = null
        cameraJob = null
        mutableState.update { it.copy(loading = false) }
    }

    fun onDevicePosition(point: GeoPoint) {
        if (active) refreshIfNeeded(point)
    }

    fun onCameraSettled(point: GeoPoint) {
        if (!active || !needsRefresh(point)) return
        cameraJob?.cancel()
        cameraJob = scope.launch {
            delay(cameraDebounceMillis)
            if (active) refreshIfNeeded(point)
        }
    }

    fun retry() {
        if (!active) return
        val origin = mutableState.value.requestedOrigin ?: mutableState.value.loadedOrigin ?: return
        submit(origin)
    }

    fun select(siteId: String) {
        if (mutableState.value.sites.any { it.id == siteId }) {
            mutableState.update { it.copy(selectedSiteId = siteId) }
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selectedSiteId = null) }
    }

    private fun refreshIfNeeded(origin: GeoPoint) {
        if (!needsRefresh(origin)) return
        submit(origin)
    }

    private fun needsRefresh(origin: GeoPoint): Boolean {
        val current = mutableState.value
        val reference = current.requestedOrigin.takeIf { current.loading } ?: current.loadedOrigin
        return reference == null || reference.distanceMetersTo(origin) >= TERRITORY_REFRESH_DISTANCE_METERS
    }

    private fun submit(origin: GeoPoint) {
        val request = runCatching { NearbyTerritorySitesRequest(origin) }
            .getOrElse { error ->
                mutableState.update {
                    it.copy(
                        requestedOrigin = origin,
                        loading = false,
                        failure = error.toTerritoryFailure(),
                    )
                }
                return
            }
        val generation = ++requestGeneration
        requestJob?.cancel()
        mutableState.update {
            it.copy(requestedOrigin = origin, loading = true, failure = null)
        }
        requestJob = scope.launch {
            runCatching { repository.nearby(request) }
                .onSuccess { page ->
                    if (generation != requestGeneration) return@onSuccess
                    mutableState.update { current ->
                        current.copy(
                            sites = page.sites,
                            loadedOrigin = origin,
                            selectedSiteId = current.selectedSiteId
                                ?.takeIf { selected -> page.sites.any { it.id == selected } },
                            truncated = page.truncated,
                            loading = false,
                            failure = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == requestGeneration) {
                        mutableState.update {
                            it.copy(loading = false, failure = error.toTerritoryFailure())
                        }
                    }
                }
        }
    }
}

internal fun GeoPoint.distanceMetersTo(other: GeoPoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val latDelta = lat2 - lat1
    val lngDelta = Math.toRadians(other.longitude - longitude)
    val haversine = sin(latDelta / 2) * sin(latDelta / 2) +
        cos(lat1) * cos(lat2) * sin(lngDelta / 2) * sin(lngDelta / 2)
    return 2 * EARTH_RADIUS_METERS * atan2(sqrt(haversine), sqrt(1 - haversine))
}

const val TERRITORY_REFRESH_DISTANCE_METERS = 1_000.0
const val TERRITORY_CAMERA_DEBOUNCE_MILLIS = 350L
private const val EARTH_RADIUS_METERS = 6_371_000.0
