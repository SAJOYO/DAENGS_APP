package com.daengs.app.walk.support

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.pin.ActionPinObservation
import com.daengs.app.walk.pin.ActionPinRequest
import java.util.UUID

internal const val PIN_TAP = 1_700_000_100_000L
internal fun pinRequest() = ActionPinRequest(UUID.fromString("00000000-0000-4000-8000-000000000001"), "owner", "walk", 0, PIN_TAP)
internal fun pinPoint(east: Double, north: Double = 0.0) = GeoPoint(
    Math.toDegrees(north / 6_371_008.8), Math.toDegrees(east / 6_371_008.8),
)
internal fun pinFix(seconds: Int, east: Double, north: Double = 0.0) = ActionPinObservation(
    "owner", "walk", seconds + 100, 0, PIN_TAP + seconds * 1_000L, pinPoint(east, north), 5f,
)
