package com.daengs.app.ui.motion

import com.daengs.app.miniroom.IntroTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 전환 모션의 **규칙**.
 *
 * 모션이 예쁜지는 여기서 못 잡는다 — 그건 실기기에서 녹화해 본다
 * (`CLAUDE.md`: *"테스트는 좌표·배치·규격을 잡는다"*). 여기서 잡는 것은 숫자로 말할 수
 * 있는 것들이다: 길이, 나가는 쪽이 먼저 끝나는 것, **어디에 모션을 넣지 않는가.**
 */
class ScreenMotionTest {

    /**
     * 앱이 한 박자로 움직인다.
     *
     * 300ms 는 로딩에서 홈으로 넘어올 때 크림 막을 걷는 시간이다
     * ([IntroTimeline.VEIL_END_MS]). 둘이 갈라지면 같은 앱 안에서 두 가지 속도가 된다.
     */
    @Test
    fun `전환 길이는 크림 막과 같다`() {
        assertEquals(IntroTimeline.VEIL_END_MS, ScreenMotion.TOTAL_MS.toLong())
    }

    /**
     * **나가는 화면을 먼저 놓는다.**
     *
     * 이게 fade-through 를 고른 이유다. 슬라이드면 전환이 끝날 때까지 두 화면이 다 살아
     * 있어야 하는데, 화면 열다섯 개가 지도·카메라를 안고 있어서 그럴 이유가 없다.
     */
    @Test
    fun `나가는 화면이 들어오는 화면보다 먼저 끝난다`() {
        assertTrue(
            "나가는 ${ScreenMotion.EXIT_MS}ms 가 전체 ${ScreenMotion.TOTAL_MS}ms 의 절반을 넘는다",
            ScreenMotion.EXIT_MS < ScreenMotion.TOTAL_MS / 2,
        )
    }

    @Test
    fun `들어오는 화면은 나가는 화면이 빠진 뒤에 시작한다`() {
        assertEquals(ScreenMotion.TOTAL_MS, ScreenMotion.EXIT_MS + ScreenMotion.ENTER_MS)
    }

    /**
     * 시작 크기가 1 이면 순수 fade 라 "다가온다" 가 없고, 너무 작으면 화면 전체가
     * 확대되는 느낌이라 지도가 든 화면에서 어지럽다.
     */
    @Test
    fun `들어오는 화면의 시작 크기는 0_9 와 1 사이다`() {
        assertTrue(ScreenMotion.ENTER_SCALE > 0.9f)
        assertTrue(ScreenMotion.ENTER_SCALE < 1f)
    }

    /**
     * **로딩이 낀 전환에는 모션을 안 넣는다.**
     *
     * 홈은 로딩에서 넘어올 때 이미 크림 막으로 이어진다 (`ui/home/HomeIntroVeil.kt`).
     * 거기에 fade 를 더 얹으면 같은 구간을 두 번 건너고, 막이 걷히는 동안 그 밑의 방이
     * 한 번 더 흐려진다.
     */
    @Test
    fun `로딩에서 나갈 때는 모션을 넣지 않는다`() {
        assertFalse(screenTransitionAnimates(fromLoading = true, toLoading = false))
    }

    @Test
    fun `로딩으로 들어갈 때도 모션을 넣지 않는다`() {
        assertFalse(screenTransitionAnimates(fromLoading = false, toLoading = true))
    }

    @Test
    fun `로딩과 무관한 전환에는 모션을 넣는다`() {
        assertTrue(screenTransitionAnimates(fromLoading = false, toLoading = false))
    }

    /** 칸 크기 변화(인벤토리를 열어 방이 물러나는 것)는 화면 전환보다 짧다. */
    @Test
    fun `칸 애니메이션은 화면 전환보다 짧다`() {
        assertTrue(ScreenMotion.SLOT_MS < ScreenMotion.TOTAL_MS)
    }
}
