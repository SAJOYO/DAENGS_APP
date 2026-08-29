package com.daengs.app.miniroom

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WMO 날씨 코드 → 우리 세 갈래.
 *
 * 이 표는 **네트워크를 안 타는 순수 함수**라서 여기서 전부 굳힐 수 있다. 반대로
 * Open-Meteo 를 실제로 부르는 부분은 여기서 안 본다 — 단위 테스트가 바깥 서버와
 * 오늘 날씨에 매달리면 비 오는 날 빌드가 깨진다.
 *
 * 표: https://open-meteo.com/en/docs
 */
class OutsideApiTest {

    @Test
    fun `눈으로 가는 코드`() {
        // 71~77 눈, 85·86 소낙눈
        listOf(71, 73, 75, 77, 85, 86).forEach {
            assertEquals("$it 은 눈이다", OutsideWeather.SNOW, OutsideApi.weatherOf(it))
        }
    }

    /**
     * 어는 비·어는 이슬비는 **눈 쪽**이다. 기상학적으로는 비지만 창밖으로 보이는
     * 것은 하얗게 얼어붙은 바닥이라, 우리 그림 셋 중에서는 눈이 가깝다.
     */
    @Test
    fun `어는 비는 눈으로 본다`() {
        listOf(56, 57, 66, 67).forEach {
            assertEquals("$it 은 눈으로 본다", OutsideWeather.SNOW, OutsideApi.weatherOf(it))
        }
    }

    @Test
    fun `비로 가는 코드`() {
        // 51~55 이슬비, 61~65 비, 80~82 소나기, 95~99 뇌우
        listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99).forEach {
            assertEquals("$it 은 비다", OutsideWeather.RAIN, OutsideApi.weatherOf(it))
        }
    }

    /**
     * 흐림·안개에는 그림이 없다. **맑음으로 떨어뜨린다** — 억지로 비에 넣으면
     * 안 오는 비가 내린다.
     */
    @Test
    fun `그림 없는 날씨는 맑음이다`() {
        listOf(0, 1, 2, 3, 45, 48).forEach {
            assertEquals("$it 은 맑음으로 떨어진다", OutsideWeather.CLEAR, OutsideApi.weatherOf(it))
        }
    }

    /** 표에 없는 값이 와도 앱이 멈추면 안 된다. */
    @Test
    fun `모르는 코드도 맑음이다`() {
        listOf(-1, 4, 30, 100, 999).forEach {
            assertEquals(OutsideWeather.CLEAR, OutsideApi.weatherOf(it))
        }
    }
}
