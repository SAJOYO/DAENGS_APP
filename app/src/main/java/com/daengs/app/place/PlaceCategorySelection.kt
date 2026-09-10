package com.daengs.app.place

/** Geo/Dev PURPOSE_CATALOG의 ID와 업종 구성. 기타는 purpose가 아닌 별도 업종이다. */
enum class PlacePurpose(val id: String, val kinds: List<PlaceKind>) {
    HEALTHCARE("healthcare", listOf(PlaceKind.HOSPITAL, PlaceKind.PHARMACY)),
    PET_CARE("pet_care", listOf(PlaceKind.GROOMING, PlaceKind.BOARDING)),
    SHOPPING("shopping", listOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING)),
    DINING("dining", listOf(PlaceKind.CAFE, PlaceKind.RESTAURANT)),
    OUTING("outing", listOf(PlaceKind.TRAVEL, PlaceKind.LEISURE)),
    CULTURE("culture", listOf(PlaceKind.MUSEUM, PlaceKind.GALLERY, PlaceKind.ARTS_CENTER, PlaceKind.CULTURE)),
    LODGING("lodging", listOf(PlaceKind.PENSION, PlaceKind.HOTEL, PlaceKind.STAY)),
}

sealed interface PlaceCategorySelection {
    val kinds: List<PlaceKind>

    data object None : PlaceCategorySelection {
        override val kinds: List<PlaceKind> = emptyList()
    }

    data object All : PlaceCategorySelection {
        override val kinds: List<PlaceKind> = PlaceKind.entries
    }

    data class Purpose(val purpose: PlacePurpose) : PlaceCategorySelection {
        override val kinds: List<PlaceKind> get() = purpose.kinds
    }

    data class Kind(val kind: PlaceKind) : PlaceCategorySelection {
        override val kinds: List<PlaceKind> get() = listOf(kind)
    }

    data class Multiple(override val kinds: List<PlaceKind>) : PlaceCategorySelection {
        init { require(kinds.isNotEmpty() && kinds.distinct().size == kinds.size && kinds.size <= 6) }
    }

    val parentPurpose: PlacePurpose?
        get() = when (this) {
            All, None -> null
            is Purpose -> purpose
            is Kind -> PlacePurpose.entries.firstOrNull { kind in it.kinds }
            is Multiple -> PlacePurpose.entries.firstOrNull { it.kinds.containsAll(kinds) }
        }

    companion object {
        fun fromKind(kind: PlaceKind?): PlaceCategorySelection = kind?.let(::Kind) ?: All

        fun fromKinds(kinds: List<PlaceKind>): PlaceCategorySelection = when {
            kinds.isEmpty() -> None
            kinds.size == 1 -> Kind(kinds.single())
            kinds.toSet() == PlaceKind.entries.toSet() -> All
            else -> PlacePurpose.entries.singleOrNull { it.kinds.toSet() == kinds.toSet() }
                ?.let(::Purpose) ?: Multiple(kinds.distinct())
        }
    }
}

/** Browsing a purpose does not call this. Only tapping a leaf or its local All changes the query. */
fun PlaceCategorySelection.toggleKinds(items: List<PlaceKind>): PlaceCategorySelection? {
    val current = if (this == PlaceCategorySelection.All) emptyList() else kinds
    val remove = items.all { it in current }
    val next = if (remove) current.filterNot { it in items } else (current + items).distinct()
    return if (next.size > 6) null else PlaceCategorySelection.fromKinds(next)
}
