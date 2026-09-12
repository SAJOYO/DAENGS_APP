package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.territory.TerritoryBoardState
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.territory.TerritoryFailure
import com.daengs.app.territory.TerritorySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryBoardPresentationTest {
    @Test fun `map ownership follows the member flag even when another dog is selected`() {
        val point = site("one")
        for (mine in listOf(true, false)) for (certification in com.daengs.app.territory.ClaimCertification.entries) {
            val occupancy = com.daengs.app.territory.TerritoryOccupancy("owner-dog", null, null, certification, 1000)
            val target = com.daengs.app.map.features.territory.TerritoryGameSite(point,
                com.daengs.app.territory.TerritoryClaimSite(point.id, occupancy = occupancy), "두부", null, null, false,
                isOwnedByMe = mine)
            val state = WalkUiState(map = WalkMapUiState(purpose = MapPurpose.TERRITORY),
                selection = WalkSelectionState(selectedDogIds = setOf("different-dog")),
                territory = TerritoryBoardState(sites = listOf(point)),
                territoryGame = com.daengs.app.map.features.territory.TerritoryGameState(enabled = true, sites = listOf(target)))
            val marker = state.toMapPresentation().scene.territorySites.single()
            assertEquals(mine, marker.isMine)
            assertEquals(certification.name, marker.occupancy.name)
            assertEquals(mine, state.copy(selection = WalkSelectionState(selectedDogIds = setOf("owner-dog")))
                .toMapPresentation().scene.territorySites.single().isMine)
        }
    }

    @Test fun `reading a far site keeps the inspected viewport instead of fitting the device too`() {
        val far = site("far").copy(point = GeoPoint(35.0, 129.0))
        val target = com.daengs.app.map.features.territory.TerritoryGameSite(far,
            com.daengs.app.territory.TerritoryClaimSite(far.id), "", null, null, false)
        val state = WalkUiState(map = WalkMapUiState(purpose = MapPurpose.TERRITORY, frameSelectedTerritory = true),
            location = WalkLocationUiState(currentPosition = GeoPoint(37.5, 127.0), permissionGranted = true),
            territory = TerritoryBoardState(sites = listOf(far), selectedSiteId = far.id),
            territoryGame = com.daengs.app.map.features.territory.TerritoryGameState(enabled = true,
                readOnly = true, sites = listOf(target), targetId = far.id))
        val presentation = state.toMapPresentation()
        assertEquals(null, presentation.fitBounds)
        assertTrue(presentation.scene.territorySites.single().selected)
    }

    @Test fun `sites without a game provider are unknown rather than vacant`() {
        val point = site("one")
        val state = WalkUiState(map = WalkMapUiState(purpose = MapPurpose.TERRITORY),
            territory = TerritoryBoardState(sites = listOf(point), selectedSiteId = point.id))
        val marker = state.toMapPresentation().scene.territorySites.single()
        assertEquals("점유 확인 전", marker.label)
        assertFalse(marker.occupancyKnown)
        assertTrue(marker.selected)
        assertEquals(null, marker.radiusMeters)
    }

    @Test fun `read failure never projects as a known vacant marker`() {
        val point = site("one")
        val target = com.daengs.app.map.features.territory.TerritoryGameSite(point,
            com.daengs.app.territory.TerritoryClaimSite(point.id), "", null, null, false,
            occupancyKnown = false,
            occupancyReadState = com.daengs.app.map.features.territory.TerritoryOccupancyReadState.FAILED)
        val state = WalkUiState(map = WalkMapUiState(purpose = MapPurpose.TERRITORY),
            territory = TerritoryBoardState(sites = listOf(point), selectedSiteId = point.id),
            territoryGame = com.daengs.app.map.features.territory.TerritoryGameState(enabled = true,
                readOnly = true, sites = listOf(target), targetId = point.id))
        val failed = state.toMapPresentation().scene.territorySites.single()
        assertEquals("점유 조회 실패", failed.label)
        assertFalse(failed.occupancyKnown)
        val neutral = state.copy(territoryGame = state.territoryGame.copy(sites = listOf(target.copy(
            occupancyKnown = true,
            occupancyReadState = com.daengs.app.map.features.territory.TerritoryOccupancyReadState.READY))))
            .toMapPresentation().scene.territorySites.single()
        assertTrue(neutral.occupancyKnown)
        assertEquals("미점유", neutral.label)
    }

    @Test fun `occupancy time is rendered in the device zone`() {
        val time = java.time.Instant.parse("2026-09-08T13:00:00Z").toEpochMilli()
        assertEquals("2026.09.08 22:00", territoryOccupiedAtLabel(time, java.time.ZoneId.of("Asia/Seoul")))
    }

    @Test
    fun `empty initial state waits for a device position`() {
        assertEquals(
            "현재 위치가 잡히면 주변 점령지를 보여드려요",
            territoryStatusLabel(TerritoryBoardState()),
        )
    }

    @Test
    fun `refresh preserves the visible site count in its message`() {
        val state = TerritoryBoardState(
            sites = listOf(site("one"), site("two")),
            loadedOrigin = GeoPoint(37.5, 127.0),
            loading = true,
        )

        assertEquals("점령지 2곳 · 새 지역을 불러오는 중이에요", territoryStatusLabel(state))
    }

    @Test
    fun `failure explains retry without erasing existing sites`() {
        val state = TerritoryBoardState(
            sites = listOf(site("old")),
            loadedOrigin = GeoPoint(37.5, 127.0),
            failure = TerritoryFailure.Offline,
        )

        assertEquals("인터넷 연결을 확인해 주세요.", territoryStatusLabel(state))
    }

    @Test
    fun `device fixes cannot replace a territory page after the user pans away`() {
        assertFalse(shouldRefreshTerritoryFromDevice(MapPurpose.TERRITORY, followDevice = false))
        assertTrue(shouldRefreshTerritoryFromDevice(MapPurpose.TERRITORY, followDevice = true))
        assertFalse(shouldRefreshTerritoryFromDevice(MapPurpose.WALK, followDevice = true))
    }

    private fun site(id: String) = TerritorySite(
        id = id,
        point = GeoPoint(37.5, 127.0),
        distanceMeters = 10.0,
    )
}
