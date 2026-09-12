package com.daengs.app.walk.trajectory

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import kotlin.math.*

/** Finalized-input evaluation. Source order, not upload/chunk boundaries, defines every neighbor. */
internal fun evaluateLegacyObservationConnections(detail: WalkSessionDetail,
    policy: LegacyConnectionPolicy = LegacyConnectionPolicy()): LegacyConnectivityReview {
    val basis = detail.legacyRouteEvidence?.takeIf { it.matches(detail) }
    val raw = detail.observations.sortedBy { it.clientSeq }
    val positions = raw.map { assessPosition(it, policy) }
    val unsupported = when {
        detail.summary.endedAtMillis == null -> "unfinished_record"
        detail.summary.endedAtMillis < detail.summary.startedAtMillis -> "invalid_record_window"
        detail.summary.measurementVersion != null -> "not_a_legacy_read"
        raw.any { it.clientSeq < 0 || it.chainIndex < 0 } -> "invalid_source_address"
        raw.map { it.clientSeq }.distinct().size != raw.size -> "duplicate_source_address"
        basis == null -> "legacy_read_basis_unavailable"
        else -> null
    }
    if (unsupported != null) return LegacyConnectivityReview(null, policy, unsupported, positions,
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    val evidence = checkNotNull(basis)
    val indices = raw.withIndex().associate { it.value.clientSeq to it.index }
    val owners = arrayOfNulls<LegacyAcceptedInterval>((raw.size - 1).coerceAtLeast(0))
    for (owner in evidence.acceptedIntervals) {
        for (i in indices.getValue(owner.edge.fromSeq) until indices.getValue(owner.edge.toSeq)) {
            check(owners[i] == null) { "Overlapping legacy accounting owners" }
            owners[i] = owner
        }
    }
    val pairs = positions.zipWithNext { a, b -> assessPair(a, b, detail, policy) }
    val drafts = pairs.mapIndexed { i, pair ->
        // A fix with duplicate/reversed time cannot immediately become the next edge's anchor.
        // Reacquisition starts at the following chronological observation, without dropping the pin.
        if (i > 0 && ConnectionReason.NON_INCREASING_TIME in pairs[i - 1].reasons && pair.state == ObservationConnection.CONNECTED)
            pair.copy(state = ObservationConnection.UNRESOLVED, reasons = listOf(ConnectionReason.UNTRUSTED_START_TIME))
        else pair
    }
    val neighbors = mutableListOf<NeighborConsistency>()
    val inconsistent = mutableSetOf<Int>()
    for (i in 0 until drafts.lastIndex) {
        val a = drafts[i]; val b = drafts[i + 1]
        if (a.state != ObservationConnection.CONNECTED || b.state != ObservationConnection.CONNECTED) continue
        val ka = checkNotNull(a.kinematics); val kb = checkNotNull(b.kinematics)
        val change = hypot(a.eastMps - b.eastMps, a.northMps - b.northMps)
        val allowance = policy.maxVelocityChangeMps2 * (ka.seconds + kb.seconds) / 2 +
            ka.accuracyAllowanceMeters / ka.seconds + kb.accuracyAllowanceMeters / kb.seconds
        val consistent = change <= allowance
        neighbors += NeighborConsistency(raw[i].clientSeq, raw[i + 1].clientSeq, raw[i + 2].clientSeq,
            change, allowance, consistent)
        if (!consistent) { inconsistent += i; inconsistent += i + 1 }
    }
    val reasons = drafts.mapIndexed { i, d -> d.reasons.toMutableList().apply {
        if (i in inconsistent) add(ConnectionReason.NEIGHBOR_VELOCITY_CHANGE)
    } }
    val states = drafts.mapIndexed { i, d -> if (i in inconsistent) ObservationConnection.UNRESOLVED else d.state }.toMutableList()
    var first = 0
    while (first < states.size) {
        if (states[first] != ObservationConnection.CONNECTED) { first++; continue }
        var end = first + 1
        while (end < states.size && states[end] == ObservationConnection.CONNECTED) end++
        for (i in first until end) {
            if (end - first < policy.minConsistentEdges) {
                states[i] = ObservationConnection.UNRESOLVED
                reasons[i] += ConnectionReason.INSUFFICIENT_NEIGHBORS
            } else reasons[i] += ConnectionReason.CONSISTENT_NEIGHBORS
        }
        first = end
    }
    val rejected = setOf(TrailDisposition.TOO_FAST, TrailDisposition.LOW_ACCURACY, TrailDisposition.NOT_RECORDING)
    val intervals = drafts.mapIndexed { i, draft ->
        val a = raw[i]; val b = raw[i + 1]; val owner = owners[i]
        val accounting = mutableListOf<ConnectionReason>()
        val use = when {
            owner != null -> {
                accounting += ConnectionReason.LEGACY_OWNER_RANGE
                if (states[i] != ObservationConnection.CONNECTED) accounting += ConnectionReason.OWNER_CONNECTION_UNRESOLVED
                if (evidence.disposition(a.clientSeq) in rejected || evidence.disposition(b.clientSeq) in rejected)
                    accounting += ConnectionReason.OWNER_CONTAINS_REJECTED_OBSERVATION
                LegacyWalkingUse.INCLUDED
            }
            a.recordingEligible == false || b.recordingEligible == false -> {
                accounting += ConnectionReason.RECORDING_INELIGIBLE; LegacyWalkingUse.EXCLUDED
            }
            evidence.disposition(b.clientSeq) in rejected -> {
                accounting += ConnectionReason.LEGACY_REJECTED_ENDPOINT; LegacyWalkingUse.EXCLUDED
            }
            else -> { accounting += ConnectionReason.NO_ACCOUNTING_OWNER; LegacyWalkingUse.UNRESOLVED }
        }
        val auxiliary = when {
            owner != null && accounting.size > 1 -> AuxiliaryUse.OWNERSHIP_CONFLICT
            owner != null -> AuxiliaryUse.RESERVED_BY_WALKING
            states[i] == ObservationConnection.CONNECTED -> AuxiliaryUse.REVIEW_CANDIDATE
            else -> AuxiliaryUse.UNAVAILABLE
        }
        AssessedObservationInterval(a.clientSeq, b.clientSeq, states[i], draft.clock, reasons[i].toList(),
            draft.kinematics, use, owner?.edge?.toSeq, accounting.toList(), auxiliary)
    }
    val runs = mutableListOf<AuxiliaryObservationRun>()
    first = 0
    while (first < intervals.size) {
        val start = intervals[first]
        if (start.auxiliaryUse != AuxiliaryUse.REVIEW_CANDIDATE) { first++; continue }
        var end = first + 1
        while (end < intervals.size && intervals[end].auxiliaryUse == AuxiliaryUse.REVIEW_CANDIDATE &&
            intervals[end].walkingUse == start.walkingUse) end++
        runs += AuxiliaryObservationRun(start.fromSeq, intervals[end - 1].toSeq, start.walkingUse,
            raw.subList(first, end + 1).map { it.clientSeq })
        first = end
    }
    val transitions = intervals.zipWithNext().mapNotNull { (a, b) ->
        if (a.connection == b.connection && a.walkingUse == b.walkingUse && a.auxiliaryUse == b.auxiliaryUse) null
        else ConnectionTransition(b.fromSeq, a.connection, b.connection, a.walkingUse, b.walkingUse, a.auxiliaryUse, b.auxiliaryUse)
    }
    return LegacyConnectivityReview(evidence, policy, null, positions, evidence.acceptedIntervals, intervals,
        neighbors.toList(), runs.toList(), transitions)
}

private fun assessPosition(fix: RecordedFix, policy: LegacyConnectionPolicy): AssessedObservation {
    val reasons = mutableListOf<ConnectionReason>()
    if (!fix.lat.isFinite() || fix.lat !in -90.0..90.0 || !fix.lng.isFinite() || fix.lng !in -180.0..180.0)
        reasons += ConnectionReason.INVALID_COORDINATE
    if (fix.isMock) reasons += ConnectionReason.MOCK_LOCATION
    if (fix.accuracyM != null && (!fix.accuracyM.isFinite() || fix.accuracyM < 0)) reasons += ConnectionReason.INVALID_ACCURACY
    if (reasons.isNotEmpty()) return AssessedObservation(fix, ObservationQuality.INVALID, reasons.toList())
    if (fix.accuracyM == null) reasons += ConnectionReason.MISSING_ACCURACY
    else if (fix.accuracyM > policy.maxAccuracyMeters) reasons += ConnectionReason.LOW_ACCURACY
    return AssessedObservation(fix, if (reasons.isEmpty()) ObservationQuality.USABLE else ObservationQuality.UNCERTAIN, reasons.toList())
}

private data class PairDraft(val state: ObservationConnection, val clock: ObservationClock,
    val reasons: List<ConnectionReason>, val kinematics: ConnectionKinematics? = null,
    val eastMps: Double = 0.0, val northMps: Double = 0.0)

private fun assessPair(a: AssessedObservation, b: AssessedObservation, detail: WalkSessionDetail,
    policy: LegacyConnectionPolicy): PairDraft {
    val x = a.fix; val y = b.fix
    val broken = mutableListOf<ConnectionReason>()
    val unknown = mutableListOf<ConnectionReason>()
    if (x.chainIndex != y.chainIndex) broken += ConnectionReason.CHAIN_BOUNDARY
    if (x.sourceEpoch != y.sourceEpoch) broken += ConnectionReason.SOURCE_EPOCH_BOUNDARY
    if (x.clockEpochId != y.clockEpochId) broken += ConnectionReason.CLOCK_EPOCH_BOUNDARY
    if (x.recordingEligible == false || y.recordingEligible == false) broken += ConnectionReason.RECORDING_INELIGIBLE
    val window = detail.summary.startedAtMillis..checkNotNull(detail.summary.endedAtMillis)
    if (x.atMillis !in window || y.atMillis !in window) broken += ConnectionReason.OUTSIDE_RECORD_WINDOW
    if (a.quality == ObservationQuality.INVALID || b.quality == ObservationQuality.INVALID) broken += ConnectionReason.POSITION_INVALID
    if (a.quality == ObservationQuality.UNCERTAIN || b.quality == ObservationQuality.UNCERTAIN) unknown += ConnectionReason.POSITION_UNCERTAIN
    if (y.clientSeq.toLong() != x.clientSeq.toLong() + 1) unknown += ConnectionReason.MISSING_SEQUENCE
    val wallMillis = try { Math.subtractExact(y.atMillis, x.atMillis) } catch (_: ArithmeticException) { -1L }
    if (wallMillis <= 0) unknown += ConnectionReason.NON_INCREASING_TIME
    var seconds = wallMillis / 1000.0
    var clock = ObservationClock.LEGACY_WALL_TIME
    if (x.elapsedRealtimeNanos != null || y.elapsedRealtimeNanos != null) {
        clock = ObservationClock.UNRESOLVED
        if (x.elapsedRealtimeNanos == null || y.elapsedRealtimeNanos == null) unknown += ConnectionReason.INCOMPLETE_CLOCK
        else if (x.clockEpochId == null || y.clockEpochId == null) unknown += ConnectionReason.UNSCOPED_MONOTONIC
        else if (x.elapsedRealtimeNanos < 0 || y.elapsedRealtimeNanos <= x.elapsedRealtimeNanos) unknown += ConnectionReason.NON_INCREASING_TIME
        else if (x.clockEpochId == y.clockEpochId && x.sourceEpoch == y.sourceEpoch) {
            seconds = (y.elapsedRealtimeNanos - x.elapsedRealtimeNanos) / 1e9
            clock = ObservationClock.SCOPED_MONOTONIC
            if (abs(wallMillis - seconds * 1000) > policy.maxClockDifferenceMillis) unknown += ConnectionReason.CLOCK_DISAGREEMENT
        }
    }
    if (seconds * 1000 < policy.minIntervalMillis) unknown += ConnectionReason.INTERVAL_TOO_SHORT
    if (seconds * 1000 > policy.maxIntervalMillis) unknown += ConnectionReason.OBSERVATION_GAP
    if (broken.isNotEmpty() || unknown.isNotEmpty()) return PairDraft(
        if (broken.isNotEmpty()) ObservationConnection.BROKEN else ObservationConnection.UNRESOLVED,
        clock, (broken + unknown).distinct())
    val start = GeoPoint(x.lat, x.lng); val finish = GeoPoint(y.lat, y.lng)
    val distance = start.distanceTo(finish)
    if (!distance.isFinite()) return PairDraft(ObservationConnection.UNRESOLVED, clock, listOf(ConnectionReason.NONFINITE_DISPLACEMENT))
    val accuracy = checkNotNull(x.accuracyM).toDouble() + checkNotNull(y.accuracyM)
    val adjustedSpeed = (distance - accuracy).coerceAtLeast(0.0) / seconds
    val kinematics = ConnectionKinematics(seconds, distance, distance / seconds, accuracy, adjustedSpeed)
    if (adjustedSpeed > policy.maxAdjustedSpeedMps) return PairDraft(ObservationConnection.UNRESOLVED,
        clock, listOf(ConnectionReason.SPEED_BEYOND_REVIEW_LIMIT), kinematics)
    // Tangent direction of the geodesic, with normalized longitude difference at the date line.
    val latA = Math.toRadians(x.lat); val latB = Math.toRadians(y.lat)
    val delta = Math.toRadians(((y.lng - x.lng + 540) % 360) - 180)
    val bearing = atan2(sin(delta) * cos(latB), cos(latA) * sin(latB) - sin(latA) * cos(latB) * cos(delta))
    return PairDraft(ObservationConnection.CONNECTED, clock, emptyList(), kinematics,
        sin(bearing) * distance / seconds, cos(bearing) * distance / seconds)
}
