package com.daengs.app.map.features.territory

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.territory.TerritoryProximityRange
import com.daengs.app.territory.TerritorySite
import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Test

class TerritoryLocationEvidenceTest {
    private val sample = LocationSample(GeoPoint(37.5, 127.0), 1000, 1_000_000_000L, 3f)
    private val site = TerritorySite("A", sample.point, 999.0)
    private val tracking = WalkTrackingState(activeSessionId = "walk", latestMomentFix = sample,
        lastSample = sample, trail = TrailSnapshot(state = TrackingState.RECORDING))
    private fun read(state: WalkTrackingState = tracking, permitted: Boolean = true,
                     now: Long = 2_000_000_000L, screen: LocationSample? = sample) =
        territoryLocationEvidence(state, permitted, now, 10_000_000_000L, screen).proximity(site, 20.0)

    @Test fun `screen sample works without a session or participants and never uses query distance`() {
        assertEquals(0.0, read(WalkTrackingState()).distanceMeters!!, 0.0001)
        assertEquals(TerritoryProximityRange.IN_RANGE, read(WalkTrackingState()).range)
        val paused = tracking.copy(trail = TrailSnapshot(state = TrackingState.PAUSED), latestMomentFix = null)
        assertEquals(TerritoryProximityRange.IN_RANGE, read(paused).range)
    }

    @Test fun `recording never borrows the good screen fix to replace missing rejected or mock evidence`() {
        for (state in listOf(
            tracking.copy(latestMomentFix = null),
            tracking.copy(latestMomentFix = sample.copy(isMock = true)),
            tracking.copy(lastSample = sample.copy(isMock = true)),
            tracking.copy(trail = tracking.trail.copy(skippedTooFast = 1)),
            tracking.copy(trail = tracking.trail.copy(skippedLowAccuracy = 1)),
            tracking.copy(errorMessage = "location unavailable"),
        )) assertEquals(TerritoryProximityRange.UNAVAILABLE, read(state).range)
    }

    @Test fun `permission freshness and valid coordinates apply to both screen and walk fixes`() {
        assertEquals(TerritoryProximityRange.UNAVAILABLE, read(permitted = false).range)
        assertEquals(TerritoryProximityRange.IN_RANGE, read(now = 11_000_000_000L).range)
        assertEquals(TerritoryProximityRange.UNAVAILABLE, read(now = 11_000_000_001L).range)
        assertEquals(TerritoryProximityRange.UNAVAILABLE, read(now = 999_999_999L).range)
        for (bad in listOf(sample.copy(isMock = true), sample.copy(elapsedRealtimeNanos = null),
            sample.copy(point = GeoPoint(91.0, 127.0)), sample.copy(point = GeoPoint(Double.NaN, 127.0)),
            sample.copy(accuracyMeters = -1f))) {
            assertEquals(TerritoryProximityRange.UNAVAILABLE, read(WalkTrackingState(), screen = bad).range)
            assertEquals(TerritoryProximityRange.UNAVAILABLE, read(tracking.copy(latestMomentFix = bad)).range)
        }
    }
}
