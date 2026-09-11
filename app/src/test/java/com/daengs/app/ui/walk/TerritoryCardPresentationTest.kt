package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.territory.*
import com.daengs.app.territory.*
import org.junit.Assert.*
import org.junit.Test

class TerritoryCardPresentationTest {
    @Test fun `unknown occupancy cannot advertise vacant land or a reward`() {
        val game = cardGame().let { it.copy(sites = listOf(it.target!!.copy(occupancyKnown = false,
            occupancyReadState = TerritoryOccupancyReadState.FAILED))) }
        val card = territoryCardPresentation(game)!!
        assertEquals("점유 조회 실패", card.title)
        assertNull(card.reward)
    }
    @Test fun `first season maximum is not an unconditional award`() {
        val card = territoryCardPresentation(cardGame())!!
        assertTrue(card.reward!!.contains("최대 100점"))
        assertTrue(card.rewardDetail!!.contains("이미 받은 보상 제외"))
        assertFalse(card.reward.contains("+100"))
    }
    @Test fun `old or absent policy cannot display first season numbers`() {
        val game = cardGame().let { it.copy(sites = listOf(it.target!!.copy(sharedState = null))) }
        assertNull(territoryCardPresentation(game)!!.reward)
    }
    @Test fun `only another members occupied site advertises takeover bonus`() {
        assertTrue(territoryCardPresentation(cardGame(occupied = true))!!.rewardDetail!!.contains("보너스 20점"))
        assertFalse(territoryCardPresentation(cardGame(occupied = true, mine = true))!!.rewardDetail!!.contains("보너스"))
        assertFalse(territoryCardPresentation(cardGame())!!.rewardDetail!!.contains("보너스"))
    }
    @Test fun `confirmed renewal keeps zero while uncertified upgrade does not invent eighty`() {
        val mine = cardGame(occupied = true, mine = true)
        assertFalse(territoryCardPresentation(mine)!!.reward!!.contains("80"))
        assertEquals("유지 연장 · 0점", territoryCardPresentation(mine.copy(photoActionLabel = "사진으로 유지 연장"))!!.reward)
    }
    @Test fun `read failures and authentication guidance survive the browsing copy`() {
        val message = "로그인 상태를 다시 확인해 주세요"
        assertEquals(message, territoryBrowsingGuidance(cardGame().copy(readOnly = true, guidance = message)))
    }
    @Test fun `unknown distance never becomes zero metres`() {
        val game = cardGame().let { it.copy(sites = listOf(it.target!!.copy(distanceMeters = Double.NaN))) }
        assertEquals("현재 위치 확인 중", territoryCardPresentation(game)!!.distance)
    }
}

internal fun cardGame(occupied: Boolean = false, mine: Boolean = false): TerritoryGameState {
    val site = TerritorySite("A", GeoPoint(37.5, 127.0), 0.0)
    val claim = TerritoryClaimSite("A", if (occupied) TerritoryOccupancy("p", null, null,
        ClaimCertification.UNVERIFIED, 1_800_000_000_000) else null)
    return TerritoryGameState(enabled = true, targetId = "A", onlinePhotos = true,
        sites = listOf(TerritoryGameSite(site, claim, "두부", null, 48.0, false,
            isOwnedByMe = mine, sharedState = SharedTerritorySite("A", 1, null, policyVersion = FIRST_SEASON_POLICY))))
}
