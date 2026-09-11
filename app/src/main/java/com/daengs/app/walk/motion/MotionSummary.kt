package com.daengs.app.walk.motion

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordingEpoch
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.WalkSummary

/** The completed/recovery reader reuses the live engine and path projector; no legacy fallback. */
internal fun summarizeMotion(session: RecordedSession, policy: SessionMotionPolicy,
    epochs: List<RecordingEpoch>, fixes: List<RecordedFix>, maxRouteSamples: Int): WalkSummary {
    require(session.id == policy.sessionId)
    if (session.endedAtMillis != null)
        check(session.endedAtMillis == epochs.lastOrNull()?.endedAtMillis) { "GPS 종료 기록과 세션 종료가 다릅니다." }
    val trail = MotionTrail(maxRouteSamples)
    var current: RecordedFix? = null
    val result = replayRecordedMotion(policy, epochs, fixes.asSequence().onEach { current = it }) { step ->
        if (step.decision != null) trail.accept(requireNotNull(current), step)
    }
    val rendered = trail.snapshot(TrackingState.OFF, result.eligibleDistanceM)
    val elapsedByNanos = linkedMapOf<Long, Long>()
    var closedNanos = 0L
    val bySource = epochs.associate { epoch ->
        (epoch.id to closedNanos).also {
            closedNanos = Math.addExact(closedNanos, requireNotNull(epoch.endedElapsedNanos) - epoch.startedElapsedNanos)
        }
    }
    val sources = epochs.associateBy { it.id }
    val retainedTimes = rendered.segments.flatMap { it.mapNotNull { sample -> sample.elapsedRealtimeNanos } }.toSet()
    val elapsedByMillis = linkedMapOf<Long, Long>()
    for (fix in fixes) {
        val time = fix.elapsedRealtimeNanos ?: continue
        if (time !in retainedTimes) continue
        val source = sources[fix.sourceEpoch] ?: continue
        if (fix.recordingEligible != true || fix.clockEpochId != source.clockEpochId ||
            time < source.startedElapsedNanos || time > requireNotNull(source.endedElapsedNanos)) continue
        val elapsed = (bySource.getValue(source.id) + time - source.startedElapsedNanos) / 1_000_000
        elapsedByNanos[time] = elapsed
        elapsedByMillis[fix.atMillis] = elapsed
    }
    return WalkSummary(session.id, session.dogIds, session.startedAtMillis, session.endedAtMillis, session.weather,
        result.eligibleDistanceM, result.closedRecordingDurationNanos / 1_000_000, rendered.segments,
        rendered.startSample?.point, elapsedByMillis, elapsedByNanos, policy.stored.measurementVersion)
}
