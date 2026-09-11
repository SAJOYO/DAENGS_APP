package com.daengs.app.walk

/** Exact coverage proof, including empty epochs and boundaries between pause/resume. */
fun checkRecordingComplete(epochs: List<RecordingEpoch>) {
    check(epochs.isNotEmpty()) { "Recording receipt is missing" }
    var expected = 0L
    for (epoch in epochs) {
        check(epoch.sessionId == epochs.first().sessionId && epoch.clockEpochId == epochs.first().clockEpochId) { "Recording identity changed" }
        check(epoch.drained && epoch.failureReason == null) { "Recording has an incomplete or failed epoch" }
        check(epoch.firstIngressSeq == expected) { "Recording has an ingress gap" }
        val target = checkNotNull(epoch.targetIngressSeq)
        check(target >= expected - 1 && epoch.persistedCount == target - expected + 1) {
            "Recording coverage does not match its close boundary"
        }
        check(epoch.endedElapsedNanos != null && epoch.endedAtMillis != null) { "Recording boundary is missing" }
        check(epoch.endedElapsedNanos >= epoch.startedElapsedNanos) { "Recording clock moved backwards" }
        check(epoch.endKind == "PAUSE" || epoch.endKind == "STOP") { "Recording was interrupted" }
        expected = target + 1
    }
    check(epochs.last().endKind == "STOP") { "Recording has no explicit stop request" }
    check(epochs.dropLast(1).none { it.endKind == "STOP" }) { "Observations follow a completed recording" }
}

/** Replays only an explicit, fully drained STOP. Interrupted tails remain available for recovery. */
suspend fun recoverDrainedRecordings(log: WalkFixLog, finishPins: suspend (String, Long) -> Unit) {
    for (session in log.unfinishedSessions()) {
        if (session.ownerId != log.ownerId) continue
        val epochs = log.recordingEpochs(session.id)
        if (runCatching { checkRecordingComplete(epochs) }.isFailure) continue
        val endedAt = requireNotNull(epochs.last().endedAtMillis)
        finishPins(session.id, endedAt)
        val summary = summarize(session, log.fixes(session.id))
        if (!summary.countsAsWalk && !log.hasEntries(session.id)) log.deleteSession(session.id)
        else log.closeSession(session.id, endedAt)
    }
}
