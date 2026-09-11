package com.daengs.app.map.layers.territory

import com.daengs.app.location.GeoPoint

/** 시설 종류와 원본을 모르는, 지도에서 찾아갈 수 있는 중립 점령지 표시 상태. */
data class TerritorySiteMarkerState(
    val id: String,
    val point: GeoPoint,
    val selected: Boolean = false,
    val occupancy: TerritoryMarkerOccupancy = TerritoryMarkerOccupancy.NEUTRAL,
    val label: String = "점유 확인 전",
    val ready: Boolean = false,
    val radiusMeters: Double? = null,
    val feedback: TerritoryFeedback? = null,
    val occupancyKnown: Boolean = false,
    /** Physical range, independent of ready/claim/session state. */
    val proximity: com.daengs.app.territory.TerritoryProximityRange = com.daengs.app.territory.TerritoryProximityRange.UNAVAILABLE,
    /** Server member ownership, independent of the selected/active dog. */
    val isMine: Boolean = false,
)

enum class TerritoryMarkerOccupancy { NEUTRAL, UNVERIFIED, VERIFIED }
