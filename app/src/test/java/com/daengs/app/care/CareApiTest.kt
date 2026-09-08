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
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * `/app/care-events` 로 **무엇을 어디에 어떻게 보내는가.** 저쪽 `routers/care_event.py`
 * (SAJOYO/DAENGS_dev#332) 가 못박은 것 — 멱등키는 **바디의 `client_event_id`** 이지 헤더가
 * 아니다, `occurred_at` 은 timezone 이 있어야 한다(없으면 422), 같은 키를 다시 보내면 201
 * 대신 200 인데 둘 다 "올라갔다" 다, 삭제는 204 라 본문이 없다.
 */
class CareApiTest {

    private val transport = FakeTransport()
    private val api = CareApi(baseUrl = { "http://server:8000/" }, transport = transport)

    private val token = "acc-token-secret-xyz"
    private val pet = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
    private val eventId = "6ba7b811-9dad-11d1-80b4-00c04fd430c8"
    private val clientEventId = "10000000-0000-4000-8000-000000000001"
    private val occurredAt = OffsetDateTime.of(2025, 9, 1, 13, 30, 0, 0, ZoneOffset.ofHours(9))

    @Test
    fun `하루 요약은 GET today 에 pet_id 쿼리다`() = runBlocking {
        transport.reply = HttpReply(200, SUMMARY_JSON)
        val got = api.today(token, pet).getOrThrow()

        val call = transport.only()
        assertEquals("GET", call.method)
        assertEquals("http://server:8000/app/care-events/today?pet_id=$pet", call.url)
        assertEquals("Bearer $token", call.headers["Authorization"])
        assertEquals("application/json", call.headers["Accept"])
        assertEquals(2, got.meal)
    }

    @Test
    fun `기록은 POST 바디에 pet_id·kind·offset 붙은 시각·client_event_id 를 싣는다`() = runBlocking {
        transport.reply = HttpReply(201, EVENT_JSON)
        val got = api.record(token, pet, CareKind.SNACK, occurredAt, clientEventId).getOrThrow()

        val call = transport.only()
        assertEquals("POST", call.method)
        assertEquals("http://server:8000/app/care-events", call.url)
        val body = JSONObject(call.body!!)
        assertEquals(pet, body.getString("pet_id"))
        assertEquals("snack", body.getString("kind"))
        assertEquals("2025-09-01T13:30:00+09:00", body.getString("occurred_at"))
        assertEquals(clientEventId, body.getString("client_event_id"))
        assertFalse("메모는 이 카드에 없다 — 칸을 아예 안 보낸다", body.has("note"))
        assertFalse("멱등키는 헤더가 아니라 바디다", call.headers.keys.any { it.contains("Idempotency", ignoreCase = true) })
        assertEquals(eventId, got.id)
    }

    @Test
    fun `같은 키를 다시 보내 200 이 와도 올라간 것이다`() = runBlocking {
        transport.reply = HttpReply(200, EVENT_JSON)
        assertEquals(eventId, api.record(token, pet, CareKind.MEAL, occurredAt, clientEventId).getOrThrow().id)
    }

    @Test
    fun `삭제는 DELETE 경로에 id 이고 204 본문 없음이 성공이다`() = runBlocking {
        transport.reply = HttpReply(204, "")
        api.delete(token, eventId).getOrThrow()

        val call = transport.only()
        assertEquals("DELETE", call.method)
        assertEquals("http://server:8000/app/care-events/$eventId", call.url)
    }

    @Test
    fun `서버 오류는 상태 코드를 든 ChatApiError 다`() = runBlocking {
        transport.reply = HttpReply(404, """{"detail": "강아지를 찾을 수 없습니다."}""")
        val error = api.today(token, pet).exceptionOrNull() as ChatApiError
        assertTrue(error.notFound)
        assertEquals("강아지를 찾을 수 없습니다.", error.message)
    }

    @Test
    fun `연결이 안 되면 상태 0 과 우리말 문장이다`() = runBlocking {
        transport.failure = IOException("Unable to resolve host")
        val error = api.today(token, pet).exceptionOrNull() as ChatApiError
        assertEquals(0, error.status)
        assertEquals("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", error.message)
    }

    private class FakeTransport : HttpTransport {
        val calls = mutableListOf<HttpCall>()
        var reply = HttpReply(200, "{}")
        var failure: Exception? = null

        override fun exchange(call: HttpCall): HttpReply {
            calls += call
            failure?.let { throw it }
            return reply
        }

        fun only(): HttpCall = calls.single()
    }

    private companion object {
        const val EVENT_JSON = """
            {"id": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
             "pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "kind": "snack", "occurred_at": "2025-09-01T13:30:00+09:00", "note": null,
             "client_event_id": "10000000-0000-4000-8000-000000000001",
             "created_at": "2025-09-01T13:30:05+09:00"}
        """
        const val SUMMARY_JSON = """
            {"pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "day": "2025-09-01", "timezone": "Asia/Seoul",
             "start": "2025-09-01T00:00:00+09:00", "end": "2025-09-02T00:00:00+09:00",
             "meal": 2, "medication": 1, "snack": 3, "walk": 1, "events": []}
        """
    }
}
