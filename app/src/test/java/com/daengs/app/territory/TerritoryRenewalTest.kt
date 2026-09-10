package com.daengs.app.territory

import com.daengs.app.auth.Session
import com.daengs.app.location.*
import com.daengs.app.territory.support.*
import com.daengs.app.walk.*
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerritoryRenewalTest {
    private val fix = LocationSample(GeoPoint(37.5, 127.0), 2000, 2_000_000_000, 2f)
    private val walking = WalkTrackingState(ownerId = "owner", activeSessionId = WALK,
        activeSessionStartedAtMillis = 1000, activeDogIds = listOf(DOG),
        trail = TrailSnapshot(state = TrackingState.RECORDING), latestMomentFix = fix)

    @Test fun `lost renewal response and restart replay one UUID without another extension`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer()
        var owner = "owner"
        var offline = false
        var loseOnce = true
        var awards = 0
        val requests = mutableListOf<Pair<String, String>>()
        val receipts = mutableMapOf<String, String>()
        val api = TerritoryActionClient { token, method, path, body ->
            if (path.contains("/renewals/")) {
                if (offline) throw IOException("offline")
                requests += path to body!!
                val response = receipts.getOrPut(path) {
                    awards++
                    JSONObject().put("renewal_id", path.substringAfterLast('/')).put("site_version", 2)
                        .put("expires_at", "1970-01-04T00:00:02Z").toString()
                }
                if (loseOnce) { loseOnce = false; throw IOException("response lost after commit") }
                response
            } else if (method == "GET" && path == "/claims/$CLAIM") {
                // The location has since been taken: a renewal receipt must not resurrect it.
                JSONObject(server.committed[SITE]!!.second).apply {
                    getJSONObject("site").put("version", 3).put("occupancy", JSONObject.NULL)
                }.toString()
            } else server.request(token, method, path, body)
        }
        fun create() = TerritoryActionSync(dao, api,
            { Session(owner, "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE) }, { owner }, { walking }, backgroundScope, {})
        var sync = create()
        sync.submit(walking, SITE, DOG, markBody(WALK, SITE, DOG, fix)); sync.deliver(); runCurrent()
        val mark = dao.all().single { it.kind == "MARK" }
        sync.submitRenewal(walking, mark, markBody(WALK, SITE, DOG, fix), 1)
        sync.deliver() // Deliberate response loss.
        offline = true
        runCurrent()
        assertEquals("PENDING", dao.all().single { it.kind == "RENEW" }.state)
        sync.submitRenewal(walking, mark, markBody(WALK, SITE, DOG, fix.copy(capturedAtMillis = 3000)), 1)
        assertEquals(1, dao.all().count { it.kind == "RENEW" })
        runCurrent()
        sync = create(); owner = "another"
        assertTrue(sync.deliver()); assertEquals(1, requests.size)
        owner = "owner"; offline = false
        assertTrue(sync.deliver()); runCurrent()
        assertEquals(1, awards)
        assertEquals(2, requests.size)
        assertEquals(requests[0], requests[1])
        assertEquals("CONFIRMED", dao.all().single { it.kind == "RENEW" }.state)
        val refreshed = dao.all().single { it.kind == "MARK" }
        assertNull(parseTerritoryClaim(refreshed.response!!, refreshed.body).site.occupancy)
        assertNull(sync.receipt.value) // No new acquisition animation on a renewal/recovery.
    }

    @Test fun `paused or changed dog cannot enqueue a renewal`() = runTest {
        val dao = MemoryActions(); val api = ClaimServer(); var state = walking
        val auth = Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val sync = TerritoryActionSync(dao, api, { auth }, { "owner" }, { state }, backgroundScope, {})
        sync.submit(state, SITE, DOG, markBody(WALK, SITE, DOG, fix)); sync.deliver(); runCurrent()
        val mark = dao.all().single { it.kind == "MARK" }
        state = state.copy(trail = TrailSnapshot(state = TrackingState.PAUSED))
        sync.submitRenewal(walking, mark, markBody(WALK, SITE, DOG, fix), 1)
        state = walking.copy(activeDogIds = listOf(DOG2))
        sync.submitRenewal(walking, mark, markBody(WALK, SITE, DOG, fix), 1)
        assertFalse(dao.all().any { it.kind == "RENEW" })
    }
}
