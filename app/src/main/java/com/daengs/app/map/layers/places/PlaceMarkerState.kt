package com.daengs.app.map.layers.places

import com.daengs.app.location.GeoPoint

data class PlaceMarkerState(
    val id: String,
    val point: GeoPoint,
    val label: String,
    val selected: Boolean = false,
    /** 어떤 마커 그림을 그릴지. 선택은 크기와 앞뒤만 바꾸고 묶음은 안 바꾼다. */
    val iconGroup: FacilityIconGroup = FacilityIconGroup.ETC,
)
