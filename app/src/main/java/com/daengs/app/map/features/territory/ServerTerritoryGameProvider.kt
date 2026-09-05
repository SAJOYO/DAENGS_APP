package com.daengs.app.map.features.territory

import com.daengs.app.auth.Session
import com.daengs.app.territory.*
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.WalkTrackingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private data class SharedBoard(
    val ownerId: String? = null,
    val sites: Map<String, SharedTerritorySite> = emptyMap(),
    val message: String = "점유 정보를 확인하고 있어요",
)

/** Read-only, process-local cache. Failures never become neutral sites or local claim grants. */
class ServerTerritoryGameProvider(
    private val api: TerritoryOccupancyClient,
    private val freshSession: suspend () -> Session?,
    private val currentOwner: () -> String?,
) : TerritoryGameProvider {
    override val refreshesFromServer = true
    private val board = MutableStateFlow(SharedBoard())
    private var generation = 0L
    override val changes = board.map { Unit }

    override fun invalidate() {
        generation++
        board.value = SharedBoard()
    }

    override suspend fun refresh(sites: List<TerritorySite>) {
        val request = ++generation
        val owner = currentOwner()
        val ids = sites.map { it.id }.toSet()
        val previous = board.value.takeIf { it.ownerId == owner }?.sites.orEmpty().filterKeys { it in ids }
        board.value = SharedBoard(ownerId = owner, sites = previous)
        if (sites.isEmpty()) return
        try {
            val session = freshSession()
            currentCoroutineContext().ensureActive()
            if (request != generation || currentOwner() != owner) return
            if (session == null || owner.isNullOrBlank() || session.appUserId != owner) {
                board.value = SharedBoard(message = "로그인하면 점유 정보를 볼 수 있어요")
                return
            }
            val loaded = sites.map { it.id }.distinct().chunked(100).flatMap { ids ->
                api.fetch(session.accessToken, ids)
            }
            currentCoroutineContext().ensureActive()
            if (request != generation || currentOwner() != owner) return
            board.value = SharedBoard(owner, loaded.associateBy { it.siteId }, "점유 정보 · 둘러보기")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (request != generation || currentOwner() != owner) return
            val message = if (error is TerritoryOccupancyApiException && error.status == 401)
                "로그인 상태를 다시 확인해 주세요"
            else "점유 정보를 불러오지 못했어요 · 잠시 후 다시 확인해요"
            board.value = SharedBoard(ownerId = owner, message = message)
        }
    }

    override fun snapshot(board: TerritoryBoardState, tracking: WalkTrackingState, permitted: Boolean,
                          petNames: Map<String, String>, nowNanos: Long): TerritoryGameState {
        val cached = this.board.value.takeIf { it.ownerId == currentOwner() }
        val sites = board.sites.map { site ->
            val shared = cached?.sites?.get(site.id)
            val occupied = shared?.occupancy
            val claim = TerritoryClaimSite(site.id, occupied?.let {
                TerritoryOccupancy(it.ownerPetId, null, null, it.certification, it.occupiedAtMillis)
            }, shared?.version ?: 0)
            TerritoryGameSite(site, claim, occupied?.ownerPetName.orEmpty(), null, null, false,
                occupancyKnown = shared != null)
        }
        return TerritoryGameState(enabled = true, readOnly = true,
            phase = when {
                tracking.activeSessionId == null || tracking.trail.state == TrackingState.OFF -> TerritoryWalkPhase.BROWSING
                tracking.trail.state == TrackingState.PAUSED -> TerritoryWalkPhase.PAUSED
                else -> TerritoryWalkPhase.WALKING
            },
            sites = sites, targetId = board.selectedSiteId,
            guidance = cached?.message ?: "로그인하면 점유 정보를 볼 수 있어요")
    }
}
