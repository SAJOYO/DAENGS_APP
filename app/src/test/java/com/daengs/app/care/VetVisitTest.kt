package com.daengs.app.care

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 저쪽 `schemas/vet_visit.py` 가 못박은 모양. 여기서 잡는 것은 **합계 줄만 없는 응답**
 * (`no_amount`) 이 나머지 칸을 그대로 싣고 온다는 것과, 전화번호 모양이다.
 */
class VetVisitTest {

    @Test
    fun `no_amount 는 총액만 비고 병원·날짜·항목은 채워져 온다`() {
        val draft = VetVisitDraft.parse(JSONObject(NO_AMOUNT_JSON))

        assertEquals(ExtractionStatus.UNREADABLE, draft.status)
        assertEquals(UnreadableReason.NO_AMOUNT, draft.unreadableReason)
        assertNull("합계 줄이 없다", draft.totalKrw)
        assertEquals("압구정동물병원", draft.hospitalName)
        assertEquals("02-543-0075", draft.hospitalPhone)
        assertEquals(LocalDate.of(2019, 5, 17), draft.visitedOn)
        assertEquals(3, draft.items.size)
        assertEquals(46_200, draft.items[1].amountKrw)
    }

    @Test
    fun `blurry 는 전부 비어 있다`() {
        val draft = VetVisitDraft.parse(JSONObject(BLURRY_JSON))

        assertEquals(UnreadableReason.BLURRY, draft.unreadableReason)
        assertNull(draft.hospitalName)
        assertTrue(draft.items.isEmpty())
        assertNull("제안이 없으면 아무것도 미리 안 고른다", draft.suggestedReasonCode)
    }

    @Test
    fun `사유 표시명은 응답에서 온다 — 앱이 한글을 안 적는다`() {
        val draft = VetVisitDraft.parse(JSONObject(NO_AMOUNT_JSON))

        assertEquals(listOf("skin", "vaccination"), draft.reasonOptions.map { it.code })
        assertEquals("피부", draft.reasonOptions[0].label)
    }

    @Test
    fun `티켓은 200 이면 있던 초안이고 201 이면 새 초안이다 — 둘 다 성공`() {
        assertFalse(VetVisitTicket.parse(JSONObject(TICKET_JSON), status = 200).created)
        assertTrue(VetVisitTicket.parse(JSONObject(TICKET_JSON), status = 201).created)
        assertEquals(
            mapOf("Content-Type" to "image/jpeg"),
            VetVisitTicket.parse(JSONObject(TICKET_JSON), 201).uploadHeaders,
        )
    }

    @Test
    fun `전화번호는 서버 CHECK 과 같은 모양만 통과한다`() {
        assertTrue(phoneLooksValid("02-543-0075"))
        assertTrue(phoneLooksValid("031-1234-5678"))
        assertTrue("빈 칸은 안 보낼 값이라 통과다", phoneLooksValid(""))
        assertFalse("카드번호 네 묶음", phoneLooksValid("5432-1234-5678-9012"))
        assertFalse("하이픈이 없다", phoneLooksValid("025430075"))
    }

    @Test
    fun `확정 본문은 채워진 칸만 싣는다 — 빈 병원 칸은 아예 안 간다`() {
        val body = VetVisitConfirmation(
            clientEventId = "10000000-0000-4000-8000-000000000001",
            reasonCode = "skin",
            reasonDetail = null,
            visitedOn = LocalDate.of(2026, 9, 10),
            totalKrw = 61_700,
            hospitalName = "압구정동물병원",
            hospitalAddress = null,
            hospitalPhone = "",
            isEmergency = false,
            isOncology = true,
        ).toJson()

        assertEquals("2026-09-10", body.getString("visited_on"))
        assertEquals(61_700, body.getInt("total_krw"))
        assertEquals("압구정동물병원", body.getString("hospital_name"))
        assertFalse("빈 전화는 칸째로 안 보낸다 — 빈 문자열은 422 다", body.has("hospital_phone"))
        assertFalse(body.has("hospital_address"))
        assertFalse(body.has("reason_detail"))
        assertTrue(body.getBoolean("is_oncology"))
    }

    @Test
    fun `확정된 기록은 코드만 들고 온다 — 표시명은 목록이 따로 붙인다`() {
        val visit = VetVisit.parse(JSONObject(VISIT_JSON))

        assertEquals("skin", visit.reasonCode)
        assertEquals(LocalDate.of(2026, 9, 2), visit.visitedOn)
        assertEquals(80_000, visit.totalKrw)
        assertNull(visit.hospitalAddress)
    }

    private companion object {
        const val NO_AMOUNT_JSON = """
            {"draft_id": "d1", "pet_id": "p1", "receipt_image_url": "http://x/y",
             "extraction_status": "unreadable", "unreadable_reason": "no_amount",
             "visited_on": "2019-05-17", "total_krw": null,
             "hospital_name": "압구정동물병원", "hospital_address": "서울 강남구",
             "hospital_phone": "02-543-0075",
             "items": [{"name": "초진료", "amount_krw": 5500},
                       {"name": "주사-비오칸엠", "amount_krw": 46200},
                       {"name": "처치료", "amount_krw": 10000}],
             "suggested_reason_code": "vaccination", "is_emergency": false,
             "possible_duplicate": false,
             "reason_options": [{"code": "skin", "label": "피부"},
                                {"code": "vaccination", "label": "예방접종"}]}
        """

        const val BLURRY_JSON = """
            {"draft_id": "d2", "pet_id": "p1", "receipt_image_url": "http://x/y",
             "extraction_status": "unreadable", "unreadable_reason": "blurry",
             "visited_on": null, "total_krw": null, "hospital_name": null,
             "hospital_address": null, "hospital_phone": null, "items": [],
             "suggested_reason_code": null, "is_emergency": false,
             "possible_duplicate": false, "reason_options": []}
        """

        const val TICKET_JSON = """
            {"draft_id": "d1", "pet_id": "p1", "storage_key": "vet/d1.jpg",
             "upload_url": "http://server:8000/app/vet-visits/_bridge/upload/vet/d1.jpg",
             "upload_headers": {"Content-Type": "image/jpeg"}, "expires_in_seconds": 600}
        """

        const val VISIT_JSON = """
            {"id": "v1", "pet_id": "p1", "visited_on": "2026-09-02", "total_krw": 80000,
             "hospital_name": "○○동물병원", "hospital_address": null,
             "hospital_phone": "02-123-4567", "reason_code": "skin",
             "reason_detail": null, "suggested_reason_code": "skin",
             "is_emergency": false, "is_oncology": false,
             "client_event_id": "10000000-0000-4000-8000-000000000001",
             "created_at": "2026-09-02T11:00:00+09:00"}
        """
    }
}
