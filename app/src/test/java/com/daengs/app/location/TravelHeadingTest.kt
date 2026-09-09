package com.daengs.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TravelHeadingTest {
    private val moving = LocationSample(GeoPoint(37.5, 127.0), 0,
        elapsedRealtimeNanos = 1_000_000_000, accuracyMeters = 5f,
        speedMetersPerSecond = 1.2f, bearingDegrees = 90f, bearingAccuracyDegrees = 10f)

    @Test fun `moving course includes north and wraps around zero`() {
        assertEquals(90f, moving.travelHeading()!!.degrees)
        assertEquals(0f, moving.copy(bearingDegrees = 0f).travelHeading()!!.degrees)
        assertEquals(359f, moving.copy(bearingDegrees = -1f).travelHeading()!!.degrees)
        assertEquals(1f, moving.copy(bearingDegrees = 361f).travelHeading()!!.degrees)
        assertEquals(90f, moving.copy(bearingAccuracyDegrees = null).travelHeading()!!.degrees)
    }

    @Test fun `stationary unknown or inaccurate course is hidden`() {
        listOf(
            moving.copy(speedMetersPerSecond = 0f), moving.copy(speedMetersPerSecond = 0.49f),
            moving.copy(speedMetersPerSecond = null), moving.copy(speedMetersPerSecond = Float.NaN),
            moving.copy(bearingDegrees = null), moving.copy(bearingDegrees = Float.POSITIVE_INFINITY),
            moving.copy(accuracyMeters = null), moving.copy(accuracyMeters = 26f),
            moving.copy(accuracyMeters = Float.NaN), moving.copy(accuracyMeters = -1f),
            moving.copy(bearingAccuracyDegrees = 46f), moving.copy(bearingAccuracyDegrees = Float.NaN),
            moving.copy(elapsedRealtimeNanos = null), moving.copy(elapsedRealtimeNanos = 0),
        ).forEach { assertNull(it.toString(), it.travelHeading()) }
    }

    @Test fun `cached course expires without needing another location update`() {
        val heading = moving.travelHeading()!!
        assertEquals(10_000L, heading.remainingMillis(1_000_000_000))
        assertEquals(1L, heading.remainingMillis(10_999_000_000))
        assertEquals(0L, heading.remainingMillis(11_000_000_000))
        assertEquals(0L, heading.remainingMillis(99_000_000_000))
        assertEquals(0L, heading.remainingMillis(999_000_000))
    }
}
