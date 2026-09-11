package com.daengs.app.walk.motion

import com.daengs.app.walk.RecordedFix

/**
 * Single-owner, deterministic engine. No Android, Room, IO, coroutine or presentation calls.
 * A step consumes one ordered journal entry and returns a delta; it retains no full route.
 */
class MotionPolicyEngine(val policy: SessionMotionPolicy) {
    private val estimator = MotionEstimator(policy.config)
    private val segments = SegmentPolicy(policy.config, policy.stored.version)
    private var lifecycle = MotionLifecycle.IDLE
    private var epoch: MotionEpoch? = null
    private var journalSeq = -1L
    private var ingressSeq = -1L
    private var observationCount = 0L
    private var closedDuration = 0L
    private var lastEndTime: Long? = null
    private var lastTimedFix: RecordedFix? = null
    private var knownLoss = false

    fun snapshot() = MotionSnapshot(lifecycle, journalSeq, observationCount, ingressSeq,
        segments.segmentCount, segments.distanceM, closedDuration, knownLoss, estimator.size,
        policy.stored.version, policy.stored.configHash)

    fun step(entry: MotionJournalEntry): MotionStep {
        require(entry.journalSeq == Math.addExact(journalSeq, 1)) { "Journal must be contiguous; replay from its beginning" }
        validate(entry.event)
        val result = when (val event = entry.event) {
            is MotionEvent.Begin -> { begin(event.epoch); null }
            is MotionEvent.End -> { end(event); null }
            is MotionEvent.Loss -> {
                knownLoss = true
                barrier(MotionReason.valueOf(event.reason.name))
                null
            }
            is MotionEvent.Observation -> observe(event.fix)
        }
        journalSeq = entry.journalSeq
        return MotionStep(result?.first, result?.second, snapshot())
    }

    /** Streaming/batch delivery uses exactly the same step; no intermediate sample is dropped. */
    fun consume(entries: Iterable<MotionJournalEntry>, onStep: (MotionStep) -> Unit = {}): MotionSnapshot {
        entries.forEach { onStep(step(it)) }
        return snapshot()
    }

    private fun validate(event: MotionEvent) {
        when (event) {
            is MotionEvent.Begin -> {
                require(lifecycle == MotionLifecycle.IDLE || lifecycle == MotionLifecycle.PAUSED)
                require(event.epoch.firstIngressSeq == Math.addExact(ingressSeq, 1))
                epoch?.let { previous ->
                    require(event.epoch.chainIndex > previous.chainIndex && event.epoch.sourceEpoch != previous.sourceEpoch)
                    if (previous.clockEpochId == event.epoch.clockEpochId)
                        require(event.epoch.startedElapsedNanos >= requireNotNull(lastEndTime))
                }
            }
            is MotionEvent.Observation -> {
                require(lifecycle == MotionLifecycle.RECORDING)
                require(event.sessionId == policy.sessionId) { "Observation belongs to another session" }
                val seq = requireNotNull(event.fix.ingressSeq) { "Legacy observations require the legacy reader" }
                require(seq >= 0 && seq > ingressSeq && seq == event.fix.clientSeq.toLong()) { "Ingress reference changed or was replayed twice" }
            }
            is MotionEvent.End -> {
                require(lifecycle == MotionLifecycle.RECORDING)
                val current = requireNotNull(epoch)
                require(event.afterIngressSeq >= ingressSeq)
                require(event.elapsedNanos >= current.startedElapsedNanos)
                require(current.endExclusiveNanos == null || event.elapsedNanos == current.endExclusiveNanos)
                require(lastTimedFix?.elapsedRealtimeNanos?.let { event.elapsedNanos >= it } != false)
                Math.addExact(closedDuration, event.elapsedNanos - current.startedElapsedNanos)
            }
            is MotionEvent.Loss -> require(lifecycle == MotionLifecycle.RECORDING)
        }
    }

    private fun begin(next: MotionEpoch) {
        epoch?.let { previous ->
            barrier(MotionReason.PAUSE)
            if (next.sourceEpoch != previous.sourceEpoch) barrier(MotionReason.SOURCE_CHANGED)
            if (next.clockEpochId != previous.clockEpochId) barrier(MotionReason.CLOCK_DOMAIN_CHANGED)
        }
        epoch = next
        lastTimedFix = null
        lifecycle = MotionLifecycle.RECORDING
    }

    private fun end(event: MotionEvent.End) {
        if (event.afterIngressSeq != ingressSeq) {
            knownLoss = true
            barrier(MotionReason.INGRESS_GAP)
            ingressSeq = event.afterIngressSeq
        }
        closedDuration += event.elapsedNanos - requireNotNull(epoch).startedElapsedNanos
        lastEndTime = event.elapsedNanos
        barrier(MotionReason.PAUSE)
        lifecycle = when (event.kind) {
            EndKind.PAUSE -> MotionLifecycle.PAUSED
            EndKind.STOP -> MotionLifecycle.STOPPED
            EndKind.INTERRUPTED -> MotionLifecycle.INTERRUPTED
        }
    }

    private fun observe(fix: RecordedFix): Pair<MotionEstimate, SegmentDecision> {
        val seq = requireNotNull(fix.ingressSeq)
        if (seq != ingressSeq + 1) {
            knownLoss = true
            barrier(MotionReason.INGRESS_GAP)
        }
        ingressSeq = seq
        observationCount++
        val current = requireNotNull(epoch)
        val ref = if (fix.sourceEpoch != null && fix.clockEpochId != null)
            MotionRef(policy.sessionId, seq, fix.sourceEpoch, fix.clockEpochId, fix.chainIndex) else null
        if (ref == null || fix.recordingEligible == null) return exclude(fix, ref, MotionReason.MISSING_METADATA, breakPath = true)
        if (fix.sourceEpoch != current.sourceEpoch) return exclude(fix, ref, MotionReason.SOURCE_CHANGED, breakPath = true)
        if (fix.clockEpochId != current.clockEpochId) return exclude(fix, ref, MotionReason.CLOCK_DOMAIN_CHANGED, breakPath = true)
        if (fix.chainIndex != current.chainIndex) return exclude(fix, ref, MotionReason.CHAIN_CHANGED, breakPath = true)
        val time = fix.elapsedRealtimeNanos
        val received = fix.receivedElapsedNanos
        if (time == null || received == null || time < 0 || received < time)
            return exclude(fix, ref, MotionReason.INVALID_TIME, breakPath = true)
        if (!fix.recordingEligible || time < current.startedElapsedNanos ||
            current.endExclusiveNanos?.let { time >= it } == true)
            return exclude(fix, ref, MotionReason.OUTSIDE_ACTIVE_INTERVAL)
        val previous = lastTimedFix
        val previousTime = previous?.elapsedRealtimeNanos
        if (previousTime != null && time <= previousTime) {
            val reason = when {
                time < previousTime -> MotionReason.OUT_OF_ORDER
                sameMeasurement(previous, fix) -> MotionReason.DUPLICATE
                else -> MotionReason.SAME_TIME_CONFLICT
            }
            return exclude(fix, ref, reason)
        }
        if (previousTime != null && seconds(previousTime, time) > policy.config.maxGapSeconds)
            barrier(MotionReason.OBSERVATION_GAP)
        lastTimedFix = fix
        val point = MotionPoint(ref, fix)
        val estimate = estimator.estimate(point)
        val decision = segments.decide(point, estimate)
        if (MotionReason.HIGH_SPEED in decision.reasons && decision.connection == Connection.SKIP &&
            MotionReason.REENTRY_PENDING !in decision.reasons || MotionReason.JUMP in decision.reasons) estimator.reset()
        return estimate to decision
    }

    private fun exclude(fix: RecordedFix, ref: MotionRef?, reason: MotionReason, breakPath: Boolean = false): Pair<MotionEstimate, SegmentDecision> {
        if (breakPath) barrier(reason)
        return MotionEstimate(ref, positionQuality(fix, policy.config), null, SpeedSource.UNKNOWN,
            SpeedQuality.UNKNOWN, fix.elapsedRealtimeNanos, Movement.UNKNOWN, setOf(reason)) to segments.skip(ref, setOf(reason))
    }

    private fun barrier(reason: MotionReason) { estimator.reset(); segments.breakConnection(reason) }
}

/** Receipt metadata is not part of a platform measurement's identity. */
private fun sameMeasurement(a: RecordedFix, b: RecordedFix): Boolean =
    a.copy(clientSeq = b.clientSeq, ingressSeq = b.ingressSeq, receivedElapsedNanos = b.receivedElapsedNanos,
        receivedAtMillis = b.receivedAtMillis, recordingEligible = b.recordingEligible) == b
