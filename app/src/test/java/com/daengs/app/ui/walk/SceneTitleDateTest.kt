package com.daengs.app.ui.walk

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneTitleDateTest {
    @Test fun `walk heading is the date rather than a generated scene title`() {
        val walk = previewDiarySummary()
        assertEquals("${formatWalkDay(walk.startedAtMillis)} 산책", walkDiaryTitle(walk))
    }
}
