package com.daengs.app.walk.shared

import com.daengs.app.walk.WalkDepartureWeather
import com.daengs.app.walk.WalkSeason
import com.daengs.app.walk.records.WalkRecordsQuery
import java.time.LocalDate
import java.time.ZoneId

/**
 * 통합 목록(`GET /app/pet-walks`)에 보내는 조건. 산책 기록 화면의 조건을 서버가 알아듣는 값으로
 * 풀어 둔 것이다 — 계절은 월, 날씨 분류는 WMO 코드로. 분류 규칙은 앱([WalkSeason]·
 * [WalkDepartureWeather]) 한 곳에만 있고 서버는 코드만 받는다.
 */
data class SharedWalkFeedQuery(
    /** 내 화면의 강아지 id. null 은 모든 강아지. */
    val petIds: Set<String>? = null,
    /** 올린 사람. null 은 모든 보호자. **내 id 는 넣지 않는다** — 내 산책은 기기 기록이 맡는다. */
    val actorIds: Set<String>? = null,
    val dateFrom: LocalDate? = null,
    val dateThrough: LocalDate? = null,
    /** 날짜·월을 자를 시간대. 기기 기록의 날짜 규칙과 같은 기기 시간대다. */
    val zoneId: String = "UTC",
    val months: Set<Int> = emptySet(),
    val weatherCodes: Set<Int> = emptySet(),
    val weatherMissing: Boolean = false,
) {
    fun params(): List<Pair<String, String>> = buildList {
        add("tz" to zoneId)
        petIds?.sorted()?.forEach { add("pet_id" to it) }
        actorIds?.sorted()?.forEach { add("actor_id" to it) }
        dateFrom?.let { add("date_from" to it.toString()) }
        dateThrough?.let { add("date_through" to it.toString()) }
        months.sorted().forEach { add("month" to it.toString()) }
        weatherCodes.sorted().forEach { add("weather_code" to it.toString()) }
        if (weatherMissing) add("weather_missing" to "true")
    }
}

/**
 * 지금 조건에서 공동 보호자 산책을 서버에 물어야 하나. **null 이면 묻지 않는다.**
 *
 * - 제목·메모 검색과 행동 조건은 공동 보호자 산책에 적용할 수 없다(서버에 제목·메모·행동 판정이
 *   없다) — 그 조건이 켜진 동안은 내 산책만 찾는다. 화면이 그 사실을 알린다.
 * - 보호자 조건에 나만 남았으면 물을 것이 없다.
 *
 * @param carerIds 보호자 조건. null 은 모든 보호자.
 */
fun sharedFeedQueryOf(
    query: WalkRecordsQuery,
    carerIds: Set<String>?,
    myId: String,
    behaviorActive: Boolean,
    zone: ZoneId,
): SharedWalkFeedQuery? {
    if (behaviorActive || query.filter.keyword.isNotBlank()) return null
    val others = carerIds?.minus(myId)
    if (others != null && others.isEmpty()) return null
    val filter = query.filter
    return SharedWalkFeedQuery(
        petIds = query.dogIds,
        actorIds = others,
        dateFrom = filter.from,
        dateThrough = filter.through,
        zoneId = zone.id,
        months = if (filter.seasons.isEmpty()) emptySet() else (1..12).filter { WalkSeason.of(it) in filter.seasons }.toSet(),
        weatherCodes = if (filter.weather.isEmpty()) emptySet()
            else (0..99).filter { WalkDepartureWeather.of(it) in filter.weather }.toSet(),
        weatherMissing = WalkDepartureWeather.UNKNOWN in filter.weather,
    )
}

/** 제목·메모 검색이나 행동 조건 때문에 공동 보호자 산책을 빼고 있나(화면 안내용). */
fun sharedWalksExcludedByText(query: WalkRecordsQuery, carerIds: Set<String>?, myId: String, behaviorActive: Boolean): Boolean =
    (behaviorActive || query.filter.keyword.isNotBlank()) && (carerIds == null || carerIds.any { it != myId })
