package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.StoryboardObservationIndex
import com.daengs.app.walk.trajectory.ObservedRouteReview
import com.daengs.app.walk.trajectory.ObservedRouteSection
import com.daengs.app.walk.trajectory.RecordContextReview

/** References belong to this loaded route only. Never persist segment/point indices as scene identity. */
internal data class CompletedRouteSection(val segment: WalkRouteSegment) {
    val index get() = segment.index
    val path = segment.points.map { it.point }
    val startedAtMillis get() = segment.points.first().capturedAtMillis
    val endedAtMillis get() = segment.points.last().capturedAtMillis
}

internal enum class SceneRouteRelation { CONNECTED, NO_ROUTE, UNLOCATED, EARLIER_LOCATION, AMBIGUOUS, OBSERVED_EXCLUDED, OBSERVED_UNRESOLVED }

/** Event and location addresses remain separate; source ranges belong only to this read. */
internal data class SceneRouteBinding(
    val eventAtMillis: Long,
    val locationAtMillis: Long,
    val observationSeq: Int,
    val readerVersion: String,
    val fromSeq: Int?,
    val toSeq: Int?,
    val displayPolicyVersion: String? = null,
    val sourceStart: MeasurementSourceRef? = null,
    val sourceEnd: MeasurementSourceRef? = null,
    val locationSource: MeasurementSourceRef? = null,
    val eventSource: MeasurementSourceRef? = null,
    val sectionId: String? = null,
)

/** A display-time correspondence, not a new walking assessment or a persisted SceneBinding. */
internal data class SceneRouteFocus(
    val relation: SceneRouteRelation,
    val paths: List<List<GeoPoint>> = emptyList(),
    val point: GeoPoint? = null,
    val binding: SceneRouteBinding? = null,
    val observedParts: List<ObservedRouteSection> = emptyList(),
    val key: SceneBindingKey? = null,
)

/**
 * Reads the path already selected by the session's stored policy. It never filters GPS again,
 * joins segments, invents a reason for exclusions, or sums display geometry into walking metrics.
 */
internal class CompletedRouteReview(val detail: WalkSessionDetail) {
    val summary = detail.summary
    val sections = detail.route.segments.filter { it.points.size >= 2 }.map(::CompletedRouteSection)
    private val points = detail.route.points
    private val byTime = points.groupBy { it.capturedAtMillis }
    private val observations = detail.observations.groupBy { it.clientSeq }
    private val observationIndex = StoryboardObservationIndex(summary, detail.observations)
    private val segments = detail.route.segments.associateBy { it.index }
    private val legacyEvidence = detail.legacyRouteEvidence?.takeIf { it.matches(detail) }
    val observed = ObservedRouteReview(detail)
    val context = RecordContextReview(detail, observed)
    val timeline = detail.measurement?.let { MeasurementTimeline(detail, observed) }
    private val measurementReview = detail.measurement?.let { MeasurementSceneReview(detail) }

    /** Combined record presentation. sceneFocus remains the unchanged walking-only correspondence. */
    fun recordSceneFocus(scene: DiaryScene, entry: WalkEntry? = null): SceneRouteFocus {
        measurementReview?.let { return it.focus(scene, entry) }
        if (scene.relational != null && scene.source?.observation == null)
            return SceneRouteFocus(SceneRouteRelation.NO_ROUTE, point = scene.point)
        val walking = sceneFocus(scene, entry)
        return if (walking.relation == SceneRouteRelation.CONNECTED) walking
            else observed.sceneFocus(scene, entry) ?: walking
    }

    fun sceneFocus(scene: DiaryScene, entry: WalkEntry? = null): SceneRouteFocus {
        measurementReview?.let { return it.focus(scene, entry) }
        if (scene.relational != null && scene.source?.observation == null)
            return SceneRouteFocus(SceneRouteRelation.NO_ROUTE, point = scene.point)
        fun unavailable(relation: SceneRouteRelation = SceneRouteRelation.NO_ROUTE) = SceneRouteFocus(relation)
        if (scene.sessionId != summary.sessionId || summary.endedAtMillis == null ||
            scene.atMillis !in summary.startedAtMillis..summary.endedAtMillis) return unavailable()
        val location = scene.point ?: return unavailable(SceneRouteRelation.UNLOCATED)
        if (scene.entryId != null && scene.content == null) {
            if (entry == null || entry.id != scene.entryId || entry.sessionId != scene.sessionId ||
                entry.recordedAtMillis != scene.atMillis) return unavailable()
            val pin = entry.pin
            if (pin?.method == "last_known" || pin == null && entry.locationCapturedAtMillis != scene.atMillis)
                return unavailable(SceneRouteRelation.EARLIER_LOCATION)
            if (pin?.state in setOf("provisional", "legacy")) return unavailable()
            val currentLocation = pin?.point ?: entry.point ?: return unavailable()
            if (currentLocation.distanceTo(location) > 1.0) return unavailable()
        }
        val content = scene.content
        if (content?.locationMethod == "last_known" ||
            content?.locationAtMillis?.let { it != scene.atMillis } == true)
            return unavailable(SceneRouteRelation.EARLIER_LOCATION)
        if (content?.positionState in setOf("provisional", "legacy")) return unavailable()

        val anchor = scene.source?.observation
        if (anchor != null) {
            val raw = observations[anchor.clientSeq]?.singleOrNull() ?: return unavailable()
            val verified = observationIndex.resolve(anchor) ?: return unavailable()
            if (scene.atMillis != anchor.atMillis || location.distanceTo(verified) > 1.0) return unavailable()
            // Follow recorder provenance before looking for a display vertex. A minimum-distance
            // omission can belong to an accepted edge; exclusions cannot borrow a matching vertex.
            val evidence = legacyEvidence
            val disposition = evidence?.disposition(raw.clientSeq)
            if (evidence != null && disposition != TrailDisposition.RETAINED) {
                val edge = evidence.omittedEdge(raw.clientSeq)?.takeIf {
                    // A long display interval can contain frequent, verified stationary fixes.
                    // The evidence checks every raw neighbor, so it is not an observation gap.
                    disposition == TrailDisposition.BELOW_MIN_DISTANCE
                }
                val result = if (edge == null) SceneRouteFocus(SceneRouteRelation.NO_ROUTE, point = location)
                    else focus(edge.before, edge.after, location, evidence::supports)
                return result.copy(binding = SceneRouteBinding(scene.atMillis, raw.atMillis, raw.clientSeq,
                    evidence.readerVersion, edge?.fromSeq, edge?.toSeq))
            }
            // An excluded observation cannot borrow a nearby accepted point or another visit.
            // Retain the monotonic clock too: wall time and position can repeat after a clock change.
            val matches = byTime[raw.atMillis].orEmpty().filter {
                it.point == GeoPoint(raw.lat, raw.lng) && (it.elapsedRealtimeNanos == raw.elapsedRealtimeNanos ||
                    summary.measurementVersion == null && it.elapsedRealtimeNanos == null)
            }
            if (matches.size > 1) return unavailable(SceneRouteRelation.AMBIGUOUS)
            val match = matches.singleOrNull() ?: return unavailable()
            // Without a monotonic clock, another raw observation at the same time/place is ambiguous.
            if (match.elapsedRealtimeNanos == null && detail.observations.count {
                    it.atMillis == raw.atMillis && it.lat == raw.lat && it.lng == raw.lng
                } != 1) return unavailable(SceneRouteRelation.AMBIGUOUS)
            return focus(match, match, match.point)
        }

        // Photos/notes have an event time but may lack a raw observation address. Narrow by time
        // first, then check the recorded location. Never use nearest-road matching across visits.
        val exact = byTime[scene.atMillis].orEmpty()
        val candidates = detail.route.segments.flatMap { segment -> segment.points.zipWithNext().filter { (a, b) ->
            a.capturedAtMillis < scene.atMillis && scene.atMillis < b.capturedAtMillis && locallyTimed(a, b)
        } }
        if (exact.size + candidates.size > 1) return unavailable(SceneRouteRelation.AMBIGUOUS)
        if (exact.size == 1) {
            val point = exact.single()
            return if (point.point.distanceTo(location) <= SCENE_LOCATION_TOLERANCE_METERS)
                focus(point, point, point.point) else unavailable()
        }
        val (a, b) = candidates.singleOrNull() ?: return unavailable()
        val fraction = (scene.atMillis - a.capturedAtMillis).toDouble() / (b.capturedAtMillis - a.capturedAtMillis)
        val point = GeoPoint(a.point.latitude + (b.point.latitude - a.point.latitude) * fraction,
            a.point.longitude + (b.point.longitude - a.point.longitude) * fraction)
        return if (point.distanceTo(location) <= SCENE_LOCATION_TOLERANCE_METERS) focus(a, b, point) else unavailable()
    }

    private fun focus(a: WalkRoutePoint, b: WalkRoutePoint, point: GeoPoint,
        supports: (WalkRoutePoint, WalkRoutePoint) -> Boolean = { _, _ -> true }): SceneRouteFocus {
        val samples = segments.getValue(a.segmentIndex).points
        var first = samples.indexOf(a)
        var last = samples.indexOf(b)
        val anchorFirst = first
        val anchorLast = last
        var distance = 0.0
        while (first > 0 && locallyTimed(samples[first - 1], samples[first]) &&
            supports(samples[first - 1], samples[first]) &&
            a.capturedAtMillis - samples[first - 1].capturedAtMillis <= 30_000) {
            distance += samples[first - 1].point.distanceTo(samples[first].point)
            if (distance > 40.0 && first < anchorFirst) break
            first--
        }
        distance = 0.0
        while (last < samples.lastIndex && locallyTimed(samples[last], samples[last + 1]) &&
            supports(samples[last], samples[last + 1]) &&
            samples[last + 1].capturedAtMillis - b.capturedAtMillis <= 30_000) {
            distance += samples[last].point.distanceTo(samples[last + 1].point)
            if (distance > 40.0 && last > anchorLast) break
            last++
        }
        // A single confirmed point is a location, not proof of an incident walking edge.
        val path = samples.subList(first, last + 1).map { it.point }
        return SceneRouteFocus(if (path.size >= 2) SceneRouteRelation.CONNECTED else SceneRouteRelation.NO_ROUTE,
            paths = if (path.size >= 2) listOf(path) else emptyList(), point = point)
    }
}

/** Presentation interpolation uses the existing explorer's conservative time window, not a GPS policy. */
private fun locallyTimed(a: WalkRoutePoint, b: WalkRoutePoint): Boolean =
    a.segmentIndex == b.segmentIndex && b.pointIndex == a.pointIndex + 1 &&
        b.capturedAtMillis - a.capturedAtMillis in 1L..SCENE_OBSERVATION_WINDOW_MILLIS &&
        b.activeElapsedMillis - a.activeElapsedMillis in 1L..SCENE_OBSERVATION_WINDOW_MILLIS

private const val SCENE_LOCATION_TOLERANCE_METERS = 12.0
