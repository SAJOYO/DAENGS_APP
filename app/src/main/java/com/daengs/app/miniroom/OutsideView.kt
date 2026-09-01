package com.daengs.app.miniroom

import androidx.annotation.DrawableRes
import com.daengs.app.R

/** 창밖·문밖의 시간. */
enum class OutsideTime { DAY, NIGHT }

/**
 * 창밖·문밖의 날씨.
 *
 * **[CLOUDY] 는 그림이 따로 없다.** 맑음 그림에 회색 막을 씌워 만든다
 * (`drawWindowOutside`). 그림을 굽는 대신 코드로 만든 이유는, 흐린 날에 해가 떠
 * 있는 것이 제일 큰 거짓말인데 그걸 고치려고 그림 파이프라인을 돌리기에는
 * 시간이 걸리기 때문이다. 진짜 흐림 그림이 나오면 여기 PNG 만 갈아끼우면 된다.
 */
enum class OutsideWeather { CLEAR, CLOUDY, RAIN, SNOW }

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
    /**
     * 그림 위에 회색 막을 씌우나.
     *
     * 흐림만 `true` 다. 맑음 그림을 그대로 쓰고 코드로 흐리게 만든다 —
     * [OutsideWeather.CLOUDY] 주석 참고.
     */
    val veil: Boolean = false,
) {
    DAY_CLEAR(
        OutsideTime.DAY, OutsideWeather.CLEAR,
        R.drawable.window_day_clear, R.drawable.door_day_clear,
    ),
    DAY_CLOUDY(
        OutsideTime.DAY, OutsideWeather.CLOUDY,
        R.drawable.window_day_clear, R.drawable.door_day_clear,
        veil = true,
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
    NIGHT_CLOUDY(
        OutsideTime.NIGHT, OutsideWeather.CLOUDY,
        R.drawable.window_night_clear, R.drawable.door_night_clear,
        veil = true,
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
            OutsideWeather.CLOUDY -> "흐림"
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

/**
 * 지금 바깥 한 장 — **그림 한 벌과 기온**.
 *
 * [view] 는 창밖·문밖에 얹을 여섯 벌 중 하나다. [temperatureC] 는 문구가 "더운지
 * 추운지" 를 가를 때만 쓴다.
 *
 * **[OutsideNow] 와 역할이 다르다.** 저쪽은 WMO 원본이라 산책 기록에 그대로 남기는
 * 값이고, 이쪽은 화면에 뿌리려고 접은 값이다. 하나로 합치면 폴백에서 없는 WMO 코드를
 * 지어내야 한다 — 그건 "못 받았다" 와 "진짜 맑음" 을 같은 값으로 만든다.
 *
 * **기온은 못 받을 수 있다.** 그때 `null` 이고 문구는 그래도 나온다. `0f` 은 영하
 * 0도라는 **아는 값**이라 `null` 과 다르다.
 */
data class OutsideSnapshot(
    val view: OutsideView,
    val temperatureC: Float?,
    /**
     * 진짜 날씨를 받아 왔나.
     *
     * `false` 면 [view] 는 **기기 시계로 어림잡은 폴백**이라 낮·밤만 맞고 날씨는
     * 모르는 것이다. 그때 "오늘 하늘은 맑아요" 라고 쓰면 비 오는 날 창밖을 보고 있는
     * 사람에게 앱이 거짓말을 한다 — 실기기에서 그렇게 걸렸다.
     *
     * 이 칸이 없으면 "못 받았다" 와 "진짜 맑음" 이 같은 값이 된다. 이 클래스 주석이
     * [OutsideNow] 와 굳이 갈라 둔 이유가 그것이었는데, 정작 접은 쪽에는 그 구분이
     * 없었다.
     */
    val known: Boolean = false,
) {
    companion object {
        /** 날씨를 못 읽었을 때. **기온도 모르는 것**이다 — 지어내지 않는다. */
        val DEFAULT = OutsideSnapshot(OutsideView.DEFAULT, null, known = false)

        fun of(now: OutsideNow): OutsideSnapshot = OutsideSnapshot(
            view = OutsideView.of(now.time, OutsideApi.weatherOf(now.weatherCode)),
            temperatureC = now.temperatureC,
            known = true,
        )
    }
}
