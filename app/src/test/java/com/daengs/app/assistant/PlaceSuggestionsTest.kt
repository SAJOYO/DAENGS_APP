package com.daengs.app.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `place-capability-v1` 파서가 저쪽 계약을 제대로 읽는가.
 *
 * 픽스처는 저쪽 `SAJOYO/DAENGS_dev` `feat/assistant-place-routing`
 * (`backend/tests/fixtures/place_capability` 아래 json 일곱 개, `tools.place_fixtures` 로 생성)를
 * **그대로 옮겨 얼렸다** — 백엔드 PR #204 가 배포되기 전에도 실제 응답 모양으로
 * 파서·프레젠테이션을 검증하기 위해서다. 백엔드가 계약을 바꾸면 이 픽스처를
 * 새로 받아 와야 하고, 그때까지는 이 테스트가 지금 계약을 고정한다.
 */
class PlaceSuggestionsTest {

    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/place_capability/$name.json")!!.readText())

    @Test
    fun `장소 성공 응답에서 그룹과 후보를 읽는다`() {
        val response = AssistantResponse.parse(fixture("place_success"))

        assertEquals(AssistantResponse.Status.ANSWERED, response.status)
        val places = response.places!!
        assertEquals(1, places.groups.size)
        val group = places.groups[0]
        assertEquals("purpose.pet_care", group.lensId)
        assertEquals("미용/위탁", group.label)
        assertEquals(2, group.candidates.size)

        val first = group.candidates[0]
        assertEquals("kto", first.placeId.source)
        assertEquals("P-1001", first.placeId.ref)
        assertEquals("멍멍이 미용실 홍대점", first.title)
        assertEquals("미용", first.kindLabel)
        assertEquals(420, first.distanceMeters)
        assertEquals(37.5563, first.lat!!, 1e-9)
        assertEquals(126.9236, first.lon!!, 1e-9)
        assertEquals("서울 마포구 양화로 1", first.address)
        assertEquals(2, first.facts.size)
        assertEquals("반려동물 동반이 가능해요.", first.facts[0].text)
        assertEquals(PlaceSuggestions.Severity.INFO, first.facts[0].severity)
        assertTrue(first.notices.isEmpty())
        assertEquals(listOf("요청하신 미용 목적과 일치해요."), first.whyMatched)

        val second = group.candidates[1]
        // 모르는(못 확인한) 사실은 그대로 "확인되지 않았어요" 문장과 WARNING 톤으로
        // 온다 — 앱이 이걸 "가능" 으로 뒤집지 않는다.
        assertEquals("확인되지 않았어요.", second.facts[0].text)
        assertEquals(PlaceSuggestions.Severity.WARNING, second.facts[0].severity)
        assertEquals(1, second.notices.size)
        assertEquals("방문 전에 전화로 확인해 주세요.", second.notices[0].message)
        assertEquals(PlaceSuggestions.Severity.WARNING, second.notices[0].severity)

        // Option B 고지는 조건 없이, 그대로.
        assertTrue(
            places.notices.any {
                it == "현재 기기 위치를 기준으로 찾았어요. " +
                    "질문에 지역 이름이 있어도 아직 그 지역으로는 찾지 못하고, 반영하지 않았습니다."
            },
        )
    }

    @Test
    fun `후보가 없으면 그룹은 남아도 candidates가 빈다`() {
        val response = AssistantResponse.parse(fixture("no_candidates"))

        assertEquals(AssistantResponse.Status.UNCERTAIN, response.status)
        val places = response.places!!
        assertEquals(1, places.groups.size)
        assertTrue(places.groups[0].candidates.isEmpty())
    }

    @Test
    fun `의미를 못 골랐으면 그룹도 refinements도 빈다`() {
        val response = AssistantResponse.parse(fixture("place_abstention"))

        val places = response.places!!
        assertTrue(places.groups.isEmpty())
        assertTrue(places.refinements.isEmpty())
    }

    @Test
    fun `되묻기가 필요하면 refinements에 선택지가 담긴다`() {
        val response = AssistantResponse.parse(fixture("refinement_required"))

        val places = response.places!!
        assertTrue(places.groups.isEmpty())
        assertEquals(1, places.refinements.size)
        val refinement = places.refinements[0]
        assertEquals("반려견 크기", refinement.label)
        assertTrue(refinement.required)
        assertEquals(2, refinement.options.size)
        assertEquals("소형견", refinement.options[0].label)
        assertEquals("available", refinement.options[0].availability)
    }

    @Test
    fun `위치가 없으면 결과 없이 CLARIFY만 온다`() {
        val response = AssistantResponse.parse(fixture("missing_location"))

        assertEquals(AssistantResponse.Status.CLARIFY, response.status)
        assertNull(response.places)
        assertEquals(listOf("location.lat", "location.lon"), response.clarify?.missing)
    }

    @Test
    fun `산책이 실패해도 장소는 따로 읽힌다`() {
        // partial_success: walk 는 ERROR(data 없음), place 는 OK. 능력 하나가
        // 죽어도 다른 능력 파서가 같이 죽으면 안 된다.
        val response = AssistantResponse.parse(fixture("partial_success"))

        assertEquals(AssistantResponse.Status.PARTIAL, response.status)
        assertNull(response.walk)
        assertEquals(1, response.places!!.groups.size)
        assertEquals(2, response.places!!.groups[0].candidates.size)
    }

    @Test
    fun `산책과 장소가 같이 성공하면 둘 다 읽힌다`() {
        val response = AssistantResponse.parse(fixture("place_plus_walk_success"))

        assertEquals(WalkVerdict.Grade.GOOD, response.walk?.grade)
        val places = response.places!!
        assertEquals(1, places.groups.size)
        assertEquals("선유도공원", places.groups[0].candidates[0].title)
        assertEquals(
            PlaceSuggestions.Severity.INFO,
            places.groups[0].candidates[0].facts[0].severity,
        )
    }

    @Test
    fun `place 결과가 없으면 null이다`() {
        val response = AssistantResponse.parse(
            JSONObject(
                """{"request_id": "r", "status": "ANSWERED", "message": "훈련 답변",
                    "results": [], "handoffs": [], "clarify": null}""",
            ),
        )
        assertNull(response.places)
    }

    @Test
    fun `data가 없는 place 결과는 null로 떨어진다`() {
        // ERROR 상태라 data 가 없는 경우. 저쪽이 안 죽었어도, 우리가 없는 값을
        // 지어내지 않는다.
        val response = AssistantResponse.parse(
            JSONObject(
                """{"request_id": "r", "status": "PARTIAL", "message": "…",
                    "results": [{"capability": "place", "status": "ERROR", "data": null}],
                    "handoffs": [], "clarify": null}""",
            ),
        )
        assertNull(response.places)
    }

    @Test
    fun `모르는 severity는 UNKNOWN으로 떨어진다`() {
        val place = PlaceSuggestions.parse(
            JSONObject(
                """
                {
                  "answer": "찾았습니다.",
                  "groups": [{
                    "lens_id": "l", "label": "라벨", "support_note": "",
                    "candidates": [{
                      "place_id": {"source": "kto", "ref": "P-1"},
                      "title": "장소", "summary": "", "kind": {"id": "k", "label": "종류"},
                      "location": {"lat": 37.0, "lon": 127.0, "distance_m": 100},
                      "address": "", "facts": [
                        {"id": "f", "label": "라벨", "text": "텍스트", "severity": "future_severity"}
                      ],
                      "notices": [], "why_matched": []
                    }]
                  }],
                  "refinements": [], "notices": []
                }
                """.trimIndent(),
            ),
        )
        assertEquals(PlaceSuggestions.Severity.UNKNOWN, place.groups[0].candidates[0].facts[0].severity)
    }

    @Test
    fun `범위를 벗어난 좌표는 null이다`() {
        val place = PlaceSuggestions.parse(
            JSONObject(
                """
                {
                  "answer": "", "groups": [{
                    "lens_id": "l", "label": "라벨", "support_note": "",
                    "candidates": [{
                      "place_id": {"source": "kto", "ref": "P-1"},
                      "title": "장소", "summary": "", "kind": {"id": "k", "label": "종류"},
                      "location": {"lat": 999.0, "lon": 127.0, "distance_m": 100},
                      "address": "", "facts": [], "notices": [], "why_matched": []
                    }]
                  }],
                  "refinements": [], "notices": []
                }
                """.trimIndent(),
            ),
        )
        assertNull(place.groups[0].candidates[0].lat)
        assertFalse(place.groups[0].candidates[0].lon == null)
    }
}
