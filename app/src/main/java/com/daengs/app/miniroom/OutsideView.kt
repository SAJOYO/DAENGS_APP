package com.daengs.app.miniroom

import androidx.annotation.DrawableRes
import com.daengs.app.R

/** 창밖·문밖의 시간. */
enum class OutsideTime { DAY, NIGHT }

/** 창밖·문밖의 날씨. */
enum class OutsideWeather { CLEAR, RAIN, SNOW }

/**
 * 창과 문 너머로 보이는 **바깥** — 시간 x 날씨 여섯 벌.
 *
 * 그림은 `tools/make_outside.py` 가 굽는다. 창밖은 **유리 모양으로 잘린 알파 PNG** 라
 * 방 그림 위 [WindowSpec.glass] 자리에 그대로 얹으면 되고, 문밖은 네모라 그리는
 * 쪽이 문 윤곽으로 오려낸다 ([drawDoorOpening]).
 *
 * **테마를 타지 않는다.** 방 그림 여섯 테마에서 다른 것은 창틀·창살(테마색 나무)
 * 뿐이고 유리 안 풍경은 픽셀 단위로 같다. 그래서 한 벌이 테마 전부를 덮는다 —
 * 36장이 아니라 12장인 이유다.
 *
 * 눈일 때는 나뭇잎이 없다. 잎 달린 나무에 눈이 오면 계절이 어긋나 보인다.
 */
enum class OutsideView(
    val time: OutsideTime,
    val weather: OutsideWeather,
    /** 창유리에 얹을 그림. 유리 모양으로 잘려 있다. */
    @DrawableRes val window: Int,
    /** 문 너머에 깔 그림. 네모다 — 아치는 그리는 쪽이 오려낸다. */
    @DrawableRes val door: Int,
) {
    DAY_CLEAR(
        OutsideTime.DAY, OutsideWeather.CLEAR,
        R.drawable.window_day_clear, R.drawable.door_day_clear,
    ),
    DAY_RAIN(
        OutsideTime.DAY, OutsideWeather.RAIN,
        R.drawable.window_day_rain, R.drawable.door_day_rain,
    ),
    DAY_SNOW(
        OutsideTime.DAY, OutsideWeather.SNOW,
        R.drawable.window_day_snow, R.drawable.door_day_snow,
    ),
    NIGHT_CLEAR(
        OutsideTime.NIGHT, OutsideWeather.CLEAR,
        R.drawable.window_night_clear, R.drawable.door_night_clear,
    ),
    NIGHT_RAIN(
        OutsideTime.NIGHT, OutsideWeather.RAIN,
        R.drawable.window_night_rain, R.drawable.door_night_rain,
    ),
    NIGHT_SNOW(
        OutsideTime.NIGHT, OutsideWeather.SNOW,
        R.drawable.window_night_snow, R.drawable.door_night_snow,
    );

    /** 개발자 패널 칩에 쓰는 이름. */
    val label: String
        get() = (if (time == OutsideTime.DAY) "낮" else "밤") + " " + when (weather) {
            OutsideWeather.CLEAR -> "해"
            OutsideWeather.RAIN -> "비"
            OutsideWeather.SNOW -> "눈"
        }

    companion object {
        /**
         * 날씨를 못 읽었을 때 보여줄 것.
         *
         * 권한 거부 · 비행기 모드 · 응답 실패 어느 쪽이든 창밖이 비면 안 된다.
         * 원래 방 그림이 낮·맑음이었으므로 그게 기본값이다.
         */
        val DEFAULT = DAY_CLEAR

        private val index = entries.associateBy { it.time to it.weather }

        fun of(time: OutsideTime, weather: OutsideWeather): OutsideView =
            index.getValue(time to weather)
    }
}
