package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import org.junit.Assert.*
import org.junit.Test

class StayStampRecorderTest {
    private fun sample(t: Int, x: Double = 0.0) = LocationSample(
        GeoPoint(37.5 + Math.toDegrees(x / 6_371_000.0), 127.0),
        t * 1000L, accuracyMeters = 3f,
    )
    private fun feed(r: StayStampRecorder, from: Int, to: Int, x: Double = 0.0) {
        for (t in from..to step 2) r.add(sample(t, x))
    }

    @Test fun `stationary stamp appears at threshold and never grows`() {
        val r = StayStampRecorder()
        feed(r, 0, 28); assertTrue(r.snapshot().isEmpty())
        feed(r, 30, 30); val fixed = r.snapshot()
        feed(r, 32, 100_000, 1.0)
        assertSame(fixed, r.snapshot())
        assertEquals(30_000L, fixed.single().confirmedAtMillis)
        r.reset(); assertTrue(r.snapshot().isEmpty())
    }

    @Test fun `slow drift uses fixed origin and does not create a rolling stay`() {
        val r = StayStampRecorder()
        for (t in 0..200 step 2) r.add(sample(t, t * .3))
        assertTrue(r.snapshot().isEmpty())
    }

    @Test fun `candidate resets after gap without counting unknown time`() {
        val r = StayStampRecorder()
        feed(r, 0, 20); feed(r, 40, 68); assertTrue(r.snapshot().isEmpty())
        r.add(sample(70)); assertEquals(70_000L, r.snapshot().single().confirmedAtMillis)
    }

    @Test fun `pause resets candidate even with a short gap`() {
        val r = StayStampRecorder()
        feed(r, 0, 20); r.breakContinuity(); feed(r, 22, 50)
        assertTrue(r.snapshot().isEmpty())
        r.add(sample(52)); assertEquals(22_000L, r.snapshot().single().startedAtMillis)
    }

    @Test fun `gap after confirmation cannot prove departure`() {
        val r = StayStampRecorder()
        feed(r, 0, 30); val fixed = r.snapshot()
        feed(r, 100, 200, 8.0); assertSame(fixed, r.snapshot())
        feed(r, 202, 206, 12.0)
        feed(r, 300, 304, 12.0) // departure evidence before the gap is discarded
        feed(r, 306, 334, 12.0); assertEquals(1, r.snapshot().size)
        r.add(sample(336, 12.0)); assertEquals(2, r.snapshot().size)
    }

    @Test fun `new place after exit qualifies while revisit keeps original stamp`() {
        val r = StayStampRecorder()
        feed(r, 0, 30); val first = r.snapshot().single()
        feed(r, 32, 100, 12.0); assertEquals(2, r.snapshot().size)
        feed(r, 102, 180); assertEquals(2, r.snapshot().size)
        assertEquals(first, r.snapshot().first())
    }

    @Test fun `bad accuracy null accuracy mock nonfinite and duplicate fixes reset evidence`() {
        val bad = listOf(sample(22).copy(accuracyMeters = null), sample(22).copy(accuracyMeters = 40f),
            sample(22).copy(accuracyMeters = Float.NaN), sample(22).copy(accuracyMeters = -1f),
            sample(22).copy(isMock = true), sample(22).copy(point = GeoPoint(Double.NaN, 127.0)), sample(20))
        for (fix in bad) {
            val r = StayStampRecorder(); feed(r, 0, 20); r.add(fix); feed(r, 24, 52)
            assertTrue(r.snapshot().isEmpty())
        }
    }

    @Test fun `spike cannot confirm departure and resetting timestamps cannot add evidence`() {
        val r = StayStampRecorder(); feed(r, 0, 30)
        r.add(sample(32, 100.0)); feed(r, 34, 80)
        assertEquals(1, r.snapshot().size)
        r.add(sample(10)); feed(r, 12, 50, 20.0)
        assertEquals(1, r.snapshot().size)
    }

    @Test fun `raw sub-three-meter observations qualify even when trail prunes them`() {
        val r = StayStampRecorder(); val trail = TrailRecorder(); trail.start()
        for (t in 0..60 step 2) { val fix = sample(t, kotlin.math.sin(t.toDouble())); r.add(fix); trail.add(fix) }
        assertEquals(1, trail.snapshot().sampleCount)
        assertEquals(1, r.snapshot().size)
        assertEquals(0f, WALK_OBSERVATION_CONFIG.minDistanceMeters)
    }

    @Test fun `persisted replay matches live including chain breaks gaps and quality`() {
        val r = StayStampRecorder()
        val fixes = mutableListOf<RecordedFix>()
        for (t in 0..180 step 2) {
            val chain = if (t >= 20) 1 else 0
            if (t == 20) r.breakContinuity()
            val sample = sample(t, if (t < 100) 0.0 else 12.0).copy(speedMetersPerSecond = 99f)
            r.add(sample)
            fixes += RecordedFix(fixes.size, chain, sample.capturedAtMillis, sample.point.latitude,
                sample.point.longitude, sample.accuracyMeters, sample.isMock)
        }
        assertEquals(2, r.snapshot().size)
        assertEquals(r.snapshot(), detectStayStamps(fixes.reversed()))
    }
}
