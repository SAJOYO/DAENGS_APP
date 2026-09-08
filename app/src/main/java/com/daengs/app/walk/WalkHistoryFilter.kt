package com.daengs.app.walk

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.text.Normalizer

enum class WalkSeason(val label: String) {
    SPRING("봄"), SUMMER("여름"), AUTUMN("가을"), WINTER("겨울");
    companion object {
        fun of(month: Int) = when (month) {
            in 3..5 -> SPRING
            in 6..8 -> SUMMER
            in 9..11 -> AUTUMN
            else -> WINTER
        }
    }
}

enum class WalkDepartureWeather(val label: String) {
    CLEAR("맑음"), CLOUDY("흐림·안개"), RAIN("비"), SNOW("눈"), UNKNOWN("정보 없음");
    companion object {
        // Same known WMO groups as the existing walk labels; unknown codes are never clear.
        fun of(code: Int?) = when (code) {
            0, 1 -> CLEAR
            2, 3, 45, 48 -> CLOUDY
            51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99 -> RAIN
            56, 57, 66, 67, 71, 73, 75, 77, 85, 86 -> SNOW
            else -> UNKNOWN
        }
    }
}

/** AND across fields, OR within a field. Day/season uses the same device zone as walk history. */
data class WalkHistoryFilter(
    val keyword: String = "",
    val from: LocalDate? = null,
    val through: LocalDate? = null,
    val seasons: Set<WalkSeason> = emptySet(),
    val weather: Set<WalkDepartureWeather> = emptySet(),
) {
    init { require(from == null || through == null || from <= through) }
    val active get() = keyword.isNotBlank() || from != null || through != null || seasons.isNotEmpty() || weather.isNotEmpty()
    fun matches(session: RecordedSession, zone: ZoneId): Boolean {
        val date = Instant.ofEpochMilli(session.startedAtMillis).atZone(zone).toLocalDate()
        return (from == null || date >= from) && (through == null || date <= through) &&
            (seasons.isEmpty() || WalkSeason.of(date.monthValue) in seasons) &&
            (weather.isEmpty() || WalkDepartureWeather.of(session.weather?.weatherCode) in weather)
    }
    fun matchesText(fields: List<String>): Boolean = keyword.isBlank() || fields.any {
        normalized(it).contains(normalized(keyword.trim()), ignoreCase = true)
    }
    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC)
}
