package com.daengs.app.ui.home

import com.daengs.app.walk.WalkDayTotals
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeWalkSpeedTest {
    @Test fun speedUsesCombinedDistanceAndActivityTime() {
        // 1 km in 10 min + 2 km in 50 min => 3 km/h, not the per-walk mean 4.2.
        assertEquals("3.0km/h", formatHomeWalkSpeed(WalkDayTotals(2, 3_600_000, 3000.0)))
        assertEquals("4.3km/h", formatHomeWalkSpeed(WalkDayTotals(2, 1_920_000, 2300.0)))
    }

    @Test fun missingOrInvalidActivityHasNoInventedSpeed() {
        listOf(null, WalkDayTotals.EMPTY, WalkDayTotals(1, 0, 100.0),
            WalkDayTotals(1, -1, 100.0), WalkDayTotals(1, 60_000, Double.NaN),
            WalkDayTotals(1, 60_000, Double.POSITIVE_INFINITY), WalkDayTotals(1, 60_000, -10.0))
            .forEach { assertEquals("—", formatHomeWalkSpeed(it)) }
    }
}
