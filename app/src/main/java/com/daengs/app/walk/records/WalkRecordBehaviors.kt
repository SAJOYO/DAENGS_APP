package com.daengs.app.walk.records

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType

/** A recorded action and its walk context; neither location nor a map trace is required. */
data class WalkBehaviorRecord(val entry: WalkEntry, val walk: WalkRecord) {
    init {
        require(entry.sessionId == walk.summary.sessionId && entry.id.isNotBlank())
        require(entry.type in BEHAVIOR_TYPES)
    }

    // Length prefixes keep (walk="a:b", entry="c") distinct from (walk="a", entry="b:c").
    val key: String = "${entry.sessionId.length}:${entry.sessionId}${entry.id.length}:${entry.id}"

    // An explicit pin owns display, including unlocated null; only legacy entries lack a pin.
    // Display never rewrites the original GPS content or invents a location for an unlocated pin.
    val point: GeoPoint? = (if (entry.pin != null) entry.pin.point else entry.point)?.takeIf {
        it.latitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude.isFinite() && it.longitude in -180.0..180.0
    }
    val locationLabel: String = entry.pin?.label
        ?: if (point != null) "위치와 함께 남긴 기록" else "위치 없이 남긴 행동"
}

/** An action query inside the unchanged baseline, separate from the comparison-report API. */
class WalkRecordBehaviors(
    val baseline: WalkRecordsSelection,
    val behavior: WalkMomentType,
    records: List<WalkBehaviorRecord>,
) {
    val records: List<WalkBehaviorRecord> = records.toList()
    val related: WalkRecordsSelection

    init {
        require(behavior in BEHAVIOR_TYPES)
        require(this.records.all { it.entry.type == behavior })
        require(this.records.map { it.key }.distinct().size == this.records.size)
        val relatedIds = this.records.map { it.walk.summary.sessionId }.toSet()
        require(baseline.sessionIds.containsAll(relatedIds))
        related = WalkRecordsSelection(baseline.query,
            baseline.records.filter { it.summary.sessionId in relatedIds })
    }
}

/** Dates already selected walks in S. Do not apply another date or location filter to entries. */
fun selectWalkRecordBehaviors(
    selection: WalkRecordsSelection,
    behavior: WalkMomentType,
): WalkRecordBehaviors {
    require(behavior in BEHAVIOR_TYPES)
    val records = selection.records.asSequence().flatMap { walk ->
        walk.entries.asSequence()
            .filter { it.type == behavior && selection.query.includesEntryDog(it.petId) }
            .map { WalkBehaviorRecord(it, walk) }
    }.sortedWith(compareByDescending<WalkBehaviorRecord> { it.entry.recordedAtMillis }
        .thenBy { it.key }).toList()
    return WalkRecordBehaviors(selection, behavior, records)
}

private val BEHAVIOR_TYPES = setOf(
    WalkMomentType.SNIFFING, WalkMomentType.EXCRETION, WalkMomentType.BARKING,
)
