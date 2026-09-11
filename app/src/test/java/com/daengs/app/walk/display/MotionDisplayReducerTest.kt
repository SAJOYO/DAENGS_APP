package com.daengs.app.walk.display

import org.junit.Assert.*
import org.junit.Test

class MotionDisplayReducerTest {
    private val reducer = MotionDisplayReducer()
    private val epoch = DisplayEpoch(0, "source-0", "boot-0", 0)
    private fun ns(seconds: Long) = seconds * 1_000_000_000L
    private fun initial() = reducer.initial("walk", epoch, 0)
    private fun sample(second: Long, speed: Double? = 1.2, source: DisplaySpeedSource = DisplaySpeedSource.DEVICE) =
        DisplaySpeedSample("walk", epoch, ns(second), speed, source)
    private fun accept(state: MotionDisplayState, second: Long, speed: Double? = 1.2) =
        reducer.samples(state, listOf(sample(second, speed)), ns(second))

    @Test fun initialZeroIsNotAReportedStopAndSilenceEventuallyBecomesStale() {
        val start = initial()
        assertEquals(0.0, start.display.speedMps, 0.0)
        assertNull(start.display.measuredNanos)
        assertEquals(DisplayFreshness.INITIAL, reducer.tick(start, ns(9)).display.freshness)
        val stale = reducer.tick(start, ns(10)).display
        assertEquals(DisplayFreshness.STALE, stale.freshness)
        assertEquals(DisplaySignal.DELAYED, stale.signal)
    }

    @Test fun briefLossHoldsTheNumberAndLampUntilExactStaleBoundary() {
        val live = accept(initial(), 0)
        val held = reducer.tick(live, ns(3) + 1)
        assertEquals(DisplayFreshness.HELD, held.display.freshness)
        assertEquals(DisplaySignal.RECEIVING, held.display.signal)
        assertEquals(live.display.speedMps, held.display.speedMps, 0.0)
        assertEquals(DisplaySignal.RECEIVING, reducer.tick(held, ns(10) - 1).display.signal)
        val stale = reducer.tick(held, ns(10))
        assertEquals(DisplayFreshness.STALE, stale.display.freshness)
        assertEquals(DisplaySignal.DELAYED, stale.display.signal)
    }

    @Test fun alternatingLossDoesNotFlashTheLampInEitherDirection() {
        var state = accept(initial(), 0)
        for (second in 1L..60L) {
            state = accept(state, second, if (second % 2 == 0L) 1.2 else null)
            assertEquals(DisplaySignal.RECEIVING, state.display.signal)
        }
        state = reducer.tick(state, ns(70))
        for (second in 71L..130L) {
            state = accept(state, second, if (second % 2 == 0L) 1.2 else null)
            assertEquals(DisplaySignal.DELAYED, state.display.signal)
        }
    }

    @Test fun recoveryNeedsContinuousMeasurementAndArrivalTimeButSpeedUpdatesImmediately() {
        var state = reducer.tick(accept(initial(), 0), ns(10))
        for (second in 11L..15L) {
            state = accept(state, second, 1.4)
            assertEquals(1.4, state.display.speedMps, 0.0)
            assertEquals(DisplaySignal.DELAYED, state.display.signal)
        }
        state = accept(state, 16, 1.4)
        assertEquals(DisplaySignal.RECEIVING, state.display.signal)
    }

    @Test fun oneGoodSampleAndClockTicksCannotProveRecovery() {
        val state = accept(reducer.tick(accept(initial(), 0), ns(10)), 11)
        assertEquals(DisplaySignal.DELAYED, reducer.tick(state, ns(16)).display.signal)
    }

    @Test fun steadyDeliveryWithConstantLatencyStillRecovers() {
        var state = reducer.tick(accept(initial(), 0), ns(10))
        for (second in 11L..15L) {
            state = reducer.samples(state, listOf(sample(second)), ns(second + 2))
            assertEquals(DisplaySignal.DELAYED, state.display.signal)
        }
        state = reducer.samples(state, listOf(sample(16)), ns(18))
        assertEquals(DisplaySignal.RECEIVING, state.display.signal)
        assertEquals(ns(16), state.display.measuredNanos)
    }

    @Test fun delayedBatchCannotReplayOldNumbersOrManufactureRecovery() {
        val stale = reducer.tick(accept(initial(), 0), ns(10))
        val oldBatch = (1L..6L).map { sample(it, 8.0) }
        val oldResult = reducer.samples(stale, oldBatch, ns(10))
        assertEquals(1.2, oldResult.display.speedMps, 0.0)
        assertEquals(0L, oldResult.display.measuredNanos)
        val recentBatch = (11L..20L).map { sample(it, it.toDouble()) }
        val result = reducer.samples(oldResult, recentBatch, ns(20))
        assertEquals(20.0, result.display.speedMps, 0.0)
        assertEquals(ns(20), result.display.measuredNanos)
        assertEquals(DisplaySignal.DELAYED, result.display.signal)
    }

    @Test fun gapInsideRecoveryRestartsTheWholeRecoveryWindow() {
        var state = reducer.tick(accept(initial(), 0), ns(10))
        for (second in 11L..14L) state = accept(state, second)
        state = accept(state, 17)
        for (second in 18L..21L) state = accept(state, second)
        assertEquals(DisplaySignal.DELAYED, state.display.signal)
        assertEquals(DisplaySignal.RECEIVING, accept(state, 22).display.signal)
    }

    @Test fun invalidNumbersNeverEraseTheReadingAndZeroIsImmediate() {
        var state = accept(initial(), 0)
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0).forEachIndexed { i, value ->
            state = accept(state, (i + 1).toLong(), value)
            assertEquals(1.2, state.display.speedMps, 0.0)
        }
        state = reducer.tick(state, ns(10))
        state = accept(state, 11, 0.0)
        assertEquals(0.0, state.display.speedMps, 0.0)
        assertEquals(DisplayFreshness.LIVE, state.display.freshness)
        assertEquals(DisplaySignal.DELAYED, state.display.signal)
    }

    @Test fun sourceAndMeasurementTimeArePreservedWhileHeld() {
        val sample = sample(0, 0.8, DisplaySpeedSource.COORDINATE_WINDOW)
        val state = reducer.samples(initial(), listOf(sample), 0)
        val held = reducer.tick(state, ns(30)).display
        assertEquals(DisplaySpeedSource.COORDINATE_WINDOW, held.source)
        assertEquals("boot-0", held.measuredClockId)
        assertEquals(0L, held.measuredNanos)
        assertEquals(0.8, held.speedMps, 0.0)
    }

    @Test fun duplicateFutureOutOfOrderAndForeignSamplesCannotOverwriteTheReading() {
        val live = accept(initial(), 2)
        val input = listOf(sample(2, 9.0), sample(1, 8.0), sample(4, 7.0),
            sample(3, 6.0).copy(sessionId = "other"),
            sample(3, 5.0).copy(epoch = epoch.copy(sourceId = "other")), sample(-1, 4.0))
        val result = reducer.samples(live, input, ns(3))
        assertEquals(live.display, result.display)
        assertEquals(ns(2), result.lastObservedNanos)
    }

    @Test fun unavailableNewerObservationPreventsOlderValidSpeedFromWinning() {
        val result = reducer.samples(accept(initial(), 0), listOf(sample(2, null), sample(1, 9.0)), ns(2))
        assertEquals(1.2, result.display.speedMps, 0.0)
        assertEquals(DisplayFreshness.HELD, result.display.freshness)
    }

    @Test fun pauseResumeAndFinalKeepNumberButRejectOldSourceAndLateUpdates() {
        val paused = reducer.lifecycle(accept(initial(), 0), DisplayLifecycle.PAUSED, ns(1))
        val ignored = reducer.samples(paused, listOf(sample(2, 9.0)), ns(2))
        assertEquals(paused.display, ignored.display)
        val nextEpoch = DisplayEpoch(1, "source-1", "boot-0", ns(3))
        val resumed = reducer.sourceChanged(ignored, nextEpoch, ns(3))
        assertEquals(1.2, resumed.display.speedMps, 0.0)
        assertEquals(DisplaySignal.WAITING, resumed.display.signal)
        assertEquals(resumed.display, reducer.samples(resumed, listOf(sample(3, 9.0)), ns(3)).display)
        val final = reducer.lifecycle(resumed, DisplayLifecycle.FINISHED, ns(4))
        val after = reducer.samples(final, listOf(sample(5, 2.0).copy(epoch = nextEpoch)), ns(5))
        assertEquals(final.display, after.display)
        assertEquals(DisplayFreshness.FINAL, after.display.freshness)
        assertThrows(IllegalArgumentException::class.java) { reducer.sourceChanged(after, nextEpoch.copy(generation = 2), ns(6)) }
    }

    @Test fun newClockKeepsOldReadingWithoutSubtractingDifferentClockDomains() {
        val live = accept(initial(), 100)
        val epoch2 = DisplayEpoch(1, "source-1", "boot-1", 0)
        val resumed = reducer.sourceChanged(live, epoch2, 0)
        assertEquals(ns(100), resumed.display.measuredNanos)
        assertEquals("boot-0", resumed.display.measuredClockId)
        assertEquals(DisplayFreshness.HELD, reducer.tick(resumed, ns(9)).display.freshness)
        assertEquals(DisplayFreshness.STALE, reducer.tick(resumed, ns(10)).display.freshness)
    }

    @Test fun clockRegressionAndOldSourceGenerationAreContractErrors() {
        val state = accept(initial(), 2)
        assertThrows(IllegalArgumentException::class.java) { reducer.tick(state, ns(1)) }
        assertThrows(IllegalArgumentException::class.java) { reducer.sourceChanged(state, epoch, ns(2)) }
    }

    @Test fun displayTimingsAreValidatedAndIndependent() {
        assertThrows(IllegalArgumentException::class.java) { MotionDisplayConfig(freshForNanos = 0) }
        assertThrows(IllegalArgumentException::class.java) { MotionDisplayConfig(staleAfterNanos = 1) }
        assertThrows(IllegalArgumentException::class.java) { MotionDisplayConfig(recoveryForNanos = 0) }
        assertThrows(IllegalArgumentException::class.java) { MotionDisplayConfig(recoveryMaxGapNanos = Long.MAX_VALUE) }
    }
}
