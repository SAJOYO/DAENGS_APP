package com.daengs.app.map.features.places

import com.daengs.app.journey.JourneyMode
import com.daengs.app.journey.toJourneyResponse
import com.daengs.app.map.features.journey.PlaceJourneyState
import com.daengs.app.place.DogAccessState
import com.daengs.app.place.PlaceFailure
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceSearchResponse
import com.daengs.app.place.toPlaceSearchResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceDiscoveryPanelTest {
    @Test
    fun `category chips expose every canonical fact kind exactly once`() {
        assertEquals(PlaceKind.entries.toSet(), PLACE_CATEGORIES.map { it.kind }.toSet())
        assertEquals(PlaceKind.entries.size, PLACE_CATEGORIES.size)
        assertEquals(PlaceKind.CAFE, DEFAULT_PLACE_KIND)
    }

    @Test
    fun `marker projection preserves server order and stable source ref selection`() {
        val response = response()
        val selected = PlaceKey("kto", "cafe-unknown")
        val state = PlaceDiscoveryState(
            search = PlaceSearchState.Content(response),
            selectedPlaceKey = selected,
        )

        val markers = canonicalPlaceMarkers(state)
        val keys = canonicalPlaceKeysByMarker(state)

        assertEquals(
            listOf("먼 주차 카페", "가까운 정보 미상 카페", "댕스동물병원"),
            markers.map { it.label },
        )
        assertFalse(markers[0].selected)
        assertTrue(markers[1].selected)
        assertEquals(selected, keys.getValue(markers[1].id))
    }

    @Test
    fun `marker identity cannot collide when source and ref boundaries differ`() {
        assertNotEquals(
            placeMarkerId(PlaceKey(source = "ab", ref = "c")),
            placeMarkerId(PlaceKey(source = "a", ref = "bc")),
        )
    }

    @Test
    fun `one canonical place in multiple categories produces one selected marker`() {
        val response = response()
        val group = response.groups.first()
        val state = PlaceDiscoveryState(
            search = PlaceSearchState.Content(response.copy(groups = listOf(group, group.copy(kind = PlaceKind.RESTAURANT)))),
            selectedPlaceKey = group.results.first().place.key,
        )
        val markers = canonicalPlaceMarkers(state)
        assertEquals(group.results.size, markers.size)
        assertEquals(1, markers.count { it.selected })
    }

    @Test
    fun `labels keep unknown facts separate from negative facts`() {
        assertEquals("주차 가능", parkingLabel(true))
        assertEquals("주차 불가", parkingLabel(false))
        assertEquals("주차 정보 없음", parkingLabel(null))
        assertEquals("조건 불일치", dogAccessLabel(DogAccessState.INCOMPATIBLE))
        assertEquals("정보 부족 · 확인 필요", dogAccessLabel(DogAccessState.UNKNOWN))
        assertEquals("시설 제한 정보 없음", dogAccessReasonLabel("missing_restriction"))
    }

    @Test
    fun `sorting explanation comes from server metadata`() {
        val groups = response().groups

        assertEquals("500m 구간 안에서 주차 가능 우선", sortLabel(groups[0]))
        assertEquals("가까운 순", sortLabel(groups[1]))
        assertEquals(
            DogAccessCoverage(compatible = 0, incompatible = 1, unknown = 1),
            dogAccessCoverage(groups[0]),
        )
        assertEquals(null, dogAccessCoverage(groups[1]))
    }

    @Test
    fun `the panel says which origin the results are from`() {
        assertEquals("내 위치 기준", originLabel(PlaceOriginMode.DEVICE))
        assertEquals("지도를 움직인 위치 기준", originLabel(PlaceOriginMode.PINNED))
    }

    @Test
    fun `panel presentation keeps failures distinct from empty results`() {
        val presentation = PlaceDiscoveryState(
            requestedKinds = listOf(PlaceKind.CAFE),
            search = PlaceSearchState.Failed(PlaceFailure.Offline),
        ).toPanelPresentation()

        assertEquals("인터넷 연결을 확인해주세요.", presentation.errorMessage)
        assertTrue(presentation.retryable)
        assertEquals(PlaceResultsPresentation.Failed, presentation.results)
    }

    @Test
    fun `panel presentation derives result counts and coverage before composition`() {
        val presentation = PlaceDiscoveryState(
            requestedKinds = listOf(PlaceKind.CAFE),
            search = PlaceSearchState.Content(response()),
        ).toPanelPresentation()

        assertEquals("500m 구간 안에서 주차 가능 우선", presentation.sortDescription)
        assertEquals("반환 결과 주차 정보 · 가능 1 · 불가 0 · 미상 1", presentation.parkingCoverage)
        assertEquals("입장 평가 · 가능 0 · 불일치 1 · 미상 1", presentation.dogAccessCoverage)
        val results = presentation.results as PlaceResultsPresentation.Content
        assertEquals("2곳", results.countLabel)
        assertEquals(listOf("먼 주차 카페", "가까운 정보 미상 카페"), results.hits.map { it.place.name })
    }

    @Test
    fun `card presentation preserves positive negative and unknown fact semantics`() {
        val groups = response().groups
        val cafeCards = groups.first().results.map { it.toCardPresentation() }
        val hospitalCard = groups.last().results.single().toCardPresentation()

        assertEquals(
            PlaceTextPresentation("주차 가능", PlaceTextTone.SUCCESS),
            cafeCards[0].parking,
        )
        assertEquals(
            PlaceTextPresentation("조건 불일치 · 크기 등급 초과", PlaceTextTone.ERROR),
            cafeCards[0].dogAccess,
        )
        assertEquals(
            PlaceTextPresentation("주차 정보 없음", PlaceTextTone.WARNING),
            cafeCards[1].parking,
        )
        assertEquals(
            PlaceTextPresentation("정보 부족 · 확인 필요 · 시설 제한 정보 없음", PlaceTextTone.WARNING),
            cafeCards[1].dogAccess,
        )
        assertEquals("현재 영업 여부 미상 · 전화번호 정보 없음", hospitalCard.operation?.text)
        assertEquals(PlaceTextTone.WARNING, hospitalCard.operation?.tone)
        assertEquals("오늘 09:00~18:00", hospitalCard.todayHours)
        assertEquals(null, hospitalCard.phoneAction)
    }

    @Test
    fun `every canonical kind has a label without throwing`() {
        PlaceKind.entries.forEach { kind ->
            assertTrue(categoryLabel(kind).isNotBlank())
        }
        assertEquals("동물병원", categoryLabel(PlaceKind.HOSPITAL))
        assertEquals("내 주변 동물병원", placePanelTitle(PlaceKind.HOSPITAL))
        assertEquals("내 주변 장소", placePanelTitle(PlaceKind.CAFE))
    }

    @Test
    fun `hospital actions expose uncertainty and source date without changing Place facts`() {
        val hospital = response().groups
            .single { it.kind == PlaceKind.HOSPITAL }
            .results.single().place

        assertEquals(
            "현재 영업 여부 미상 · 전화번호 정보 없음",
            hospitalOperationLabel(hospital.facts.medical, hasPhone = false),
        )
        assertEquals("오늘 09:00~18:00", todayHoursLabel(hospital.facts.medical))
        assertEquals("인허가 정보 기준 2026-08-26", hospitalSourceDateLabel(hospital))
        assertEquals(
            "병원에 전화해 확인 · 02-1234-5678",
            callActionLabel(PlaceKind.HOSPITAL, "02-1234-5678"),
        )
        assertEquals("전화 02-1234-5678", callActionLabel(PlaceKind.CAFE, "02-1234-5678"))
    }

    @Test
    fun `journey presentation follows server mode priority and labels estimates honestly`() {
        val text = javaClass.getResource("/journey_response.json")!!.readText()
        val item = Json.parseToJsonElement(text).jsonObject.toJourneyResponse().items.single()

        val primary = primaryJourney(item)!!

        assertEquals(JourneyMode.WALK, primary.mode)
        assertEquals("도보 약 31분 · 2.3km · 추정", journeySummary(primary))
        assertTrue(primary.leg.handoff!!.naver.startsWith("nmap://route/walk"))
    }

    @Test
    fun `journey action presentation closes every card action state`() {
        val text = javaClass.getResource("/journey_response.json")!!.readText()
        val item = Json.parseToJsonElement(text).jsonObject.toJourneyResponse().items.single()

        assertEquals(JourneyActionPresentation.Ready, null.toActionPresentation())
        assertEquals(
            JourneyActionPresentation.Loading,
            PlaceJourneyState(loading = true).toActionPresentation(),
        )
        assertEquals(
            JourneyActionPresentation.Failed("길찾기 실패"),
            PlaceJourneyState(error = "길찾기 실패").toActionPresentation(),
        )
        val handoff = PlaceJourneyState(item = item).toActionPresentation()
            as JourneyActionPresentation.Handoff
        assertEquals("도보 약 31분 · 2.3km · 추정", handoff.summary)
        assertEquals("네이버 지도에서 도보 길찾기", handoff.actionLabel)
        assertTrue(handoff.url.startsWith("nmap://route/walk"))
    }

    private fun response(): PlaceSearchResponse {
        val text = javaClass.getResource("/place_search_response.json")!!.readText()
        return Json.parseToJsonElement(text).jsonObject.toPlaceSearchResponse()
    }
}
