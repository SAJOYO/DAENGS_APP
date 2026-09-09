package com.daengs.app.territory

import com.daengs.app.auth.Session
import com.daengs.app.location.*
import com.daengs.app.walk.*
import com.daengs.app.map.features.territory.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal const val WALK = "00000000-0000-0000-0000-000000000001"
internal const val DOG = "00000000-0000-0000-0000-000000000002"
internal const val DOG2 = "00000000-0000-0000-0000-000000000003"
internal const val SITE = "territory-site:hex-v1:140:1:2"
internal const val CLAIM = "00000000-0000-0000-0000-000000000004"

internal class MemoryActions : TerritoryActionDao {
    val rows = MutableStateFlow<List<TerritoryOperation>>(emptyList())
    private var next = 0L
    override fun observe(): Flow<List<TerritoryOperation>> = rows
    override suspend fun all() = rows.value
    override suspend fun insert(row: TerritoryOperation): Long {
        if (rows.value.any { it.identity == row.identity }) return -1
        val id = ++next
        rows.value = rows.value + row.copy(sequence = id)
        return id
    }
    override suspend fun update(row: TerritoryOperation) { rows.value = rows.value.map { if (it.sequence == row.sequence) row else it } }
    override suspend fun delete(sequence: Long) { rows.value = rows.value.filter { it.sequence != sequence } }
}

internal class ClaimServer : TerritoryActionClient {
    var start: String? = null
    var phase = "RECORDING"
    var version = 0L
    var failMarkAfterCommit = false
    var rejectMark: String? = null
    var conflictPhaseOnce = false
    var wrongClaim = false
    val calls = mutableListOf<Triple<String, String, String?>>()
    val committed = mutableMapOf<String, Pair<String, String>>()
    fun sessionResponse() = JSONObject(checkNotNull(start)).put("client_session_id", WALK)
        .put("phase", phase).put("version", version).toString()
    override suspend fun request(token: String, method: String, path: String, body: String?): String {
        calls += Triple(method, path, body)
        if (method == "PUT") { if (start == null) start = body; return sessionResponse() }
        if (method == "GET") return sessionResponse()
        if (method == "PATCH") {
            if (conflictPhaseOnce) { conflictPhaseOnce = false; version++; throw TerritoryActionException(409, "session_changed") }
            val request = JSONObject(body!!)
            val desired = request.getString("phase")
            val expected = request.getLong("expected_version")
            if (expected != version && !(version == expected + 1 && phase == desired)) throw TerritoryActionException(409, "session_changed")
            if (phase != desired) { version++; phase = desired }
            return sessionResponse()
        }
        val request = JSONObject(body!!)
        val site = request.getString("site_id")
        committed[site]?.let {
            if (it.first != body) throw TerritoryActionException(409, "attempt_identity_conflict")
            return it.second
        }
        rejectMark?.let { throw TerritoryActionException(409, it) }
        check(phase == "RECORDING")
        val response = """{"claim_id":"$CLAIM","client_session_id":"$WALK","claiming_pet_id":"${request.getString("claiming_pet_id")}","disposition":"GRANTED","photo_status":"NOT_SUBMITTED","current_photo_id":null,"resolution_code":null,"site":{"site_id":"$site","version":1,"occupancy":{"owner_pet_id":"${request.getString("claiming_pet_id")}","owner_pet_name":"보리","is_mine":true,"certification":"UNVERIFIED","occupied_at":"1970-01-01T00:00:02Z"}}}"""
        committed[site] = body to response
        if (failMarkAfterCommit) { failMarkAfterCommit = false; throw IOException("response lost") }
        return if (wrongClaim) response.replace(CLAIM, "invalid") else response
    }
}

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
