package com.daengs.app.map.features.territory

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.territory.*
import org.junit.Assert.*
import org.junit.Test

class TerritoryRangeTrackerTest {
    private val location = TerritoryLocationEvidence(LocationSample(GeoPoint(0.0, 0.0), 1, 1, 3f), true)
    private fun site(id: String = "A", meters: Double) = TerritorySite(id,
        GeoPoint(Math.toDegrees(meters / 6_371_000.0), 0.0), 999.0)
    private fun game(sites: List<TerritorySite>) = TerritoryGameState(enabled = true, readOnly = true,
        sites = sites.map { TerritoryGameSite(it, TerritoryClaimSite(it.id), "", null, 999.0, false,
            proximity = location.proximity(it, 20.0)) })

    @Test fun `automatic ranges have separate entry and exit thresholds and ignore query distance`() {
        val tracker = TerritoryRangeTracker()
        fun update(distance: Double): Set<String> {
            val sites = listOf(site(meters = distance))
            return tracker.update(game(sites), TerritoryNearbyState(sites = sites), location, true)
        }
        assertEquals(emptySet<String>(), update(61.0))
        assertEquals(setOf("A"), update(59.0))
        assertEquals(setOf("A"), update(69.0))
        assertEquals(emptySet<String>(), update(71.0))
        assertEquals(emptySet<String>(), update(65.0))
        assertEquals(setOf("A"), update(30.0))
    }

    @Test fun `uncertain claim boundary keeps its range visible without claiming in range`() {
        val sites = listOf(site(meters = 19.0))
        val game = game(sites)
        assertEquals(TerritoryProximityRange.UNAVAILABLE, game.sites.single().proximity.range)
        assertEquals(setOf("A"), TerritoryRangeTracker().update(game, TerritoryNearbyState(sites = sites), location, true))
        assertFalse(game.canMark)
        assertFalse(game.canPhotograph)
    }

    @Test fun `only nearby candidates appear and all eligible nearby ranges coexist`() {
        val sites = listOf(site("A", 10.0), site("B", 40.0), site("viewport", 1.0))
        assertEquals(setOf("A", "B"), TerritoryRangeTracker().update(game(sites),
            TerritoryNearbyState(sites = sites.take(2)), location, true))
    }

    @Test fun `hidden paused disabled stale and missing candidate states clear range memory`() {
        val tracker = TerritoryRangeTracker()
        val sites = listOf(site(meters = 40.0))
        val game = game(sites)
        val nearby = TerritoryNearbyState(sites = sites)
        val resets = listOf<() -> Set<String>>(
            { tracker.update(game, nearby, location, false) },
            { tracker.update(game.copy(phase = TerritoryWalkPhase.PAUSED), nearby, location, true) },
            { tracker.update(game.copy(enabled = false), nearby, location, true) },
            { tracker.update(game, nearby, location.copy(trusted = false), true) },
            { tracker.update(game, TerritoryNearbyState(), location, true) },
            { tracker.update(game, nearby, location.copy(sample = null), true) },
            { tracker.update(game, nearby, location.copy(sample = location.sample!!.copy(accuracyMeters = Float.NaN)), true) },
        )
        resets.forEach { reset ->
            assertEquals(setOf("A"), tracker.update(game, nearby, location, true))
            assertTrue(reset().isEmpty())
            val farther = listOf(site(meters = 65.0))
            assertTrue(tracker.update(game(farther), TerritoryNearbyState(sites = farther), location, true).isEmpty())
        }
    }
}
