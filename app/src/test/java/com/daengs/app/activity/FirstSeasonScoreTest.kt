package com.daengs.app.activity

import org.junit.Assert.*
import org.junit.Test

class FirstSeasonScoreTest {
    private fun body(extra: String = "", rank: String = "null") = """{
      "season_id":"territory-2026-09","pet_id":"00000000-0000-0000-0000-000000000002",
      "status":"READY","source_revision":2,"processed_revision":2,"statistics":null,
      "score":{"bonus":120,"holding_units":432000000000,"held_site_ms":7200000,
      "current_count":1,"scoring_count":1,"peak":1,"claims":1,"takeovers":1,"last_ms":2000 $extra},
      "score_as_of_ms":2000,"sources":[],"final_rank":$rank} """

    @Test fun `new score displays member base bonus takeover and holding separately`() {
        val summary = ActivityJson.territory(body(",\"base_bonus\":100,\"takeover_bonus\":20", "1"))
        assertEquals(1L, summary.finalRank)
        assertEquals(120L, summary.score!!.bonus)
        assertEquals("기본 점령 100점 · 탈취 보너스 20점 · 보유 12점", activityScoreBreakdown(summary.score!!))
    }
    @Test fun `legacy score does not invent zero breakdown and invalid sum is rejected`() {
        assertNull(activityScoreBreakdown(ActivityJson.territory(body()).score!!))
        assertTrue(runCatching { ActivityJson.territory(body(",\"base_bonus\":100,\"takeover_bonus\":100")) }.isFailure)
        assertTrue(runCatching { ActivityJson.territory(body(rank = "0")) }.isFailure)
        assertNull(ActivityJson.territory(body()).finalRank)
    }
}
