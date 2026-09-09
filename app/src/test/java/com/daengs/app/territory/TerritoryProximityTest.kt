package com.daengs.app.territory

import org.junit.Assert.*
import org.junit.Test

class TerritoryProximityTest {
    @Test fun `distance plus GPS error includes the boundary and preserves uncertain versus far`() {
        assertEquals(TerritoryProximityRange.IN_RANGE, evaluateTerritoryProximity(true, 15.0, 5.0, 20.0).range)
        assertEquals(TerritoryProximityRange.UNAVAILABLE, evaluateTerritoryProximity(true, 15.0, 6.0, 20.0).range)
        assertEquals(TerritoryProximityRange.APPROACHING, evaluateTerritoryProximity(true, 21.0, 3.0, 20.0).range)
        assertEquals(TerritoryProximityRange.IN_RANGE, evaluateTerritoryProximity(true, 7.0, 3.0, 10.0).range)
        assertEquals(TerritoryProximityRange.UNAVAILABLE, evaluateTerritoryProximity(true, 7.0, 3.01, 10.0).range)
    }

    @Test fun `untrusted and malformed measurements never become in range`() {
        assertEquals(TerritoryProximityRange.UNAVAILABLE, evaluateTerritoryProximity(false, 0.0, 0.0, 20.0).range)
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0)) {
            val distance = evaluateTerritoryProximity(true, invalid, 3.0, 20.0)
            assertEquals(TerritoryProximityRange.UNAVAILABLE, distance.range)
            assertNull(distance.distanceMeters)
            val accuracy = evaluateTerritoryProximity(true, 0.0, invalid, 10.0)
            assertEquals(TerritoryProximityRange.UNAVAILABLE, accuracy.range)
            assertNull(accuracy.accuracyMeters)
        }
    }

    @Test fun `physical range alone does not authorize a paused claim`() {
        assertEquals(TerritoryProximityRange.IN_RANGE, evaluateTerritoryProximity(true, 0.0, 3.0, 20.0).range)
        val access = evaluateClaimAccess("walk", "site", false, true, 0.0, 3.0, 20.0)
        assertEquals(ClaimAccess.UNAVAILABLE, access.access)
        assertEquals(ClaimAccessReason.NOT_RECORDING, access.reason)
    }
}
