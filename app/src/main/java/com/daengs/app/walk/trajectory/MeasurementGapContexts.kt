package com.daengs.app.walk.trajectory

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*

/** Only gaps between confirmed source observations. No wall-time interpolation or invented path. */
internal fun measurementGapContexts(detail: WalkSessionDetail): List<RecordContext> {
    val measurement = requireNotNull(detail.measurement)
    val confirmed = detail.observations.filter { it.sourceEpoch != null && it.clockEpochId != null &&
        it.measurementRef(detail.summary.sessionId) in measurement.usableSources }
    val runs = measurement.observedRuns
    var runIndex = 0
    val connected = confirmed.zipWithNext().map { (a, b) ->
        while (runIndex < runs.size && runs[runIndex].last().clientSeq < b.clientSeq) runIndex++
        runs.getOrNull(runIndex)?.let { run -> run.first().clientSeq <= a.clientSeq &&
            b.clientSeq <= run.last().clientSeq && a.sourceEpoch == run.first().sourceEpoch &&
            b.sourceEpoch == a.sourceEpoch && a.clockEpochId == run.first().clockEpochId &&
            b.clockEpochId == a.clockEpochId } == true
    }
    fun location(f: RecordedFix) = ConfirmedRecordLocation(f.clientSeq, f.atMillis, GeoPoint(f.lat, f.lng))
    val rawIndex = detail.observations.withIndex().associate { it.value.clientSeq to it.index }
    return buildList {
        var i = 0
        while (i < connected.size) {
            if (connected[i]) { i++; continue }
            val first = i
            while (i + 1 < connected.size && !connected[i + 1]) i++
            val a = confirmed[first]; val b = confirmed[i + 1]
            val raw = detail.observations.subList(rawIndex.getValue(a.clientSeq), rawIndex.getValue(b.clientSeq) + 1)
            val trustedTime = raw.all { it.sourceEpoch == a.sourceEpoch && it.clockEpochId == a.clockEpochId &&
                it.elapsedRealtimeNanos != null } && raw.zipWithNext().all { (x, y) ->
                requireNotNull(y.elapsedRealtimeNanos) > requireNotNull(x.elapsedRealtimeNanos) }
            val duration = if (trustedTime) (requireNotNull(b.elapsedRealtimeNanos) -
                requireNotNull(a.elapsedRealtimeNanos)) / 1_000_000 else null
            val reasons = buildSet {
                if (a.sourceEpoch != b.sourceEpoch) add(ConnectionReason.SOURCE_EPOCH_BOUNDARY)
                if (a.clockEpochId != b.clockEpochId) add(ConnectionReason.CLOCK_EPOCH_BOUNDARY)
                if (!trustedTime) add(ConnectionReason.INCOMPLETE_CLOCK)
                if (duration != null && duration > 20_000) add(ConnectionReason.OBSERVATION_GAP)
            }
            add(RecordContext("record-context-v1:${detail.summary.sessionId}:${measurement.id}:gap:${a.clientSeq}-${b.clientSeq}",
                RecordContextKind.GAP, a.clientSeq, b.clientSeq, a.atMillis, b.atMillis, duration,
                location(a), location(b), null, null, reasons))
            i++
        }
    }
}
