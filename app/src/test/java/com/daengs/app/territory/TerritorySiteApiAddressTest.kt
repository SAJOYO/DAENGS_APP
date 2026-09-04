package com.daengs.app.territory

import com.daengs.app.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class TerritorySiteApiAddressTest {
    @Test
    fun `nearby address carries the bounded map query`() {
        val address = territorySitesNearbyUrl(
            "https://example.test/",
            NearbyTerritorySitesRequest(
                origin = GeoPoint(37.5, 127.125),
                radiusMeters = 3_000,
                limit = 500,
            ),
        )

        assertEquals(
            "https://example.test/territory/sites/nearby" +
                "?lat=37.5&lng=127.125&radius_m=3000&limit=500",
            address,
        )
    }
}
