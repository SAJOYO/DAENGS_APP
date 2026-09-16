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
        val body = confirmation(hospitalName = "압구정동물병원", hospitalPhone = "").toJson()

        assertEquals("2026-09-10", body.getString("visited_on"))
        assertEquals(61_700, body.getInt("total_krw"))
        assertEquals("압구정동물병원", body.getString("hospital_name"))
        assertFalse("빈 전화는 칸째로 안 보낸다 — 빈 문자열은 422 다", body.has("hospital_phone"))
        assertFalse(body.has("hospital_address"))
    }

    @Test
    fun `한 마리도 splits 로 보낸다 — 특수 케이스가 아니라 길이 1 이다`() {
        val body = confirmation().toJson()

        assertEquals(1, body.getJSONArray("splits").length())
        assertFalse("옛 평평한 본문은 더 안 보낸다", body.has("client_event_id"))
        assertFalse(body.has("reason_code"))
    }

    @Test
    fun `행마다 제 키와 제 블록 번호를 싣는다`() {
        val body = VetVisitConfirmation(
            visitedOn = LocalDate.of(2026, 9, 15),
            totalKrw = 191_300,
            hospitalName = null,
            hospitalAddress = null,
            hospitalPhone = null,
            splits = listOf(
                split(clientEventId = "id-0", petId = "pet-a", totalKrw = 109_200, patientIndex = 0),
                split(clientEventId = "id-1", petId = "pet-b", totalKrw = 82_100, patientIndex = 1),
            ),
        ).toJson()

        val splits = body.getJSONArray("splits")
        assertEquals("id-0", splits.getJSONObject(0).getString("client_event_id"))
        assertEquals("id-1", splits.getJSONObject(1).getString("client_event_id"))
        assertEquals(0, splits.getJSONObject(0).getInt("patient_index"))
        assertEquals(1, splits.getJSONObject(1).getInt("patient_index"))
        assertEquals(109_200, splits.getJSONObject(0).getInt("total_krw"))
        assertEquals("pet-b", splits.getJSONObject(1).getString("pet_id"))
    }

    @Test
    fun `어느 블록인지 모를 때는 patient_index 를 안 싣는다`() {
        // null 을 0 으로 접으면 첫째 블록의 항목이 이 기록에 붙는다. 저쪽은 안 실으면
        // 항목을 하나도 안 넣는데, 그게 **일부러 그렇게 한 것**이다 (카드 "꼭 지켜야 하는 것" 3).
        val body = confirmation(patientIndex = null).toJson()

        assertFalse(body.getJSONArray("splits").getJSONObject(0).has("patient_index"))
    }

    @Test
    fun `행의 빈 메모는 안 보낸다`() {
        val body = confirmation(reasonDetail = null).toJson()

        assertFalse(body.getJSONArray("splits").getJSONObject(0).has("reason_detail"))
    }

    @Test
    fun `pet_id 를 안 주면 칸째로 뺀다 — 저쪽이 초안의 강아지를 쓴다`() {
        val body = confirmation(petId = null).toJson()

        assertFalse(body.getJSONArray("splits").getJSONObject(0).has("pet_id"))
    }

    @Test
    fun `확정된 기록은 코드만 들고 온다 — 표시명은 목록이 따로 붙인다`() {
        val visit = VetVisit.parse(JSONObject(VISIT_JSON))

        assertEquals("skin", visit.reasonCode)
        assertEquals(LocalDate.of(2026, 9, 2), visit.visitedOn)
        assertEquals(80_000, visit.totalKrw)
        assertNull(visit.hospitalAddress)
    }

    @Test
    fun `patient_count 가 블록 수를 싣고 온다`() {
        val draft = VetVisitDraft.parse(JSONObject(MULTI_PET_JSON))

        assertEquals(2, draft.patientCount)
    }

    @Test
    fun `patient_count 가 없는 응답은 한 마리로 읽는다 — 옛 서버에서 분할을 묻지 않는다`() {
        val draft = VetVisitDraft.parse(JSONObject(NO_AMOUNT_JSON))

        assertEquals(1, draft.patientCount)
    }

    @Test
    fun `항목은 제가 어느 블록의 것인지 들고 온다`() {
        val draft = VetVisitDraft.parse(JSONObject(MULTI_PET_JSON))

        assertEquals(listOf(0, 0, 1), draft.items.map { it.patientIndex })
    }

    @Test
    fun `patient_index 가 없는 항목은 어느 블록인지 모르는 것이다`() {
        val draft = VetVisitDraft.parse(JSONObject(NO_AMOUNT_JSON))

        assertNull("모르는 것을 0 으로 접으면 남의 아이에게 붙는다", draft.items[0].patientIndex)
    }

    private fun split(
        clientEventId: String = "10000000-0000-4000-8000-000000000001",
        petId: String? = "pet-a",
        reasonCode: String = "skin",
        reasonDetail: String? = null,
        totalKrw: Int = 61_700,
        isEmergency: Boolean = false,
        isOncology: Boolean = true,
        patientIndex: Int? = 0,
    ) = VetVisitSplit(
        clientEventId = clientEventId,
        petId = petId,
        reasonCode = reasonCode,
        reasonDetail = reasonDetail,
        totalKrw = totalKrw,
        isEmergency = isEmergency,
        isOncology = isOncology,
        patientIndex = patientIndex,
    )

    private fun confirmation(
        hospitalName: String? = null,
        hospitalAddress: String? = null,
        hospitalPhone: String? = null,
        petId: String? = "pet-a",
        reasonDetail: String? = null,
        patientIndex: Int? = 0,
    ) = VetVisitConfirmation(
        visitedOn = LocalDate.of(2026, 9, 10),
        totalKrw = 61_700,
        hospitalName = hospitalName,
        hospitalAddress = hospitalAddress,
        hospitalPhone = hospitalPhone,
        splits = listOf(split(petId = petId, reasonDetail = reasonDetail, patientIndex = patientIndex)),
    )

    private companion object {
        const val MULTI_PET_JSON = """
            {"draft_id": "d3", "pet_id": "p1", "receipt_image_url": "http://x/y",
             "extraction_status": "ok", "unreadable_reason": null,
             "visited_on": "2026-09-15", "total_krw": 191300,
             "hospital_name": "행복동물의료센터", "hospital_address": null,
             "hospital_phone": null, "patient_count": 2,
             "items": [{"name": "진료-초진", "amount_krw": 15000, "patient_index": 0},
                       {"name": "*검사-귀-도말", "amount_krw": 94200, "patient_index": 0},
                       {"name": "종합백신 5차", "amount_krw": 82100, "patient_index": 1}],
             "suggested_reason_code": "ear", "is_emergency": false,
             "possible_duplicate": false,
             "reason_options": [{"code": "ear", "label": "귀"}]}
        """

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
