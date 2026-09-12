package com.daengs.app.walk.display

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordingEpoch
import com.daengs.app.walk.motion.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One service session, serialized on Main. No recording controls or writes; no UI-owned lifetime. */
internal class WalkSpeedRuntime(
    policy: SessionMotionPolicy,
    private val onFailure: (Exception) -> Unit = {},
) {
    private val sessionId = policy.sessionId
    private val engine = MotionPolicyEngine(policy)
    val usesMeasurement = policy.stored.measurementVersion == MotionPolicies.MEASUREMENT_VERSION
    private val trail = MotionTrail()
    private var sealedNanos: Long? = null
    private var journalSeq = 0L
    private var epoch: DisplayEpoch? = null
    private var presentation: MotionDisplaySession? = null
    private var failed = false
    private var finished = false
    private val mutableDisplay = MutableStateFlow(MotionDisplay())
    val display = mutableDisplay.asStateFlow()

    /** Failure must not expose a partial measurement as a valid diagnostic result. */
    fun motionSnapshot(): MotionSnapshot? = if (failed) null else engine.snapshot()

    fun trailSnapshot(state: com.daengs.app.walk.TrackingState): com.daengs.app.walk.TrailSnapshot =
        trail.snapshot(state, engine.snapshot().eligibleDistanceM)

    fun measurementTiming(): MeasurementTiming {
        val snapshot = engine.snapshot()
        val started = epoch?.startedNanos
        if (snapshot.lifecycle != MotionLifecycle.RECORDING || started == null)
            return MeasurementTiming(snapshot.closedRecordingDurationNanos / 1_000_000, null)
        val sealed = sealedNanos
        return if (sealed == null) MeasurementTiming(snapshot.closedRecordingDurationNanos / 1_000_000, started / 1_000_000)
        else MeasurementTiming((snapshot.closedRecordingDurationNanos + (sealed - started).coerceAtLeast(0)) / 1_000_000, null)
    }

    val measurementError: String? get() = if (failed && usesMeasurement) "GPS 계산을 중단했어요. 저장된 원본으로 완료 결과를 확인해 주세요." else null

    fun begin(source: RecordingEpoch, nowNanos: Long) = safely {
        check(!finished && source.sessionId == sessionId)
        send(MotionEvent.Begin(MotionEpoch(source.id, source.clockEpochId, source.chainIndex,
            source.startedElapsedNanos, source.firstIngressSeq)))
        val next = DisplayEpoch(source.chainIndex.toLong(), source.id, source.clockEpochId, source.startedElapsedNanos)
        val owner = presentation
        if (owner == null) presentation = MotionDisplaySession(sessionId, next, nowNanos)
        else owner.onSourceChanged(next, nowNanos)
        epoch = next
        sealedNanos = null
        publish()
    }

    /** Every committed observation enters the engine; each bounded page publishes only its final display. */
    fun observations(fixes: List<RecordedFix>, nowNanos: Long) = safely {
        val current = checkNotNull(epoch)
        val samples = fixes.mapNotNull { fix ->
            val step = send(MotionEvent.Observation(sessionId, fix))
            if (usesMeasurement) trail.accept(fix, step)
            step.estimate?.displaySample(sessionId, current)
        }
        presentation?.onSamples(samples, nowNanos)
        publish()
    }

    fun tick(nowNanos: Long) = safely {
        presentation?.onTick(nowNanos)
        publish()
    }

    /** Gate notification happens immediately, before awaiting raw drain. FINAL is not a saved receipt. */
    fun onLifecycle(lifecycle: DisplayLifecycle, nowNanos: Long) {
        if (finished) return
        check(lifecycle != DisplayLifecycle.ACTIVE)
        finished = lifecycle == DisplayLifecycle.FINISHED
        if (sealedNanos == null) sealedNanos = nowNanos
        safely {
            presentation?.onLifecycle(lifecycle, nowNanos)
            publish()
        }
        if (failed || presentation == null) {
            mutableDisplay.value = display.value.copy(freshness = if (finished) DisplayFreshness.FINAL else DisplayFreshness.PAUSED,
                signal = DisplaySignal.WAITING)
        }
    }

    /** Close the engine only after the service has projected all accepted raw observations. */
    fun drained(receipt: RecordingEpoch) = safely {
        check(receipt.sessionId == sessionId && receipt.id == epoch?.sourceId && receipt.drained)
        // Stopping an already paused session can supply the same PAUSE receipt again.
        if (engine.snapshot().lifecycle != MotionLifecycle.RECORDING) return@safely
        send(MotionEvent.End(requireNotNull(receipt.targetIngressSeq), requireNotNull(receipt.endedElapsedNanos),
            if (receipt.failureReason != null) EndKind.INTERRUPTED else EndKind.valueOf(requireNotNull(receipt.endKind))))
    }

    /** A durable zero-length STOP after PAUSE changes completion state without counting paused time. */
    fun completePausedStop(receipt: RecordingEpoch) = safely {
        check(finished && engine.snapshot().lifecycle == MotionLifecycle.PAUSED)
        check(receipt.sessionId == sessionId && receipt.drained && receipt.endKind == "STOP" &&
            receipt.failureReason == null && receipt.persistedCount == 0L &&
            receipt.startedElapsedNanos == receipt.endedElapsedNanos)
        send(MotionEvent.Begin(MotionEpoch(receipt.id, receipt.clockEpochId, receipt.chainIndex,
            receipt.startedElapsedNanos, receipt.firstIngressSeq, receipt.endedElapsedNanos)))
        send(MotionEvent.End(requireNotNull(receipt.targetIngressSeq), requireNotNull(receipt.endedElapsedNanos), EndKind.STOP))
    }

    private fun send(event: MotionEvent): MotionStep = engine.step(MotionJournalEntry(journalSeq, event)).also { journalSeq++ }
    private fun publish() { presentation?.let { mutableDisplay.value = it.display.value } }

    /** A projection fault must never stop raw recording. Completion replays durable input. Fail once per session. */
    private inline fun safely(block: () -> Unit) {
        if (failed) return
        try { block() } catch (error: Exception) {
            failed = true
            mutableDisplay.value = display.value.copy(
                freshness = when (display.value.freshness) {
                    DisplayFreshness.PAUSED, DisplayFreshness.FINAL -> display.value.freshness
                    else -> DisplayFreshness.STALE
                }, signal = DisplaySignal.DELAYED)
            onFailure(error)
        }
    }
}

internal data class MeasurementTiming(val closedMillis: Long, val activeSinceRealtimeMillis: Long?)
