package com.daengs.app.walk.routeexplorer

import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Test

internal fun measuredTimedDetail(clockCorrection: Boolean = false): WalkSessionDetail {
    val detail = measuredObservedDetail(clockCorrection)
    return detail.copy(measurement = detail.measurement!!.copy(recordingEpochs = listOf(
        RecordingEpoch("epoch-0", detail.summary.sessionId, "clock-0", 0, 0, 0, 0,
            endedAtMillis = 100_000, endedElapsedNanos = 100_000_000_000, endKind = "stop", drained = true))))
}

class MeasurementTimelineTest {
    @Test fun `display speed follows the replay edge and remains absent in gaps`() {
        val original = CompletedRouteReview(measuredTimedDetail()).timeline!!
        val shifted = CompletedRouteReview(measuredTimedDetail(true)).timeline!!
        for (at in listOf(13_000L, 57_000L)) {
            val speed = original.frameAt(at).derivedSpeedMetersPerSecond
            assertNotNull(speed)
            assertTrue(speed!! >= 0 && speed.isFinite())
            assertEquals(speed, shifted.frameAt(at).derivedSpeedMetersPerSecond)
        }
        assertNull(original.frameAt(30_000).derivedSpeedMetersPerSecond)
    }
    @Test fun `large boot clocks are subtracted before adding recording offsets`() {
        val base = measuredSceneDetail(secondVisit = true)
        val boot = Long.MAX_VALUE - 12_000_000_000L
        val epochs = (0..1).map { n -> RecordingEpoch("epoch-$n", "measured", "clock-$n", n, 0,
            if (n == 0) 0 else boot, n * 5L, endedAtMillis = 100_000,
            endedElapsedNanos = if (n == 0) 12_000_000_000 else Long.MAX_VALUE, endKind = "stop", drained = true) }
        val time = CompletedRouteReview(base.copy(measurement = base.measurement!!.copy(recordingEpochs = epochs))).timeline!!
        assertEquals(24_000L, time.durationMillis)
        val address = time.address(18_000)!!
        assertEquals(boot + 6_000_000_000L, address.elapsedNanos)
        assertEquals(18_000L, time.position(address))
    }
    @Test fun `time slices preserve measured owners and do not bridge a gap or change metrics`() {
        val detail = measuredTimedDetail(); val review = CompletedRouteReview(detail); val time = review.timeline!!
        assertEquals(100_000L, time.durationMillis)
        val first = time.slice(0, 30_000)!!; val second = time.slice(30_000, 100_000)!!
        assertEquals(listOf(detail.route.segments.first().points.map { it.point }), first.walking)
        assertEquals(listOf(detail.route.segments.last().points.map { it.point }), second.walking)
        assertTrue(first.observed.all { it.fixes.last().clientSeq <= 6 })
        assertTrue(second.observed.all { it.fixes.first().clientSeq >= 7 })
        val gap = time.slice(30_000, 40_000)!!
        assertTrue(gap.walking.isEmpty()); assertTrue(gap.observed.isEmpty())
        assertEquals(123.0, detail.summary.distanceMeters, 0.0)
        assertNull(time.slice(-1, 100)); assertNull(time.slice(1, 1)); assertNull(time.slice(0, 100_001))
    }
    @Test fun `playback follows included or excluded original edges and hides unresolved and missing locations`() {
        val time = CompletedRouteReview(measuredTimedDetail()).timeline!!
        for (millis in listOf(13_000L, 15_000L, 57_000L, 65_000L)) assertFalse("$millis", time.frameAt(millis).inGap)
        for (millis in listOf(0L, 19_000L, 30_000L, 55_000L, 61_000L, 90_000L)) assertTrue("$millis", time.frameAt(millis).inGap)
        assertNull(time.frameAt(30_000).point)
        assertFalse(time.frameAt(20_000).inGap) // An exact usable source observation is a real point.
    }
    @Test fun `wall clock correction changes labels but not time address geometry or restored position`() {
        val original = CompletedRouteReview(measuredTimedDetail()).timeline!!
        val shifted = CompletedRouteReview(measuredTimedDetail(true)).timeline!!
        assertEquals(original.address(57_000), shifted.address(57_000))
        assertEquals(original.frameAt(57_000).point, shifted.frameAt(57_000).point)
        assertEquals(57_000L, shifted.position(original.address(57_000)!!))
        assertNotEquals(original.frameAt(57_000).recordedAtMillis, shifted.frameAt(57_000).recordedAtMillis)
        assertNull(shifted.position(original.address(57_000)!!.copy(clockEpoch = "foreign")))
    }
    @Test fun `two epochs with repeated clocks have separate source addresses and an empty boundary`() {
        val base = measuredSceneDetail(secondVisit = true)
        val epochs = (0..1).map { n -> RecordingEpoch("epoch-$n", "measured", "clock-$n", n,
            0, 0, n * 5L, endedAtMillis = 100_000, endedElapsedNanos = 12_000_000_000, endKind = "stop", drained = true) }
        val time = CompletedRouteReview(base.copy(measurement = base.measurement!!.copy(recordingEpochs = epochs))).timeline!!
        assertEquals(24_000L, time.durationMillis)
        assertEquals("epoch-1", time.address(12_000)!!.sourceEpoch)
        assertTrue(time.frameAt(12_000).inGap)
        assertEquals(6_000L, time.sourcePosition(base.observations[2].measurementRef("measured")))
        assertEquals(18_000L, time.sourcePosition(base.observations[7].measurementRef("measured")))
    }
}
