package com.daengs.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 창 모양에서 홈 배치를 고르는 규칙.
 *
 * 여기 드는 dp 는 전부 **합성 안 dp** 다 — `DaengsTheme` 이 `LocalDensity` 를
 * 갈아끼우므로 화면 dp 와 다르다 ([WideLayout] 머리말 참고).
 *
 * 갤럭시 Z 플립 커버 화면에서 **미니룸이 통째로 사라진 적이 있다.** 방이
 * `weight(1f)` 로 남는 높이를 가져가는 구조인데 카드가 먼저 다 먹어서 남는 게
 * 10dp 였다. 이 저장소가 담당하는 물건이 홈 화면과 미니룸인데 그 미니룸이
 * 안 보였다. 그래서 "한 화면에 다 들어가는가" 를 눈이 아니라 여기서 잡는다.
 */
class WideLayoutTest {

    /**
     * **기준 폰은 지금 그대로여야 한다.**
     *
     * 411x914 폰의 본문은 736dp 다 (상태바 24 + 상단바 64, 하단바 64 + 내비 26 을
     * 뺀 값). 여기서 스크롤이 생기면 멀쩡하던 폰이 바뀐다 — 이번 작업에서
     * 제일 중요한 한 줄이다.
     */
    @Test
    fun `기준 폰 본문에서는 한 화면에 다 들어간다`() {
        assertFalse(homeScrolls(736.dp))
    }

    /** 플립 펼침(407x994)의 본문 836dp. 여기도 지금 멀쩡하니 건드리지 않는다. */
    @Test
    fun `플립 펼침에서도 한 화면에 다 들어간다`() {
        assertFalse(homeScrolls(836.dp))
    }

    /**
     * 플립 커버(411x427)의 본문 337dp. 방에 10dp 밖에 안 남던 바로 그 값이다.
     */
    @Test
    fun `플립 커버에서는 스크롤 한 칸이 된다`() {
        assertTrue(homeScrolls(337.dp))
    }

    /**
     * 플렉스 모드에서 쓸 수 있는 위쪽 절반(411x502)의 본문 412dp.
     *
     * 커버와 **같은 병이다** — 세로가 짧다. 그래서 판정도 하나다.
     */
    @Test
    fun `플렉스 위쪽 절반에서도 스크롤 한 칸이 된다`() {
        assertTrue(homeScrolls(412.dp))
    }

    /**
     * 경계는 카드 덩어리와 방 최소치의 합이다.
     *
     * 이 관계를 박아 두는 이유는 [ROOM_MIN_HEIGHT] 를 만졌을 때 경계가 같이
     * 움직이게 하려는 것이다. 숫자를 따로 적어 두면 둘이 어긋난다.
     */
    @Test
    fun `카드와 방 최소치가 딱 들어가면 스크롤하지 않는다`() {
        assertFalse(homeScrolls(CARDS_BLOCK_HEIGHT + ROOM_MIN_HEIGHT))
    }

    /** 그보다 1dp 만 모자라도 스크롤이다. */
    @Test
    fun `방 최소치에 1dp 모자라면 스크롤한다`() {
        assertTrue(homeScrolls(CARDS_BLOCK_HEIGHT + ROOM_MIN_HEIGHT - 1.dp))
    }
}
