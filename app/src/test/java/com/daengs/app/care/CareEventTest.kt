package com.daengs.app.care

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 저쪽 `schemas/care_event.py`(SAJOYO/DAENGS_dev#332) 의 응답 모양을 이쪽이 그대로 읽는가.
 * 한쪽만 고치지 말라는 계약이라, 칸 이름이 어긋나면 여기서 먼저 깨져야 한다.
 */
class CareEventTest {

    @Test
    fun `기록 한 건을 응답 그대로 읽는다`() {
        val event = CareEvent.parse(JSONObject(EVENT_JSON))

        assertEquals("6ba7b811-9dad-11d1-80b4-00c04fd430c8", event.id)
        assertEquals("3f2504e0-4f89-11d3-9a0c-0305e82c3301", event.petId)
        assertEquals(CareKind.MEDICATION, event.kind)
        assertEquals(1_756_701_000_000L, event.occurredAtMs) // 2025-09-01T04:30:00Z
        assertEquals("사료 반만", event.note)
        assertEquals("10000000-0000-4000-8000-000000000001", event.clientEventId)
        assertEquals("20000000-0000-4000-8000-000000000002", event.actor?.appUserId)
        assertEquals("키키", event.actor?.displayName)
    }

    @Test
    fun `메모가 null 이면 null 로 읽는다`() {
        val event = CareEvent.parse(JSONObject(EVENT_JSON).put("note", JSONObject.NULL))
        assertNull(event.note)
    }

    @Test
    fun `옛 기록에 작성자가 없어도 읽는다`() {
        val event = CareEvent.parse(JSONObject(EVENT_JSON).apply { remove("actor") })
        assertNull(event.actor)
    }

    @Test
    fun `kind 는 밥·약·간식 셋뿐이다 — walk 는 산책 표가 진실이라 여기 없다`() {
        assertEquals(CareKind.MEAL, CareKind.parse("meal"))
        assertEquals(CareKind.SNACK, CareKind.parse("snack"))
        assertEquals("medication", CareKind.MEDICATION.wire)
        assertThrows(IllegalArgumentException::class.java) { CareKind.parse("walk") }
    }

    @Test
    fun `하루 요약은 건수 넷과 그날 목록을 같이 준다`() {
        val summary = CareDaySummary.parse(JSONObject(SUMMARY_JSON))

        assertEquals("3f2504e0-4f89-11d3-9a0c-0305e82c3301", summary.petId)
        assertEquals("2025-09-01", summary.day)
        assertEquals("Asia/Seoul", summary.timezone)
        assertEquals(2, summary.meal)
        assertEquals(1, summary.medication)
        assertEquals(3, summary.snack)
        assertEquals(1, summary.walk)
        assertEquals(listOf(CareKind.MEDICATION), summary.events.map { it.kind })
    }

    @Test
    fun `건수 칸이 없으면 0 으로 읽는다 — 옛 서버가 줄여 보내도 화면이 안 죽는다`() {
        val json = JSONObject(SUMMARY_JSON).apply { remove("walk"); remove("events") }
        val summary = CareDaySummary.parse(json)
        assertEquals(0, summary.walk)
        assertEquals(emptyList<CareEvent>(), summary.events)
    }

    private companion object {
        const val EVENT_JSON = """
            {"id": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
             "pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "kind": "medication",
             "occurred_at": "2025-09-01T13:30:00+09:00",
             "note": "사료 반만",
             "client_event_id": "10000000-0000-4000-8000-000000000001",
             "actor": {"app_user_id":"20000000-0000-4000-8000-000000000002","nickname":"키키"},
             "created_at": "2025-09-01T13:30:05+09:00"}
        """
        const val SUMMARY_JSON = """
            {"pet_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
             "day": "2025-09-01", "timezone": "Asia/Seoul",
             "start": "2025-09-01T00:00:00+09:00", "end": "2025-09-02T00:00:00+09:00",
             "meal": 2, "medication": 1, "snack": 3, "walk": 1,
             "events": [$EVENT_JSON]}
        """
    }
}
