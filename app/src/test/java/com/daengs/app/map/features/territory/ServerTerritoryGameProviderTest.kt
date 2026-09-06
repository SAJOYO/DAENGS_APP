package com.daengs.app.map.features.territory

import com.daengs.app.auth.Session
import com.daengs.app.location.GeoPoint
import com.daengs.app.territory.*
import com.daengs.app.walk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerTerritoryGameProviderTest {
    private val site = TerritorySite("territory-site:hex-v1:140:1:2", GeoPoint(37.5, 127.0), 0.0)
    private val board = TerritoryBoardState(sites = listOf(site), selectedSiteId = site.id)
    private val session = Session("user", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val occupied = SharedTerritorySite(site.id, 4,
        SharedTerritoryOccupancy("dog", "두부", false, ClaimCertification.VERIFIED, 1000))
    private fun snapshot(provider: TerritoryGameProvider, tracking: WalkTrackingState = WalkTrackingState()) =
        provider.snapshot(board, tracking, true, emptyMap(), 0)

    @Test fun `same map displays remote dog and server read mode never grants actions`() = runTest {
        val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { token, ids ->
            assertEquals("token", token); assertEquals(listOf(site.id), ids); listOf(occupied)
        }, { session }, { "user" })
        assertFalse(snapshot(provider).target!!.occupancyKnown)
        provider.refresh(listOf(site))
        for (state in TrackingState.entries) {
            val tracking = WalkTrackingState(activeSessionId = "walk", activeDogIds = listOf("dog"),
                trail = TrailSnapshot(state = state))
            val result = snapshot(provider, tracking)
            assertTrue(result.readOnly)
            assertEquals("두부 · 인증", result.target!!.occupancyLabel)
            assertEquals(4L, result.target!!.claim.version)
            assertNull(result.target!!.claim.occupancy!!.sourceSessionId)
            assertNull(result.target!!.claim.occupancy!!.sourceAttemptId)
            assertFalse(result.canMark); assertFalse(result.canPhotograph)
            assertTrue(result.eligiblePets.isEmpty())
            assertNull(provider.captureTarget(site.id, board, tracking, true, emptyMap(), 0))
        }
    }

    @Test fun `failure clears stale ownership without calling it neutral and retry recovers`() = runTest {
        var fail = false
        val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ ->
            if (fail) throw java.io.IOException("secret server response")
            listOf(occupied)
        }, { session }, { "user" })
        provider.refresh(listOf(site))
        fail = true
        provider.refresh(listOf(site))
        val failed = snapshot(provider)
        assertFalse(failed.target!!.occupancyKnown)
        assertEquals("점유 확인 전", failed.target!!.occupancyLabel)
        assertTrue(failed.guidance.contains("불러오지 못했어요"))
        assertFalse(failed.guidance.contains("secret"))
        fail = false
        provider.refresh(listOf(site))
        assertEquals("두부 · 인증", snapshot(provider).target!!.occupancyLabel)
    }

    @Test fun `no session does not issue HTTP and neutral needs explicit server result`() = runTest {
        var calls = 0
        val api = TerritoryOccupancyClient { _, _ -> calls++; listOf(occupied.copy(occupancy = null)) }
        val loggedOut = ServerTerritoryGameProvider(api, { null }, { null })
        loggedOut.refresh(listOf(site))
        assertEquals(0, calls)
        assertTrue(snapshot(loggedOut).guidance.contains("로그인"))
        val signedIn = ServerTerritoryGameProvider(api, { session }, { "user" })
        signedIn.refresh(listOf(site))
        assertEquals("미점유", snapshot(signedIn).target!!.occupancyLabel)
    }

    @Test fun `late result after hiding or changing account cannot populate cache`() = runTest {
        var owner: String? = "user"
        val response = CompletableDeferred<List<SharedTerritorySite>>()
        val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ -> response.await() },
            { session }, { owner })
        val request = launch { provider.refresh(listOf(site)) }
        runCurrent()
        provider.invalidate()
        response.complete(listOf(occupied))
        request.join()
        assertFalse(snapshot(provider).target!!.occupancyKnown)

        val response2 = CompletableDeferred<List<SharedTerritorySite>>()
        val other = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ -> response2.await() },
            { session }, { owner })
        val request2 = launch { other.refresh(listOf(site)) }
        runCurrent()
        owner = "new-user"
        response2.complete(listOf(occupied))
        request2.join()
        assertFalse(snapshot(other).target!!.occupancyKnown)
    }

    @Test fun `older viewport response cannot replace newer viewport`() = runTest {
        val old = CompletableDeferred<List<SharedTerritorySite>>()
        var calls = 0
        val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ ->
            if (++calls == 1) old.await() else listOf(occupied.copy(version = 5, occupancy = null))
        }, { session }, { "user" })
        val first = launch { provider.refresh(listOf(site)) }
        runCurrent()
        provider.refresh(listOf(site))
        old.complete(listOf(occupied)); first.join()
        assertEquals(5L, snapshot(provider).target!!.claim.version)
        assertEquals("미점유", snapshot(provider).target!!.occupancyLabel)
    }

    @Test fun `query is batched at one hundred and local mode stays the default`() = runTest {
        val sizes = mutableListOf<Int>()
        val sites = (0..204).map { site.copy(id = "territory-site:hex-v1:140:$it:2") }
        val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, ids ->
            sizes += ids.size; ids.map { SharedTerritorySite(it, 0, null) }
        }, { session }, { "user" })
        provider.refresh(sites)
        assertEquals(listOf(100, 100, 5), sizes)
        assertEquals(TerritoryGameMode.LOCAL, territoryGameMode(true, false))
        assertEquals(TerritoryGameMode.SERVER_READ, territoryGameMode(true, true))
        assertEquals(TerritoryGameMode.DISABLED, territoryGameMode(false, true))
    }
}
