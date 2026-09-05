package com.daengs.app.map.features.territory

import com.daengs.app.territory.TerritorySite
import com.daengs.app.walk.WalkTrackingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Screen boundary: local actions and asynchronous shared reads use the same map presentation. */
interface TerritoryGameProvider {
    val refreshesFromServer: Boolean get() = false
    val changes: Flow<Unit> get() = emptyFlow()
    suspend fun refresh(sites: List<TerritorySite>) {}
    fun invalidate() {}
    fun snapshot(board: TerritoryBoardState, tracking: WalkTrackingState, permitted: Boolean,
                 petNames: Map<String, String>, nowNanos: Long): TerritoryGameState
    fun selectPet(petId: String, siteId: String, tracking: WalkTrackingState) {}
    suspend fun submitMark(siteId: String, board: TerritoryBoardState, tracking: WalkTrackingState,
        permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): String =
        mark(siteId, board, tracking, permitted, petNames, nowNanos, atMillis)
    fun mark(siteId: String, board: TerritoryBoardState, tracking: WalkTrackingState,
             permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): String =
        "현재는 점유 정보를 둘러볼 수 있어요"
    fun captureTarget(siteId: String, board: TerritoryBoardState, tracking: WalkTrackingState,
                      permitted: Boolean, petNames: Map<String, String>, nowNanos: Long): TerritoryCaptureTarget? = null
    fun captureAttempt(target: TerritoryCaptureTarget, board: TerritoryBoardState, tracking: WalkTrackingState,
                       permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): String? = null
}

enum class TerritoryGameMode { DISABLED, LOCAL, SERVER_READ, SERVER_ACTIONS }
fun territoryGameMode(debug: Boolean, serverRead: Boolean, serverActions: Boolean = false): TerritoryGameMode = when {
    !debug -> TerritoryGameMode.DISABLED
    serverActions -> TerritoryGameMode.SERVER_ACTIONS
    serverRead -> TerritoryGameMode.SERVER_READ
    else -> TerritoryGameMode.LOCAL
}
