package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.StoryboardObservation
import kotlin.math.abs
import com.daengs.app.walk.trajectory.*

/** Source-owned binding for a verified measurement; never falls back to legacy/nearest geometry. */
internal class MeasurementSceneReview(private val detail: WalkSessionDetail) {
    private val measurement = requireNotNull(detail.measurement)
    private val sessionId = detail.summary.sessionId
    private val raw = detail.observations.groupBy { it.clientSeq }
    private data class Section(val source: MeasurementWalkingSection, val route: WalkRouteSegment, val auxiliary: ObservedRouteSection? = null)
    private val walkingSections = measurement.walkingSections.zip(detail.route.segments).map { Section(it.first, it.second) }
    private val sections = walkingSections + measurementObservedSections(detail).map { part ->
        Section(MeasurementWalkingSection(part.id, part.fixes.map { it.measurementRef(sessionId) }),
            WalkRouteSegment(-1, part.fixes.mapIndexed { i, f -> WalkRoutePoint(GeoPoint(f.lat, f.lng), f.atMillis,
                f.accuracyM, requireNotNull(f.elapsedRealtimeNanos) / 1_000_000, 0.0, null, -1, i, f.elapsedRealtimeNanos) }), part)
    }
    private fun fix(ref: MeasurementSourceRef): RecordedFix? = raw[ref.clientSeq]?.singleOrNull()?.takeIf {
        ref.sessionId == sessionId && ref.controlKind == null && ref.sourceEpoch == it.sourceEpoch &&
            ref.clockEpochId == it.clockEpochId && !it.isMock && it.recordingEligible == true &&
            it.elapsedRealtimeNanos != null && ref in measurement.usableSources
    }
    private val valid = measurement.ownerId.isNotBlank() && measurement.walkingSections.size == detail.route.segments.size &&
        measurement.walkingSections.map { it.id }.distinct().size == walkingSections.size &&
        walkingSections.all { section -> section.source.points.size == section.route.points.size && section.source.points.size >= 2 &&
            section.source.points.zip(section.route.points).all { (ref, point) -> fix(ref)?.let { f ->
                point.point == GeoPoint(f.lat, f.lng) && point.capturedAtMillis == f.atMillis &&
                    point.elapsedRealtimeNanos == f.elapsedRealtimeNanos
            } == true } && section.source.points.zipWithNext().all { (a, b) ->
                a.sourceEpoch == b.sourceEpoch && a.clockEpochId == b.clockEpochId &&
                    requireNotNull(a.clientSeq) < requireNotNull(b.clientSeq) &&
                    requireNotNull(fix(a)?.elapsedRealtimeNanos) < requireNotNull(fix(b)?.elapsedRealtimeNanos)
            }
        }

    fun focus(scene: DiaryScene, entry: WalkEntry?): SceneRouteFocus {
        val key = SceneBindingKey.of(measurement, scene, entry)
        var photoLocation: GeoPoint? = null
        fun result(relation: SceneRouteRelation = SceneRouteRelation.NO_ROUTE, point: GeoPoint? = photoLocation,
            binding: SceneRouteBinding? = null) = SceneRouteFocus(relation, point = point, binding = binding, key = key)
        if (!valid || scene.sessionId != sessionId || detail.summary.endedAtMillis == null) return result()
        val location = scene.point?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 } ?: return result(SceneRouteRelation.UNLOCATED)
        if (scene.source?.let { !it.available || it.hidden || it.atMillis != scene.atMillis } == true) return result()
        if (scene.entryId != null) {
            if (entry == null || entry.id != scene.entryId || entry.sessionId != sessionId || entry.recordedAtMillis != scene.atMillis) return result()
            if (entry.pin?.state in setOf("provisional", "legacy", "unlocated")) return result(SceneRouteRelation.UNLOCATED)
            val point = entry.pin?.point ?: entry.point ?: return result(SceneRouteRelation.UNLOCATED)
            if (point.distanceTo(location) > 1.0) return result()
        }
        scene.photo?.let { if (it.sessionId != sessionId || it.capturedAtMillis != scene.atMillis || !(it.point.distanceTo(location) <= 1.0)) return result() }
        val content = scene.content
        if (content?.positionState in setOf("provisional", "legacy", "unlocated")) return result(SceneRouteRelation.UNLOCATED)

        // A saved photo position is independent of whether a walking visit can be identified.
        // Keep it only after the current scene/photo receipt checks; never borrow a stale point.
        if (scene.photo != null && scene.entryId == null && scene.source?.observation == null) photoLocation = location

        val controlName = scene.source?.id?.removePrefix("geo:")?.takeIf {
            it in setOf("start", "end") && scene.entryId == null && scene.photo == null
        }
        val control = controlName?.let { measurement.boundaries[if (it == "start") "record_start" else "record_end"] }
        if (controlName != null && (control == null || control.atMillis != scene.atMillis)) return result()
        val eventSource = control?.let { MeasurementSourceRef(sessionId, it.sourceEpoch, it.clockEpochId, controlKind = it.controlKind) }
        val earlier = control == null && (content?.locationMethod == "last_known" ||
            content?.locationAtMillis?.let { it != scene.atMillis } == true || entry?.pin?.method == "last_known" ||
            entry != null && entry.pin == null && entry.locationCapturedAtMillis != scene.atMillis)
        val anchor = scene.source?.observation
        val pin = entry?.pin
        val pinRefs = try { pin?.sceneReferences(scene.atMillis) } catch (_: Exception) { return result() }
        if (pinRefs != null) {
            requireNotNull(pin)
            if (pinRefs.isEmpty()) return result()
            val fixes = pinRefs.map { r ->
                val f = raw[r.clientSeq]?.singleOrNull() ?: return result()
                if (f.chainIndex != r.chainIndex || f.atMillis != r.atMillis ||
                    f.sourceEpoch == null || f.clockEpochId == null || fix(f.measurementRef(sessionId)) == null) return result()
                f
            }
            if (anchor != null && resolve(anchor) !in fixes) return result()
            if (pin.method in setOf("observed", "last_known")) {
                val f = fixes.singleOrNull() ?: return result()
                if (GeoPoint(f.lat, f.lng).distanceTo(location) > 1.0) return result()
                val ref = f.measurementRef(sessionId)
                if (pin.method == "last_known" || f.atMillis != scene.atMillis)
                    return result(SceneRouteRelation.EARLIER_LOCATION, location, binding(scene, f.atMillis, ref, ref, ref, null, null))
                return sourceFocus(scene, ref, location, key, null)
            }
            if (pin.method != "estimated" || anchor != null) return result()
            // Estimator evidence must be owned by one final section. Prediction outside that
            // section is a known pin position, not a measured walking interval.
            val refs = fixes.map { it.measurementRef(sessionId) }
            val owners = sections.filter { section -> refs.all { ref ->
                val first = section.source.points.first(); val last = section.source.points.last()
                ref.sourceEpoch == first.sourceEpoch && ref.clockEpochId == first.clockEpochId &&
                    requireNotNull(ref.clientSeq) in requireNotNull(first.clientSeq)..requireNotNull(last.clientSeq)
            } }
            val section = owners.singleOrNull() ?: return result(point = location)
            section.route.points.indices.singleOrNull { section.route.points[it].capturedAtMillis == scene.atMillis }?.let { i ->
                return connected(scene, section, i, i, location, scene.atMillis, null, key, null)
            }
            val i = (0 until section.route.points.lastIndex).singleOrNull { i ->
                val a = section.route.points[i]; val b = section.route.points[i+1]
                scene.atMillis in a.capturedAtMillis..b.capturedAtMillis && timed(section, i)
            } ?: return result(point = location)
            return connected(scene, section, i, i+1, location, scene.atMillis, null, key, null)
        }
        if (anchor != null) {
            val f = resolve(anchor) ?: return result()
            if (content?.locationAtMillis?.let { it != f.atMillis } == true) return result()
            if (location.distanceTo(GeoPoint(f.lat, f.lng)) > 1.0) return result()
            if (control != null) {
                val boundary = measurement.boundaries[if (controlName == "start") "first_observed" else "last_observed"] ?: return result()
                if (boundary.seq != f.clientSeq || boundary.sourceEpoch != f.sourceEpoch || boundary.clockEpochId != f.clockEpochId) return result()
            } else if (scene.atMillis != f.atMillis && !earlier) return result()
            val ref = f.measurementRef(sessionId)
            if (earlier) return result(SceneRouteRelation.EARLIER_LOCATION, location,
                binding(scene, f.atMillis, ref, ref, ref, null, eventSource))
            return sourceFocus(scene, ref, location, key, eventSource)
        }
        if (control != null) return result() // A control's wall time is not a GPS observation.
        if (earlier) return result(SceneRouteRelation.EARLIER_LOCATION, location)

        // A source-less photo/note can use only an unambiguous, clock-consistent time address.
        // Count all original exact-time observations before comparing location; another visit cannot win by proximity.
        val exact = detail.observations.filter { it.atMillis == scene.atMillis }
        if (exact.size > 1) return result(SceneRouteRelation.AMBIGUOUS)
        val edges = sections.flatMap { section -> (0 until section.route.points.lastIndex).mapNotNull { i ->
            val a = section.route.points[i]; val b = section.route.points[i+1]
            if (a.capturedAtMillis < scene.atMillis && scene.atMillis < b.capturedAtMillis && timed(section, i)) section to i else null
        } }
        if (exact.isEmpty() && edges.size > 1) return result(SceneRouteRelation.AMBIGUOUS)
        exact.singleOrNull()?.let { f ->
            val ref = f.sourceEpoch?.let { f.clockEpochId?.let { f.measurementRef(sessionId) } } ?: return result()
            if (edges.any { (section, i) ->
                val a = section.source.points[i]; val b = section.source.points[i+1]
                a.sourceEpoch != ref.sourceEpoch || a.clockEpochId != ref.clockEpochId ||
                    requireNotNull(a.clientSeq) >= f.clientSeq || requireNotNull(b.clientSeq) <= f.clientSeq
            }) return result(SceneRouteRelation.AMBIGUOUS)
            if (fix(ref) == null || GeoPoint(f.lat, f.lng).distanceTo(location) > 12.0) return result()
            return sourceFocus(scene, ref, location, key, null)
        }
        val (section, i) = edges.singleOrNull() ?: return result(point = location)
        val a = section.route.points[i]; val b = section.route.points[i+1]
        val fraction = (scene.atMillis - a.capturedAtMillis).toDouble() / (b.capturedAtMillis - a.capturedAtMillis)
        val lng = ((b.point.longitude - a.point.longitude + 540.0) % 360.0) - 180.0
        val projected = GeoPoint(a.point.latitude + (b.point.latitude - a.point.latitude) * fraction,
            ((a.point.longitude + lng * fraction + 540.0) % 360.0) - 180.0)
        if (projected.distanceTo(location) > 12.0) return result(point = location)
        return connected(scene, section, i, i+1, location, scene.atMillis, null, key, null)
    }

    private fun resolve(anchor: StoryboardObservation): RecordedFix? {
        if (!anchor.point.latitude.isFinite() || !anchor.point.longitude.isFinite() ||
            anchor.point.latitude !in -90.0..90.0 || anchor.point.longitude !in -180.0..180.0) return null
        val f = raw[anchor.clientSeq]?.singleOrNull() ?: return null
        val epoch = f.sourceEpoch ?: return null; val clock = f.clockEpochId ?: return null
        if (f.chainIndex != anchor.chainIndex || f.atMillis != anchor.atMillis ||
            GeoPoint(f.lat, f.lng).distanceTo(anchor.point) > 1.0) return null
        return fix(MeasurementSourceRef(sessionId, epoch, clock, f.clientSeq))
    }

    private fun sourceFocus(scene: DiaryScene, ref: MeasurementSourceRef, point: GeoPoint,
        key: SceneBindingKey, eventSource: MeasurementSourceRef?): SceneRouteFocus {
        val f = requireNotNull(fix(ref))
        val incident = sections.mapNotNull { section -> section.source.points.indexOf(ref).takeIf { it >= 0 }?.let { section to it } }
        // A vertex belongs to its incoming interval. Walking ownership wins at a shared endpoint.
        val locations = incident.filter { it.first.auxiliary == null }.ifEmpty {
            incident.filter { it.second > 0 }.ifEmpty { incident }
        }
        if (locations.size > 1) return SceneRouteFocus(SceneRouteRelation.AMBIGUOUS, key = key)
        locations.singleOrNull()?.let { (section, i) -> return connected(scene, section, i, i, point, f.atMillis, ref, key, eventSource) }
        // An omitted, usable observation belongs to its final source interval, even without a display vertex.
        val owners = sections.flatMap { section -> section.source.points.zipWithNext().mapIndexedNotNull { i, (a, b) ->
            if (a.sourceEpoch == ref.sourceEpoch && b.sourceEpoch == ref.sourceEpoch && a.clockEpochId == ref.clockEpochId &&
                b.clockEpochId == ref.clockEpochId && requireNotNull(a.clientSeq) < requireNotNull(ref.clientSeq) &&
                ref.clientSeq < requireNotNull(b.clientSeq)) section to i else null
        } }
        if (owners.size > 1) return SceneRouteFocus(SceneRouteRelation.AMBIGUOUS, key = key)
        owners.singleOrNull()?.let { (section, i) -> return connected(scene, section, i, i+1, point, f.atMillis, ref, key, eventSource) }
        return SceneRouteFocus(SceneRouteRelation.NO_ROUTE, point = point,
            binding = binding(scene, f.atMillis, ref, ref, ref, null, eventSource), key = key)
    }

    private fun binding(scene: DiaryScene, at: Long, a: MeasurementSourceRef, b: MeasurementSourceRef,
        location: MeasurementSourceRef?, sectionId: String?, event: MeasurementSourceRef?) =
        SceneRouteBinding(scene.atMillis, at, location?.clientSeq ?: requireNotNull(a.clientSeq), SceneBindingKey.POLICY,
            a.clientSeq, b.clientSeq, sourceStart = a, sourceEnd = b, locationSource = location, eventSource = event, sectionId = sectionId)

    private fun connected(scene: DiaryScene, section: Section, a: Int, b: Int, point: GeoPoint, locationAt: Long,
        locationSource: MeasurementSourceRef?, key: SceneBindingKey, eventSource: MeasurementSourceRef?): SceneRouteFocus {
        val points = section.route.points
        var first = a; var last = b; var distance = 0.0
        while (first > 0 && within(points[first-1], points[a])) {
            distance += points[first-1].point.distanceTo(points[first].point)
            if (distance > 40.0 && first < a) break
            first--
        }
        distance = 0.0
        while (last < points.lastIndex && within(points[b], points[last+1])) {
            distance += points[last].point.distanceTo(points[last+1].point)
            if (distance > 40.0 && last > b) break
            last++
        }
        val path = points.subList(first, last+1).map { it.point }
        section.auxiliary?.let { part ->
            val fixes = part.fixes.subList(first, last+1)
            val selected = part.copy(fixes = fixes, directions = part.directions.filter {
                it.evidenceFromSeq >= fixes.first().clientSeq && it.evidenceToSeq <= fixes.last().clientSeq })
            return SceneRouteFocus(if (part.walkingUse == LegacyWalkingUse.EXCLUDED) SceneRouteRelation.OBSERVED_EXCLUDED
                else SceneRouteRelation.OBSERVED_UNRESOLVED, point = point, observedParts = listOf(selected), key = key,
                binding = binding(scene, locationAt, section.source.points[a], section.source.points[b], locationSource,
                    section.source.id, eventSource).copy(displayPolicyVersion = "motion-observed-display-v1"))
        }
        return SceneRouteFocus(if (path.size > 1) SceneRouteRelation.CONNECTED else SceneRouteRelation.NO_ROUTE,
            paths = if (path.size > 1) listOf(path) else emptyList(), point = point,
            binding = binding(scene, locationAt, section.source.points[a], section.source.points[b], locationSource, section.source.id, eventSource), key = key)
    }

    private fun within(a: WalkRoutePoint, b: WalkRoutePoint): Boolean {
        val start = a.elapsedRealtimeNanos ?: return false; val end = b.elapsedRealtimeNanos ?: return false
        return end > start && end - start <= 30_000_000_000L
    }

    private fun timed(section: Section, i: Int): Boolean {
        val a = section.route.points[i]; val b = section.route.points[i+1]
        val from = section.source.points[i]; val to = section.source.points[i+1]
        if (from.sourceEpoch != to.sourceEpoch || from.clockEpochId != to.clockEpochId) return false
        val nanos = requireNotNull(b.elapsedRealtimeNanos) - requireNotNull(a.elapsedRealtimeNanos)
        val wall = b.capturedAtMillis - a.capturedAtMillis
        return nanos in 1..15_000_000_000L && wall in 1..15_000L && abs(wall - nanos / 1_000_000) <= 1_000
    }
}
