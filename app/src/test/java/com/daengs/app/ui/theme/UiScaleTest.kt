package com.daengs.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 폭에서 배율을 내는 규칙.
 *
 * 이 숫자 하나가 앱의 **모든** `dp` 와 `sp` 를 움직인다 — `DaengsTheme` 이
 * `LocalDensity` 를 이 값으로 갈아끼우기 때문이다. 그래서 여기가 틀리면 화면
 * 전체가 틀린다.
 */
class UiScaleTest {

    @Test
    fun `기준 폭에서는 그대로다`() {
        assertEquals(1f, uiScale(REFERENCE_WIDTH_DP.toInt()), 1e-6f)
    }

    /** 실기기(SM-S938N)가 411dp 다. **그 폰에서 지금과 똑같이 보여야 한다.** */
    @Test
    fun `실기기 폭에서는 사실상 그대로다`() {
        assertEquals(1f, uiScale(411), 1e-6f)
    }

    @Test
    fun `좁은 화면에서는 줄어든다`() {
        assertTrue(uiScale(360) < 1f)
        // 360 / 411 = 0.876
        assertEquals(360f / REFERENCE_WIDTH_DP, uiScale(360), 1e-6f)
    }

    @Test
    fun `넓은 화면에서는 커진다`() {
        assertTrue(uiScale(430) > 1f)
        assertEquals(430f / REFERENCE_WIDTH_DP, uiScale(430), 1e-6f)
    }

    /**
     * ⚠️ **폰이 아닌 폭에서 배율이 튀면 안 된다.**
     *
     * 태블릿(800dp)에서 그대로 비례하면 1.95배가 되어 글자가 거대해진다. 세로
     * 고정이라 그럴 일이 드물지만, 안 막으면 언젠가 본다.
     */
    @Test
    fun `아주 넓은 화면에서도 상한을 안 넘는다`() {
        listOf(600, 800, 1280).forEach {
            assertEquals("${it}dp", MAX_SCALE, uiScale(it), 1e-6f)
        }
    }

    @Test
    fun `아주 좁은 화면에서도 하한 아래로 안 간다`() {
        listOf(240, 280, 300).forEach {
            assertEquals("${it}dp", MIN_SCALE, uiScale(it), 1e-6f)
        }
    }

    /** 흔한 폰 폭은 전부 상한·하한 **안쪽**이라 그대로 비례해야 한다. */
    @Test
    fun `흔한 폰 폭은 잘리지 않는다`() {
        listOf(360, 384, 393, 411, 412, 430).forEach {
            assertEquals("${it}dp", it / REFERENCE_WIDTH_DP, uiScale(it), 1e-6f)
        }
    }

    /** 0 이나 음수가 와도 앱이 멈추면 안 된다 (구성이 아직 안 잡힌 순간). */
    @Test
    fun `말이 안 되는 폭도 견딘다`() {
        listOf(0, -1, -1000).forEach {
            assertEquals("${it}dp", MIN_SCALE, uiScale(it), 1e-6f)
        }
    }

    @Test
    fun `폭이 넓어질수록 배율도 커지거나 같다`() {
        var previous = uiScale(200)
        for (width in 201..1200) {
            val current = uiScale(width)
            assertTrue("${width}dp 에서 배율이 줄었다", current >= previous)
            previous = current
        }
    }
}
