package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.detail.WalkDiaryReadView
import com.daengs.app.ui.walk.detail.focusFor
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.routeexplorer.MeasurementTimeAddress
import com.daengs.app.map.layers.moments.MomentMarkerState
import kotlin.math.abs

internal data class DiaryReplayEvent(
    val id: String, val elapsed: Long, val scene: DiaryScene? = null,
    val ordinal: Int? = null, val action: WalkEntry? = null,
)

internal data class DiaryReplayCheckpoint(val elapsed: Long, val events: List<DiaryReplayEvent>) {
    val markerIds get() = events.mapTo(hashSetOf()) { it.id }
}

/** A scene explicitly sourced from an action reads once; both original marker IDs remain selected. */
internal fun DiaryReplayCheckpoint.readingEvents(): List<DiaryReplayEvent> {
    val actions = events.mapNotNull { it.action }.associateBy { it.id }
    val described = events.mapNotNull { it.scene?.entryId }.toSet()
    return events.mapNotNull { event ->
        if (event.scene != null) event.copy(action = actions[event.scene.entryId])
        else event.takeUnless { it.action?.id in described }
    }
}

/** Read-only event projection onto the existing playback axis; never moves a source coordinate. */
internal class DiaryReplayTimeline(events: List<DiaryReplayEvent>, val unresolvedCount: Int = 0) {
    val checkpoints = events.sortedWith(compareBy<DiaryReplayEvent> { it.elapsed }
        .thenBy { if (it.scene != null) 0 else 1 }.thenBy { it.id })
        .groupBy { it.elapsed }.map { (elapsed, members) -> DiaryReplayCheckpoint(elapsed, members) }

    // Derive from absolute time, not a forward-only queue: reverse seeking and skipped ticks agree.
    fun at(elapsed: Long, from: Long = 0, until: Long = Long.MAX_VALUE): DiaryReplayCheckpoint? =
        checkpoints.lastOrNull { it.elapsed in from..until && it.elapsed <= elapsed }

    fun current(elapsed: Long, duration: Long, from: Long = 0, until: Long = duration): DiaryReplayCheckpoint? =
        at(elapsed, from, until)?.takeIf { elapsed < duration || it.elapsed == duration }

    fun previous(elapsed: Long, from: Long = 0): Long? = checkpoints.lastOrNull { it.elapsed in from until elapsed }?.elapsed
    fun next(elapsed: Long, until: Long): Long? = checkpoints.firstOrNull { it.elapsed > elapsed && it.elapsed <= until }?.elapsed
}

internal fun diaryReplayMarkers(markers: List<MomentMarkerState>, ids: Set<String>): List<MomentMarkerState> =
    markers.map { it.copy(selected=it.id in ids, diaryPin=it.diaryPin?.copy(inspected=false, dimmed=false)) }

internal fun diaryReplayTimeline(read: WalkDiaryReadView): DiaryReplayTimeline {
    val detail = read.route.detail
    val review = read.route.review
    val timeline = review.timeline
    val sessionId = detail.summary.sessionId
    data class ClockPoint(val wall: Long, val elapsed: Long, val epoch: String)
    val clockPoints = if (timeline != null) detail.observations.mapNotNull { fix ->
        if (fix.isMock || fix.recordingEligible != true) return@mapNotNull null
        val source = fix.sourceEpoch ?: return@mapNotNull null
        val clock = fix.clockEpochId ?: return@mapNotNull null
        val nanos = fix.elapsedRealtimeNanos ?: return@mapNotNull null
        timeline.position(MeasurementTimeAddress(source, clock, nanos))?.let {
            ClockPoint(fix.atMillis, it, "$source/$clock")
        }
    } else detail.route.points.map { ClockPoint(it.capturedAtMillis, it.activeElapsedMillis, "${it.segmentIndex}") }
    val edges = clockPoints.zipWithNext().filter { (a, b) ->
        a.epoch == b.epoch && b.wall - a.wall in 1..15_000 && b.elapsed > a.elapsed &&
            abs((b.wall-a.wall) - (b.elapsed-a.elapsed)) <= 1_000
    }
    fun position(wall: Long): Long? {
        // The legacy context explicitly validates chronology and includes no-fix periods.
        if (timeline == null && review.context.durationMillis != null) {
            val elapsed = wall - detail.summary.startedAtMillis
            return elapsed.takeIf { it in 0..requireNotNull(review.context.durationMillis) }
        }
        val matches = clockPoints.filter { it.wall == wall }.map { it.elapsed } + edges.mapNotNull { (a, b) ->
            if (wall <= a.wall || wall >= b.wall) null else
                a.elapsed + ((b.elapsed-a.elapsed) * ((wall-a.wall).toDouble()/(b.wall-a.wall))).toLong()
        }
        // Repeated wall clocks must not choose a different visit arbitrarily.
        return matches.distinct().singleOrNull()
    }
    var unresolved = 0
    val events = buildList {
        read.diary?.scenes.orEmpty().filter { it.sessionId == sessionId && !it.isWalkBoundary() }
            .forEachIndexed { index, scene ->
                val elapsed = if (timeline != null) read.focusFor(scene)?.let(timeline::scenePosition) ?: position(scene.atMillis)
                    else position(scene.atMillis)
                if (elapsed == null) unresolved++ else add(DiaryReplayEvent(scene.id, elapsed, scene, index+1))
            }
        read.diary?.sourceEntries.orEmpty().filter { it.sessionId == sessionId && it.type != WalkMomentType.NOTE }.forEach { entry ->
            // Event time is the tap time, not an earlier location capture or a relocated pin time.
            val elapsed = position(entry.recordedAtMillis)
            if (elapsed == null) unresolved++ else add(DiaryReplayEvent(diaryActionKey(entry), elapsed, action=entry))
        }
    }
    return DiaryReplayTimeline(events, unresolved)
}
