package com.daengs.app.walk.routeexplorer

import com.daengs.app.walk.RecordingEpoch
import org.junit.Assert.*
import org.junit.Test

class MeasurementRangePlaybackTest {
    @Test fun `gap advances only to a proven edge within the requested bound and ignores wall clock correction`() {
        for (corrected in listOf(false, true)) {
            val time = CompletedRouteReview(measuredTimedDetail(corrected)).timeline!!
            assertNull(time.nextPlayablePosition(30_000, 40_000))
            assertNull(time.nextPlayablePosition(0, 10_000)) // Touching the first point is not a movement interval.
            assertNull(time.nextPlayablePosition(55_000, 55_999))
            assertEquals(56_000L, time.nextPlayablePosition(55_000, 60_000))
            assertFalse(time.frameAt(time.nextPlayablePosition(55_000, 60_000)!!).inGap)
            assertNull(time.nextPlayablePosition(-1, 60_000))
            assertNull(time.nextPlayablePosition(60_000, 55_000))
            assertNull(time.nextPlayablePosition(0, 100_001))
        }
    }

    @Test fun `recording epoch boundaries with repeated wall clocks never become replay edges`() {
        val base = measuredSceneDetail(secondVisit = true)
        val epochs = (0..1).map { n -> RecordingEpoch("epoch-$n", "measured", "clock-$n", n,
            0, 0, n * 5L, endedAtMillis = 100_000, endedElapsedNanos = 12_000_000_000, endKind = "stop", drained = true) }
        val time = CompletedRouteReview(base.copy(measurement = base.measurement!!.copy(recordingEpochs = epochs))).timeline!!
        assertNull(time.nextPlayablePosition(11_000, 13_999))
        assertEquals(14_000L, time.nextPlayablePosition(11_000, 16_000))
        assertEquals("epoch-1", time.address(14_000)!!.sourceEpoch)
        assertTrue(time.frameAt(12_000).inGap)
    }

    @Test fun `fractional millisecond edge starts round into the edge instead of accepting an earlier isolated point`() {
        val base = measuredSceneDetail()
        val epoch = RecordingEpoch("epoch-0", "measured", "clock-0", 0, 0, 0, 0,
            endedAtMillis = 100_000, endedElapsedNanos = 12_000_000_000, endKind = "stop", drained = true)
        val shifted = base.copy(observations = base.observations.map { it.copy(elapsedRealtimeNanos = it.elapsedRealtimeNanos!! + 250_000) },
            measurement = base.measurement!!.copy(recordingEpochs = listOf(epoch)))
        val time = CompletedRouteReview(shifted).timeline!!
        assertNull(time.nextPlayablePosition(0, 2_000))
        assertEquals(2_001L, time.nextPlayablePosition(0, 3_000))
        assertTrue(time.frameAt(2_000).inGap)
        assertFalse(time.frameAt(2_001).inGap)
    }
}
