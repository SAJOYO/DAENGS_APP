package com.daengs.app.map.features.records

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.moments.RecordPinAppearance
import com.daengs.app.walk.records.WalkRecord

/** Selection changes emphasis only: all source actions and silhouettes remain in each glyph. */
fun WalkRecordsActionPins.detachedMarkers(selectedKey: String?, inspectedKeys: List<String>) =
    groups.map { group ->
        val marker = group.marker(selectedKey)
        marker.copy(
            selected = marker.selected || (selectedKey == null && group.records.any { it.key in inspectedKeys }),
            recordPin = RecordPinAppearance(group.records.size),
        )
    }

/** Native clusters return coordinate-group IDs; expand them back to the original session/action keys. */
fun WalkRecordsActionPins.resolveGroup(markerIds: List<String>): WalkActionPinGroup? {
    val ids = markerIds.toSet()
    val members = groups.filter { it.id in ids }.flatMap { it.records }.distinctBy { it.key }
    return members.takeIf { it.isNotEmpty() }?.let { WalkActionPinGroup(requireNotNull(it.first().point), it) }
}

/** Preserve segment gaps; trace pixels and bounds cannot substitute for an observed route. */
fun recordPinAvoidancePaths(records: List<WalkRecord>, hiddenIds: Set<String>): List<List<GeoPoint>> =
    records.filterNot { it.summary.sessionId in hiddenIds }.flatMap { record ->
        record.summary.segments.map { segment -> segment.map { it.point } }.filter { it.size > 1 }
    }
