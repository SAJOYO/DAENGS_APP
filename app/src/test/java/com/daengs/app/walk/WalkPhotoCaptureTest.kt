package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import org.junit.Assert.*
import org.junit.Test

class WalkPhotoCaptureTest {
    private val sample = LocationSample(GeoPoint(37.5, 127.0), 1000, 1_000_000_000, 5f)
    private val walking = WalkTrackingState(ownerId = "owner", activeSessionId = "walk",
        trail = TrailSnapshot(state = TrackingState.RECORDING), latestMomentFix = sample)
    private fun capture(state: WalkTrackingState = walking, elapsed: Long = 2_000_000_000) =
        beginWalkPhotoCapture(state, "owner", 2000, elapsed)

    @Test fun `셔터 뒤 이동과 새 세션은 촬영 정보를 바꾸지 않는다`() {
        val captured = capture()!!
        val moved = walking.copy(activeSessionId = "next", latestMomentFix = sample.copy(point = GeoPoint(37.6, 127.1)))
        assertNotEquals(capture(moved), captured)
        assertEquals("walk", captured.sessionId)
        assertEquals(sample.point, captured.sample.point)
        assertEquals(2000L, captured.capturedAtMillis)
        assertEquals(1000L, captured.sample.capturedAtMillis)
    }

    @Test fun `시작 전 일시정지 종료 중 계정 변경에는 촬영하지 못한다`() {
        assertNull(capture(walking.copy(activeSessionId = null)))
        assertNull(capture(walking.copy(trail = TrailSnapshot(state = TrackingState.PAUSED))))
        assertNull(capture(walking.copy(trail = TrailSnapshot(state = TrackingState.OFF))))
        assertNull(capture(walking.copy(finishingSessionId = "walk")))
        assertNull(capture(walking.copy(ownerId = "other")))
    }

    @Test fun `오래되거나 모의 위치거나 부정확한 GPS로 사진 Pin을 만들지 않는다`() {
        assertNotNull(capture(elapsed = 11_000_000_000))
        assertNull(capture(elapsed = 11_000_000_001))
        assertNull(capture(elapsed = 999_999_999))
        assertNull(capture(walking.copy(latestMomentFix = null)))
        for (bad in listOf(sample.copy(isMock = true), sample.copy(elapsedRealtimeNanos = null),
            sample.copy(accuracyMeters = null), sample.copy(accuracyMeters = Float.NaN),
            sample.copy(accuracyMeters = Float.POSITIVE_INFINITY), sample.copy(accuracyMeters = -1f),
            sample.copy(accuracyMeters = 15.1f))) {
            assertNull(capture(walking.copy(latestMomentFix = bad)))
        }
    }
}
