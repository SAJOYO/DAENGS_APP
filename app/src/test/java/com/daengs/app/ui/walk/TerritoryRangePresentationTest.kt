package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.territory.*
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.territory.*
import org.junit.Assert.*
import org.junit.Test

class TerritoryRangePresentationTest {
    private val far = TerritorySite("far", GeoPoint(37.55, 127.0), 0.0)
    private val near = TerritorySite("near", GeoPoint(37.5, 127.0), 999.0)
    private fun state(phase: TerritoryWalkPhase = TerritoryWalkPhase.BROWSING): WalkUiState = WalkUiState(
        map = WalkMapUiState(purpose = MapPurpose.TERRITORY),
        territory = TerritoryBoardState(sites = listOf(far), selectedSiteId = far.id),
        territoryGame = TerritoryGameState(enabled = true, readOnly = true, phase = phase,
            targetId = far.id, visibleRangeSiteIds = setOf(near.id), radiusMeters = 20.0,
            sites = listOf(far, near).map { TerritoryGameSite(it, TerritoryClaimSite(it.id), "", null, null, false,
                proximity = TerritoryProximity(if (it == near) TerritoryProximityRange.IN_RANGE else TerritoryProximityRange.APPROACHING)) }))

    @Test fun `manual and automatic ranges coexist in read only browsing without changing selected card or camera`() {
        val state = state()
        val presentation = state.toMapPresentation()
        val markers = presentation.scene.territorySites
        assertEquals(listOf("far", "near"), markers.map { it.id })
        assertEquals(listOf(20.0, 20.0), markers.map { it.radiusMeters })
        assertEquals(listOf(true, false), markers.map { it.selected })
        assertEquals(TerritoryProximityRange.IN_RANGE, markers.last().proximity)
        assertFalse(markers.last().ready)
        assertNull(presentation.fitBounds)
        assertEquals("far", state.territoryGame.targetId)
    }

    @Test fun `manual range survives pause and unavailable location while automatic ranges can be removed`() {
        val state = state(TerritoryWalkPhase.PAUSED).let { it.copy(territoryGame = it.territoryGame.copy(
            visibleRangeSiteIds = emptySet(), sites = it.territoryGame.sites.map { site -> site.copy(proximity = TerritoryProximity()) })) }
        val marker = state.toMapPresentation().scene.territorySites.single()
        assertEquals(20.0, marker.radiusMeters)
        assertEquals(TerritoryProximityRange.UNAVAILABLE, marker.proximity)
        assertFalse(marker.ready)
    }

    @Test fun `clearing manual selection retains only automatic range and viewport coordinates win duplicates`() {
        val state = state().let { it.copy(territory = it.territory.copy(sites = listOf(far, near), selectedSiteId = null),
            territoryGame = it.territoryGame.copy(targetId = null,
                sites = it.territoryGame.sites.map { gameSite -> gameSite.copy(site = gameSite.site.copy(point = GeoPoint(0.0, 0.0))) })) }
        val markers = state.toMapPresentation().scene.territorySites
        assertEquals(2, markers.size)
        assertNull(markers.first().radiusMeters)
        assertEquals(20.0, markers.last().radiusMeters)
        assertEquals(near.point, markers.last().point)
        assertTrue(markers.none { it.selected })
    }

    @Test fun `other map modes hide the entire territory layer`() {
        assertTrue(state().copy(map = WalkMapUiState(purpose = MapPurpose.WALK))
            .toMapPresentation().scene.territorySites.isEmpty())
    }
}
