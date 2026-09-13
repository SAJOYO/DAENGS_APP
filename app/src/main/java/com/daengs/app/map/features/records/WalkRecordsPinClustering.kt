package com.daengs.app.map.features.records

import com.daengs.app.map.layout.*

/** Compatibility names for record callers; all spatial calculations use the common map policy. */
typealias RecordPinScreenPoint = MarkerPoint
typealias RecordPinScreenGroup = MarkerGroup

fun clusterRecordPins(points: List<RecordPinScreenPoint>, maximumDiameter: Double = 44.0) =
    clusterMapMarkers(points, maximumDiameter)

fun placeRecordPinBadges(groups: List<RecordPinScreenGroup>): List<RecordPinScreenPoint> =
    placeMapMarkers(groups.map { MarkerGlyph(it, MarkerFootprint(40.0, 40.0)) }).map { it.point }
