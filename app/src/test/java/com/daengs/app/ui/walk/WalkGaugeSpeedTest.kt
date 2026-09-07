package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.TrackingState
import org.junit.Assert.*
import org.junit.Test

class WalkGaugeSpeedTest {
    private val now = 20_000_000_000L
    private val fix = LocationSample(GeoPoint(37.5, 127.0), 0, now - 1_000_000_000L, 5f, 1.2f)

    @Test fun freshSpeedAndRealZeroAreAvailable() {
        assertEquals(1.2f, walkGaugeSpeed(fix, TrackingState.RECORDING, now))
        assertEquals(0f, walkGaugeSpeed(fix.copy(speedMetersPerSecond = 0f), TrackingState.RECORDING, now))
    }
    @Test fun pausedStaleFutureAndMissingFixHaveNoNeedle() {
        assertNull(walkGaugeSpeed(fix, TrackingState.PAUSED, now))
        assertNull(walkGaugeSpeed(fix, TrackingState.OFF, now))
        assertNull(walkGaugeSpeed(fix, TrackingState.RECORDING, now + 8_000_000_000L))
        assertNull(walkGaugeSpeed(fix.copy(elapsedRealtimeNanos = now + 1), TrackingState.RECORDING, now))
        assertNull(walkGaugeSpeed(null, TrackingState.RECORDING, now))
    }
    @Test fun unusableSpeedOrAccuracyDoesNotBecomeZero() {
        for (speed in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f))
            assertNull(walkGaugeSpeed(fix.copy(speedMetersPerSecond = speed), TrackingState.RECORDING, now))
        assertNull(walkGaugeSpeed(fix.copy(accuracyMeters = 80f), TrackingState.RECORDING, now))
    }
}
