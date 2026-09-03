package com.daengs.app.ui.walk

import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.walk.WalkRoutePoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun shouldRefreshTerritoryFromDevice(
    purpose: MapPurpose,
    followDevice: Boolean,
): Boolean = purpose == MapPurpose.TERRITORY && followDevice

internal fun formatClock(millis: Long, seconds: Boolean = false): String =
    (if (seconds) CLOCK_SECONDS_FORMAT else CLOCK_FORMAT)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

internal val WalkRoutePoint.routePointKey: String get() = "$segmentIndex:$pointIndex"

internal const val ROUTE_POINT_TAP_RADIUS_METERS = 35.0

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
private val CLOCK_SECONDS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREAN)
