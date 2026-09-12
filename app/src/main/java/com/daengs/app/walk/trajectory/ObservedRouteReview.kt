package com.daengs.app.walk.trajectory

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.*
import kotlin.math.*

/** Display adoption is explicit and narrower than #338's diagnostic default. No metric authority. */
internal object ObservedRouteDisplayPolicy {
    const val VERSION = "legacy-observed-display-v1"
    val connection = LegacyConnectionPolicy(maxVelocityChangeMps2 = 4.0)
    const val DIRECTION_DISPLACEMENT_MARGIN_METERS = 3.0
    const val DIRECTION_CONTEXT_MILLIS = 10_000L
}

internal data class ObservedDirectionEdge(val fromSeq: Int, val toSeq: Int, val from: GeoPoint, val to: GeoPoint,
    val evidenceFromSeq: Int, val evidenceToSeq: Int)

/** A read-scoped source range; slicing preserves id and the original edge addresses. */
internal data class ObservedRouteSection(val id: String, val walkingUse: LegacyWalkingUse,
    val fixes: List<RecordedFix>, val directions: List<ObservedDirectionEdge>) {
    val path get() = fixes.map { GeoPoint(it.lat, it.lng) }
    val startedAtMillis get() = fixes.first().atMillis
    val endedAtMillis get() = fixes.last().atMillis
}

internal class ObservedRouteReview(private val detail: WalkSessionDetail) {
    val assessment = evaluateLegacyObservationConnections(detail, ObservedRouteDisplayPolicy.connection)
    private val observationIndex = StoryboardObservationIndex(detail.summary, detail.observations)
    private val raw = detail.observations.associateBy { it.clientSeq }
    val sections = assessment.auxiliaryRuns.map { run ->
        val fixes = run.sourceSeqs.map(raw::getValue)
        ObservedRouteSection("${ObservedRouteDisplayPolicy.VERSION}-${detail.summary.sessionId}-${run.fromSeq}-${run.toSeq}",
            run.walkingUse, fixes, observedDirectionEdges(fixes))
    }

    fun matches(current: WalkSessionDetail) = assessment.matches(current)

    fun sceneFocus(scene: DiaryScene, entry: WalkEntry? = null): SceneRouteFocus? {
        val end = detail.summary.endedAtMillis ?: return null
        if (!matches(detail) || scene.sessionId != detail.summary.sessionId ||
            scene.atMillis !in detail.summary.startedAtMillis..end) return null
        val location = scene.point?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() } ?: return null
        val content = scene.content
        if (content?.positionState in setOf("provisional", "legacy")) return null
        if (scene.entryId != null && content == null) {
            if (entry == null || entry.id != scene.entryId || entry.sessionId != scene.sessionId ||
                entry.recordedAtMillis != scene.atMillis || entry.pin?.state in setOf("provisional", "legacy")) return null
            val pin = entry.pin?.point ?: entry.point ?: return null
            if (pin.distanceTo(location) > 1.0) return null
        }
        val anchor = scene.source?.observation
        val locationAt: Long
        val candidates: List<Pair<ObservedRouteSection, IntRange>>
        if (anchor != null) {
            val verified = observationIndex.resolve(anchor) ?: return null
            if (verified.distanceTo(location) > 1.0) return null
            // A source address is not permission to relabel a changed scene time. Only an explicit
            // start/end control event may reference its first/last confirmed observation as context.
            if (anchor.atMillis != scene.atMillis) {
                val id = scene.source.id.removePrefix("geo:")
                val endpoint = when {
                    id == "end" && scene.atMillis == end -> detail.observations.maxByOrNull { it.clientSeq }
                    id == "start" && scene.atMillis == detail.summary.startedAtMillis -> detail.observations.minByOrNull { it.clientSeq }
                    else -> null
                }
                if (endpoint?.clientSeq != anchor.clientSeq) return null
            }
            if (content?.locationAtMillis?.let { it != anchor.atMillis } == true) return null
            if (content?.locationMethod == "last_known" && anchor.atMillis == scene.atMillis) return null
            locationAt = anchor.atMillis
            candidates = sections.mapNotNull { section ->
                val index = section.fixes.indexOfFirst { it.clientSeq == anchor.clientSeq }
                if (index < 0) null else section to (index..index)
            }
        } else {
            // A photo/note may have independent location evidence. Narrow by event time BEFORE
            // matching position. Never pick a spatially nearby visit or interpolate across a gap.
            if (content?.locationMethod == "last_known" || content?.locationAtMillis?.let { it != scene.atMillis } == true ||
                entry?.pin?.method == "last_known" || entry != null && entry.pin == null && entry.locationCapturedAtMillis != scene.atMillis)
                return null
            locationAt = scene.atMillis
            candidates = sections.flatMap { section ->
                val exact = section.fixes.withIndex().filter { it.value.atMillis == locationAt }.map { section to (it.index..it.index) }
                exact + section.fixes.zipWithNext().mapIndexedNotNull { i, (a, b) ->
                    if (a.atMillis < locationAt && locationAt < b.atMillis) section to (i..i + 1) else null
                }
            }
            // Include competing walking visits in ambiguity checks even if their coordinates differ.
            if (detail.route.points.any { it.capturedAtMillis == locationAt } || detail.route.segments.any { segment ->
                    segment.points.zipWithNext().any { (a, b) -> a.capturedAtMillis < locationAt && locationAt < b.capturedAtMillis }
                }) return null
        }
        val (section, range) = candidates.singleOrNull() ?: return null
        if (anchor == null) {
            val a = section.fixes[range.first]; val b = section.fixes[range.last]
            val t = if (a == b) 0.0 else (locationAt - a.atMillis).toDouble() / (b.atMillis - a.atMillis)
            val deltaLng = ((b.lng - a.lng + 540) % 360) - 180
            val expected = GeoPoint(a.lat + (b.lat - a.lat) * t, ((a.lng + deltaLng * t + 540) % 360) - 180)
            if (expected.distanceTo(location) > 12.0) return null
        }
        var first = range.first; var last = range.last
        var distance = 0.0
        while (first > 0 && locationAt - section.fixes[first - 1].atMillis <= 10_000) {
            val a = section.fixes[first - 1]; val b = section.fixes[first]
            distance += GeoPoint(a.lat, a.lng).distanceTo(GeoPoint(b.lat, b.lng))
            if (distance > 40.0 && first < range.first) break
            first--
        }
        distance = 0.0
        while (last < section.fixes.lastIndex && section.fixes[last + 1].atMillis - locationAt <= 10_000) {
            val a = section.fixes[last]; val b = section.fixes[last + 1]
            distance += GeoPoint(a.lat, a.lng).distanceTo(GeoPoint(b.lat, b.lng))
            if (distance > 40.0 && last > range.last) break
            last++
        }
        val fixes = section.fixes.subList(first, last + 1)
        if (fixes.size < 2) return null
        val part = section.copy(fixes = fixes, directions = section.directions.filter {
            it.fromSeq >= fixes.first().clientSeq && it.toSeq <= fixes.last().clientSeq
        })
        return SceneRouteFocus(if (section.walkingUse == LegacyWalkingUse.EXCLUDED) SceneRouteRelation.OBSERVED_EXCLUDED
            else SceneRouteRelation.OBSERVED_UNRESOLVED, point = location, observedParts = listOf(part),
            binding = SceneRouteBinding(scene.atMillis, locationAt, anchor?.clientSeq ?: section.fixes[range.first].clientSeq,
                checkNotNull(detail.legacyRouteEvidence).readerVersion, fixes.first().clientSeq, fixes.last().clientSeq,
                displayPolicyVersion = ObservedRouteDisplayPolicy.VERSION))
    }
}

/** Input is one confirmed run. The guide follows original edges; context never adds a chord to the map. */
internal fun observedDirectionEdges(fixes: List<RecordedFix>): List<ObservedDirectionEdge> =
    fixes.zipWithNext().mapIndexedNotNull { i, (a, b) ->
        val from = GeoPoint(a.lat, a.lng); val to = GeoPoint(b.lat, b.lng)
        val margin = ObservedRouteDisplayPolicy.DIRECTION_DISPLACEMENT_MARGIN_METERS
        if (from.distanceTo(to) <= margin) return@mapIndexedNotNull null
        var first = i; var last = i + 1
        if (from.distanceTo(to) <= (a.accuracyM ?: return@mapIndexedNotNull null) +
            (b.accuracyM ?: return@mapIndexedNotNull null) + margin) {
            first = (i - 2).coerceAtLeast(0); last = (i + 3).coerceAtMost(fixes.lastIndex)
            if (fixes[last].atMillis - fixes[first].atMillis > ObservedRouteDisplayPolicy.DIRECTION_CONTEXT_MILLIS)
                return@mapIndexedNotNull null
            val start = GeoPoint(fixes[first].lat, fixes[first].lng); val end = GeoPoint(fixes[last].lat, fixes[last].lng)
            val allowance = (fixes[first].accuracyM ?: return@mapIndexedNotNull null) +
                (fixes[last].accuracyM ?: return@mapIndexedNotNull null) + margin
            if (start.distanceTo(end) <= allowance || cos(bearing(from, to) - bearing(start, end)) < .5)
                return@mapIndexedNotNull null
        }
        ObservedDirectionEdge(a.clientSeq, b.clientSeq, from, to, fixes[first].clientSeq, fixes[last].clientSeq)
    }

private fun bearing(a: GeoPoint, b: GeoPoint): Double {
    val latA = Math.toRadians(a.latitude); val latB = Math.toRadians(b.latitude)
    val delta = Math.toRadians(((b.longitude - a.longitude + 540) % 360) - 180)
    return atan2(sin(delta) * cos(latB), cos(latA) * sin(latB) - sin(latA) * cos(latB) * cos(delta))
}
