package com.daengs.app.location

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class LocationBearingMappingTest {
    @Test fun `absent provider bearing does not become north`() {
        val sample = Location("fused").toSample()
        assertNull(sample.bearingDegrees)
        assertNull(sample.bearingAccuracyDegrees)
    }

    @Test fun `provider course and precision survive conversion`() {
        val sample = Location("fused").apply {
            bearing = 270f
            bearingAccuracyDegrees = 12f
            speed = 1.3f
            elapsedRealtimeNanos = 123_000_000L
        }.toSample()
        assertEquals(270f, sample.bearingDegrees)
        assertEquals(12f, sample.bearingAccuracyDegrees)
        assertEquals(1.3f, sample.speedMetersPerSecond)
        assertEquals(123_000_000L, sample.elapsedRealtimeNanos)
    }
}
