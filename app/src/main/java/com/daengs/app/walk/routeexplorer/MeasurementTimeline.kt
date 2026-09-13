package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.trajectory.*
import kotlin.math.abs

/** A recording-clock address, never a display index or a wall-clock guess. */
internal data class MeasurementTimeAddress(val sourceEpoch: String, val clockEpoch: String, val elapsedNanos: Long)
internal data class MeasurementTimeSlice(val from: Long, val until: Long,
    val walking: List<List<GeoPoint>>, val observed: List<ObservedRouteSection>)

/** Epochs concatenate recorded time. Pauses have no invented length and never acquire an edge. */
internal class MeasurementTimeline(private val detail: WalkSessionDetail, private val observed: ObservedRouteReview) {
    private val measurement = requireNotNull(detail.measurement)
    private data class Epoch(val value: RecordingEpoch, val offset: Long)
    private val epochs = buildList {
        var offset = 0L
        measurement.recordingEpochs.forEach { e ->
            require(e.sessionId == detail.summary.sessionId && e.endedElapsedNanos != null &&
                e.endedElapsedNanos >= e.startedElapsedNanos)
            add(Epoch(e, offset))
            offset = Math.addExact(offset, e.endedElapsedNanos - e.startedElapsedNanos)
        }
    }
    val durationMillis = epochs.lastOrNull()?.let { (it.offset + (requireNotNull(it.value.endedElapsedNanos) -
        it.value.startedElapsedNanos)) / 1_000_000 }
    private val byEpoch = epochs.associateBy { it.value.id }
    private val raw = detail.observations.associateBy { it.clientSeq }
    private val walking = measurement.walkingSections.map { section -> section.points.map { raw.getValue(requireNotNull(it.clientSeq)) } }
    private val replayRuns = walking + observed.sections.filter { it.walkingUse == LegacyWalkingUse.EXCLUDED }.map { it.fixes }
    private val replayEdges = replayRuns.flatMap { it.zipWithNext() }.groupBy { it.first.sourceEpoch }
        .mapValues { (_, edges) -> edges.sortedBy { it.first.elapsedRealtimeNanos } }
    private val exactFixes = detail.observations.filter { it.sourceEpoch != null && it.clockEpochId != null &&
        it.measurementRef(detail.summary.sessionId) in measurement.usableSources }
        .groupBy { Triple(it.sourceEpoch, it.clockEpochId, it.elapsedRealtimeNanos) }

    fun position(address: MeasurementTimeAddress): Long? {
        val epoch = byEpoch[address.sourceEpoch] ?: return null
        val e = epoch.value
        if (address.clockEpoch != e.clockEpochId || address.elapsedNanos !in e.startedElapsedNanos..requireNotNull(e.endedElapsedNanos)) return null
        return (epoch.offset + (address.elapsedNanos - e.startedElapsedNanos)) / 1_000_000
    }
    fun address(atMillis: Long): MeasurementTimeAddress? {
        val duration = durationMillis ?: return null
        if (atMillis !in 0..duration) return null
        val nanos = atMillis * 1_000_000
        val epoch = epochs.lastOrNull { it.offset <= nanos } ?: return null
        val e = epoch.value
        return MeasurementTimeAddress(e.id, e.clockEpochId,
            (e.startedElapsedNanos + (nanos - epoch.offset)).coerceAtMost(requireNotNull(e.endedElapsedNanos)))
    }
    private fun at(f: RecordedFix): Long? = if (f.sourceEpoch == null || f.clockEpochId == null || f.elapsedRealtimeNanos == null) null
        else position(MeasurementTimeAddress(f.sourceEpoch, f.clockEpochId, f.elapsedRealtimeNanos))
    fun sourcePosition(ref: MeasurementSourceRef): Long? {
        if (ref.sessionId != detail.summary.sessionId) return null
        if (ref.controlKind != null) {
            val e = byEpoch[ref.sourceEpoch]?.value ?: return null
            val nanos = when (ref.controlKind) { "epoch_start" -> e.startedElapsedNanos; "epoch_end" -> e.endedElapsedNanos; else -> null } ?: return null
            return position(MeasurementTimeAddress(e.id, ref.clockEpochId, nanos))
        }
        return raw[ref.clientSeq]?.takeIf { it.measurementRef(detail.summary.sessionId) == ref && ref in measurement.usableSources }?.let(::at)
    }
    fun scenePosition(focus: SceneRouteFocus): Long? {
        val binding = focus.binding ?: return null
        binding.eventSource?.let { return sourcePosition(it) }
        if (binding.eventAtMillis != binding.locationAtMillis) return null
        binding.locationSource?.let { return sourcePosition(it) }
        val a = raw[binding.sourceStart?.clientSeq] ?: return null
        val b = raw[binding.sourceEnd?.clientSeq] ?: return null
        val from = at(a) ?: return null; val to = at(b) ?: return null
        if (a.sourceEpoch != b.sourceEpoch || a.clockEpochId != b.clockEpochId ||
            b.atMillis <= a.atMillis || abs((b.atMillis - a.atMillis) - (to - from)) > 1_000 ||
            binding.eventAtMillis !in a.atMillis..b.atMillis) return null
        return from + ((to - from) * ((binding.eventAtMillis - a.atMillis).toDouble() / (b.atMillis - a.atMillis))).toLong()
    }
    private var lastSlice: MeasurementTimeSlice? = null
    fun slice(from: Long, until: Long): MeasurementTimeSlice? {
        lastSlice?.takeIf { it.from == from && it.until == until }?.let { return it }
        val duration = durationMillis ?: return null
        if (from < 0 || until <= from || until > duration) return null
        fun range(fixes: List<RecordedFix>): IntRange? {
            val edges = fixes.zipWithNext().indices.filter { i ->
                val a = at(fixes[i]); val b = at(fixes[i + 1])
                a != null && b != null && a < until && b > from
            }
            return if (edges.isEmpty()) null else edges.first()..edges.last() + 1
        }
        val paths = walking.mapNotNull { fixes -> range(fixes)?.let { fixes.slice(it).map { f -> GeoPoint(f.lat, f.lng) } } }
        val parts = observed.sections.mapNotNull { part -> range(part.fixes)?.let { indices ->
            val fixes = part.fixes.slice(indices)
            part.copy(fixes = fixes, directions = part.directions.filter {
                it.evidenceFromSeq >= fixes.first().clientSeq && it.evidenceToSeq <= fixes.last().clientSeq })
        } }
        return MeasurementTimeSlice(from, until, paths, parts).also { lastSlice = it }
    }
    fun frameAt(millis: Long): RouteReplayFrame {
        val address = address(millis) ?: return RouteReplayFrame(null, null, true)
        // Exact points are not invented, including an isolated usable observation.
        val exact = exactFixes[Triple(address.sourceEpoch, address.clockEpoch, address.elapsedNanos)]?.singleOrNull()
        exact?.let { return RouteReplayFrame(GeoPoint(it.lat, it.lng), it.atMillis, false) }
        val edges = replayEdges[address.sourceEpoch].orEmpty()
        val found = edges.binarySearch { requireNotNull(it.first.elapsedRealtimeNanos).compareTo(address.elapsedNanos) }
        val candidate = edges.getOrNull(if (found >= 0) found else -found - 2)
        candidate?.let { (a, b) ->
            if (a.sourceEpoch != address.sourceEpoch || b.sourceEpoch != address.sourceEpoch ||
                a.clockEpochId != address.clockEpoch || b.clockEpochId != address.clockEpoch) return@let
            val start = requireNotNull(a.elapsedRealtimeNanos); val end = requireNotNull(b.elapsedRealtimeNanos)
            if (address.elapsedNanos <= start || address.elapsedNanos >= end) return@let
            val t = (address.elapsedNanos - start).toDouble() / (end - start)
            val lng = ((b.lng - a.lng + 540) % 360) - 180
            val point = GeoPoint(a.lat + (b.lat - a.lat) * t, ((a.lng + lng * t + 540) % 360) - 180)
            val wall = if (abs((b.atMillis - a.atMillis) - (end - start) / 1_000_000) <= 1_000)
                a.atMillis + ((b.atMillis - a.atMillis) * t).toLong() else null
            return RouteReplayFrame(point, wall, false)
        }
        return RouteReplayFrame(null, null, true)
    }

    // Only continuous, proven movement supports automatic playback. Isolated usable
    // observations remain seekable with frameAt, but do not manufacture a replay edge.
    private val playableIntervals = run {
        val intervals = replayEdges.values.flatten().mapNotNull { (a, b) ->
            if (a.sourceEpoch != b.sourceEpoch || a.clockEpochId != b.clockEpochId) return@mapNotNull null
            val epoch = byEpoch[a.sourceEpoch] ?: return@mapNotNull null
            val nanos = epoch.offset + ((a.elapsedRealtimeNanos ?: return@mapNotNull null) - epoch.value.startedElapsedNanos)
            val from = nanos / 1_000_000 + if (nanos % 1_000_000 != 0L) 1 else 0
            val until = at(b) ?: return@mapNotNull null
            if (until <= from) null else from..until
        }.sortedBy { it.first }
        buildList<LongRange> {
            intervals.forEach { interval ->
                val last = lastOrNull()
                if (last != null && interval.first <= last.last) {
                    removeAt(lastIndex); add(last.first..maxOf(last.last, interval.last))
                } else add(interval)
            }
        }
    }

    /** First millisecond with proven movement ahead inside the range; never joins its missing part. */
    fun nextPlayablePosition(from: Long, until: Long): Long? {
        if (from < 0 || until < from || until > (durationMillis ?: return null)) return null
        val found = playableIntervals.binarySearch { it.last.compareTo(from) }
        val start = if (found >= 0) found else -found - 1
        for (i in start until playableIntervals.size) {
            val interval = playableIntervals[i]
            val candidate = maxOf(from, interval.first)
            val end = minOf(until, interval.last)
            if (candidate > until) break
            if (candidate < end && !frameAt(candidate).inGap) return candidate
            // A sub-millisecond source or epoch boundary may round just outside an edge.
            if (candidate + 1 < end && !frameAt(candidate + 1).inGap) return candidate + 1
        }
        return null
    }
}
