package com.daengs.app.walk.display

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MotionDisplaySessionTest {
    private val epoch = DisplayEpoch(0, "source", "boot", 0)

    @Test fun uiUnsubscribeAndResubscribeKeepStateAndNewSessionStartsAtZero() = runTest {
        val owner = MotionDisplaySession("walk", epoch, 0)
        val first = mutableListOf<MotionDisplay>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { owner.display.collect { first += it } }
        owner.onSamples(listOf(DisplaySpeedSample("walk", epoch, 0, 1.2)), 0)
        job.cancel()
        owner.onTick(11_000_000_000L)
        val second = mutableListOf<MotionDisplay>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { owner.display.collect { second += it } }
        assertEquals(1.2, second.single().speedMps, 0.0)
        assertEquals(DisplayFreshness.STALE, second.single().freshness)
        assertEquals(0.0, MotionDisplaySession("next", epoch, 0).display.value.speedMps, 0.0)
    }

    @Test fun oneBatchPublishesOnlyItsLatestReadingAndIdenticalTicksDoNotPublish() = runTest {
        val owner = MotionDisplaySession("walk", epoch, 0)
        val values = mutableListOf<MotionDisplay>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { owner.display.collect { values += it } }
        owner.onSamples((0L..3).map { DisplaySpeedSample("walk", epoch, it * 1_000_000_000L, it.toDouble()) }, 3_000_000_000L)
        assertEquals(listOf(0.0, 3.0), values.map { it.speedMps })
        repeat(10) { owner.onTick(3_000_000_000L + it) }
        assertEquals(2, values.size)
    }
}
