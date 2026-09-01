package com.daengs.app.ui.home

import com.daengs.app.miniroom.OutsideTime
import com.daengs.app.miniroom.OutsideView
import com.daengs.app.miniroom.OutsideWeather
import com.daengs.app.ui.DaengsIcon

/**
 * 지금 바깥으로 짓는 홈 화면 문구.
 *
 * **서버에 안 묻는다.** 서버에 산책 적합도(`GET /walk`)와 챗봇(`POST /ask`)이 떠 있지만,
 * 이 문구가 뜨는 자리는 **앱을 켜자마자 보이는 카드**다 — 네트워크를 타면 빈 카드가
 * 먼저 뜨고, 실패했을 때 무엇을 그릴지 또 정해야 한다.
 *
 * 창밖 그림을 이미 같은 값으로 그리므로, 문구도 여기서 지으면 **창밖과 카드가 절대
 * 어긋나지 않는다.** 비 내리는 창문 옆에서 "산책 가기 좋은 날" 이라고 말하던 것이
 * 이 파일이 생긴 이유다.
 *
 * 순수 함수라 `WeatherWordsTest` 가 전부 잡는다.
 */
data class HomeWeatherWords(
    /** TODAY 카드 한 줄. **지금 바깥이 어떤지**만 말한다 (관찰·존댓말). */
    val today: String,
    /** 오늘의 한 마디 두 줄. **그래서 뭘 하자**를 말한다 (강아지 말투·제안). */
    val daily: List<String>,
)

/**
 * 기온 구간.
 *
 * 목소리가 갈리는 자리는 사실상 맑을 때다 — 비·눈은 기온을 몰라도 할 말이 정해져 있다.
 */
enum class TempBand { HOT, MILD, COLD, UNKNOWN }

/**
 * 더위로 치는 선(℃). **아스팔트가 뜨거워지는 온도**다 — 발바닥이 데는 이야기가
 * 나오기 시작하는 자리라 사람 기준(33도 폭염)보다 낮다.
 */
const val HOT_C = 28f

/** 추위로 치는 선(℃). 소형견에 옷을 입히기 시작하는 자리다. */
const val COLD_C = 4f

/**
 * **모르는 기온을 한쪽으로 단정하지 않는다.**
 *
 * `null` 을 추위로 치면 한여름에 "많이 추워요" 가 뜬다.
 */
fun tempBand(temperatureC: Float?): TempBand = when {
    temperatureC == null -> TempBand.UNKNOWN
    temperatureC >= HOT_C -> TempBand.HOT
    temperatureC <= COLD_C -> TempBand.COLD
    else -> TempBand.MILD
}

/**
 * 두 카드의 문구를 **한 번에** 짓는다.
 *
 * 따로 부르면 두 카드가 다른 순간의 날씨를 말할 수 있다. 그리고 둘이 겹치지 않는지도
 * 여기서 한눈에 보인다 — 한 화면에 나란히 있어서 같은 문장이 두 번 뜨면 하나가 고장 난
 * 것처럼 보인다.
 */
fun homeWeatherWords(
    view: OutsideView,
    temperatureC: Float?,
    /**
     * 진짜 날씨를 받아 왔나 ([OutsideSnapshot.known]).
     *
     * `false` 면 [view] 의 날씨는 폴백이라 **하늘을 두고 아무 말도 하지 않는다.**
     * 기본값이 `true` 인 것은 부르는 쪽 대부분이 이미 받아 온 값을 넘기기 때문이고,
     * 모르는 상태를 넘길 자리는 홈 하나다.
     */
    known: Boolean = true,
): HomeWeatherWords {
    // **아직 모르면 모른다고 한다.** 폴백은 낮·밤만 시계로 어림잡은 값이라 날씨는
    // 언제나 맑음이다. 그대로 문구를 지으면 비 오는 날 창밖을 보고 있는 사람에게
    // "산책 가기 좋은 날!" 이라고 말하게 된다 — 실기기에서 그렇게 걸렸다.
    if (!known) return LOADING_WORDS

    val night = view.time == OutsideTime.NIGHT
    val band = tempBand(temperatureC)
    return when (view.weather) {
        // 눈은 그 자체가 추위를 말한다. 기온으로 또 나누면 같은 말이 두 번 된다.
        OutsideWeather.SNOW -> if (night) {
            HomeWeatherWords("밤새 눈이 와요", listOf("눈 오는 밤이니", "내일 아침에 뛰자댕!"))
        } else {
            HomeWeatherWords("눈이 내리고 있어요", listOf("눈 밟으러", "잠깐만 나가자댕!"))
        }

        // 22도 비나 15도 비나 할 말이 같다. 찬비만 따로 말할 값어치가 있다.
        OutsideWeather.RAIN -> when {
            night && band == TempBand.COLD ->
                HomeWeatherWords("차가운 밤비예요", listOf("비가 차가우니", "이불 속으로 가자댕"))
            night ->
                HomeWeatherWords("밤비가 내려요", listOf("창밖에 빗소리 나니", "포근하게 자자댕"))
            band == TempBand.COLD ->
                HomeWeatherWords("찬비가 내려요", listOf("비도 오고 추우니", "오늘은 쉬어 가자댕"))
            else ->
                HomeWeatherWords("비가 오고 있어요", listOf("밖이 축축하니", "오늘은 집에서 놀자댕!"))
        }

        // 흐림. **비가 아니라는 것부터 말한다** — 하늘만 보고 나갈지 말지 정하는
        // 사람에게 "흐림" 과 "비" 는 다른 소식이다.
        OutsideWeather.CLOUDY -> if (night) cloudyNight(band) else cloudyDay(band)

        OutsideWeather.CLEAR -> if (night) clearNight(band) else clearDay(band)
    }
}

/**
 * 맑은 낮.
 *
 * 기존 문구("산책 가기 좋은 날!" · "바람이 좋아서 / 산책하기 딱 좋은 날이댕!")가
 * **여기 그대로 앉는다.** 화면이 지금까지 늘 이 말을 하고 있었으니, 이 칸에서만 같은
 * 말이 나오는 것이 이번 변경이 맞게 들어갔다는 확인이 된다.
 */
/**
 * 아직 날씨를 못 받았을 때.
 *
 * **하늘을 두고 단정하지 않는다.** 낮·밤은 시계로 아는 값이라 아이콘(해·달)은 그대로
 * 두고, 문구만 모른다고 말한다. 잠깐 스치는 상태라 길게 쓰지 않는다.
 */
private val LOADING_WORDS =
    HomeWeatherWords("날씨를 보고 있어요", listOf("창밖을 보고", "금방 올게댕!"))

private fun clearDay(band: TempBand): HomeWeatherWords = when (band) {
    TempBand.HOT ->
        HomeWeatherWords("한낮은 너무 더워요", listOf("볕이 뜨거우니", "해 지고 나가자댕!"))
    TempBand.COLD ->
        HomeWeatherWords("바깥이 꽤 추워요", listOf("옷 단단히 입고", "짧게 다녀오자댕!"))
    // **기온을 모를 때 "좋은 날" 이라고 하지 않는다.** 좋은 날인지 너무 더운 날인지가
    // 기온에서만 갈리므로, 모르면 하늘만 말한다.
    TempBand.UNKNOWN ->
        HomeWeatherWords("오늘 하늘은 맑아요", listOf("하늘이 맑으니", "잠깐 나가 볼까댕?"))
    TempBand.MILD ->
        HomeWeatherWords("산책 가기 좋은 날!", listOf("바람이 좋아서", "산책하기 딱 좋은 날이댕!"))
}

/**
 * 흐린 낮.
 *
 * **"좋은 날" 이라고 하지 않는다.** 해가 안 보이는데 좋은 날이라고 하면 창밖과
 * 카드가 또 어긋난다. 대신 흐린 것이 산책에 나쁘지 않다는 쪽으로 민다 —
 * 더운 날에는 오히려 반가운 하늘이다.
 */
private fun cloudyDay(band: TempBand): HomeWeatherWords = when (band) {
    TempBand.HOT ->
        HomeWeatherWords("구름이 해를 가렸어요", listOf("볕이 가려졌으니", "지금 나가자댕!"))
    TempBand.COLD ->
        HomeWeatherWords("흐리고 쌀쌀해요", listOf("바람이 차니", "따뜻하게 입자댕"))
    TempBand.UNKNOWN ->
        HomeWeatherWords("하늘이 흐려요", listOf("하늘은 흐리지만", "나가 볼까댕?"))
    TempBand.MILD ->
        HomeWeatherWords("흐리지만 선선해요", listOf("눈부시지 않으니", "걷기 좋은 날이댕!"))
}

/** 흐린 밤. 별이 안 보이는 밤이다 — 맑은 밤의 "고요함" 과 갈라 준다. */
private fun cloudyNight(band: TempBand): HomeWeatherWords = when (band) {
    TempBand.HOT ->
        HomeWeatherWords("흐리고 후덥지근해요", listOf("공기가 무거우니", "짧게 걷자댕"))
    TempBand.COLD ->
        HomeWeatherWords("흐리고 밤이 차요", listOf("밤바람이 차니", "따뜻하게 있자댕"))
    TempBand.UNKNOWN ->
        HomeWeatherWords("구름 낀 밤이에요", listOf("별은 안 보여도", "한 바퀴 어떨까댕?"))
    TempBand.MILD ->
        HomeWeatherWords("흐린 밤, 선선해요", listOf("밤공기가 선선하니", "가볍게 걷자댕!"))
}

private fun clearNight(band: TempBand): HomeWeatherWords = when (band) {
    TempBand.HOT ->
        HomeWeatherWords("밤에도 후덥지근해요", listOf("더위가 안 가셨으니", "천천히 걷자댕"))
    TempBand.COLD ->
        HomeWeatherWords("밤공기가 차가워요", listOf("밤이 많이 추우니", "따뜻하게 있자댕"))
    TempBand.UNKNOWN ->
        HomeWeatherWords("고요한 밤이에요", listOf("밤이 조용하니", "가볍게 걷자댕"))
    TempBand.MILD ->
        HomeWeatherWords("선선한 밤이에요", listOf("밤공기가 좋으니", "한 바퀴 돌자댕!"))
}

/**
 * TODAY 카드에 걸 아이콘.
 *
 * **밤에 해가 떠 있으면 안 된다** — 창밖은 밤인데 카드만 해였다.
 *
 * 비·눈은 낮밤을 안 가른다. 17dp 자리에서 구름에 초승달까지 얹으면 형체가 뭉개지고,
 * 낮밤은 어차피 문구가 말한다.
 */
fun weatherIcon(view: OutsideView): DaengsIcon = when (view.weather) {
    OutsideWeather.RAIN -> DaengsIcon.CloudRain
    OutsideWeather.SNOW -> DaengsIcon.CloudSnow
    // 흐림도 낮밤을 안 가른다. 비·눈과 같은 이유다 — 17dp 에서 구름에 달까지 얹으면
    // 형체가 뭉개지고, 낮밤은 문구가 말한다.
    OutsideWeather.CLOUDY -> DaengsIcon.Cloud
    OutsideWeather.CLEAR ->
        if (view.time == OutsideTime.DAY) DaengsIcon.Sun else DaengsIcon.Moon
}
