package com.daengs.app.territory

import com.daengs.app.location.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TerritorySiteModelsTest {
    @Test
    fun `neutral nearby response keeps only the public site contract`() {
        val page = Json.parseToJsonElement(
            """
            {
              "count": 1,
              "truncated": false,
              "sites": [
                {"site_id":"territory:v1:8a0","lat":37.5,"lng":127.0,"distance_m":42.75}
              ]
            }
            """.trimIndent(),
        ).jsonObject.toTerritorySitePage()

        assertEquals(1, page.count)
        assertFalse(page.truncated)
        assertEquals("territory:v1:8a0", page.sites.single().id)
        assertEquals(GeoPoint(37.5, 127.0), page.sites.single().point)
        assertEquals(42.75, page.sites.single().distanceMeters, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `response count must match returned sites`() {
        Json.parseToJsonElement(
            """{"count":2,"truncated":false,"sites":[]}""",
        ).jsonObject.toTerritorySitePage()
    }
}
