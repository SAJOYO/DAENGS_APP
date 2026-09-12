package com.daengs.app.walk.motion

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordingEpoch
import com.daengs.app.walk.countsAsWalk
import com.daengs.app.walk.summarizeLegacy

/** One consistent storage read; no inferred observations or rewritten source references. */
data class RecordedMotionInput(val session: RecordedSession, val epochs: List<RecordingEpoch>,
    val fixes: List<RecordedFix>)

sealed interface RecordedMotionComparison {
    data class Unavailable(val reason: String) : RecordedMotionComparison
    data class Ready(val sessionId: String, val legacyDistanceM: Double,
        val legacyActiveDurationMillis: Long, val candidate: MotionSnapshot) : RecordedMotionComparison {
        val distanceDeltaM: Double get() = candidate.eligibleDistanceM - legacyDistanceM
        val candidateRecordingDurationMillis: Long get() = candidate.closedRecordingDurationNanos / 1_000_000
        val legacyCountsAsWalk: Boolean get() = countsAsWalk(legacyDistanceM, legacyActiveDurationMillis)
        val candidateCountsAsWalk: Boolean get() = countsAsWalk(candidate.eligibleDistanceM, candidateRecordingDurationMillis)
    }
}

/** Read-only comparison. Neither candidate distance nor its 60s/50m decision activates a release policy. */
fun compareRecordedMotion(input: RecordedMotionInput): RecordedMotionComparison {
    val (session, epochs, fixes) = input
    val selection = MotionPolicies.resolveJson(session.id, session.motionPolicyJson)
    val policy = when (selection) {
        MotionPolicySelection.Legacy -> return RecordedMotionComparison.Unavailable("LEGACY_POLICY")
        is MotionPolicySelection.Unsupported -> return RecordedMotionComparison.Unavailable(selection.reason)
        is MotionPolicySelection.Supported -> selection.policy
    }
    if (session.endedAtMillis == null || epochs.lastOrNull()?.endedAtMillis != session.endedAtMillis)
        return RecordedMotionComparison.Unavailable("INCOMPLETE_RECORDING")
    val replay = try { replayRecordedMotion(policy, epochs, fixes.asSequence()) }
    catch (_: IllegalStateException) { return RecordedMotionComparison.Unavailable("INCOMPLETE_RECORDING") }
    catch (_: IllegalArgumentException) { return RecordedMotionComparison.Unavailable("INVALID_RECORDING") }
    val legacy = summarizeLegacy(session, fixes)
    return RecordedMotionComparison.Ready(session.id, legacy.distanceMeters, legacy.activeDurationMillis, replay)
}
