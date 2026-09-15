package com.daengs.app.walk.shared

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저쪽 `schemas/walk_group.py`(SAJOYO/DAENGS_dev#539) 응답을 이쪽이 그대로 읽는가. 칸 이름이
 * 어긋나면 여기서 먼저 깨져야 한다.
 */
class SharedWalkTest {

    @Test
    fun `목록 한 페이지를 응답 그대로 읽는다`() {
        val page = SharedWalkPage.parse(JSONObject(PAGE_JSON))

        assertEquals("p1", page.petId)
        assertEquals("next-1", page.nextCursor)
        val first = page.walks[0]
        assertEquals("w1", first.id)
        assertEquals(1_756_681_200_000L, first.startedAtMs) // 2025-09-01T08:00+09:00
        assertEquals(1_756_683_600_000L, first.endedAtMs)
        assertEquals(2_400L, first.durationS)
        assertEquals(1_234, first.distanceM)
        assertEquals(2_100, first.movingS)
        assertEquals("키키", first.actor.displayName)
        assertFalse(first.isMine)
        assertEquals(listOf("p1"), first.petIds)

        val second = page.walks[1]
        assertNull("계산 전이면 거리는 없다", second.distanceM)
        assertNull(second.movingS)
        assertEquals("u3", second.actor.appUserId)
        assertEquals("지금 구성원이 아니면 이전 보호자다", "이전 보호자", second.actor.displayName)
        assertTrue(second.isMine)
    }

    @Test
    fun `마지막 페이지는 다음 커서가 없다`() {
        val page = SharedWalkPage.parse(JSONObject(PAGE_JSON).put("next_cursor", JSONObject.NULL))

        assertNull(page.nextCursor)
    }

    @Test
    fun `상세의 경로는 소수를 문자열로 받아도 숫자로 받아도 읽는다`() {
        val json = JSONObject(WALK_W1).put(
            "points",
            JSONArray(
                """[
                    {"at": "2025-09-01T08:00:00+09:00", "lat": "37.5665000", "lng": "126.9780000"},
                    {"at": "2025-09-01T08:01:00+09:00", "lat": 37.567, "lng": 126.979}
                ]""",
            ),
        )

        val detail = SharedWalkDetail.parse(json)

        assertEquals("w1", detail.walk.id)
        assertEquals(2, detail.points.size)
        assertEquals(37.5665, detail.points[0].lat, 1e-9)
        assertEquals(126.978, detail.points[0].lng, 1e-9)
        assertEquals(126.979, detail.points[1].lng, 1e-9)
        assertEquals(1_756_681_260_000L, detail.points[1].atMs)
    }

    /** 게임·점령 결과는 모델에 칸이 없다 — 이번 결정으로 후순위 보류다. 섞여 와도 그대로 버린다. */
    @Test
    fun `게임 칸이 섞여 와도 모델은 산책 정보만 든다`() {
        val plain = SharedWalk.parse(JSONObject(WALK_W1))
        val withGame = SharedWalk.parse(JSONObject(WALK_W1).put("contribution", JSONObject().put("score", 10)))

        assertEquals(plain, withGame)
    }

    private companion object {
        const val WALK_W1 = """
            {"id": "w1", "started_at": "2025-09-01T08:00:00+09:00", "ended_at": "2025-09-01T08:40:00+09:00",
             "duration_s": 2400, "distance_m": 1234, "moving_s": 2100,
             "actor": {"app_user_id": "u2", "nickname": "키키"}, "is_mine": false, "pet_ids": ["p1"]}
        """
        const val WALK_W2 = """
            {"id": "w2", "started_at": "2025-09-01T07:00:00+09:00", "ended_at": "2025-09-01T07:20:00+09:00",
             "duration_s": 1200, "distance_m": null, "moving_s": null,
             "actor": {"app_user_id": "u3", "nickname": null}, "is_mine": true, "pet_ids": ["p1"]}
        """
        const val PAGE_JSON = """{"pet_id": "p1", "walks": [$WALK_W1, $WALK_W2], "next_cursor": "next-1"}"""
    }
}
