package com.daengs.app.care

import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.HttpCall
import com.daengs.app.chat.HttpReply
import com.daengs.app.chat.HttpTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * `/app/vet-visits` 로 **무엇을 어디에 어떻게 보내는가.** 저쪽 `routers/vet_visit.py`
 * (SAJOYO/DAENGS_dev#353) 가 못박은 것 — 초안은 200 도 성공이다, bridge PUT 은 토큰을
 * 안 붙이고 티켓의 헤더를 그대로 쓴다, 그 409 는 "이미 올라가 있다" 다, 추출은 못 읽어도
 * 200 이다, 확정에는 항목을 안 싣는다.
 */
class VetVisitApiTest {

    private val transport = FakeTransport()
    private val uploader = FakeUploader()
    private val api = VetVisitApi({ "http://server:8000/" }, transport, uploader)

    private val token = "acc-token-secret-xyz"
    private val pet = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
    private val draftId = "6ba7b811-9dad-11d1-80b4-00c04fd430c8"
    private val clientEventId = "10000000-0000-4000-8000-000000000001"

    @Test
    fun `초안은 POST 바디에 pet_id·content_type·client_event_id 를 싣는다`() = runBlocking {
        transport.reply = HttpReply(201, TICKET_JSON)
        val ticket = api.startDraft(token, pet, clientEventId).getOrThrow()

        val call = transport.only()
        assertEquals("POST", call.method)
        assertEquals("http://server:8000/app/vet-visits", call.url)
        assertEquals("Bearer $token", call.headers["Authorization"])
        val body = JSONObject(call.body!!)
        assertEquals(pet, body.getString("pet_id"))
        assertEquals("image/jpeg", body.getString("content_type"))
        assertEquals(clientEventId, body.getString("client_event_id"))
        assertTrue(ticket.created)
    }

    @Test
    fun `같은 client_event_id 의 200 도 성공이다 — 있던 초안이 온다`() = runBlocking {
        transport.reply = HttpReply(200, TICKET_JSON)
        val ticket = api.startDraft(token, pet, clientEventId).getOrThrow()

        assertFalse("새로 만든 게 아니다", ticket.created)
        assertEquals(draftId, ticket.draftId)
    }

    @Test
    fun `업로드는 티켓의 주소·헤더를 그대로 쓰고 토큰을 안 붙인다`() = runBlocking {
        uploader.status = 200
        api.upload(ticket(), byteArrayOf(1, 2, 3)).getOrThrow()

        assertEquals("http://bridge/vet/d1.jpg", uploader.url)
        assertEquals(mapOf("Content-Type" to "image/jpeg"), uploader.headers)
        assertFalse("bridge 는 인증 헤더를 안 받는다", uploader.headers.containsKey("Authorization"))
        assertEquals(3, uploader.bytes?.size)
    }

    @Test
    fun `업로드 409 는 이미 올라가 있다는 뜻이라 성공으로 접는다`() = runBlocking {
        uploader.status = 409
        assertTrue(api.upload(ticket(), byteArrayOf(1)).isSuccess)
    }

    @Test
    fun `GCS 의 412 도 같은 뜻이라 같이 접는다 — 저장소가 바뀌어도 안 죽는다`() = runBlocking {
        // LocalBridge 는 FileExistsError → 409, GCS 는 x-goog-if-generation-match 0 이
        // 깨져 412 다. 둘 다 "그 자리에 이미 온전한 바이트가 있다" 다.
        uploader.status = 412
        assertTrue(api.upload(ticket(), byteArrayOf(1)).isSuccess)
    }

    @Test
    fun `상한을 넘는 사진은 올리기 전에 막는다 — 다 보내고 413 을 받지 않는다`() = runBlocking {
        val error = api.upload(ticket(), ByteArray(MAX_RECEIPT_BYTES + 1))
            .exceptionOrNull() as ChatApiError

        assertEquals("서버를 아예 안 두드린다", null, uploader.url)
        assertEquals("영수증 사진이 너무 커요. 다시 찍어 주세요.", error.message)
    }

    @Test
    fun `업로드 415 는 실패다 — 상태 코드를 든 오류가 온다`() = runBlocking {
        uploader.status = 415
        val error = api.upload(ticket(), byteArrayOf(1)).exceptionOrNull() as ChatApiError
        assertEquals(415, error.status)
    }

    @Test
    fun `업로드가 닿지 못하면 status 0 이다`() = runBlocking {
        uploader.failWith = IOException("no route")
        val error = api.upload(ticket(), byteArrayOf(1)).exceptionOrNull() as ChatApiError
        assertEquals(0, error.status)
    }

    @Test
    fun `추출은 POST extract 이고 못 읽어도 200 이라 성공이다`() = runBlocking {
        transport.reply = HttpReply(200, UNREADABLE_JSON)
        val draft = api.extract(token, draftId).getOrThrow()

        val call = transport.only()
        assertEquals("POST", call.method)
        assertEquals("http://server:8000/app/vet-visits/$draftId/extract", call.url)
        assertEquals(ExtractionStatus.UNREADABLE, draft.status)
        assertEquals(UnreadableReason.NO_AMOUNT, draft.unreadableReason)
    }

    @Test
    fun `추출 409 는 서버가 준 문장을 그대로 보여 준다`() = runBlocking {
        transport.reply = HttpReply(409, CONFLICT_JSON)
        val error = api.extract(token, draftId).exceptionOrNull() as ChatApiError

        assertEquals(409, error.status)
        assertEquals("photo_not_uploaded", error.code)
        assertEquals("업로드된 영수증 사진을 찾을 수 없습니다.", error.message)
    }

    @Test
    fun `확정은 POST confirm 이고 항목을 안 싣는다`() = runBlocking {
        // 확정 응답은 언제나 배열이다 — 한 마리여도 길이 1 이다.
        transport.reply = HttpReply(200, "[$VISIT_JSON]")
        api.confirm(token, draftId, confirmation()).getOrThrow()

        val call = transport.only()
        assertEquals("http://server:8000/app/vet-visits/$draftId/confirm", call.url)
        val body = JSONObject(call.body!!)
        assertFalse("항목은 초안에서만 읽는다 — 본문에 자리가 없다", body.has("items"))
        assertFalse(body.has("raw_ocr_items"))
        assertEquals(clientEventId, body.getJSONArray("splits").getJSONObject(0).getString("client_event_id"))
    }

    @Test
    fun `확정 응답은 배열이다 — 아이 수만큼 기록이 생긴다`() = runBlocking {
        transport.reply = HttpReply(200, TWO_VISITS_JSON)

        val visits = api.confirm(token, draftId, confirmation(rows = 2)).getOrThrow()

        assertEquals(listOf("v-a", "v-b"), visits.map { it.id })
        assertEquals(listOf("pet-a", "pet-b"), visits.map { it.petId })
    }

    @Test
    fun `한 마리를 확정해도 배열 한 줄로 온다`() = runBlocking {
        transport.reply = HttpReply(200, "[$VISIT_JSON]")

        assertEquals(1, api.confirm(token, draftId, confirmation()).getOrThrow().size)
    }

    @Test
    fun `목록은 pet_id 쿼리이고 최근 먼저 온다`() = runBlocking {
        transport.reply = HttpReply(200, LIST_JSON)
        val visits = api.list(token, pet).getOrThrow()

        assertEquals("http://server:8000/app/vet-visits?pet_id=$pet", transport.only().url)
        assertEquals(listOf("v1", "v2"), visits.map { it.id })
    }

    @Test
    fun `사유 목록은 배열로 온다 — 최근 사유가 앞이다`() = runBlocking {
        transport.reply = HttpReply(200, OPTIONS_JSON)
        val options = api.reasonOptions(token, pet).getOrThrow()

        assertEquals(
            "http://server:8000/app/vet-visits/reason-options?pet_id=$pet",
            transport.only().url,
        )
        assertEquals(listOf("skin", "vaccination"), options.map { it.code })
        assertEquals("피부", options[0].label)
    }

    @Test
    fun `삭제는 DELETE 이고 204 라 본문이 없다`() = runBlocking {
        transport.reply = HttpReply(204, "")
        api.delete(token, "v1").getOrThrow()

        val call = transport.only()
        assertEquals("DELETE", call.method)
        assertEquals("http://server:8000/app/vet-visits/v1", call.url)
    }

    @Test
    fun `서버 주소가 비면 부르기 전에 막는다`() = runBlocking {
        val blind = VetVisitApi({ "" }, transport, uploader)
        assertTrue(blind.startDraft(token, pet, clientEventId).exceptionOrNull() is IllegalStateException)
        assertEquals("서버를 아예 안 두드린다", 0, transport.calls.size)
    }

    private fun ticket() = VetVisitTicket(
        draftId = draftId,
        uploadUrl = "http://bridge/vet/d1.jpg",
        uploadHeaders = mapOf("Content-Type" to "image/jpeg"),
        created = true,
    )

    private fun confirmation(rows: Int = 1) = VetVisitConfirmation(
        visitedOn = LocalDate.of(2026, 9, 10),
        totalKrw = 61_700,
        hospitalName = null,
        hospitalAddress = null,
        hospitalPhone = null,
        splits = List(rows) { index ->
            VetVisitSplit(
                clientEventId = if (index == 0) clientEventId else "$clientEventId-$index",
                petId = null,
                reasonCode = "skin",
                reasonDetail = null,
                totalKrw = 61_700 / rows,
                isEmergency = false,
                isOncology = false,
                patientIndex = index,
            )
        },
    )

    private class FakeTransport : HttpTransport {
        val calls = mutableListOf<HttpCall>()
        var reply = HttpReply(200, "{}")
        override fun exchange(call: HttpCall): HttpReply {
            calls += call
            return reply
        }
        fun only(): HttpCall = calls.single()
    }

    private class FakeUploader : BridgeUploader {
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var bytes: ByteArray? = null
        var status = 200
        var failWith: Throwable? = null
        override fun put(url: String, headers: Map<String, String>, bytes: ByteArray): Int {
            this.url = url
            this.headers = headers
            this.bytes = bytes
            failWith?.let { throw it }
            return status
        }
    }

    private companion object {
        const val TWO_VISITS_JSON = """
            [{"id": "v-a", "pet_id": "pet-a", "visited_on": "2026-09-15", "total_krw": 109200,
              "hospital_name": null, "hospital_address": null, "hospital_phone": null,
              "reason_code": "ear", "reason_detail": null, "is_emergency": false,
              "is_oncology": false, "client_event_id": "id-0"},
             {"id": "v-b", "pet_id": "pet-b", "visited_on": "2026-09-15", "total_krw": 82100,
              "hospital_name": null, "hospital_address": null, "hospital_phone": null,
              "reason_code": "vaccination", "reason_detail": null, "is_emergency": false,
              "is_oncology": false, "client_event_id": "id-1"}]
        """

        const val TICKET_JSON = """
            {"draft_id": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
             "pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "storage_key": "vet/d1.jpg", "upload_url": "http://bridge/vet/d1.jpg",
             "upload_headers": {"Content-Type": "image/jpeg"}, "expires_in_seconds": 600}
        """

        const val UNREADABLE_JSON = """
            {"draft_id": "d1", "pet_id": "p1", "receipt_image_url": "http://x/y",
             "extraction_status": "unreadable", "unreadable_reason": "no_amount",
             "visited_on": "2019-05-17", "total_krw": null,
             "hospital_name": "압구정동물병원", "hospital_address": null,
             "hospital_phone": null, "items": [], "suggested_reason_code": null,
             "is_emergency": false, "possible_duplicate": false, "reason_options": []}
        """

        const val CONFLICT_JSON = """
            {"detail": {"code": "photo_not_uploaded",
                        "message": "업로드된 영수증 사진을 찾을 수 없습니다."}}
        """

        const val VISIT_JSON = """
            {"id": "v1", "pet_id": "p1", "visited_on": "2026-09-10", "total_krw": 61700,
             "hospital_name": null, "hospital_address": null, "hospital_phone": null,
             "reason_code": "skin", "reason_detail": null, "suggested_reason_code": null,
             "is_emergency": false, "is_oncology": false,
             "client_event_id": "10000000-0000-4000-8000-000000000001",
             "created_at": "2026-09-10T11:00:00+09:00"}
        """

        const val LIST_JSON = """
            {"pet_id": "p1", "start": "2025-09-10", "end": "2026-09-10",
             "visits": [
               {"id": "v1", "pet_id": "p1", "visited_on": "2026-09-10", "total_krw": 61700,
                "hospital_name": null, "hospital_address": null, "hospital_phone": null,
                "reason_code": "skin", "reason_detail": null, "suggested_reason_code": null,
                "is_emergency": false, "is_oncology": false, "client_event_id": "c1",
                "created_at": "2026-09-10T11:00:00+09:00"},
               {"id": "v2", "pet_id": "p1", "visited_on": "2026-09-02", "total_krw": 80000,
                "hospital_name": null, "hospital_address": null, "hospital_phone": null,
                "reason_code": "ear", "reason_detail": null, "suggested_reason_code": null,
                "is_emergency": false, "is_oncology": false, "client_event_id": "c2",
                "created_at": "2026-09-02T11:00:00+09:00"}]}
        """

        const val OPTIONS_JSON = """
            [{"code": "skin", "label": "피부"}, {"code": "vaccination", "label": "예방접종"}]
        """
    }
}
