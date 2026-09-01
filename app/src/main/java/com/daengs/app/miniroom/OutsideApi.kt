package com.daengs.app.miniroom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 창밖에 무엇을 보여줄지 — **Open-Meteo** 에서 받아 온다.
 *
 * **API 키가 없다.** 가입도 필요 없고 HTTPS 다. `local.properties` 에 줄을 더 넣는
 * 방식이면 안 채운 팀원은 창밖이 멈추는데, 이건 받자마자 전부 돌아간다.
 *
 * **팀 백엔드를 안 거친다.** `API_BASE_URL` 은 로컬 개발 서버라 같은 와이파이가
 * 아니면 안 닿는다(README). 장식용 창문이 서버 상태에 묶이면 대부분의 시간에
 * 기본값만 보인다.
 *
 * `AuthApi` 와 같은 이유로 HTTP 라이브러리를 안 쓴다 — 부를 엔드포인트가 하나이고
 * 읽을 필드가 둘이다.
 */
object OutsideApi {

    private const val TIMEOUT_MS = 6_000

    /**
     * 받은 그대로.
     *
     * 창밖 그림은 [OutsideSnapshot] 이 이걸 세 갈래로 접어서 쓰지만, **산책 기록에는 원본을
     * 남긴다** — 접은 값만 저장하면 나중에 "소나기였는지 뇌우였는지"를 되살릴 수 없다.
     * 기온도 같이 받는다 (URL 에 한 단어를 더한 것뿐이다).
     */
    suspend fun fetchNow(latitude: Double, longitude: Double): OutsideNow? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$latitude&longitude=$longitude" +
                        "&current=weather_code,is_day,temperature_2m&timezone=auto"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (conn.responseCode !in 200..299) return@runCatching null
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val current = JSONObject(body).getJSONObject("current")
                    OutsideNow(
                        weatherCode = current.getInt("weather_code"),
                        time = if (current.getInt("is_day") == 1) OutsideTime.DAY
                        else OutsideTime.NIGHT,
                        // 기온만 빠져도 날씨 전체를 버리지 않는다.
                        temperatureC = if (current.has("temperature_2m")) {
                            current.getDouble("temperature_2m").toFloat()
                        } else {
                            null
                        },
                    )
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }

    /**
     * WMO 날씨 코드를 우리 세 갈래로 접는다.
     *
     * 우리가 가진 그림이 해·비·눈 셋뿐이라 스물 몇 가지를 여기로 모은다. 애매한 것은
     * **얼어붙는 쪽을 눈으로** 본다 — 어는 비(66·67)와 어는 이슬비(56·57)는 바닥이
     * 하얘지므로 창밖 그림으로는 눈이 맞다.
     *
     * 안개(45·48)와 흐림(1·2·3)은 맑음으로 간다. 따로 그림이 없고, 억지로 비나 눈에
     * 넣으면 안 오는 비가 내린다.
     *
     * 표: https://open-meteo.com/en/docs (WMO Weather interpretation codes)
     */
    fun weatherOf(code: Int): OutsideWeather = when (code) {
        56, 57, 66, 67 -> OutsideWeather.SNOW      // 어는 이슬비 · 어는 비
        in 71..77, 85, 86 -> OutsideWeather.SNOW   // 눈 · 싸락눈 · 소낙눈
        in 51..55 -> OutsideWeather.RAIN           // 이슬비
        in 61..65 -> OutsideWeather.RAIN           // 비
        in 80..82 -> OutsideWeather.RAIN           // 소나기
        in 95..99 -> OutsideWeather.RAIN           // 뇌우
        else -> OutsideWeather.CLEAR               // 맑음 · 흐림 · 안개
    }
}

/**
 * 지금 바깥 날씨. **접기 전의 값**이다.
 *
 * 창밖 그림은 [OutsideApi.weatherOf] 로 세 갈래로 접어 쓰고, 산책 기록은 이걸 그대로
 * 저장한다 — 같은 한 번의 호출에서 둘 다 나온다.
 */
data class OutsideNow(
    /** WMO 코드. 표: https://open-meteo.com/en/docs */
    val weatherCode: Int,
    val time: OutsideTime,
    /** 섭씨. 못 받으면 null 이고 기록에서 기온 줄만 빠진다. */
    val temperatureC: Float?,
)
