package com.daengs.app.map.layers.places

import com.daengs.app.location.GeoPoint

data class PlaceMarkerState(
    val id: String,
    val point: GeoPoint,
    val label: String,
    val selected: Boolean = false,
    /** Which marker icon to draw. Selection changes size and z-order, not the group. */
    val iconGroup: FacilityIconGroup = FacilityIconGroup.ETC,
)
