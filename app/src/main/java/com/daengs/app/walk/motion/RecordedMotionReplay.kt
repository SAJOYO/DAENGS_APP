package com.daengs.app.walk.motion

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordingEpoch
import com.daengs.app.walk.checkRecordingComplete

/**
 * Pure adapter for #283's fully drained journal. Reads fixes once, without sorting or rewriting refs.
 * Missing STOP/failure/legacy data is rejected; it must not manufacture a completed replay.
 */
fun replayRecordedMotion(policy: SessionMotionPolicy, epochs: List<RecordingEpoch>,
    fixes: Sequence<RecordedFix>, onStep: (MotionStep) -> Unit = {}): MotionSnapshot {
    checkRecordingComplete(epochs)
    require(epochs.all { it.sessionId == policy.sessionId })
    val engine = MotionPolicyEngine(policy)
    val iterator = fixes.iterator()
    var journal = 0L
    fun send(event: MotionEvent) { onStep(engine.step(MotionJournalEntry(journal++, event))) }
    for (epoch in epochs) {
        val end = requireNotNull(epoch.endedElapsedNanos)
        send(MotionEvent.Begin(MotionEpoch(epoch.id, epoch.clockEpochId, epoch.chainIndex,
            epoch.startedElapsedNanos, epoch.firstIngressSeq, end)))
        var count = 0L
        while (count < epoch.persistedCount) {
            require(iterator.hasNext()) { "Recording observations are missing" }
            val fix = iterator.next()
            require(fix.ingressSeq == epoch.firstIngressSeq + count && fix.sourceEpoch == epoch.id &&
                fix.clockEpochId == epoch.clockEpochId && fix.chainIndex == epoch.chainIndex) { "Recording reference mismatch" }
            send(MotionEvent.Observation(policy.sessionId, fix))
            count++
        }
        send(MotionEvent.End(requireNotNull(epoch.targetIngressSeq), end,
            if (epoch.endKind == "STOP") EndKind.STOP else EndKind.PAUSE))
    }
    require(!iterator.hasNext()) { "Observations follow the recording receipt" }
    return engine.snapshot()
}
