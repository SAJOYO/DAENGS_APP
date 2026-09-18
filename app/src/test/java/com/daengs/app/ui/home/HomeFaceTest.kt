package com.daengs.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 홈 자리에서 **무엇이 보이나.**
 *
 * 하단 탭 넷 중 도감·내 주변은 `screen` 을 바꾸므로 바깥 화면 전환이 잇는다.
 * 홈과 저장소는 이 화면 안에서 갈리고 마이는 그 위에 덮이는데, 그 셋을 한 값으로
 * 모아 같은 모션을 태운다. 예전에는 이른 `return@Scaffold` 두 번이라 분기를
 * `AnimatedContent` 에 넘길 수가 없었다.
 */
class HomeFaceTest {

    @Test
    fun `홈 탭이면 방이다`() {
        assertEquals(HomeFace.Room, homeFace(myOpen = false, tab = BottomTab.Home))
    }

    @Test
    fun `저장소 탭이면 저장소다`() {
        assertEquals(HomeFace.Storage, homeFace(myOpen = false, tab = BottomTab.Storage))
    }

    /**
     * **마이가 저장소를 이긴다.**
     *
     * 마이는 탭이 아니라 상단바의 프로필에서 열리는, 어느 탭 위에도 덮이는 화면이다
     * (`BottomTab.Storage` 주석: *"마이는 상단바의 프로필 사진 버튼으로 간다. 탭이
     * 아니다"*). 저장소를 보던 중에 프로필을 눌러도 마이가 떠야 한다.
     */
    @Test
    fun `저장소를 보는 중에 마이를 열면 마이다`() {
        assertEquals(HomeFace.My, homeFace(myOpen = true, tab = BottomTab.Storage))
    }

    @Test
    fun `홈에서 마이를 열면 마이다`() {
        assertEquals(HomeFace.My, homeFace(myOpen = true, tab = BottomTab.Home))
    }

    /**
     * 도감·내 주변은 여기까지 안 온다 — 그 둘은 `screen` 을 바꾼다. 혹시 값이 들어와도
     * 방으로 떨어져야 한다 (빈 화면이 되면 안 된다).
     */
    @Test
    fun `다른 탭 값은 방으로 떨어진다`() {
        assertEquals(HomeFace.Room, homeFace(myOpen = false, tab = BottomTab.Dex))
        assertEquals(HomeFace.Room, homeFace(myOpen = false, tab = BottomTab.Nearby))
    }
}
