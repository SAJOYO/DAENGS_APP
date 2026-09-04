package com.daengs.app.map.features.places

import com.daengs.app.place.PlaceKind
import org.junit.Assert.*
import org.junit.Test

class PlaceCategoryMenuTest {
    @Test fun `every possible selected kind remains in the seven shortcuts without duplicates`() {
        PlaceKind.entries.forEach { selected ->
            val visible = visiblePlaceCategories(selected).map { it.kind }
            assertEquals(7, visible.size)
            assertEquals(7, visible.distinct().size)
            assertTrue(selected in visible)
            assertTrue(visible.all { it in PlaceKind.entries })
        }
    }

    @Test fun `all canonical categories remain available without invented park or all kind`() {
        assertEquals(PlaceKind.entries.toSet(), PLACE_CATEGORIES.map { it.kind }.toSet())
        assertFalse(PLACE_CATEGORIES.any { it.label == "공원" || it.label == "전체" })
    }
}
