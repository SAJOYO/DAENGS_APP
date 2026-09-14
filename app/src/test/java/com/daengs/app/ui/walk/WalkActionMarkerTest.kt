package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.walk.WalkMoment
import com.daengs.app.walk.WalkMomentAction
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.WalkSessionRoute
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.WalkTrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkActionMarkerTest {
    private val point = GeoPoint(37.5, 127.0)
    private fun moment(vararg types: WalkMomentType) = WalkMoment(
        "action", point, types.associateWith { WalkMomentAction(it, 100, 100) },
        locationLabel = "추정 위치",
    )

    @Test fun `live walk and territory preserve behavior identity coordinates and selection`() {
        for (purpose in listOf(MapPurpose.WALK, MapPurpose.TERRITORY)) {
            for (type in WalkMomentType.entries) {
                val state = WalkUiState(
                    tracking = WalkTrackingState(momentGroups = listOf(moment(type))),
                    map = WalkMapUiState(purpose = purpose, selectedMomentId = "action"),
                )
                val marker = state.toMapPresentation().scene.moments.single()
                assertEquals(setOf(type), marker.behaviors)
                assertEquals(point, marker.point)
                assertTrue(marker.selected)
                assertTrue(marker.label.contains("추정 위치"))
            }
        }
    }

    @Test fun `completed walk retains every co-located behavior instead of guessing from caption`() {
        val group = moment(WalkMomentType.SNIFFING, WalkMomentType.BARKING, WalkMomentType.EXCRETION)
        val detail = WalkSessionDetail(
            WalkSummary("finished", emptyList(), 0, 10_000, null, 100.0, 10_000, emptyList(), point),
            WalkSessionRoute(emptyList()), listOf(group),
        )
        val marker = WalkUiState(completion = WalkCompletionUiState(detail = detail))
            .toMapPresentation().scene.moments.single()
        assertEquals(group.types, marker.behaviors)
        assertEquals(point, marker.point)
        assertTrue(marker.label.contains("외 2개"))
    }
}
