package com.daengs.app.ui.walk

import com.daengs.app.walk.WalkSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkGameFormatTest {
    @Test
    fun `산책 시간은 시 분 초를 항상 보여준다`() {
        assertEquals("00:00:00", formatDuration(0L))
        assertEquals("01:02:03", formatDuration(3_723_999L))
    }

    @Test
    fun `거리는 천 미터부터 킬로미터로 바뀐다`() {
        assertEquals("842 m", formatDistance(842.4))
        assertEquals("1.84 km", formatDistance(1_840.0))
    }

    @Test
    fun `평균 속도는 이동 거리와 활동 시간으로 계산한다`() {
        val summary = previewSummary(distanceMeters = 2_000.0, activeDurationMillis = 1_800_000L)

        assertEquals("4.0 km/h", formatAverageSpeed(summary))
        assertEquals("-", formatAverageSpeed(summary.copy(activeDurationMillis = 0L)))
    }

    private fun previewSummary(distanceMeters: Double, activeDurationMillis: Long) = WalkSummary(
        sessionId = "walk-1",
        dogIds = emptyList(),
        startedAtMillis = 0L,
        endedAtMillis = 1L,
        weather = null,
        distanceMeters = distanceMeters,
        activeDurationMillis = activeDurationMillis,
        segments = emptyList(),
        anchor = null,
    )
}
