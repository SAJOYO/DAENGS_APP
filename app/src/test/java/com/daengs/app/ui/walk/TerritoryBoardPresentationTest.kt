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
