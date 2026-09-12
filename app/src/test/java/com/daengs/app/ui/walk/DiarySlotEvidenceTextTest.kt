package com.daengs.app.ui.walk

import com.daengs.app.walk.diary.DiarySlotEvidence
import org.junit.Assert.*
import org.junit.Test

class DiarySlotEvidenceTextTest {
    @Test fun `missing or null temperature is never displayed as zero or current weather`() {
        for (facts in listOf("{}", "{\"temperature_c\":null,\"observed_at\":null,\"grid\":null}")) {
            val text = slotEvidenceDescription(DiarySlotEvidence("test", "environment", "grid_temperature_observation", facts))
            assertFalse(text.contains("기온 (°C):"))
            assertFalse(text.contains("관측 시각:"))
            assertFalse(text.contains("격자 (nx, ny):"))
        }
    }

    @Test fun `zero degrees and zero observation age are valid supplied facts`() {
        val text = slotEvidenceDescription(DiarySlotEvidence("test", "environment", "grid_temperature_observation",
            "{\"temperature_c\":0,\"observation_age_s\":0,\"observed_at\":\"2026-09-11T17:00:00+09:00\"}"))
        assertTrue(text.contains("기온 (°C): 0"))
        assertTrue(text.contains("기록보다 앞선 시간 (초): 0"))
        assertTrue(text.contains("관측 시각: 2026-09-11T17:00:00+09:00"))
        assertFalse(text.contains("풍속"))
        assertFalse(text.contains("격자"))
    }
}
