package com.daengs.app.ui.places

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import kotlinx.serialization.json.JsonObject

enum class PlaceBrowseTab { SEARCH, BOOKMARKS }

/** UI snapshot only. A future source must query all bookmarks, not filter a capped search page. */
data class PlaceBrowseFilters(
    val kinds: Set<PlaceKind> = setOf(PlaceKind.CAFE),
    val name: String = "",
    val origin: GeoPoint? = null,
    val radiusMeters: Int? = 3_000,
    val dogIds: Set<String> = emptySet(),
    val parkingFirst: Boolean = false,
    val requiredConditions: JsonObject? = null,
) {
    val narrowsBookmarks: Boolean
        get() = kinds.isNotEmpty() || name.isNotBlank() || radiusMeters != null || requiredConditions != null

    fun allBookmarks() = copy(kinds = emptySet(), name = "", origin = null,
        radiusMeters = null, parkingFirst = false, requiredConditions = null)
}

data class PlaceBrowseSnapshot(
    val filters: PlaceBrowseFilters = PlaceBrowseFilters(),
    val draft: String = filters.name,
    val selected: PlaceKey? = null,
    val detail: PlaceKey? = null,
    val camera: MapCameraSnapshot? = null,
)

/** Tab changes keep each view's edits and camera. New search conditions reseed bookmark filters. */
data class PlaceBrowseSession(
    val search: PlaceBrowseSnapshot = PlaceBrowseSnapshot(),
    val bookmarks: PlaceBrowseSnapshot? = null,
    val tab: PlaceBrowseTab = PlaceBrowseTab.SEARCH,
    private val bookmarkSeed: PlaceBrowseFilters? = null,
) {
    val current: PlaceBrowseSnapshot get() = if (tab == PlaceBrowseTab.SEARCH) search else requireNotNull(bookmarks)

    fun select(next: PlaceBrowseTab): PlaceBrowseSession {
        if (next == tab) return this
        if (next == PlaceBrowseTab.SEARCH) return copy(tab = next)
        val initial = if (bookmarks == null || bookmarkSeed != search.filters)
            search.copy(draft = search.filters.name, selected = null, detail = null) else bookmarks
        return copy(tab = next, bookmarks = initial, bookmarkSeed = search.filters)
    }

    fun updateCurrent(update: (PlaceBrowseSnapshot) -> PlaceBrowseSnapshot): PlaceBrowseSession =
        if (tab == PlaceBrowseTab.SEARCH) copy(search = update(search))
        else copy(bookmarks = update(requireNotNull(bookmarks)))

    fun showAllBookmarks(): PlaceBrowseSession = select(PlaceBrowseTab.BOOKMARKS).updateCurrent {
        it.copy(filters = it.filters.allBookmarks(), draft = "", selected = null, detail = null, camera = null)
    }
}
