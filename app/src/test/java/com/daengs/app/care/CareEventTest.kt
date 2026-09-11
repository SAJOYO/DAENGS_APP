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

    /**
     * **저쪽은 `actor` 를 통째로 비우지 않는다.** `routers/care_event.py` 의 `_to_response`
     * 는 `actor_app_user_id` 가 없어도 객체는 만들어 보내므로, 컬럼이 생기기 전에 쌓인
     * 기록과 탈퇴자는 이 모양으로 온다 — 속 두 칸이 다 null 이다.
     *
     * 예전 `getString` 파싱은 여기서 갈렸다: 단위 테스트의 참조 `org.json` 은 예외를
     * 던져 **그날 케어 기록이 통째로 안 읽혔고**, 안드로이드의 `org.json` 은 문자열
     * `"null"` 을 돌려주어 **가짜 id 가 모델에 앉았다.** 두 구현이 같은 답을 내야 한다.
     */
    @Test
    fun `작성자 칸이 비어 온 기록도 읽고 이전 보호자로 그린다`() {
        val actorless = JSONObject(EVENT_JSON).put(
            "actor",
            JSONObject().put("app_user_id", JSONObject.NULL).put("nickname", JSONObject.NULL),
        )

        val event = CareEvent.parse(actorless)

        assertNull("id 자리에 \"null\" 문자열이 앉으면 안 된다", event.actor?.appUserId)
        assertNull(event.actor?.nickname)
        assertEquals("이전 보호자", event.actor?.displayName)
    }

    /** 한 줄이 이러면 그날 목록 전체가 안 읽히던 자리다. 목록 파싱까지 살아야 한다. */
    @Test
    fun `작성자 칸이 빈 기록이 섞여도 하루 요약이 통째로 깨지지 않는다`() {
        val summary = CareDaySummary.parse(
            JSONObject(SUMMARY_JSON).apply {
                getJSONArray("events").getJSONObject(0).put(
                    "actor",
                    JSONObject().put("app_user_id", JSONObject.NULL).put("nickname", JSONObject.NULL),
                )
            },
        )

        assertEquals(1, summary.events.size)
        assertNull(summary.events.first().actor?.appUserId)
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
