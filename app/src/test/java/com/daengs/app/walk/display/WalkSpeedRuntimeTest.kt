package com.daengs.app.walk.display

import com.daengs.app.walk.RecordingEpoch
import com.daengs.app.walk.motion.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WalkSpeedRuntimeTest {
    private fun source(id: String = "e", chain: Int = 0, start: Double = 0.0, first: Long = 0) =
        RecordingEpoch(id, "s", "c", chain, 0, nanos(start), first)
    private fun runtime() = WalkSpeedRuntime("s") { throw AssertionError(it) }.also { it.begin(source(), 0) }

    @Test fun noSubscribersDoesNotStopMeasurementOrMissingInputAging() {
        val owner = runtime()
        owner.observations(listOf(fix(0, 0.0, 1.0, speed = 1.5f)), nanos(1.1))
        owner.tick(nanos(5.0))
        assertEquals(DisplayFreshness.HELD, owner.display.value.freshness)
        assertEquals(DisplaySignal.RECEIVING, owner.display.value.signal)
        owner.tick(nanos(11.0))
        assertEquals(1.5, owner.display.value.speedMps, 0.0)
        assertEquals(DisplayFreshness.STALE, owner.display.value.freshness)
        assertEquals(DisplaySignal.DELAYED, owner.display.value.signal)
    }

    @Test fun noFirstFixBecomesDelayedAndMeasuredZeroIsDifferentFromMissingEvidence() {
        val owner = runtime()
        owner.tick(nanos(10.0))
        assertEquals(DisplaySignal.DELAYED, owner.display.value.signal)
        assertNull(owner.display.value.measuredNanos)
        owner.observations(listOf(fix(0, 0.0, 11.0, speed = 0f)), nanos(11.1))
        assertEquals(DisplayFreshness.LIVE, owner.display.value.freshness)
        assertEquals(nanos(11.0), owner.display.value.measuredNanos)
        assertEquals(0.0, owner.display.value.speedMps, 0.0)
        assertEquals(DisplaySignal.DELAYED, owner.display.value.signal)
    }

    @Test fun sealFreezesReadingWhileAcceptedTailStillDrainsBeforeNewSource() {
        val owner = runtime()
        owner.observations(listOf(fix(0, 0.0, 1.0, speed = 2f)), nanos(1.1))
        owner.onLifecycle(DisplayLifecycle.PAUSED, nanos(3.0))
        owner.observations(listOf(fix(1, 1.0, 2.0, speed = 3f)), nanos(4.0))
        owner.drained(source().copy(endedElapsedNanos = nanos(3.0), endKind = "PAUSE", targetIngressSeq = 1, drained = true))
        assertEquals(2.0, owner.display.value.speedMps, 0.0)
        assertEquals(DisplayFreshness.PAUSED, owner.display.value.freshness)
        owner.tick(nanos(20.0))
        owner.begin(source("next", 1, 21.0, 2), nanos(21.0))
        assertEquals(2.0, owner.display.value.speedMps, 0.0)
        assertEquals(DisplaySignal.WAITING, owner.display.value.signal)
        // Late old-source input cannot revive an old reading, even with a new ingress number.
        owner.observations(listOf(fix(2, 2.0, 21.1, speed = 8f)), nanos(21.2))
        assertEquals(2.0, owner.display.value.speedMps, 0.0)
        owner.observations(listOf(fix(3, 3.0, 22.0, speed = 0f, source = "next", chain = 1)), nanos(22.1))
        assertEquals(0.0, owner.display.value.speedMps, 0.0)
        assertEquals(DisplayFreshness.LIVE, owner.display.value.freshness)
    }

    @Test fun stopWhilePausedAndNewSessionDoNotReviveOldSpeed() {
        val owner = runtime()
        owner.observations(listOf(fix(0, 0.0, 1.0, speed = 2f)), nanos(1.1))
        val receipt = source().copy(endedElapsedNanos = nanos(2.0), endKind = "PAUSE", targetIngressSeq = 0, drained = true)
        owner.onLifecycle(DisplayLifecycle.PAUSED, nanos(2.0)); owner.drained(receipt)
        owner.onLifecycle(DisplayLifecycle.FINISHED, nanos(3.0)); owner.drained(receipt)
        owner.tick(nanos(100.0))
        assertEquals(DisplayFreshness.FINAL, owner.display.value.freshness)
        assertEquals(2.0, owner.display.value.speedMps, 0.0)
        assertEquals(MotionDisplay(), WalkSpeedRuntime("new").display.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun delayedBatchPublishesOnlyLatestFreshValueAndCannotInstantlyRecover() = runTest {
        val owner = runtime()
        owner.observations(listOf(fix(0, 0.0, 1.0)), nanos(1.1))
        owner.tick(nanos(20.0))
        val readings = mutableListOf<MotionDisplay>()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { owner.display.collect { readings += it } }
        owner.observations((1..20).map { fix(it, it.toDouble(), it + 1.0, speed = it.toFloat()) }, nanos(21.1))
        assertEquals(2, readings.size)
        assertEquals(20.0, readings.last().speedMps, 0.0)
        assertEquals(DisplaySignal.DELAYED, readings.last().signal)
        observer.cancel()
    }

    @Test fun cachedAndInvalidObservationsAreConsumedWithoutBreakingIngressReferences() {
        val owner = runtime()
        owner.observations(listOf(fix(0, 0.0, 1.0, speed = 99f).copy(recordingEligible = false),
            fix(1, 0.0, 2.0, speed = 1.5f)), nanos(2.1))
        assertEquals(1.5, owner.display.value.speedMps, 0.0)
        owner.onLifecycle(DisplayLifecycle.FINISHED, nanos(3.0))
        owner.drained(source().copy(endedElapsedNanos = nanos(3.0), endKind = "STOP", targetIngressSeq = 1, drained = true))
        assertEquals(DisplayFreshness.FINAL, owner.display.value.freshness)
    }

    @Test fun projectionContractFailureIsContainedAndReportedOnce() {
        val errors = mutableListOf<Exception>()
        val owner = WalkSpeedRuntime("s", errors::add)
        owner.begin(source(), 0)
        owner.observations(listOf(fix(0, 0.0, 1.0, speed = 2f)), nanos(1.1))
        owner.observations(listOf(fix(0, 0.0, 2.0)), nanos(2.1)) // corrupted duplicate ingress
        owner.observations(listOf(fix(1, 1.0, 3.0)), nanos(3.1))
        owner.tick(nanos(20.0))
        assertEquals(1, errors.size)
        assertEquals(2.0, owner.display.value.speedMps, 0.0)
        assertEquals(DisplayFreshness.STALE, owner.display.value.freshness)
        owner.onLifecycle(DisplayLifecycle.FINISHED, nanos(21.0))
        assertEquals(DisplayFreshness.FINAL, owner.display.value.freshness)
    }

    @Test fun badClockAndUnverifiedSpeedCannotReplaceGoodSpeed() {
        val owner = runtime()
        owner.observations(listOf(fix(0, 0.0, 1.0, speed = 2f)), nanos(1.1))
        owner.observations(listOf(fix(1, 0.0, 2.0, speed = 9f, clock = "old")), nanos(2.1))
        owner.observations(listOf(fix(2, 0.0, 3.0, speed = 9f, speedAccuracy = null)), nanos(3.1))
        assertEquals(2.0, owner.display.value.speedMps, 0.0)
        assertEquals(DisplayFreshness.HELD, owner.display.value.freshness)
    }
}
