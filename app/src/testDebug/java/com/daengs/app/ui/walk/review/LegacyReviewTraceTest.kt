package com.daengs.app.ui.walk.review

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.summarizeLegacy
import org.junit.Assert.*
import org.junit.Test

class LegacyReviewTraceTest {
    @Test fun `diagnostic replay agrees with the historical summary and separates discarded point causes`() {
        val fixes = listOf(fix(0, 0.0), fix(1, 1.0), fix(2, 4.0), fix(3, 40.0),
            fix(4, 6.0).copy(accuracyM = 100f), fix(5, 8.0), fix(6, 10.0).copy(recordingEligible = false),
            fix(7, 1000.0).copy(chainIndex = 1), fix(8, 1004.0).copy(chainIndex = 1))
        val trace = legacyReviewTrace(fixes)
        val summary = summarizeLegacy(RecordedSession(id = "review", dogIds = emptyList(),
            startedAtMillis = 0, endedAtMillis = 9000, weather = null), fixes)
        assertEquals(summary.distanceMeters, trace.getDouble("distance_m"), 1e-8)
        val rows = trace.getJSONArray("decisions")
        assertEquals(fixes.size, rows.length())
        assertEquals(listOf("retained", "below_min_distance", "retained", "too_fast", "low_accuracy",
            "retained", "recording_ineligible", "retained", "retained"),
            (0 until rows.length()).map { rows.getJSONObject(it).getString("disposition") })
        assertEquals(0.0, rows.getJSONObject(7).getDouble("distance_delta_m"), 0.0)
        assertTrue(legacyReviewTrace(fixes, speedLimit = Double.POSITIVE_INFINITY)
            .getDouble("distance_m") > summary.distanceMeters)
    }

    private fun fix(seq: Int, meters: Double) = RecordedFix(seq, 0, seq * 1000L,
        0.0, meters / 111_195, 3f, false)
}
