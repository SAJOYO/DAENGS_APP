package com.daengs.app.map.provider.naver

import org.junit.Assert.assertEquals
import org.junit.Test

class TravelHeadingRotationTest {
    @Test fun `east faces right on north up map and up on east up map`() {
        assertEquals(90f, screenTravelBearing(90f, 0.0))
        assertEquals(0f, screenTravelBearing(90f, 90.0))
        assertEquals(270f, screenTravelBearing(0f, 90.0))
        assertEquals(2f, screenTravelBearing(1f, 359.0))
    }
}
