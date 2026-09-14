package com.daengs.app.map.features.territory

import com.daengs.app.territory.TerritorySite
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.WalkTrackingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Screen boundary: local actions and asynchronous shared reads use the same map presentation. */
interface TerritoryGameProvider {
    val onlinePhotos: Boolean get() = false
    suspend fun prepareCapture(siteId: String, board: TerritoryBoardState, tracking: WalkTrackingState,
        permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): TerritoryCaptureTarget? =
        captureTarget(siteId, board, tracking, permitted, petNames, nowNanos)
    suspend fun beginCapture(target: TerritoryCaptureTarget, board: TerritoryBoardState, tracking: WalkTrackingState,
        permitted: Boolean, petNames: Map<String, String>, nowNanos: Long, atMillis: Long): String? =
        captureAttempt(target, board, tracking, permitted, petNames, nowNanos, atMillis)
    fun saveCapture(captureId: String, file: java.io.File): kotlinx.coroutines.Deferred<Boolean>? = null
    fun cancelCapture(captureId: String) {}
    val refreshesFromServer: Boolean get() = false
    val changes: Flow<Unit> get() = emptyFlow()
    suspend fun refresh(sites: List<TerritorySite>) {}
    fun invalidate() {}
    fun snapshot(board: TerritoryBoardState, tracking: WalkTrackingState, permitted: Boolean,
                 petNames: Map<String, String>, nowNanos: Long, screenSample: LocationSample? = null): TerritoryGameState
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
fun territoryGameMode(debug: Boolean, serverRead: Boolean = true, serverActions: Boolean = false): TerritoryGameMode = when {
    serverActions -> TerritoryGameMode.SERVER_ACTIONS
    // Release may send server actions, but never falls back to local practice.
    !debug -> TerritoryGameMode.SERVER_READ
    serverRead -> TerritoryGameMode.SERVER_READ
    else -> TerritoryGameMode.LOCAL
}
