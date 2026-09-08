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

    data object All : PlaceCategorySelection {
        override val kinds: List<PlaceKind> = PlaceKind.entries
    }

    data class Purpose(val purpose: PlacePurpose) : PlaceCategorySelection {
        override val kinds: List<PlaceKind> get() = purpose.kinds
    }

    data class Kind(val kind: PlaceKind) : PlaceCategorySelection {
        override val kinds: List<PlaceKind> get() = listOf(kind)
    }

    val parentPurpose: PlacePurpose?
        get() = when (this) {
            All -> null
            is Purpose -> purpose
            is Kind -> PlacePurpose.entries.firstOrNull { kind in it.kinds }
        }

    companion object {
        fun fromKind(kind: PlaceKind?): PlaceCategorySelection = kind?.let(::Kind) ?: All

        fun fromKinds(kinds: List<PlaceKind>): PlaceCategorySelection = when {
            kinds.isEmpty() -> Kind(PlaceKind.CAFE) // 기존 첫 검색 기본값
            kinds.size == 1 -> Kind(kinds.single())
            kinds.toSet() == PlaceKind.entries.toSet() -> All
            else -> Purpose(PlacePurpose.entries.single { it.kinds.toSet() == kinds.toSet() })
        }
    }
}
