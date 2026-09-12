package com.daengs.app.walk

import com.daengs.app.location.LocationSample
import java.util.IdentityHashMap

/**
 * Evidence of one legacy read, not a new GPS policy or a MeasurementSnapshot. The read basis is
 * the exact summary instance, full source values and route produced together. Copies with changed
 * inputs/routes cannot borrow it. Only the completed reader retains this O(n) index.
 */
class LegacyRouteEvidence private constructor(
    private val summary: WalkSummary,
    private val route: WalkSessionRoute,
    private val observations: List<RecordedFix>,
    private val decisions: Map<Int, SourceDecision>,
    private val omittedEdges: Map<Int, LegacySourceEdge>,
    private val safeEdges: Map<Pair<Int, Int>, LegacySourceEdge>,
) {
    internal val readerVersion = "legacy-trail-scene-evidence-v1"

    internal fun matches(detail: WalkSessionDetail): Boolean =
        detail.summary === summary && detail.route == route && detail.observations == observations

    internal fun disposition(seq: Int): TrailDisposition? = decisions[seq]?.disposition
    internal fun omittedEdge(seq: Int): LegacySourceEdge? = omittedEdges[seq]
    internal fun supports(a: WalkRoutePoint, b: WalkRoutePoint): Boolean =
        safeEdges[b.segmentIndex to b.pointIndex]?.let { it.before == a && it.after == b } == true

    internal class Collector {
        private val sources = IdentityHashMap<LocationSample, RecordedFix>()
        private val decisions = mutableListOf<SourceDecision>()

        fun source(sample: LocationSample, fix: RecordedFix) { sources[sample] = fix }

        fun record(decision: TrailDecision) {
            decisions += SourceDecision(sources.getValue(decision.sample), decision.disposition,
                decision.previousAccepted?.let(sources::getValue), decision.startsSegment)
        }

        fun finish(summary: WalkSummary, route: WalkSessionRoute, observations: List<RecordedFix>): LegacyRouteEvidence? {
            if (summary.measurementVersion != null) return null
            val ordered = observations.sortedBy { it.clientSeq }
            // A duplicate source address is ambiguous even if the coordinates happen to match.
            if (ordered.map { it.clientSeq }.distinct().size != ordered.size) return null
            val indices = ordered.withIndex().associate { it.value.clientSeq to it.index }
            val bySeq = decisions.associateBy { it.fix.clientSeq }
            val routeBySeq = buildMap {
                summary.segments.forEachIndexed { segment, samples -> samples.forEachIndexed { point, sample ->
                    sources[sample]?.let { put(it.clientSeq, route.segments[segment].points[point]) }
                } }
            }
            val omitted = mutableMapOf<Int, LegacySourceEdge>()
            val safe = mutableMapOf<Pair<Int, Int>, LegacySourceEdge>()
            for (decision in decisions) {
                if (decision.disposition != TrailDisposition.RETAINED || decision.startsSegment) continue
                val previous = decision.previous ?: continue
                val first = indices.getValue(previous.clientSeq)
                val last = indices.getValue(decision.fix.clientSeq)
                if (first >= last || !validPosition(previous)) continue
                // These disjoint accepted ranges are scanned once in total, not once per scene.
                val valid = (first + 1..last).all { index ->
                    val a = ordered[index - 1]
                    val b = ordered[index]
                    val assessed = bySeq[b.clientSeq]
                    validPosition(b) && adjacentInSource(a, b) &&
                        assessed != null && assessed.previous == previous &&
                        assessed.disposition == (if (index == last) TrailDisposition.RETAINED else TrailDisposition.BELOW_MIN_DISTANCE)
                }
                if (!valid) continue
                val a = routeBySeq[previous.clientSeq] ?: continue
                val b = routeBySeq[decision.fix.clientSeq] ?: continue
                if (a.segmentIndex != b.segmentIndex || a.pointIndex + 1 != b.pointIndex) continue
                val edge = LegacySourceEdge(previous.clientSeq, decision.fix.clientSeq, a, b)
                safe[b.segmentIndex to b.pointIndex] = edge
                for (index in first + 1 until last) omitted[ordered[index].clientSeq] = edge
            }
            return LegacyRouteEvidence(summary, route, observations.toList(), bySeq, omitted, safe)
        }
    }
}

internal data class LegacySourceEdge(
    val fromSeq: Int,
    val toSeq: Int,
    val before: WalkRoutePoint,
    val after: WalkRoutePoint,
)

private data class SourceDecision(
    val fix: RecordedFix,
    val disposition: TrailDisposition,
    val previous: RecordedFix?,
    val startsSegment: Boolean,
)

// Binding guards validate source continuity; they do not change recorder acceptance or totals.
private fun validPosition(fix: RecordedFix): Boolean = !fix.isMock && fix.recordingEligible != false &&
    fix.lat.isFinite() && fix.lat in -90.0..90.0 && fix.lng.isFinite() && fix.lng in -180.0..180.0 &&
    (fix.accuracyM == null || fix.accuracyM.isFinite() && fix.accuracyM >= 0f)

private fun adjacentInSource(a: RecordedFix, b: RecordedFix): Boolean =
    b.clientSeq.toLong() == a.clientSeq.toLong() + 1 && b.chainIndex == a.chainIndex &&
        b.sourceEpoch == a.sourceEpoch && b.clockEpochId == a.clockEpochId &&
        b.atMillis - a.atMillis in 1L..SCENE_OBSERVATION_WINDOW_MILLIS &&
        (a.elapsedRealtimeNanos == null && b.elapsedRealtimeNanos == null ||
            a.elapsedRealtimeNanos != null && b.elapsedRealtimeNanos != null && b.elapsedRealtimeNanos > a.elapsedRealtimeNanos)

/** Existing scene interpolation window; with provenance it applies to raw neighbors, not omissions. */
internal const val SCENE_OBSERVATION_WINDOW_MILLIS = 15_000L
