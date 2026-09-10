package com.daengs.app.place

import org.junit.Assert.*
import org.junit.Test

class PlaceCategorySelectionTest {
    @Test fun mixedParentsToggleWithoutLosingOtherKindsAndLastRemovalIsEmpty() {
        val cafe = PlaceCategorySelection.Kind(PlaceKind.CAFE)
        val mixed = cafe.toggleKinds(listOf(PlaceKind.HOTEL))!!
        assertEquals(listOf(PlaceKind.CAFE, PlaceKind.HOTEL), mixed.kinds)
        assertNull(mixed.parentPurpose)
        assertEquals(cafe, mixed.toggleKinds(listOf(PlaceKind.HOTEL)))
        assertEquals(PlaceCategorySelection.None, cafe.toggleKinds(listOf(PlaceKind.CAFE)))
        assertEquals(PlaceCategorySelection.None, PlaceCategorySelection.fromKinds(emptyList()))
    }

    @Test fun sixKindLimitRejectsWholeAdditionWithoutRemovingExistingSelection() {
        val kinds = PlacePurpose.CULTURE.kinds + PlacePurpose.DINING.kinds
        val selection = PlaceCategorySelection.fromKinds(kinds)
        assertNull(selection.toggleKinds(PlacePurpose.LODGING.kinds))
        assertEquals(kinds, selection.kinds)
        assertEquals(PlaceCategorySelection.Purpose(PlacePurpose.CULTURE), selection.toggleKinds(PlacePurpose.DINING.kinds))
    }

    @Test fun catalogMatchesDevPurposeIdsAndCoversEveryKindExactlyOnce() {
        assertEquals(listOf("healthcare", "pet_care", "shopping", "dining", "outing", "culture", "lodging"),
            PlacePurpose.entries.map { it.id })
        val kinds = PlacePurpose.entries.flatMap { it.kinds } + PlaceKind.ETC
        assertEquals(PlaceKind.entries.toSet(), kinds.toSet())
        assertEquals(kinds.size, kinds.distinct().size)
        assertEquals(listOf(PlaceKind.CAFE, PlaceKind.RESTAURANT), PlacePurpose.DINING.kinds)
        assertTrue(PlacePurpose.entries.all { it.kinds.size in 2..6 })
    }

    @Test fun allAndEtcHaveDifferentScopesAndEverySelectionRoundTrips() {
        val selections = listOf(PlaceCategorySelection.All) +
            PlacePurpose.entries.map(PlaceCategorySelection::Purpose) +
            PlaceKind.entries.map(PlaceCategorySelection::Kind)
        selections.forEach { assertEquals(it, PlaceCategorySelection.fromKinds(it.kinds.reversed())) }
        assertEquals(18, PlaceCategorySelection.All.kinds.size)
        assertEquals(listOf(PlaceKind.ETC), PlaceCategorySelection.Kind(PlaceKind.ETC).kinds)
        assertNull(PlaceCategorySelection.All.parentPurpose)
        assertNull(PlaceCategorySelection.Kind(PlaceKind.ETC).parentPurpose)
    }
}
