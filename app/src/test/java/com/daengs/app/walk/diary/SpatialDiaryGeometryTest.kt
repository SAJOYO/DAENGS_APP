package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialDiaryGeometryTest {
    @Test
    fun `matches all 28 backend hex-v1 golden vectors`() {
        val fixture = JSONObject(javaClass.getResource("/hex_grid_golden.json")!!.readText())
        assertEquals(SPATIAL_DIARY_GRID_VERSION, fixture.getString("grid_version"))
        val cases = fixture.getJSONArray("cases")
        assertEquals(28, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val cell = SpatialDiaryHexGrid.cellFor(
                point = GeoPoint(case.getDouble("lat"), case.getDouble("lng")),
                radiusU = case.getDouble("radius_u"),
            )
            assertEquals("golden case $index", case.getInt("q"), cell.q)
            assertEquals("golden case $index", case.getInt("r"), cell.r)
        }
    }

    @Test
    fun `restores a finite six-corner boundary that maps back to the same cell`() {
        val cell = SpatialDiaryCellId(832649, 375728)
        val center = SpatialDiaryHexGrid.center(cell, 8.0)
        val boundary = SpatialDiaryHexGrid.boundary(cell, 8.0)

        assertEquals(6, boundary.size)
        assertEquals(cell, SpatialDiaryHexGrid.cellFor(center, 8.0))
        assertTrue(boundary.all { it.latitude.isFinite() && it.longitude.isFinite() })
        assertEquals(6, boundary.distinct().size)
    }

    @Test
    fun `turns response cells into provider-independent polygons`() {
        val view = SpatialDiaryView.parse(
            JSONObject(javaClass.getResource("/spatial_diary_response.json")!!.readText()),
        )

        val polygons = view.cellPolygons()

        assertEquals(view.field.cells.size, polygons.size)
        assertEquals(SpatialDiaryCellId(832649, 375728), polygons.first().id)
        assertEquals(view.field.cells.first().value, polygons.first().value, 0.0)
        assertEquals(6, polygons.first().boundary.size)
    }

    @Test
    fun `rejects invalid projection radii`() {
        listOf(0.0, -1.0, Double.POSITIVE_INFINITY, Double.NaN).forEach { radius ->
            assertThrows(IllegalArgumentException::class.java) {
                SpatialDiaryHexGrid.center(SpatialDiaryCellId(0, 0), radius)
            }
        }
    }
}
