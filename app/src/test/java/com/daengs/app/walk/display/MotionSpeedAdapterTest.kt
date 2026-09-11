package com.daengs.app.walk.display

import com.daengs.app.walk.motion.*
import org.junit.Assert.*
import org.junit.Test

class MotionSpeedAdapterTest {
    private val epoch = DisplayEpoch(0, "e", "c", 0)
    private fun estimate() = MotionEstimate(MotionRef("s", 0, "e", "c", 0), PositionQuality.USABLE,
        1.5, SpeedSource.DEVICE, SpeedQuality.TRUSTED, 1, Movement.MOVING, emptySet())
    private fun MotionEstimate.sample() = displaySample("s", epoch)

    @Test fun identityAndClockMustMatch() {
        val ref = estimate().ref!!
        for (other in listOf(ref.copy(sessionId = "other"), ref.copy(sourceEpoch = "old"),
            ref.copy(clockEpochId = "old"), ref.copy(chainIndex = 1))) assertNull(estimate().copy(ref = other).sample())
        assertNull(estimate().copy(ref = null).sample())
        assertNull(estimate().copy(observedElapsedNanos = null).sample())
    }

    @Test fun unknownUnverifiedMockAndConflictingEvidenceAreMissingNotZero() {
        for (quality in listOf(SpeedQuality.UNKNOWN, SpeedQuality.UNVERIFIED))
            assertNull(estimate().copy(speedQuality = quality).sample()!!.speedMps)
        assertNull(estimate().copy(positionQuality = PositionQuality.MOCK).sample()!!.speedMps)
        for (reason in listOf(MotionReason.SPEED_CONFLICT, MotionReason.MOCK, MotionReason.INVALID_TIME,
            MotionReason.OUTSIDE_ACTIVE_INTERVAL, MotionReason.OUT_OF_ORDER, MotionReason.SAME_TIME_CONFLICT))
            assertNull(estimate().copy(reasons = setOf(reason)).sample()!!.speedMps)
        for (speed in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0))
            assertNull(estimate().copy(speedMps = speed).sample()!!.speedMps)
        assertEquals(0.0, estimate().copy(speedMps = 0.0).sample()!!.speedMps!!, 0.0)
    }

    @Test fun coordinateFallbackDoesNotRequireDeviceSpeedAndPathRejectionDoesNotHideTrustedSpeed() {
        val coordinate = estimate().copy(speedSource = SpeedSource.COORDINATE_WINDOW, speedQuality = SpeedQuality.ESTIMATED,
            reasons = setOf(MotionReason.SPEED_MISSING)).sample()!!
        assertEquals(DisplaySpeedSource.COORDINATE_WINDOW, coordinate.source)
        assertEquals(1.5, coordinate.speedMps!!, 0.0)
        assertEquals(12.0, estimate().copy(speedMps = 12.0, positionQuality = PositionQuality.UNCERTAIN,
            movement = Movement.HIGH_SPEED_SUSPECTED, reasons = setOf(MotionReason.POSITION_UNCERTAIN)).sample()!!.speedMps!!, 0.0)
    }
}
