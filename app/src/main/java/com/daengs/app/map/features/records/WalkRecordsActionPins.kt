package com.daengs.app.map.features.records

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.records.*

val RECORD_ACTION_TYPES = setOf(WalkMomentType.SNIFFING, WalkMomentType.EXCRETION, WalkMomentType.BARKING)

data class WalkActionPinGroup(val point: GeoPoint, val records: List<WalkBehaviorRecord>) {
    val id = "record-actions:${point.latitude}:${point.longitude}"
    fun marker(selectedKey: String?) = MomentMarkerState(id, point,
        if (records.size == 1) records.single().entry.type.label else "액션 ${records.size}건",
        selected = records.any { it.key == selectedKey }, aboveRouteEndpoints = true,
        sequenceLabel = records.size.takeIf { it > 1 }?.toString(), behaviors = records.map { it.entry.type }.toSet())
}

data class WalkRecordsActionPins(val records: List<WalkBehaviorRecord>, val groups: List<WalkActionPinGroup>) {
    val unlocatedCount get() = records.count { it.point == null }
    val visibleCount get() = groups.sumOf { it.records.size }
}

/** Display-only action selection. It never creates a smaller walk selection or modifies density. */
fun walkRecordsActionPins(selection: WalkRecordsSelection, types: Set<WalkMomentType> = RECORD_ACTION_TYPES,
    hiddenIds: Set<String> = emptySet(), enabled: Boolean = true): WalkRecordsActionPins {
    require(types.all { it in RECORD_ACTION_TYPES })
    val records = selection.records.flatMap { walk -> walk.entries
        .filter { it.type in types && selection.query.includesEntryDog(it.petId) }
        .map { WalkBehaviorRecord(it, walk) }
    }.sortedWith(compareByDescending<WalkBehaviorRecord> { it.entry.recordedAtMillis }.thenBy { it.key })
    val groups = if (!enabled) emptyList() else records.filter {
        it.point != null && it.walk.summary.sessionId !in hiddenIds
    }.groupBy { requireNotNull(it.point) }.map { (point, entries) -> WalkActionPinGroup(point, entries) }
    return WalkRecordsActionPins(records, groups)
}
