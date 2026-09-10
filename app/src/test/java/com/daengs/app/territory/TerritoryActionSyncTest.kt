package com.daengs.app.territory

import com.daengs.app.territory.support.CLAIM
import com.daengs.app.territory.support.ClaimServer
import com.daengs.app.territory.support.DOG
import com.daengs.app.territory.support.DOG2
import com.daengs.app.territory.support.MemoryActions
import com.daengs.app.territory.support.SITE
import com.daengs.app.territory.support.WALK
import com.daengs.app.auth.Session
import com.daengs.app.location.*
import com.daengs.app.walk.*
import com.daengs.app.map.features.territory.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerritoryActionSyncTest {
    @Test fun `settling B retains verified A across restart without replaying success or crossing accounts`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); val state = walking(); var owner = "owner"
        val second = "territory-site:hex-v1:140:2:2"
        val board = TerritoryBoardState(sites = listOf(SITE, second).map { TerritorySite(it, fix().point, 0.0) }, selectedSiteId = SITE)
        var readVersion = 0L
        fun createSync() = TerritoryActionSync(dao, server, { auth(owner) }, { owner }, { state }, backgroundScope, {})
        fun createProvider(sync: TerritoryActionSync) = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, ids ->
            ids.map { SharedTerritorySite(it, readVersion, null) }
        }, { auth(owner) }, { owner }, sync)
        var sync = createSync()
        var provider = createProvider(sync)
        fun view() = provider.snapshot(board, state, true, emptyMap(), 2_000_000_000)
        provider.refresh(board.sites)
        sync.submit(state, SITE, DOG, markBody(WALK, SITE, DOG, fix()))
        sync.submit(state, second, DOG, markBody(WALK, second, DOG, fix()))
        runCurrent()
        // Photo settlement persists the MARK response before publishing its one-shot receipt.
        suspend fun settle(site: String) {
            val mark = dao.all().first { it.kind == "MARK" && JSONObject(it.body).getString("site_id") == site }
            val response = JSONObject(mark.response!!).apply {
                getJSONObject("site").put("version", 2).getJSONObject("occupancy").put("certification", "VERIFIED")
            }.toString()
            dao.update(mark.copy(response = response))
            sync.publishPhotoReceipt(TerritoryMarkReceipt(mark, parseTerritoryClaim(response, mark.body), "photo:$site"))
            runCurrent()
        }
        settle(SITE)
        assertEquals(2L, view().sites.first().claim.version)
        settle(second)
        assertTrue(view().sites.all { it.claim.version == 2L && it.claim.occupancy?.certification == ClaimCertification.VERIFIED })
        // Recreate both consumers using only the persisted responses, with no receipt event.
        sync = createSync(); provider = createProvider(sync)
        provider.refresh(board.sites); runCurrent()
        assertTrue(view().sites.all { it.claim.version == 2L })
        assertNull(view().confirmedMarkId)
        // An authoritative newer read supersedes historical grants, including a neutral result.
        readVersion = 3
        provider.refresh(board.sites)
        assertTrue(view().sites.all { it.claim.version == 3L && it.claim.occupancy == null })
        owner = "other"; readVersion = 0
        provider.refresh(board.sites)
        assertTrue(view().sites.all { it.claim.version == 0L && it.claim.occupancy == null })
        provider.invalidate()
        assertTrue(view().sites.none { it.occupancyKnown })
    }

    @Test fun `fresh mark sends before scheduler returns and survives caller cancellation`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); val state = walking()
        val scheduling = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val sync = TerritoryActionSync(dao, TerritoryActionClient { token, method, path, body ->
            server.request(token, method, path, body).also { if (method == "POST") response.await() }
        }, { auth() }, { "owner" }, { state },
            backgroundScope, { scheduling.await() })
        val original = markBody(WALK, SITE, DOG, fix())
        val caller = launch { sync.submit(state, SITE, DOG, original) }
        runCurrent()
        assertFalse(caller.isCompleted)
        assertEquals(original, server.committed[SITE]!!.first)
        assertEquals(listOf("PUT", "POST"), server.calls.map { it.first })
        caller.cancelAndJoin()
        response.complete(Unit); runCurrent()
        assertTrue(dao.all().all { it.state == "CONFIRMED" })
    }

    @Test fun `automatic delivery preserves pending body when account switches before sending`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); val state = walking(); var owner = "owner"
        val sync = TerritoryActionSync(dao, server, { auth(owner) }, { owner }, { state }, backgroundScope, {})
        val original = markBody(WALK, SITE, DOG, fix())
        sync.submit(state, SITE, DOG, original)
        owner = "other"
        runCurrent()
        assertTrue(server.calls.isEmpty())
        assertEquals(original, dao.all().last().body)
        assertEquals("PENDING", dao.all().last().state)
    }

    @Test fun `server writes require an explicit debug flag and release remains read only`() {
        assertEquals(TerritoryGameMode.LOCAL, territoryGameMode(true, false, false))
        assertEquals(TerritoryGameMode.SERVER_READ, territoryGameMode(true, true, false))
        assertEquals(TerritoryGameMode.SERVER_ACTIONS, territoryGameMode(true, false, true))
        assertEquals(TerritoryGameMode.SERVER_READ, territoryGameMode(false, true, true))
    }

    @Test fun `server ended session cannot be reopened by an offline resume`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); var state = walking()
        val sync = TerritoryActionSync(dao, server, { auth() }, { "owner" }, { state }, backgroundScope, {})
        sync.syncTracking(); sync.deliver()
        state = state.copy(trail = TrailSnapshot(state = TrackingState.PAUSED)); sync.syncTracking()
        state = walking(); sync.syncTracking()
        server.phase = "ENDED"
        assertTrue(sync.deliver())
        assertTrue(server.calls.none { it.first == "PATCH" })
        assertEquals(2, dao.all().count { it.failure == "session_ended" })
    }

    private fun walking() = WalkTrackingState(ownerId = "owner", activeSessionId = WALK,
        activeSessionStartedAtMillis = 1000, activeDogIds = listOf(DOG, DOG2),
        trail = TrailSnapshot(state = TrackingState.RECORDING), latestMomentFix = fix())
    private fun fix() = LocationSample(GeoPoint(37.5, 127.0), 2000, 2_000_000_000, 2f)

    @Test fun `double taps commit one original body and independent sites choose independent dogs`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); val state = walking()
        val sync = TerritoryActionSync(dao, server, { auth() }, { "owner" }, { state }, backgroundScope, {})
        sync.syncTracking(); sync.deliver()
        val original = markBody(WALK, SITE, DOG, fix())
        coroutineScope { repeat(5) { launch { sync.submit(state, SITE, DOG, original) } } }
        sync.submit(state, SITE, DOG2, markBody(WALK, SITE, DOG2, fix().copy(capturedAtMillis = 2100)))
        val second = "territory-site:hex-v1:140:2:2"
        sync.submit(state, second, DOG2, markBody(WALK, second, DOG2, fix()))
        assertEquals(2, dao.all().count { it.kind == "MARK" })
        assertTrue(sync.deliver())
        assertEquals(original, server.committed[SITE]!!.first)
        assertEquals(DOG2, JSONObject(server.committed[second]!!.first).getString("claiming_pet_id"))
    }

    @Test fun `response loss replays exact GPS dog and timestamp after process restart before ending session`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); var state = walking()
        fun create() = TerritoryActionSync(dao, server, { auth() }, { "owner" }, { state }, backgroundScope, {})
        val sync = create(); sync.syncTracking(); sync.deliver()
        val original = markBody(WALK, SITE, DOG, fix())
        sync.submit(state, SITE, DOG, original)
        server.failMarkAfterCommit = true
        assertFalse(sync.deliver()); assertNull(sync.receipt.value)
        assertEquals("PENDING", dao.all().last().state)
        state = WalkTrackingState()
        val restarted = create(); restarted.recover()
        assertTrue(restarted.deliver())
        assertEquals(listOf(original, original), server.calls.filter { it.first == "POST" }.map { it.third })
        assertEquals("ENDED", server.phase)
        assertNull(restarted.receipt.value)
        assertEquals(1, server.committed.size)
        assertTrue(dao.all().all { it.state == "CONFIRMED" })
    }

    @Test fun `pause resume mark end maintain delivery order and recover version conflict`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); var state = walking()
        val sync = TerritoryActionSync(dao, server, { auth() }, { "owner" }, { state }, backgroundScope, {})
        sync.syncTracking()
        state = state.copy(trail = TrailSnapshot(state = TrackingState.PAUSED)); sync.syncTracking()
        state = walking(); sync.syncTracking()
        sync.submit(state, SITE, DOG, markBody(WALK, SITE, DOG, fix()))
        state = WalkTrackingState(); sync.syncTracking()
        server.conflictPhaseOnce = true
        assertFalse(sync.deliver())
        assertTrue(sync.deliver())
        assertEquals(listOf("PUT", "GET", "PATCH", "GET", "PATCH", "GET", "PATCH", "POST", "GET", "PATCH"), server.calls.map { it.first })
        assertEquals("ENDED", server.phase)
    }

    @Test fun `account switch during HTTP never publishes old owner's success or sends its later operations`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); val state = walking(); var owner = "owner"
        val sync = TerritoryActionSync(dao, TerritoryActionClient { token, method, path, body ->
            assertEquals("token-$owner", token)
            server.request(token, method, path, body).also { if (method == "POST") owner = "other" }
        }, { auth(owner) }, { owner }, { state }, backgroundScope, {})
        sync.syncTracking(); sync.deliver()
        sync.submit(state, SITE, DOG, markBody(WALK, SITE, DOG, fix()))
        sync.submit(state, "territory-site:hex-v1:140:2:2", DOG, markBody(WALK, "territory-site:hex-v1:140:2:2", DOG, fix()))
        sync.deliver()
        assertNull(sync.receipt.value)
        assertEquals(1, server.calls.count { it.first == "POST" })
        assertTrue(sync.deliver())
        assertEquals(1, server.calls.count { it.first == "POST" })
    }

    @Test fun `definitively rejected location can be replaced after resume but ambiguous or identity conflicts stay locked`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); var state = walking()
        val sync = TerritoryActionSync(dao, server, { auth() }, { "owner" }, { state }, backgroundScope, {})
        sync.syncTracking(); sync.deliver()
        sync.submit(state, SITE, DOG, markBody(WALK, SITE, DOG, fix()))
        server.rejectMark = "stale_location"; sync.deliver()
        assertTrue(dao.all().last().canReplaceRejectedMark())
        state = state.copy(trail = TrailSnapshot(state = TrackingState.PAUSED)); sync.syncTracking()
        state = walking(); sync.syncTracking()
        sync.submit(state, SITE, DOG2, markBody(WALK, SITE, DOG2, fix()))
        assertEquals("MARK", dao.all().last().kind)
        server.rejectMark = "attempt_identity_conflict"; sync.deliver()
        val rejected = dao.all().last()
        assertFalse(rejected.canReplaceRejectedMark())
        sync.submit(state, SITE, DOG, markBody(WALK, SITE, DOG, fix()))
        assertEquals(rejected.body, dao.all().last().body)
    }

    @Test fun `malformed success stays pending and recovery cannot fake an ownership animation`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); val state = walking()
        val sync = TerritoryActionSync(dao, server, { auth() }, { "owner" }, { state }, backgroundScope, {})
        sync.syncTracking(); sync.deliver()
        sync.submit(state, SITE, DOG, markBody(WALK, SITE, DOG, fix()))
        server.wrongClaim = true
        assertFalse(sync.deliver()); assertNull(sync.receipt.value)
        assertEquals("PENDING", dao.all().last().state)
        assertTrue(sync.deliver()); assertNull(sync.receipt.value)
    }

    @Test fun `actions require confirmed session trusted proximity and neutral shared read while photo stays separate`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer(); var state = walking()
        val sync = TerritoryActionSync(dao, server, { auth() }, { "owner" }, { state }, backgroundScope, {})
        val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ -> listOf(SharedTerritorySite(SITE, 0, null)) },
            { auth() }, { "owner" }, sync)
        val board = TerritoryBoardState(sites = listOf(TerritorySite(SITE, fix().point, 0.0)), selectedSiteId = SITE)
        fun view() = provider.snapshot(board, state, true, mapOf(DOG to "보리", DOG2 to "두부"), 2_000_000_000)
        provider.refresh(board.sites); runCurrent()
        assertFalse(view().canMark)
        assertEquals(TerritoryProximityRange.IN_RANGE, view().target!!.proximity.range)
        assertEquals(ClaimAccess.READY, view().target!!.interaction!!.access)
        assertEquals("게임 세션을 연결하고 있어요", view().guidance)
        sync.deliver(); runCurrent()
        assertTrue(view().canMark); assertFalse(view().canPhotograph)
        assertEquals(TerritoryProximityRange.IN_RANGE, view().target!!.proximity.range)
        state = state.copy(trail = TrailSnapshot(state = TrackingState.PAUSED))
        assertFalse(view().canMark)
        assertEquals(TerritoryProximityRange.IN_RANGE, view().target!!.proximity.range)
        state = walking().copy(latestMomentFix = fix().copy(accuracyMeters = 21f)); assertFalse(view().canMark)
        state = walking(); provider.selectPet(DOG2, SITE, state)
        provider.submitMark(SITE, board, state, true, emptyMap(), 2_000_000_000, 2000); runCurrent()
        assertFalse(view().canMark); assertTrue(view().petLocked)
        // submitMark now starts delivery in application scope, without a Worker tick.
        assertEquals("보리 · 미인증", view().target!!.occupancyLabel)
        sync.deliver(); runCurrent()
        assertEquals("보리 · 미인증", view().target!!.occupancyLabel)
        assertEquals(CLAIM, view().confirmedMarkId)
        assertEquals(DOG2, view().claimingPetId)
        state = WalkTrackingState()
        assertTrue(view().readOnly); assertTrue(view().eligiblePets.isEmpty())
        assertFalse(view().canMark)
    }

    private fun auth(owner: String = "owner") = Session(owner, "token-$owner", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
}
