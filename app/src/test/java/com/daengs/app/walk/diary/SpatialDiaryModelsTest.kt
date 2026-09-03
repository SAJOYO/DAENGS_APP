package com.daengs.app.walk.diary

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpatialDiaryModelsTest {
    @Test
    fun `query writes the versioned server contract in canonical facet order`() {
        val query = SpatialDiaryQuery(
            petId = "11111111-1111-1111-1111-111111111111",
            since = LocalDate.parse("2026-09-01"),
            until = LocalDate.parse("2026-09-30"),
            contextFilters = listOf(
                DaylightFilter(setOf(SpatialDiaryDaylight.NIGHT)),
                PrecipitationFilter(
                    setOf(SpatialDiaryPrecipitation.SNOW, SpatialDiaryPrecipitation.RAIN),
                ),
            ),
            metric = SpatialDiaryMetric.WALK_UTILIZATION,
        )

        val json = query.toJson()
        val selector = json.getJSONObject("walk_selector")
        val facets = selector.getJSONArray("context_facets")

        assertEquals(1, json.getInt("view_version"))
        assertEquals("2026-09-01", selector.getString("since"))
        assertEquals("2026-09-30", selector.getString("until"))
        assertEquals("walk_utilization", json.getString("field_metric"))
        assertEquals("daylight", facets.getJSONObject(0).getString("axis"))
        assertEquals("precipitation", facets.getJSONObject(1).getString("axis"))
        assertEquals(2, facets.getJSONObject(1).getInt("policy_version"))
        assertEquals(
            listOf("rain", "snow"),
            facets.getJSONObject(1).getJSONArray("values").let { values ->
                (0 until values.length()).map(values::getString)
            },
        )
    }

    @Test
    fun `parses the complete spatial diary response without losing its denominator`() {
        val view = SpatialDiaryView.parse(JSONObject(fixture()))

        assertEquals(SpatialDiaryMetric.VISIT_RATE, view.field.metric)
        assertEquals(3.0, view.field.denominator, 0.0)
        assertEquals(2, view.field.cells.size)
        assertEquals(2.0, view.field.cells.first().numerator, 0.0)
        assertEquals(3, view.receipt.selectedCapsules)
        assertEquals(1, view.receipt.contextUnknownCount)
        assertEquals("paint-v1", view.projection.paintFingerprint)
        assertTrue(view.query.contextFilters.first() is DaylightFilter)
        assertTrue(view.query.contextFilters.last() is PrecipitationFilter)
    }

    @Test
    fun `parses shuffled response facets into canonical order`() {
        val json = JSONObject(fixture())
        val facets = json
            .getJSONObject("spec")
            .getJSONObject("walk_selector")
            .getJSONArray("context_facets")
        val daylight = facets.getJSONObject(0)
        val precipitation = facets.getJSONObject(1)
        facets.put(0, precipitation)
        facets.put(1, daylight)

        val query = SpatialDiaryView.parse(json).query

        assertTrue(query.contextFilters.first() is DaylightFilter)
        assertTrue(query.contextFilters.last() is PrecipitationFilter)
    }

    @Test
    fun `rejects a grid generation the app cannot draw`() {
        val json = JSONObject(fixture())
        json.getJSONObject("projection").put("grid_version", "hex-v2")

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SpatialDiaryView.parse(json)
        }

        assertTrue(failure.message!!.contains("hex-v2"))
    }

    @Test
    fun `rejects a field denominator that disagrees with its receipt`() {
        val json = JSONObject(fixture())
        json.getJSONObject("field").put("denominator", 2.0)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SpatialDiaryView.parse(json)
        }

        assertTrue(failure.message!!.contains("분모"))
    }

    @Test
    fun `rejects ranges beyond the synchronous server limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpatialDiaryQuery(
                petId = "pet",
                since = LocalDate.parse("2025-01-01"),
                until = LocalDate.parse("2026-01-02"),
            )
        }
    }

    private fun fixture(): String = javaClass.getResource("/spatial_diary_response.json")!!.readText()
}
