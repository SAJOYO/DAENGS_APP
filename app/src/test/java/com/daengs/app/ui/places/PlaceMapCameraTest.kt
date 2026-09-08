package com.daengs.app.ui.places

import com.daengs.app.location.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class PlaceMapCameraTest {
    private val origin = GeoPoint(37.5665, 126.9780)
    private val other = GeoPoint(37.57, 126.98)

    @Test fun initialAndProgrammaticMovesNeverOfferAnotherSearch() {
        assertNull(PlaceMapCamera().idle(other).searchPoint(origin))
        assertNull(PlaceMapCamera().idle(other).searchPoint(null))
    }

    @Test fun aGestureMustFinishAndMoveBeyondCameraJitter() {
        val moving = PlaceMapCamera().idle(origin).gesture()
        assertNull(moving.searchPoint(origin))
        assertNull(moving.idle(GeoPoint(37.56651, 126.97801)).searchPoint(origin))
        assertEquals(other, moving.idle(other).searchPoint(origin))
        assertEquals(other, moving.idle(other).searchPoint(null))
    }

    @Test fun submittingHidesTheButtonUntilTheNextGestureAndNewArea() {
        val submitted = PlaceMapCamera().gesture().idle(other).submitted()
        assertNull(submitted.searchPoint(origin))
        assertNull(submitted.idle(origin).searchPoint(other))
        assertNull(submitted.gesture().idle(other).searchPoint(other))
        assertEquals(origin, submitted.gesture().idle(origin).searchPoint(other))
    }
}
