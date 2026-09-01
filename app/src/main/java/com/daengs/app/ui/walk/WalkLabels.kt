package com.daengs.app.ui.walk

import com.daengs.app.miniroom.OutsideApi
import com.daengs.app.miniroom.OutsideWeather
import com.daengs.app.walk.RecordedWeather
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 기록을 사람이 읽는 말로.
 *
 * 시간·거리 표기는 산책 중 카드가 쓰던 것([formatWalkDuration] · [formatWalkDistance])을
 * 그대로 쓴다. **같은 값을 다르게 적으면 같은 산책이 두 번 달라 보인다.**
 */

private val DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

fun formatWalkDay(atMillis: Long): String =
    Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).format(DAY)

fun formatWalkClock(atMillis: Long): String =
    Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).format(CLOCK)

/**
 * 날씨 한 줄.
 *
 * 저장은 WMO 원본 코드로 해 두고 **보여줄 때만 접는다** — 그래야 나중에 "소나기"처럼
 * 더 자세히 쓰기로 해도 지난 기록이 같이 자세해진다. 접는 규칙은 창밖 그림이 쓰는
 * [OutsideApi.weatherOf] 하나뿐이다.
 *
 * 기온은 못 받았을 수 있다. 그때는 날씨만 적는다.
 */
fun weatherLabel(weather: RecordedWeather): String {
    val sky = when (OutsideApi.weatherOf(weather.weatherCode)) {
        OutsideWeather.RAIN -> "비"
        OutsideWeather.SNOW -> "눈"
        // 흐림은 낮밤을 안 가른다. 흐린 밤을 "밤" 으로만 적으면 맑은 밤과 같아진다.
        OutsideWeather.CLOUDY -> "흐림"
        OutsideWeather.CLEAR -> if (weather.isDay) "맑음" else "밤"
    }
    val temperature = weather.temperatureC?.let { " ${it.toInt()}°" } ?: ""
    return sky + temperature
}
