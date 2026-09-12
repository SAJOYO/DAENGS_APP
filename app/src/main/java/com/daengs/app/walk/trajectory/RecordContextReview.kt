package com.daengs.app.walk.trajectory

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.routeexplorer.RouteReplayFrame

internal enum class RecordContextKind { START, END, GAP, TRANSITION }
internal enum class RecordMovement { WALKING, EXCLUDED, UNRESOLVED, GAP }
internal data class ConfirmedRecordLocation(val seq: Int, val atMillis: Long, val point: GeoPoint)

/** A source span or a control event, never a route, accounting interval or invented event position. */
internal data class RecordContext(
    val id: String, val kind: RecordContextKind,
    val fromSeq: Int?, val toSeq: Int?, val fromMillis: Long, val toMillis: Long,
    val durationMillis: Long?, val before: ConfirmedRecordLocation?, val after: ConfirmedRecordLocation?,
    val beforeMovement: RecordMovement?, val afterMovement: RecordMovement?,
    val reasons: Set<ConnectionReason> = emptySet(), val walkingEndpoint: WalkRoutePoint? = null,
) {
    val locations get() = listOfNotNull(before?.point, after?.point, walkingEndpoint?.point).distinct()
}

/** Display-time temporal context. Original summary, walking owners and adopted auxiliary runs stay intact. */
internal class RecordContextReview(private val detail: WalkSessionDetail, observed: ObservedRouteReview) {
    val available = observed.matches(detail)
    private val assessment = observed.assessment
    private val raw = assessment.observations.map { it.fix }
    private val positions = assessment.observations.associate { it.fix.clientSeq to it.quality }
    private val bySeq = raw.associateBy { it.clientSeq }
    private val start = detail.summary.startedAtMillis
    private val end = detail.summary.endedAtMillis ?: start
    private val clockProblems = setOf(ConnectionReason.NON_INCREASING_TIME, ConnectionReason.UNTRUSTED_START_TIME,
        ConnectionReason.SOURCE_EPOCH_BOUNDARY, ConnectionReason.CLOCK_EPOCH_BOUNDARY, ConnectionReason.INCOMPLETE_CLOCK,
        ConnectionReason.UNSCOPED_MONOTONIC, ConnectionReason.CLOCK_DISAGREEMENT)
    private val chronological = assessment.intervals.none { interval -> interval.reasons.any { it in clockProblems } } &&
        raw.zipWithNext().all { (a, b) -> a.atMillis < b.atMillis }
    val durationMillis: Long? = if (available && chronological) positiveDifference(end, start) else null
    private val usable = raw.mapNotNull(::location)
    private val movements = assessment.intervals.map { interval -> when {
        interval.ownerToSeq != null -> RecordMovement.WALKING
        interval.auxiliaryUse == AuxiliaryUse.REVIEW_CANDIDATE && interval.walkingUse == LegacyWalkingUse.EXCLUDED -> RecordMovement.EXCLUDED
        interval.auxiliaryUse == AuxiliaryUse.REVIEW_CANDIDATE -> RecordMovement.UNRESOLVED
        else -> RecordMovement.GAP
    } }
    private fun location(fix: RecordedFix): ConfirmedRecordLocation? =
        if (positions[fix.clientSeq] == ObservationQuality.USABLE)
            ConfirmedRecordLocation(fix.clientSeq, fix.atMillis, GeoPoint(fix.lat, fix.lng)) else null
    private fun id(suffix: String) = "record-context-v1:${detail.summary.sessionId}:$suffix"

    val contexts: List<RecordContext> = if (!available) emptyList() else buildList {
        add(RecordContext(id("start"), RecordContextKind.START, null, usable.firstOrNull()?.seq, start, start, if (chronological) 0 else null,
            null, usable.firstOrNull(), null, null, walkingEndpoint = detail.route.start))
        var i = 0
        while (i < assessment.intervals.size) {
            val interval = assessment.intervals[i]
            if (movements[i] == RecordMovement.GAP) {
                val first = i
                while (i + 1 < movements.size && movements[i + 1] == RecordMovement.GAP) i++
                val last = assessment.intervals[i]
                val a = bySeq.getValue(interval.fromSeq); val b = bySeq.getValue(last.toSeq)
                val reasons = assessment.intervals.subList(first, i + 1).flatMap { it.reasons }.toSet()
                add(RecordContext(id("gap:${a.clientSeq}-${b.clientSeq}"), RecordContextKind.GAP,
                    a.clientSeq, b.clientSeq, a.atMillis, b.atMillis,
                    if (reasons.any { it in clockProblems }) null else positiveDifference(b.atMillis, a.atMillis),
                    location(a), location(b), movements.getOrNull(first - 1), movements.getOrNull(i + 1), reasons))
            } else if (interval.reasons.any { it in clockProblems }) {
                // A legacy owner may cross a bad clock boundary. Preserve its line and accounting,
                // but expose the unresolved temporal range without a gap guide or replay cursor.
                val a = bySeq.getValue(interval.fromSeq); val b = bySeq.getValue(interval.toSeq)
                add(RecordContext(id("clock:${a.clientSeq}-${b.clientSeq}"), RecordContextKind.TRANSITION,
                    a.clientSeq, b.clientSeq, a.atMillis, b.atMillis, null, location(a), location(b),
                    movements.getOrNull(i - 1), movements[i], interval.reasons.toSet()))
            } else if (i > 0 && movements[i - 1] != RecordMovement.GAP && movements[i - 1] != movements[i]) {
                val fix = bySeq.getValue(interval.fromSeq)
                add(RecordContext(id("transition:${fix.clientSeq}"), RecordContextKind.TRANSITION,
                    fix.clientSeq, fix.clientSeq, fix.atMillis, fix.atMillis, 0, location(fix), location(fix),
                    movements[i - 1], movements[i]))
            }
            i++
        }
        add(RecordContext(id("end"), RecordContextKind.END, usable.lastOrNull()?.seq, null, end, end, if (chronological) 0 else null,
            usable.lastOrNull(), null, null, null, walkingEndpoint = detail.route.end))
    }

    fun context(id: String) = contexts.firstOrNull { it.id == id }
    fun eventFor(scene: DiaryScene): RecordContext? {
        if (scene.sessionId != detail.summary.sessionId) return null
        val kind = when (scene.source?.id?.removePrefix("geo:")) {
            "start" -> RecordContextKind.START
            "end" -> RecordContextKind.END
            else -> return null
        }
        return contexts.firstOrNull { it.kind == kind && it.fromMillis == scene.atMillis }
    }
    fun gapAt(atMillis: Long): RecordContext? = if (!chronological) null else contexts.singleOrNull {
        it.kind == RecordContextKind.GAP && it.durationMillis != null && it.fromMillis < atMillis && atMillis < it.toMillis
    }
    fun temporalNeighbors(scene: DiaryScene): Pair<ConfirmedRecordLocation?, ConfirmedRecordLocation?>? {
        if (!available || !chronological || scene.sessionId != detail.summary.sessionId || scene.atMillis !in start..end) return null
        return usable.lastOrNull { it.atMillis < scene.atMillis } to usable.firstOrNull { it.atMillis > scene.atMillis }
    }

    /** Whole record time, including leading/trailing no-fix time. Guides are not an input. */
    fun frameAt(elapsedMillis: Long): RouteReplayFrame {
        val duration = durationMillis ?: return RouteReplayFrame(null, null, true)
        if (elapsedMillis !in 0..duration) return RouteReplayFrame(null, null, true)
        val at = start + elapsedMillis
        val gap = RouteReplayFrame(null, at, true)
        // Keep existing walking geometry, but only replay edges with verified source continuity.
        val walking = assessment.owners.filter { owner ->
            val edge = owner.edge
            edge.before.capturedAtMillis <= at && at <= edge.after.capturedAtMillis &&
                detail.legacyRouteEvidence?.supports(edge.before, edge.after) == true &&
                edge.after.capturedAtMillis - edge.before.capturedAtMillis in 1L..15_000L
        }.map { it.edge.before to it.edge.after }
        val auxiliary = assessment.intervals.filterIndexed { i, edge -> movements[i] == RecordMovement.EXCLUDED &&
            bySeq.getValue(edge.fromSeq).atMillis <= at && at <= bySeq.getValue(edge.toSeq).atMillis }
        if (walking.isNotEmpty() && auxiliary.isNotEmpty()) return gap
        val path = if (walking.isNotEmpty()) {
            val (a, b) = walking.first()
            // Multiple incident edges are allowed only at their shared exact observation.
            if (walking.size > 1 && walking.any { (x, y) -> x.capturedAtMillis != at && y.capturedAtMillis != at }) return gap
            TimedEdge(a.capturedAtMillis, b.capturedAtMillis, a.point, b.point)
        } else if (auxiliary.isNotEmpty()) {
            val edge = auxiliary.first(); val a = bySeq.getValue(edge.fromSeq); val b = bySeq.getValue(edge.toSeq)
            TimedEdge(a.atMillis, b.atMillis, GeoPoint(a.lat, a.lng), GeoPoint(b.lat, b.lng))
        } else return gap
        val t = (at - path.fromMillis).toDouble() / (path.toMillis - path.fromMillis)
        val deltaLng = ((path.b.longitude - path.a.longitude + 540) % 360) - 180
        return RouteReplayFrame(GeoPoint(path.a.latitude + (path.b.latitude - path.a.latitude) * t,
            ((path.a.longitude + deltaLng * t + 540) % 360) - 180), at, false)
    }
}

private data class TimedEdge(val fromMillis: Long, val toMillis: Long, val a: GeoPoint, val b: GeoPoint)
private fun positiveDifference(after: Long, before: Long): Long? =
    try { Math.subtractExact(after, before).takeIf { it >= 0 } } catch (_: ArithmeticException) { null }
