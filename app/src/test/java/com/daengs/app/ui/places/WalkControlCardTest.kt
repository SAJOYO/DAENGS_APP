package com.daengs.app.ui.places

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.TrailSnapshot
import com.daengs.app.walk.WalkTrackingState
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkControlCardTest {
    @Test
    fun `duration uses clock form and grows an hour field when needed`() {
        assertEquals("00:00", formatWalkDuration(999L))
        assertEquals("01:01", formatWalkDuration(61_000L))
        assertEquals("1:01:01", formatWalkDuration(3_661_000L))
    }

    @Test
    fun `distance changes from meters to kilometres at one kilometre`() {
        assertEquals("999m", formatWalkDistance(999.4))
        assertEquals("1.0km", formatWalkDistance(1_000.0))
        assertEquals("2.3km", formatWalkDistance(2_349.0))
    }

    @Test
    fun `stopped trail with samples is labelled complete`() {
        val sample = LocationSample(GeoPoint(37.0, 127.0), capturedAtMillis = 1L)
        val state = WalkTrackingState(trail = TrailSnapshot(segments = listOf(listOf(sample))))

        assertEquals("기록 완료", walkStateLabel(state))
        assertEquals("기록 중", walkStateLabel(state.copy(trail = state.trail.copy(state = TrackingState.RECORDING))))
        assertEquals("일시정지", walkStateLabel(state.copy(trail = state.trail.copy(state = TrackingState.PAUSED))))
    }
}
