package com.daengs.app.ui.walk.reading

import com.daengs.app.walk.diary.DiarySceneContent
import org.junit.Assert.assertEquals
import org.junit.Test

class DiarySceneConditionsTest {
    private fun content(temp: Double?) = DiarySceneContent("", "", locationLabel = "",
        address = "서울 중랑구 신내2동", temperatureC = temp)

    @Test fun `optional temperature preserves zero and negatives without dangling separators`() {
        assertEquals("서울 중랑구 신내2동", sceneConditionsLabel(content(null)))
        assertEquals("서울 중랑구 신내2동 · 0°C", sceneConditionsLabel(content(0.0)))
        assertEquals("서울 중랑구 신내2동 · -2.3°C", sceneConditionsLabel(content(-2.3)))
        assertEquals("서울 중랑구 신내2동", sceneConditionsLabel(content(Double.NaN)))
        assertEquals("", sceneConditionsLabel(null))
        assertEquals("20°C", sceneConditionsLabel(content(20.0).copy(address = null)))
    }
}
