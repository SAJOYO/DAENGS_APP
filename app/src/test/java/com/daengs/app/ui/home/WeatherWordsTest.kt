package com.daengs.app.ui.home

import com.daengs.app.miniroom.OutsideView
import com.daengs.app.miniroom.OutsideWeather
import com.daengs.app.ui.DaengsIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홈 카드 문구가 날씨를 따르는지.
 *
 * **이 파일이 생긴 이유**는 비가 내리는 창문 옆에서 카드가 "산책 가기 좋은 날!" 이라고
 * 말하고 있었기 때문이다. 그 회귀를 막는 것이 첫 번째 테스트다.
 */
class WeatherWordsTest {

    /** 기온을 못 받았을 때까지 포함한 네 갈래. */
    private val temps = listOf(33f, 21f, -3f, null)

    // -- 회귀 --------------------------------------------------------------

    /**
     * 비·눈에는 **맑은 날과 같은 말을 하지 않는다.**
     *
     * 화면에 창밖 그림과 이 카드가 나란히 있어서, 비 오는 창문 옆에서 산책을 권하면
     * 둘 중 하나가 고장 난 것으로 보인다.
     */
    @Test
    fun `비나 눈이면 맑은 날과 다른 말을 한다`() {
        val fine = homeWeatherWords(OutsideView.DAY_CLEAR, 21f)
        for (view in OutsideView.entries.filter { it.weather != OutsideWeather.CLEAR }) {
            for (temp in temps) {
                assertNotEquals("$view $temp", fine, homeWeatherWords(view, temp))
            }
        }
    }

    /** 창밖은 밤인데 카드만 해가 떠 있었다. */
    @Test
    fun `밤에는 해를 그리지 않는다`() {
        assertEquals(DaengsIcon.Sun, weatherIcon(OutsideView.DAY_CLEAR))
        assertEquals(DaengsIcon.Moon, weatherIcon(OutsideView.NIGHT_CLEAR))
        assertEquals(DaengsIcon.CloudRain, weatherIcon(OutsideView.DAY_RAIN))
        assertEquals(DaengsIcon.CloudRain, weatherIcon(OutsideView.NIGHT_RAIN))
        assertEquals(DaengsIcon.CloudSnow, weatherIcon(OutsideView.DAY_SNOW))
        assertEquals(DaengsIcon.CloudSnow, weatherIcon(OutsideView.NIGHT_SNOW))
    }

    // -- 기온 --------------------------------------------------------------

    /** 경계값을 못 박는다. 문구를 손볼 사람이 어느 쪽인지 알아야 한다. */
    @Test
    fun `기온 구간 경계`() {
        assertEquals(TempBand.MILD, tempBand(HOT_C - 0.1f))
        assertEquals(TempBand.HOT, tempBand(HOT_C))
        assertEquals(TempBand.COLD, tempBand(COLD_C))
        assertEquals(TempBand.MILD, tempBand(COLD_C + 0.1f))
    }

    /**
     * **모르는 기온을 더위나 추위로 단정하지 않는다.**
     *
     * `null` 을 추위로 치면 한여름에 "많이 추워요" 가 뜬다.
     */
    @Test
    fun `기온을 모르면 더위도 추위도 아니다`() {
        assertEquals(TempBand.UNKNOWN, tempBand(null))

        val unknown = homeWeatherWords(OutsideView.DAY_CLEAR, null)
        assertNotEquals(homeWeatherWords(OutsideView.DAY_CLEAR, 33f), unknown)
        assertNotEquals(homeWeatherWords(OutsideView.DAY_CLEAR, -3f), unknown)
    }

    /** 0도는 **아는 값**이다. 모르는 것과 같은 칸에 넣으면 안 된다. */
    @Test
    fun `영하 0도는 모르는 것과 다르다`() {
        assertNotEquals(
            homeWeatherWords(OutsideView.DAY_CLEAR, null),
            homeWeatherWords(OutsideView.DAY_CLEAR, 0f),
        )
    }

    @Test
    fun `맑은 낮은 기온마다 다른 말을 한다`() {
        val said = temps.map { homeWeatherWords(OutsideView.DAY_CLEAR, it) }
        assertEquals("네 갈래가 다 달라야 한다", said.size, said.distinct().size)
    }

    // -- 두 카드 -----------------------------------------------------------

    /**
     * TODAY 와 오늘의 한 마디가 **같은 말을 하면 안 된다.**
     *
     * 한 화면에 나란히 있어서, 같은 문장이 두 번 뜨면 하나가 고장 난 것으로 보인다.
     * TODAY 는 관찰이고 한 마디는 제안이라 구조적으로 갈리지만, 문구를 고치다 보면
     * 겹칠 수 있어서 잡아 둔다.
     */
    @Test
    fun `두 카드가 같은 문장을 내지 않는다`() {
        forEveryCell { view, temp, words ->
            assertTrue("$view $temp", words.today !in words.daily)
        }
    }

    @Test
    fun `어느 칸에도 빈 문구가 없다`() {
        forEveryCell { view, temp, words ->
            assertTrue("$view $temp", words.today.isNotBlank())
            assertEquals("$view $temp", 2, words.daily.size)
            assertTrue("$view $temp", words.daily.all { it.isNotBlank() })
        }
    }

    /**
     * 카드에 들어갈 길이인지.
     *
     * TODAY 카드도 메모지도 폭이 좁다. 길어지면 잘리거나 카드가 밀리는데, 그건
     * 실기기에서나 보인다 — 여기서 미리 막는다.
     */
    @Test
    fun `문구가 카드에 들어갈 길이다`() {
        forEveryCell { view, temp, words ->
            assertTrue("$view $temp: ${words.today}", words.today.length <= 12)
            words.daily.forEach { assertTrue("$view $temp: $it", it.length <= 14) }
        }
    }

    /** 예전 문구는 **맑고 포근한 낮 칸에 그대로 살아 있다.** */
    @Test
    fun `맑은 낮에는 예전 문구 그대로다`() {
        val words = homeWeatherWords(OutsideView.DAY_CLEAR, 21f)
        assertEquals("산책 가기 좋은 날!", words.today)
        assertEquals(listOf("바람이 좋아서", "산책하기 딱 좋은 날이댕!"), words.daily)
    }

    /**
     * **아직 못 받았으면 하늘을 두고 아무 말도 하지 않는다.**
     *
     * 폴백은 날씨가 언제나 맑음이라, 이 갈래가 없으면 비 오는 날 앱을 켠 사람이
     * "산책 가기 좋은 날!" 을 본다. 실기기에서 그렇게 걸렸다.
     */
    @Test
    fun `날씨를 아직 못 받았으면 맑다고 말하지 않는다`() {
        for (view in OutsideView.entries) {
            for (temp in temps) {
                val words = homeWeatherWords(view, temp, known = false)
                assertEquals("날씨를 보고 있어요", words.today)
                assertEquals(listOf("창밖을 보고", "금방 올게댕!"), words.daily)
            }
        }
    }

    /** 받아 온 뒤에는 예전과 똑같이 동작한다 — 기본값이 `known = true` 다. */
    @Test
    fun `받아 왔으면 지금까지와 같다`() {
        assertEquals(
            homeWeatherWords(OutsideView.DAY_CLEAR, 21f),
            homeWeatherWords(OutsideView.DAY_CLEAR, 21f, known = true),
        )
    }

    /** 로딩 문구도 카드 칸을 넘지 않는다 (위의 길이 규칙과 같은 자리). */
    @Test
    fun `로딩 문구도 칸을 안 넘는다`() {
        val words = homeWeatherWords(OutsideView.DAY_CLEAR, null, known = false)
        assertTrue(words.today.length <= 12)
        words.daily.forEach { assertTrue(it, it.length <= 14) }
    }

    private fun forEveryCell(check: (OutsideView, Float?, HomeWeatherWords) -> Unit) {
        for (view in OutsideView.entries) {
            for (temp in temps) check(view, temp, homeWeatherWords(view, temp))
        }
    }
}
