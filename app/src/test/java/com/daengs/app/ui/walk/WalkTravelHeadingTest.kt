package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.WalkSessionRoute
import com.daengs.app.walk.WalkSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkTravelHeadingTest {
    private val sample = LocationSample(GeoPoint(37.5, 127.0), 0,
        elapsedRealtimeNanos = 1_000_000_000, accuracyMeters = 5f,
        speedMetersPerSecond = 1f, bearingDegrees = 90f)
    private val state = WalkUiState(location = WalkLocationUiState(
        permissionGranted = true, precisePermission = true,
        currentPosition = sample.point, sample = sample))

    @Test fun `screen and tracking service samples both reach walk and territory maps`() {
        for (owner in listOf(WalkLocationOwner.SCREEN, WalkLocationOwner.TRACKING_SERVICE)) {
            for (purpose in listOf(MapPurpose.WALK, MapPurpose.TERRITORY)) {
                val scene = state.copy(location = state.location.copy(owner = owner),
                    map = WalkMapUiState(purpose = purpose)).toMapPresentation().scene
                assertEquals(90f, scene.travelHeading?.degrees)
                assertEquals(sample.point, scene.currentPosition)
            }
        }
    }

    @Test fun `missing precise permission current position or course hides heading`() {
        listOf(
            state.copy(location = state.location.copy(permissionGranted = false)),
            state.copy(location = state.location.copy(precisePermission = false)),
            state.copy(location = state.location.copy(currentPosition = null)),
            state.copy(location = state.location.copy(sample = sample.copy(bearingDegrees = null))),
            state.copy(location = state.location.copy(sample = sample.copy(speedMetersPerSecond = 0f))),
            state.copy(map = WalkMapUiState(purpose = MapPurpose.PLACE_SEARCH)),
        ).forEach { assertNull(it.toMapPresentation().scene.travelHeading) }
    }

    @Test fun `finished walk never shows live direction`() {
        val detail = WalkSessionDetail(
            summary = WalkSummary("finished", emptyList(), 0, 10_000, null, 100.0,
                10_000, emptyList(), sample.point),
            route = WalkSessionRoute(emptyList()), moments = emptyList())
        val scene = state.copy(completion = WalkCompletionUiState(detail = detail)).toMapPresentation().scene
        assertNull(scene.currentPosition)
        assertNull(scene.travelHeading)
    }
}
