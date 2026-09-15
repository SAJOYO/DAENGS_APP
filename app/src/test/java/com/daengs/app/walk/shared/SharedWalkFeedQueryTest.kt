package com.daengs.app.walk.shared

import com.daengs.app.walk.WalkDepartureWeather
import com.daengs.app.walk.WalkHistoryFilter
import com.daengs.app.walk.WalkSeason
import com.daengs.app.walk.records.WalkRecordsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 산책 기록 조건 → 통합 목록 조건. 계절·날씨 분류는 앱 규칙 그대로 서버가 알아듣는 값으로 풀린다. */
class SharedWalkFeedQueryTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test fun `강아지 기간 시간대는 그대로 옮기고 보호자 조건에서 나는 뺀다`() {
        val query = WalkRecordsQuery(setOf("p1"), WalkHistoryFilter(from = LocalDate.of(2026, 8, 13), through = LocalDate.of(2026, 9, 11)))

        val feed = sharedFeedQueryOf(query, carerIds = setOf("me", "u2"), myId = "me", behaviorActive = false, zone = seoul)!!

        assertEquals(setOf("p1"), feed.petIds)
        assertEquals(setOf("u2"), feed.actorIds)
        assertEquals(LocalDate.of(2026, 8, 13), feed.dateFrom)
        assertEquals(LocalDate.of(2026, 9, 11), feed.dateThrough)
        assertEquals("Asia/Seoul", feed.zoneId)
        assertTrue(feed.months.isEmpty() && feed.weatherCodes.isEmpty() && !feed.weatherMissing)
    }

    @Test fun `모든 강아지 모든 보호자는 조건 없이 묻는다`() {
        val feed = sharedFeedQueryOf(WalkRecordsQuery(), carerIds = null, myId = "me", behaviorActive = false, zone = seoul)!!

        assertNull(feed.petIds)
        assertNull(feed.actorIds)
    }

    @Test fun `계절은 앱의 계절 규칙대로 월로 풀린다`() {
        val query = WalkRecordsQuery(filter = WalkHistoryFilter(seasons = setOf(WalkSeason.WINTER, WalkSeason.SPRING)))

        val feed = sharedFeedQueryOf(query, null, "me", false, seoul)!!

        assertEquals(setOf(12, 1, 2, 3, 4, 5), feed.months)
    }

    @Test fun `날씨는 분류에 드는 코드 전부로 풀리고 정보 없음은 모르는 코드와 날씨 없는 산책까지다`() {
        val rain = sharedFeedQueryOf(WalkRecordsQuery(filter = WalkHistoryFilter(weather = setOf(WalkDepartureWeather.RAIN))),
            null, "me", false, seoul)!!
        assertEquals(setOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99), rain.weatherCodes)
        assertFalse(rain.weatherMissing)

        val unknown = sharedFeedQueryOf(WalkRecordsQuery(filter = WalkHistoryFilter(weather = setOf(WalkDepartureWeather.UNKNOWN))),
            null, "me", false, seoul)!!
        assertTrue(unknown.weatherMissing)
        assertTrue(4 in unknown.weatherCodes)
        assertFalse(0 in unknown.weatherCodes)
        assertEquals((0..99).count { WalkDepartureWeather.of(it) == WalkDepartureWeather.UNKNOWN }, unknown.weatherCodes.size)
    }

    @Test fun `검색어나 행동 조건이 켜지면 공동 보호자 산책을 묻지 않고 그 사실을 알린다`() {
        val keyword = WalkRecordsQuery(filter = WalkHistoryFilter(keyword = "강변"))

        assertNull(sharedFeedQueryOf(keyword, null, "me", false, seoul))
        assertNull(sharedFeedQueryOf(WalkRecordsQuery(), null, "me", behaviorActive = true, zone = seoul))
        assertTrue(sharedWalksExcludedByText(keyword, null, "me", false))
        assertFalse("조건이 없으면 알릴 것이 없다", sharedWalksExcludedByText(WalkRecordsQuery(), null, "me", false))
        assertFalse("나만 골랐으면 어차피 내 산책만이다", sharedWalksExcludedByText(keyword, setOf("me"), "me", false))
    }

    @Test fun `보호자 조건에 나만 남으면 물을 것이 없다`() {
        assertNull(sharedFeedQueryOf(WalkRecordsQuery(), carerIds = setOf("me"), myId = "me", behaviorActive = false, zone = seoul))
    }
}
