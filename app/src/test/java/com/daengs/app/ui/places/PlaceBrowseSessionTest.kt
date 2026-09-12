package com.daengs.app.ui.places

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class PlaceBrowseSessionTest {
    @Test fun explorationExclusionsStillNarrowAnOtherwiseUnfilteredSavedList() {
        val excluded = setOf(PlaceKey("kcisa", "a"))
        val filters = PlaceBrowseFilters(excludedKeys = excluded).allBookmarks()
        assertEquals(excluded, filters.excludedKeys)
        assertTrue(filters.narrowsBookmarks)
    }

    private val filters = PlaceBrowseFilters(kinds = setOf(PlaceKind.CAFE, PlaceKind.RESTAURANT), name = "정원",
        origin = GeoPoint(37.54, 127.05), dogIds = setOf("bori"), parkingFirst = true,
        requiredConditions = buildJsonObject { put("parking", true) })
    private val search = PlaceBrowseSnapshot(filters, draft = "아직 제출하지 않은 이름",
        selected = PlaceKey("source", "cafe"), camera = MapCameraSnapshot(GeoPoint(37.55, 127.07), 15.0))

    @Test fun enteringBookmarksCopiesAppliedConditionsAndPreservesUnsubmittedSearchDraft() {
        val state = PlaceBrowseSession(search).select(PlaceBrowseTab.BOOKMARKS)
        assertEquals(filters, state.current.filters)
        assertEquals("정원", state.current.draft)
        assertEquals(search.camera, state.current.camera)
        assertNull(state.current.selected)
        assertEquals(search, state.select(PlaceBrowseTab.SEARCH).current)
    }

    @Test fun clearingSavedConditionsKeepsDogsAndLeavesOriginalSearchUntouched() {
        val state = PlaceBrowseSession(search).showAllBookmarks()
        assertEquals(setOf("bori"), state.current.filters.dogIds)
        assertFalse(state.current.filters.narrowsBookmarks)
        assertFalse(state.current.filters.parkingFirst)
        assertNull(state.current.filters.origin)
        assertNull(state.current.camera)
        assertEquals("", state.current.draft)
        assertEquals(search, state.search)
    }

    @Test fun eachTabRetainsItsOwnCameraAndFiltersOnReturn() {
        val camera = MapCameraSnapshot(GeoPoint(38.07, 128.62), 12.0)
        val saved = PlaceBrowseSession(search).showAllBookmarks().updateCurrent { it.copy(camera = camera) }
        val returned = saved.select(PlaceBrowseTab.SEARCH).select(PlaceBrowseTab.BOOKMARKS)
        assertEquals(camera, returned.current.camera)
        assertFalse(returned.current.filters.narrowsBookmarks)
        assertEquals(search, returned.search)
    }

    @Test fun changedSearchKeepsSavedConditionsUntilExplicitCopy() {
        val changed = PlaceBrowseSession(search).showAllBookmarks().select(PlaceBrowseTab.SEARCH)
            .updateCurrent { it.copy(filters = it.filters.copy(kinds = setOf(PlaceKind.HOSPITAL), name = "")) }
        assertFalse(changed.select(PlaceBrowseTab.BOOKMARKS).current.filters.narrowsBookmarks)
        assertEquals(changed.search.filters, changed.copySearchToBookmarks().current.filters)
        assertEquals(changed, changed.select(PlaceBrowseTab.SEARCH))
    }
}
