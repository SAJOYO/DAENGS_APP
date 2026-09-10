package com.daengs.app.territory

import com.daengs.app.auth.Session
import com.daengs.app.location.*
import com.daengs.app.map.features.territory.*
import com.daengs.app.territory.support.*
import com.daengs.app.walk.*
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerritoryFirstSeasonTest {
    private val auth = Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val fix = LocationSample(GeoPoint(37.5, 127.0), 2000, 2_000_000_000, 2f)
    private val tracking = WalkTrackingState(ownerId = "owner", activeSessionId = WALK,
        activeSessionStartedAtMillis = 1000, activeDogIds = listOf(DOG),
        trail = TrailSnapshot(state = TrackingState.RECORDING), latestMomentFix = fix)
    private val board = TerritoryBoardState(sites = listOf(TerritorySite(SITE, fix.point, 0.0)), selectedSiteId = SITE)
    private fun site(verified: Boolean = true) = SharedTerritorySite(SITE, 3,
        SharedTerritoryOccupancy(DOG, "두부", true,
            if (verified) ClaimCertification.VERIFIED else ClaimCertification.UNVERIFIED,
            2000, expiresAtMillis = 8000), serverNowMillis = 2000,
        policyVersion = FIRST_SEASON_POLICY, receivedAtNanos = 2_000_000_000, seasonId = "territory-2026-09")

    @Test fun `new season verified owner can reserve a fresh challenge and renew photo in same walk`() = runTest {
        val dao = MemoryActions(); val server = PhotoServer()
        val dir = kotlin.io.path.createTempDirectory("first-season-photo").toFile()
        try {
            val sync = TerritoryActionSync(dao, server, { auth }, { "owner" }, { tracking }, backgroundScope, {})
            sync.photos = ServerTerritoryPhotos(sync, server, server, File(dir, "proof"), { auth }, { "owner" }, { tracking }, backgroundScope, {})
            sync.submit(tracking, SITE, DOG, markBody(WALK, SITE, DOG, fix)); sync.deliver(); runCurrent()
            val mark = dao.all().single { it.kind == "MARK" }
            dao.update(mark.copy(response = JSONObject(mark.response!!).put("photo_status", "VERIFIED").toString())); runCurrent()
            var remote = site()
            val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ -> listOf(remote) }, { auth }, { "owner" }, sync, leaseNowNanos = { 2_000_000_000 })
            provider.refresh(board.sites)
            fun view() = provider.snapshot(board, tracking, true, mapOf(DOG to "두부"), 2_000_000_000)
            assertTrue(view().canPhotograph)
            assertEquals("사진으로 유지 연장", view().photoActionLabel)
            assertTrue(view().guidance.contains("0점"))
            server.accessAction = "PHOTO_RENEW"
            val target = provider.prepareCapture(SITE, board, tracking, true, emptyMap(), 2_000_000_000, 2000)
            assertNotNull(target)
            val id = provider.beginCapture(target!!, board, tracking, true, emptyMap(), 2_000_000_000, 2000)
            assertNotNull(id)
            runCurrent()
            assertEquals("인증 사진을 저장하고 있어요", view().guidance)
            assertFalse(view().canPhotograph)
            val admission = server.calls.single { it.second.contains("/challenges/") }
            assertEquals("PUT", admission.first)
            assertEquals(3L, JSONObject(admission.third!!).getLong("expected_site_version"))
            assertTrue(admission.second.endsWith(id!!))
            provider.cancelCapture(id); runCurrent()
            assertTrue(view().canPhotograph)
            remote = remote.copy(policyVersion = "certified-protection-v2")
            provider.refresh(board.sites)
            assertFalse(view().canPhotograph)
        } finally { dir.deleteRecursively() }
    }

    @Test fun `onsite button creates a mark then renews same claim and old receipt cannot cross seasons`() = runTest {
        val dao = MemoryActions(); val server = ClaimServer()
        var remote = site(false)
        var leaseTime = 2_000_000_000L
        val renewalCalls = mutableListOf<Triple<String, String, String?>>()
        val api = TerritoryActionClient { token, method, path, body ->
            when {
                path.contains("/renewals/") -> {
                    renewalCalls += Triple(method, path, body)
                    JSONObject().put("renewal_id", path.substringAfterLast('/')).put("site_version", 4)
                        .put("expires_at", "1970-01-01T00:00:08Z").toString()
                }
                method == "GET" && path == "/claims/$CLAIM" -> JSONObject(server.committed[SITE]!!.second).apply {
                    getJSONObject("site").apply {
                        put("version", 4); put("season_id", remote.seasonId); put("policy_version", FIRST_SEASON_POLICY)
                        put("server_now", "1970-01-01T00:00:02Z")
                        getJSONObject("occupancy").put("expires_at", "1970-01-01T00:00:08Z")
                    }
                }.toString()
                else -> server.request(token, method, path, body)
            }
        }
        val sync = TerritoryActionSync(dao, api, { auth }, { "owner" }, { tracking }, backgroundScope, {})
        val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ -> listOf(remote) },
            { auth }, { "owner" }, sync, leaseNowNanos = { leaseTime })
        provider.refresh(board.sites); sync.deliver(); runCurrent()
        fun view() = provider.snapshot(board, tracking, true, emptyMap(), 2_000_000_000)
        suspend fun press() = provider.submitMark(SITE, board, tracking, true, emptyMap(), 2_000_000_000, 2000)
        assertTrue(view().canMark)
        press(); sync.deliver(); runCurrent()
        assertEquals(1, dao.all().count { it.kind == "MARK" })
        assertTrue(view().canMark)
        press(); sync.deliver(); runCurrent()
        assertEquals(1, server.calls.count { it.first == "POST" && it.second == "/claims" })
        val call = renewalCalls.single()
        assertEquals("PUT", call.first)
        assertTrue(call.second.startsWith("/claims/$CLAIM/renewals/"))
        assertEquals(3L, JSONObject(call.third!!).getLong("expected_site_version"))
        assertEquals("CONFIRMED", dao.all().single { it.kind == "RENEW" }.state)
        assertEquals(4L, view().target!!.claim.version)
        // Repeated snapshots must not restart the receipt's sampled lease clock.
        leaseTime = 8_000_000_000
        assertFalse(view().target!!.occupancyKnown)
        remote = remote.copy(version = 0, occupancy = null, seasonId = "territory-2026-10")
        provider.refresh(board.sites)
        assertTrue(view().target!!.occupancyKnown)
        assertNull(view().target!!.claim.occupancy)
        assertEquals(0L, view().target!!.claim.version)
    }

    @Test fun `expired lease blocks actions without locally granting neutral occupancy`() = runTest {
        var remote = site(false)
        var leaseTime = 2_000_000_000L
        val sync = TerritoryActionSync(MemoryActions(), ClaimServer(), { auth }, { "owner" }, { tracking }, backgroundScope, {})
        val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ -> listOf(remote) }, { auth }, { "owner" }, sync, leaseNowNanos = { leaseTime })
        provider.refresh(board.sites); sync.deliver(); runCurrent()
        fun view(nanos: Long) = provider.snapshot(board, tracking, true, emptyMap(), nanos)
        assertTrue(view(2_000_000_000).canMark)
        assertEquals("유지 연장 · 0점", view(2_000_000_000).actionLabel)
        leaseTime = 8_000_000_000
        val expired = view(8_000_000_000)
        assertFalse(expired.target!!.occupancyKnown)
        assertFalse(expired.canMark); assertFalse(expired.canPhotograph)
        assertTrue(expired.target!!.leaseLabel!!.contains("확인 중"))
        // A fresh next-season read is the authority for a new neutral site.
        remote = remote.copy(version = 4, occupancy = null, seasonId = "territory-2026-10", serverNowMillis = 8000)
        provider.refresh(board.sites)
        assertTrue(view(8_000_000_000).target!!.occupancyKnown)
        assertNull(view(8_000_000_000).target!!.claim.occupancy)
        assertNull(view(8_000_000_000).target!!.leaseLabel)
    }

    @Test fun `lease clock is sampled from server and supports partial month expiry`() {
        val value = site()
        assertEquals(6000L, value.leaseRemainingMillis(2_000_000_000))
        assertEquals(1L, value.leaseRemainingMillis(7_999_000_000))
        assertEquals(0L, value.leaseRemainingMillis(8_000_000_000))
        assertNull(value.copy(occupancy = value.occupancy!!.copy(expiresAtMillis = null)).leaseLabel(Long.MAX_VALUE))
        assertNull(value.copy(serverNowMillis = null).leaseLabel(0))
    }

    @Test fun `occupancy parser keeps lease and season and permits legacy absence`() {
        val response = """[{"site_id":"$SITE","version":2,"server_now":"2026-09-30T14:00:00Z",
          "season_id":"territory-2026-09","policy_version":"$FIRST_SEASON_POLICY",
          "occupancy":{"owner_pet_id":"$DOG","owner_pet_name":"두부","is_mine":true,
          "certification":"VERIFIED","occupied_at":"2026-09-30T14:00:00Z","expires_at":"2026-09-30T15:00:00Z"}}]"""
        val parsed = parseSharedTerritories(response, listOf(SITE)).single()
        assertEquals("territory-2026-09", parsed.seasonId)
        assertEquals(3_600_000L, parsed.leaseRemainingMillis(parsed.receivedAtNanos))
        assertTrue(parsed.policyVersion.requiresTerritoryChallenge())
        assertFalse("unknown-version".requiresTerritoryChallenge())
    }
}
