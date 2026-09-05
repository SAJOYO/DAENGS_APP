package com.daengs.app.map.features.territory

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.territory.*
import com.daengs.app.territory.*
import org.junit.Assert.*
import org.junit.Test

class TerritoryFeedbackTrackerTest {
    @Test fun `server receipts animate once only when delivered on the visible selected site`() {
        val confirmed = state().copy(confirmedMarkId = "claim", confirmedMarkSiteId = "A")
        update(state())
        assertEquals(TerritoryFeedbackKind.MARKED, update(confirmed, 1)!!.kind)
        assertNull(update(confirmed, 2_000_000_001))
        val hidden = confirmed.copy(confirmedMarkId = "hidden")
        assertNull(update(hidden, 3_000_000_000, visible = false))
        assertNull(update(hidden, 3_000_000_001))
        val other = confirmed.copy(confirmedMarkId = "other", confirmedMarkSiteId = "B")
        assertNull(update(other, 4_000_000_000))
        assertNull(update(other.copy(sites = listOf(site("B")), targetId = "B"), 4_000_000_001))
    }

    @Test fun `a new screen never celebrates an initial cached server receipt`() {
        assertNull(update(state().copy(confirmedMarkId = "old", confirmedMarkSiteId = "A")))
    }
    private val tracker = TerritoryFeedbackTracker()
    private fun site(id: String = "A", occupancy: TerritoryOccupancy? = null) = TerritoryGameSite(
        TerritorySite(id, GeoPoint(37.5, 127.0), 0.0), TerritoryClaimSite(id, occupancy), "보리", null, 5.0, occupancy != null)
    private fun state(ready: Boolean = false, occupancy: TerritoryOccupancy? = null) = TerritoryGameState(
        enabled = true, phase = TerritoryWalkPhase.WALKING, sites = listOf(site(occupancy = occupancy)),
        targetId = "A", canMark = ready)
    private fun owner(certification: ClaimCertification = ClaimCertification.UNVERIFIED, session: String = "walk") =
        TerritoryOccupancy("dog", session, "attempt", certification, 10)
    private fun update(game: TerritoryGameState, now: Long = 0, visible: Boolean = true, session: String? = "walk") =
        tracker.update(game, session, visible, now)

    @Test fun `접근 준비는 실제 가능한 순간에만 한 번 나오고 GPS와 재선택에 반복하지 않는다`() {
        assertNull(update(state()))
        val first = update(state(ready = true), 1)!!
        assertEquals(TerritoryFeedbackKind.READY, first.kind)
        assertEquals(first.id, update(state(ready = true), 2)!!.id)
        assertNull(update(state(), 3))
        assertNull(update(state(ready = true), 4))
        assertNull(update(state(ready = true).copy(targetId = null), 5))
        assertNull(update(state(ready = true), 6))
        assertNotNull(update(state(ready = true), 7, session = "next"))
    }

    @Test fun `단순 점유 조회는 성공이 아니고 확정된 내 점유 변경만 성공이다`() {
        assertNull(update(state(occupancy = owner())))
        assertNull(update(state(occupancy = owner()), 1))
        val verified = update(state(occupancy = owner(ClaimCertification.VERIFIED)), 2)!!
        assertEquals(TerritoryFeedbackKind.VERIFIED, verified.kind)
        assertEquals(verified.id, update(state(occupancy = owner(ClaimCertification.VERIFIED)), 3)!!.id)
        assertNull(update(state(occupancy = owner(ClaimCertification.VERIFIED)), 2_000_000_000))
    }

    @Test fun `일반 영역표시는 미인증 성공이고 인증 대기 거절 재시도는 인증 성공이 아니다`() {
        update(state())
        val marked = update(state(occupancy = owner()), 1)!!
        assertEquals(TerritoryFeedbackKind.MARKED, marked.kind)
        for (status in listOf(ClaimPhotoStatus.PENDING, ClaimPhotoStatus.REJECTED, ClaimPhotoStatus.RETRY_PENDING)) {
            assertNull(update(state(occupancy = owner()).copy(photoStatus = status), 2_000_000_000))
        }
        assertEquals(TerritoryFeedbackKind.VERIFIED,
            update(state(occupancy = owner(ClaimCertification.VERIFIED)).copy(photoStatus = ClaimPhotoStatus.VERIFIED), 3_000_000_000)!!.kind)
    }

    @Test fun `인증 상태만 바뀌고 점유 확정이 없으면 성공 효과가 없다`() {
        update(state(occupancy = owner(session = "other")))
        assertNull(update(state(occupancy = owner(session = "other")).copy(photoStatus = ClaimPhotoStatus.VERIFIED)))
    }

    @Test fun `일시정지나 숨김 중 결과는 나중에 성공 효과로 재생하지 않는다`() {
        update(state())
        assertNull(update(state(occupancy = owner()).copy(phase = TerritoryWalkPhase.PAUSED), 1))
        assertNull(update(state(occupancy = owner()), 2))
        assertNull(update(state(occupancy = owner(ClaimCertification.VERIFIED)), 3, visible = false))
        assertNull(update(state(occupancy = owner(ClaimCertification.VERIFIED)), 4))
        assertNull(update(state(ready = true).copy(phase = TerritoryWalkPhase.BROWSING), 5, session = null))
    }

    @Test fun `다른 장소의 인증과 이미 점유된 새 조회 결과는 선택 효과를 만들지 않는다`() {
        update(state())
        val other = site("B", owner(ClaimCertification.VERIFIED))
        assertNull(update(state().copy(sites = listOf(site(), other)), 1))
        assertNull(update(state().copy(sites = listOf(site(), other), targetId = "B"), 2))
    }

    @Test fun `모든 효과는 유한하고 점령 준비에 성공 발자국이 없다`() {
        for (kind in TerritoryFeedbackKind.entries) {
            assertEquals(TerritoryFeedbackFrame(), territoryFeedbackFrame(kind, 1f))
            assertEquals(TerritoryFeedbackFrame(), territoryFeedbackFrame(kind, Float.NaN))
            for (step in 0..100) {
                val frame = territoryFeedbackFrame(kind, step / 100f)
                assertTrue(frame.markerScale in 1f..1.18f)
                assertTrue(frame.pawAlpha in 0f..1f)
                if (kind == TerritoryFeedbackKind.READY) assertEquals(0f, frame.pawAlpha)
            }
            val event = TerritoryFeedback(1, "A", kind, 10)
            assertEquals(1f, event.progressAt(2_000_000_010))
        }
    }

    @Test fun `조회 지역에서 사라졌다 다시 읽은 장소를 새 점령 성공으로 해석하지 않는다`() {
        update(state())
        assertNull(update(state().copy(sites = emptyList(), targetId = null), 1))
        assertNull(update(state(occupancy = owner(ClaimCertification.VERIFIED)), 2))
    }
}
