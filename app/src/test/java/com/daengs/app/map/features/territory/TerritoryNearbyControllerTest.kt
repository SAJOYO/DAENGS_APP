package com.daengs.app.map.features.territory

import com.daengs.app.location.GeoPoint
import com.daengs.app.territory.*
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerritoryNearbyControllerTest {
    private val origin = GeoPoint(37.5, 127.0)
    private fun page(id: String) = TerritorySitePage(1, false, listOf(TerritorySite(id, origin, 0.0)))

    @Test fun `device feed uses bounded radius and refreshes by movement or age`() = runTest {
        val requests = mutableListOf<NearbyTerritorySitesRequest>()
        val controller = TerritoryNearbyController(TerritorySiteRepository { requests += it; page("A") }, backgroundScope)
        controller.update(origin, 0); runCurrent()
        assertEquals(300, requests.single().radiusMeters)
        assertEquals(500, requests.single().limit)
        controller.update(GeoPoint(37.5004, 127.0), 59_000_000_000L); runCurrent()
        assertEquals(1, requests.size)
        controller.update(origin, 60_000_000_000L); runCurrent()
        assertEquals(2, requests.size)
        controller.update(GeoPoint(37.502, 127.0), 61_000_000_000L); runCurrent()
        assertEquals(3, requests.size)
    }

    @Test fun `failed refresh clears candidates and retries at a bounded interval`() = runTest {
        var calls = 0
        val controller = TerritoryNearbyController(TerritorySiteRepository {
            if (++calls == 2) throw UnknownHostException()
            page("A")
        }, backgroundScope)
        controller.update(origin, 0); runCurrent()
        val moved = GeoPoint(37.502, 127.0)
        controller.update(moved, 1_000_000_000L); runCurrent()
        assertTrue(controller.state.value.sites.isEmpty())
        assertEquals(TerritoryFailure.Offline, controller.state.value.failure)
        controller.update(moved, 30_000_000_000L); runCurrent()
        assertEquals(2, calls)
        controller.update(moved, 31_000_000_000L); runCurrent()
        assertEquals(3, calls)
        assertNull(controller.state.value.failure)
    }

    @Test fun `late responses cannot restore cleared or superseded candidates`() = runTest {
        val pending = mutableListOf<CompletableDeferred<TerritorySitePage>>()
        val controller = TerritoryNearbyController(TerritorySiteRepository {
            val response = CompletableDeferred<TerritorySitePage>().also(pending::add)
            withContext(NonCancellable) { response.await() }
        }, backgroundScope)
        controller.update(origin, 0); runCurrent()
        controller.update(GeoPoint(37.502, 127.0), 1); runCurrent()
        pending[1].complete(page("new")); runCurrent()
        pending[0].complete(page("old")); runCurrent()
        assertEquals("new", controller.state.value.sites.single().id)
        controller.update(GeoPoint(37.504, 127.0), 2); runCurrent()
        controller.update(null, 3)
        pending[2].complete(page("hidden")); runCurrent()
        assertEquals(TerritoryNearbyState(), controller.state.value)
        controller.update(origin, 4); runCurrent()
        pending[3].complete(page("reopened")); runCurrent()
        assertEquals("reopened", controller.state.value.sites.single().id)
    }

    @Test fun `truncation is preserved and unsupported locations do not loop each tick`() = runTest {
        var calls = 0
        val controller = TerritoryNearbyController(TerritorySiteRepository {
            calls++; page("A").copy(truncated = true)
        }, backgroundScope)
        controller.update(origin, 0); runCurrent()
        assertTrue(controller.state.value.truncated)
        controller.update(GeoPoint(0.0, 0.0), 1); runCurrent()
        controller.update(GeoPoint(0.0, 0.0), 2); runCurrent()
        assertEquals(1, calls)
        assertTrue(controller.state.value.sites.isEmpty())
        assertEquals(TerritoryFailure.UnsupportedLocation, controller.state.value.failure)
    }

    @Test fun `candidate ranking and occupancy union never change manual selection`() {
        val a = TerritorySite("A", origin, 999.0)
        val b = a.copy(id = "B")
        val far = a.copy(id = "far")
        val nearby = TerritoryNearbyState(listOf(b, a))
        val board = TerritoryBoardState(sites = listOf(far, a), selectedSiteId = "far")
        val merged = board.withNearby(nearby)
        assertEquals(listOf("far", "A", "B"), merged.sites.map { it.id })
        assertEquals("far", merged.selectedSiteId)
        fun candidate(site: TerritorySite, range: TerritoryProximityRange, distance: Double) =
            TerritoryGameSite(site, TerritoryClaimSite(site.id), "", null, distance, false,
                occupancyKnown = false, proximity = TerritoryProximity(range, distance, 3.0))
        val sites = listOf(candidate(b, TerritoryProximityRange.IN_RANGE, 5.0),
            candidate(a, TerritoryProximityRange.IN_RANGE, 5.0),
            candidate(far, TerritoryProximityRange.IN_RANGE, 0.0))
        assertEquals("A", nearbyTerritoryTarget(sites, nearby))
        assertEquals("A", nearbyTerritoryTarget(sites.reversed(), nearby))
        assertEquals("B", nearbyTerritoryTarget(listOf(sites[0],
            candidate(a, TerritoryProximityRange.UNAVAILABLE, 0.0)), nearby))
        assertEquals("A", nearbyTerritoryTarget(listOf(candidate(a, TerritoryProximityRange.APPROACHING, 25.0)), nearby))
        assertNull(nearbyTerritoryTarget(listOf(candidate(a, TerritoryProximityRange.APPROACHING, 301.0)), nearby))
        assertNull(nearbyTerritoryTarget(sites, TerritoryNearbyState()))
    }
}
