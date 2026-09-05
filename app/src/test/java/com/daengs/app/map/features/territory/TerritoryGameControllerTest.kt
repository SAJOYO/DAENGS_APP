package com.daengs.app.map.features.territory

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.territory.ClaimAccess
import com.daengs.app.territory.ClaimCertification
import com.daengs.app.territory.InMemoryTerritoryClaimRepository
import com.daengs.app.territory.TerritoryClaimSite
import com.daengs.app.territory.TerritoryOccupancy
import com.daengs.app.territory.TerritorySite
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.TrailSnapshot
import com.daengs.app.walk.WalkTrackingState
import org.junit.Assert.*
import org.junit.Test

class TerritoryGameControllerTest {
    private val here = GeoPoint(37.5, 127.0)
    private val near = TerritorySite("A", here, 999.0) // query distance must not drive proximity
    private val far = TerritorySite("B", GeoPoint(37.502, 127.0), 0.0)
    private val board = TerritoryBoardState(sites = listOf(near, far))
    private val fix = LocationSample(here, 1000, elapsedRealtimeNanos = 1_000_000_000L, accuracyMeters = 3f)
    private val tracking = WalkTrackingState(
        activeSessionId = "walk-1", activeDogIds = listOf("dog-1", "dog-2"),
        trail = TrailSnapshot(state = TrackingState.RECORDING), lastSample = fix, latestMomentFix = fix,
    )
    private val names = mapOf("dog-1" to "보리", "dog-2" to "두리")
    private fun game(repo: InMemoryTerritoryClaimRepository = InMemoryTerritoryClaimRepository(emptyList())) =
        TerritoryGameController(repo)
    private fun snapshot(game: TerritoryGameController, state: WalkTrackingState = tracking, now: Long = 2_000_000_000L) =
        game.snapshot(board, state, true, names, now)

    @Test
    fun `nearest target uses walk fix rather than camera query distance`() {
        val state = snapshot(game())
        assertEquals("A", state.targetId)
        assertTrue(state.canMark)
        assertEquals("보리", state.representativeLabel)
    }

    @Test
    fun `explicit far selection remains the target and blocks action`() {
        val state = game().snapshot(board.copy(selectedSiteId = "B"), tracking, true, names, 2_000_000_000L)
        assertEquals("B", state.targetId)
        assertFalse(state.canMark)
        assertEquals(ClaimAccess.APPROACHING, state.target!!.interaction!!.access)
    }

    @Test
    fun `mark updates ownership and a board refresh does not reset it`() {
        val repo = InMemoryTerritoryClaimRepository(emptyList())
        val game = game(repo)
        game.mark("A", board, tracking, true, names, 2_000_000_000L, 2000)
        val state = snapshot(game)
        assertEquals("dog-1", state.target!!.claim.occupancy!!.ownerPetId)
        assertEquals(ClaimCertification.UNVERIFIED, state.target!!.claim.occupancy!!.certification)
        assertFalse(state.canMark)
        game.mark("A", board, tracking, true, names, 3_000_000_000L, 3000)
        assertEquals(1L, repo.site("A").version)
        assertEquals("보리 · 미인증", snapshot(game(repo)).target!!.occupancyLabel)
    }

    @Test
    fun `same walk can mark another site after moving there`() {
        val game = game()
        game.mark("A", board, tracking, true, names, 2_000_000_000L, 2000)
        val moved = fix.copy(point = far.point, elapsedRealtimeNanos = 3_000_000_000L)
        val next = tracking.copy(lastSample = moved, latestMomentFix = moved)
        game.mark("B", board, next, true, names, 4_000_000_000L, 4000)
        assertTrue(snapshot(game, next, 4_000_000_000L).sites.all { it.claim.occupancy?.ownerPetId == "dog-1" })
    }

    @Test
    fun `tap revalidates stale permission and missing target without claiming`() {
        val repo = InMemoryTerritoryClaimRepository(emptyList())
        val game = game(repo)
        assertTrue(snapshot(game).canMark)
        game.mark("A", board, tracking, true, names, 30_000_000_000L, 30000)
        game.mark("A", board, tracking, false, names, 2_000_000_000L, 2000)
        game.mark("A", board.copy(sites = listOf(far)), tracking, true, names, 2_000_000_000L, 2000)
        assertNull(repo.site("A").occupancy)
    }

    @Test
    fun `pause stale mock and missing participant disable readiness`() {
        val game = game()
        assertFalse(snapshot(game, tracking.copy(trail = TrailSnapshot(state = TrackingState.PAUSED))).canMark)
        assertFalse(snapshot(game, now = 30_000_000_000L).canMark)
        assertFalse(snapshot(game, tracking.copy(lastSample = fix.copy(isMock = true))).canMark)
        assertFalse(snapshot(game, tracking.copy(activeDogIds = emptyList())).canMark)
        assertFalse(snapshot(game, tracking.copy(activeSessionId = null)).canMark)
        assertFalse(snapshot(game, tracking.copy(trail = tracking.trail.copy(skippedTooFast = 1))).canMark)
    }

    @Test
    fun `certified rival is displayed but cannot be taken without a camera`() {
        val repo = InMemoryTerritoryClaimRepository(listOf(TerritoryClaimSite(
            "A", TerritoryOccupancy("dog-2", "other-walk", "other-attempt", ClaimCertification.VERIFIED, 0), 1,
        )))
        val state = snapshot(game(repo))
        assertEquals("두리 · 인증", state.target!!.occupancyLabel)
        assertEquals(ClaimAccess.READY, state.target!!.interaction!!.access)
        assertFalse(state.canMark)
        assertTrue(state.guidance.contains("사진 인증"))
    }
}
