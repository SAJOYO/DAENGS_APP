package com.daengs.app.walk.sync

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.motion.MotionReason

/** Mirror only the frozen server's final interval ownership, not the legacy GPS estimator. */
internal object MeasurementObservedContract {
    const val POLICY = "motion-shadow-observed-v1:e5b752afa193582e31bf9ad75460023a2b628cb3344de6b658a7bb6aed73bba6"
    data class Projection(val runs: List<List<Int>>, val auxiliary: List<MeasurementObservedSection>)

    fun project(id: String, fixes: List<RecordedFix>, usable: Set<Int>,
        walking: Map<Pair<Int, Int>, Double>, reasons: Map<Int, Set<MotionReason>>): Projection {
        data class Edge(val a: RecordedFix, val b: RecordedFix, val use: MeasurementObservedUse?)
        val included = walking.keys.associate { it.first to it.second }
        val bySeq = fixes.associateBy { it.clientSeq }
        val edges = mutableListOf<Edge?>()
        var i = 0
        while (i < fixes.lastIndex) {
            val a = fixes[i]
            val to = included[a.clientSeq]
            if (to != null) {
                val b = bySeq.getValue(to)
                edges += Edge(a, b, null)
                // Backup client sequences are validated as consecutive before this projection.
                i = to
                continue
            }
            val b = fixes[i+1]
            val nanos = b.elapsedRealtimeNanos?.let { end -> a.elapsedRealtimeNanos?.let { end - it } }
            if (a.clientSeq !in usable || b.clientSeq !in usable || a.sourceEpoch != b.sourceEpoch ||
                a.clockEpochId != b.clockEpochId || nanos == null || nanos !in 1..20_000_000_000L ||
                GeoPoint(a.lat, a.lng).distanceTo(GeoPoint(b.lat, b.lng)) > 200.0) edges += null
            else {
                val excluded = (reasons[a.clientSeq].orEmpty() + reasons[b.clientSeq].orEmpty()).any {
                    it == MotionReason.HIGH_SPEED || it == MotionReason.REENTRY_PENDING }
                edges += Edge(a, b, if (excluded) MeasurementObservedUse.EXCLUDED else MeasurementObservedUse.UNRESOLVED)
            }
            i++
        }
        val runs = mutableListOf<MutableList<Int>>()
        val auxiliary = mutableListOf<MeasurementObservedSection>()
        var run: MutableList<Int>? = null
        var part = mutableListOf<RecordedFix>(); var use: MeasurementObservedUse? = null
        fun flush() {
            if (part.size >= 2) auxiliary += MeasurementObservedSection(
                "$id:observed:${use}:${part.first().clientSeq}-${part.last().clientSeq}", requireNotNull(use), part.toList())
            part = mutableListOf(); use = null
        }
        edges.forEach { edge ->
            if (edge == null) { run = null; flush() }
            else {
                if (run?.lastOrNull() != edge.a.clientSeq) {
                    run = mutableListOf(edge.a.clientSeq); runs += requireNotNull(run)
                }
                requireNotNull(run).add(edge.b.clientSeq)
                if (edge.use == null) flush()
                else {
                    if (use != edge.use || part.lastOrNull()?.clientSeq != edge.a.clientSeq) {
                        flush(); use = edge.use; part += edge.a
                    }
                    part += edge.b
                }
            }
        }
        flush()
        return Projection(runs, auxiliary)
    }
}
