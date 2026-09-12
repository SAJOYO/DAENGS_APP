package com.daengs.app.map.features.territory

import com.daengs.app.location.GeoPoint
import com.daengs.app.territory.NearbyTerritorySitesRequest
import com.daengs.app.territory.TerritoryFailure
import com.daengs.app.territory.TerritoryProximityRange
import com.daengs.app.territory.TerritorySite
import com.daengs.app.territory.TerritorySiteRepository
import com.daengs.app.territory.toTerritoryFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TerritoryNearbyState(
    val sites: List<TerritorySite> = emptyList(),
    val loadedOrigin: GeoPoint? = null,
    val loading: Boolean = false,
    val truncated: Boolean = false,
    val failure: TerritoryFailure? = null,
)

/** Device-centered feed. Camera and manual selection never enter this controller. */
internal class TerritoryNearbyController(
    private val repository: TerritorySiteRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(TerritoryNearbyState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    private var requestedOrigin: GeoPoint? = null
    private var requestedAt: Long? = null

    /** Null means hidden, unauthorized or stale: discard both candidates and pending responses. */
    fun update(origin: GeoPoint?, nowNanos: Long) {
        if (origin == null) {
            clear()
            return
        }
        val elapsed = requestedAt?.let { nowNanos - it }
        val moved = requestedOrigin?.distanceMetersTo(origin)?.let { it >= 100.0 } ?: true
        val retryDue = elapsed != null && elapsed >= if (mutableState.value.failure != null) 30_000_000_000L else 60_000_000_000L
        if (!moved && (mutableState.value.loading || !retryDue)) return
        val requestGeneration = ++generation
        job?.cancel()
        requestedOrigin = origin
        requestedAt = nowNanos
        mutableState.value = TerritoryNearbyState(loading = true)
        job = scope.launch {
            try {
                val page = repository.nearby(NearbyTerritorySitesRequest(origin, radiusMeters = TERRITORY_NEARBY_RADIUS_METERS))
                if (generation != requestGeneration) return@launch
                mutableState.value = TerritoryNearbyState(page.sites.distinctBy { it.id }, origin,
                    truncated = page.truncated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == requestGeneration) mutableState.value = TerritoryNearbyState(failure = error.toTerritoryFailure())
            }
        }
    }

    fun clear() {
        generation++
        job?.cancel()
        job = null
        requestedOrigin = null
        requestedAt = null
        mutableState.value = TerritoryNearbyState()
    }
}

/** One occupancy read for the union; the viewport and its selected ID remain untouched. */
internal fun TerritoryBoardState.withNearby(nearby: TerritoryNearbyState) =
    copy(sites = (sites + nearby.sites).distinctBy { it.id })

/** Prefer a reliable in-range candidate, then distance and stable ID. Never grants an action. */
internal fun nearbyTerritoryTarget(sites: List<TerritoryGameSite>, nearby: TerritoryNearbyState): String? {
    val ids = nearby.sites.mapTo(hashSetOf()) { it.id }
    return sites.asSequence().filter {
        it.site.id in ids && it.proximity.range != TerritoryProximityRange.UNAVAILABLE &&
            it.proximity.distanceMeters?.let { distance -> distance <= TERRITORY_NEARBY_RADIUS_METERS } == true
    }.sortedWith(compareBy<TerritoryGameSite> { it.proximity.range != TerritoryProximityRange.IN_RANGE }
        .thenBy { it.proximity.distanceMeters }.thenBy { it.site.id }).firstOrNull()?.site?.id
}

internal const val TERRITORY_NEARBY_RADIUS_METERS = 300
