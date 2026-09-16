package com.daengs.app.walk.shared

import com.daengs.app.location.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저쪽 `schemas/walk_group.py`(통합 목록 `GroupWalkFeedResponse`, 상세 `GroupWalkDetail`) 응답을 이쪽이
 * 그대로 읽는가. 칸 이름이 어긋나면 여기서 먼저 깨져야 한다.
 */
class SharedWalkTest {

    @Test
    fun `통합 목록 한 페이지를 응답 그대로 읽는다`() {
        val page = SharedWalkFeedPage.parse(JSONObject(FEED_JSON))

        assertEquals("next-1", page.nextCursor)
        assertEquals(SharedWalkTotals(count = 7, distanceM = 9_600, durationS = 41_376), page.totals)
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
        assertEquals(3, first.weatherCode)
        assertEquals(true, first.isDay)
        assertEquals(21.5f, first.temperatureC!!, 1e-6f)
        assertEquals(
            listOf(listOf(GeoPoint(37.5665, 126.978), GeoPoint(37.567, 126.979)), listOf(GeoPoint(37.568, 126.98))),
            first.routePreview,
        )

        val second = page.walks[1]
        assertNull("계산 전이면 거리는 없다", second.distanceM)
        assertNull(second.weatherCode)
        assertNull(second.isDay)
        assertNull(second.temperatureC)
        assertTrue("좌표가 없으면 썸네일 경로도 없다", second.routePreview.isEmpty())
        assertEquals("지금 구성원이 아니면 이전 보호자다", "이전 보호자", second.actor.displayName)

        assertEquals(listOf("me", "u2"), page.carers.map { it.appUserId })
        assertEquals("나", page.carers[0].displayName)
        assertEquals("키키", page.carers[1].displayName)
        assertEquals(listOf("p1", "p2"), page.carers[1].petIds)
    }

    @Test
    fun `마지막 페이지는 다음 커서가 없다`() {
        val page = SharedWalkFeedPage.parse(JSONObject(FEED_JSON).put("next_cursor", JSONObject.NULL))

        assertNull(page.nextCursor)
    }

    /** 상세(#539)에는 날씨·썸네일 칸이 없다 — 없으면 null·빈 목록으로 읽는다. */
    @Test
    fun `상세의 경로는 소수를 문자열로 받아도 숫자로 받아도 읽는다`() {
        val json = JSONObject(WALK_W1_DETAIL).put(
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
        assertNull(detail.walk.weatherCode)
        assertTrue(detail.walk.routePreview.isEmpty())
        assertEquals(2, detail.points.size)
        assertEquals(37.5665, detail.points[0].lat, 1e-9)
        assertEquals(126.978, detail.points[0].lng, 1e-9)
        assertEquals(126.979, detail.points[1].lng, 1e-9)
        assertEquals(1_756_681_260_000L, detail.points[1].atMs)
    }

    /** 게임·점령 결과는 모델에 칸이 없다 — 이번 결정으로 후순위 보류다. 섞여 와도 그대로 버린다. */
    @Test
    fun `게임 칸이 섞여 와도 모델은 산책 정보만 든다`() {
        val plain = SharedWalk.parse(JSONObject(WALK_W1_DETAIL))
        val withGame = SharedWalk.parse(JSONObject(WALK_W1_DETAIL).put("contribution", JSONObject().put("score", 10)))

        assertEquals(plain, withGame)
    }

    private companion object {
        const val WALK_W1_DETAIL = """
            {"id": "w1", "started_at": "2025-09-01T08:00:00+09:00", "ended_at": "2025-09-01T08:40:00+09:00",
             "duration_s": 2400, "distance_m": 1234, "moving_s": 2100,
             "actor": {"app_user_id": "u2", "nickname": "키키"}, "is_mine": false, "pet_ids": ["p1"]}
        """
        const val WALK_W1 = """
            {"id": "w1", "started_at": "2025-09-01T08:00:00+09:00", "ended_at": "2025-09-01T08:40:00+09:00",
             "duration_s": 2400, "distance_m": 1234, "moving_s": 2100,
             "actor": {"app_user_id": "u2", "nickname": "키키"}, "is_mine": false, "pet_ids": ["p1"],
             "weather_code": 3, "is_day": true, "temperature_c": "21.5",
             "route_preview": [[{"lat": 37.5665, "lng": 126.978}, {"lat": 37.567, "lng": 126.979}], [{"lat": 37.568, "lng": 126.98}]]}
        """
        const val WALK_W2 = """
            {"id": "w2", "started_at": "2025-09-01T07:00:00+09:00", "ended_at": "2025-09-01T07:20:00+09:00",
             "duration_s": 1200, "distance_m": null, "moving_s": null,
             "actor": {"app_user_id": "u3", "nickname": null}, "is_mine": false, "pet_ids": ["p1"],
             "weather_code": null, "is_day": null, "temperature_c": null, "route_preview": []}
        """
        const val FEED_JSON = """{"walks": [$WALK_W1, $WALK_W2],
            "totals": {"count": 7, "distance_m": 9600, "duration_s": 41376},
            "carers": [{"app_user_id": "me", "nickname": "롱롱씨 메인", "is_me": true, "pet_ids": ["p1"]},
                       {"app_user_id": "u2", "nickname": "키키", "is_me": false, "pet_ids": ["p1", "p2"]}],
            "next_cursor": "next-1"}"""
    }
}
