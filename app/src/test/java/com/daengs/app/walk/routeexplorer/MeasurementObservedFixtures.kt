package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.motion.MotionReason
import com.daengs.app.walk.sync.MeasurementObservedContract

/** Walking, excluded, unresolved, a real gap, and re-entry in one source sequence. */
internal fun measuredObservedDetail(clockCorrection: Boolean = false, accuracy: Float = 1f,
    repeatedWallTimes: Boolean = false): WalkSessionDetail {
    val base = measuredSceneDetail()
    val fixes = (0..13).map { seq ->
        val millis = 10_000L + seq * 2_000L + if (seq >= 7) 30_000L else 0L
        base.observations.first().copy(clientSeq = seq, ingressSeq = seq.toLong(),
            atMillis = millis - if (seq < 7) 0L else if (repeatedWallTimes) 44_000L else if (clockCorrection) 120_000L else 0L,
            elapsedRealtimeNanos = millis * 1_000_000, accuracyM = accuracy, lng = 127.0 + seq * 12.0 / 88_000)
    }
    val groups = listOf(fixes.subList(0, 3), fixes.subList(11, 14))
    val routes = groups.mapIndexed { n, group -> WalkRouteSegment(n, group.mapIndexed { i, f ->
        WalkRoutePoint(GeoPoint(f.lat, f.lng), f.atMillis, f.accuracyM, f.elapsedRealtimeNanos!! / 1_000_000,
            n * 24.0 + i * 12.0, null, n, i, f.elapsedRealtimeNanos)
    }) }
    val projected = MeasurementObservedContract.project(base.measurement!!.id, fixes, fixes.map { it.clientSeq }.toSet(),
        groups.flatMap { it.zipWithNext().map { (a, b) -> (a.clientSeq to b.clientSeq) to 12.0 } }.toMap(),
        mapOf(3 to setOf(MotionReason.HIGH_SPEED), 9 to setOf(MotionReason.REENTRY_PENDING)))
    return base.copy(route = WalkSessionRoute(routes), observations = fixes, measurement = base.measurement.copy(
        observedRuns = projected.runs.map { seqs -> seqs.map { fixes[it] } }, auxiliarySections = projected.auxiliary,
        walkingSections = groups.mapIndexed { n, group -> MeasurementWalkingSection("walking-$n",
            group.map { it.measurementRef(base.summary.sessionId) }) },
        usableSources = fixes.map { it.measurementRef(base.summary.sessionId) }.toSet()))
}
