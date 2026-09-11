package com.daengs.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/**
 * 반접기(플렉스 모드)에서 콘텐츠가 설 자리.
 *
 * 안드로이드는 접혀도 **창을 줄여 주지 않는다.** 앱 창은 화면 전체로 남고,
 * 접힘은 `androidx.window` 의 `FoldingFeature` 로만 알려 준다. 그래서 아래쪽
 * 절반이 책상에 평평하게 누워 있는데도 앱은 거기까지 그린다 — 에뮬레이터에서
 * 힌지를 90 도로 접어 `HALF_OPENED` 가 뜨는데도 창은 그대로인 것을 봤다.
 *
 * 어디까지 쓸 수 있는지를 정하는 규칙만 여기서 잡는다. 자세를 읽어 오는 일은
 * 합성 쪽이고, 여기는 그 값을 받아 자리를 내는 순수한 계산이다.
 */
class FlexModeTest {

    /** 힌지가 가로로 누워 반쯤 접혔으면 **위쪽 절반**까지만 쓴다. */
    @Test
    fun `반접힘에 가로 힌지면 힌지 위까지만 쓴다`() {
        val height = flexContentHeight(
            windowHeight = 1003.dp,
            hingeTop = 502.dp,
            halfOpened = true,
            horizontalHinge = true,
        )
        assertEquals(502.dp, height)
    }

    /** 펼쳐져 있으면 창 전체가 제 자리다. 자를 이유가 없다. */
    @Test
    fun `펼쳐져 있으면 자르지 않는다`() {
        assertNull(
            flexContentHeight(
                windowHeight = 1003.dp,
                hingeTop = 502.dp,
                halfOpened = false,
                horizontalHinge = true,
            ),
        )
    }

    /**
     * **세로 힌지는 다른 이야기다.**
     *
     * 폴드를 펼치면 힌지가 세로로 서서 화면을 좌우로 가른다. 위아래로 접히는
     * 플립과 전혀 다른 문제라 여기서 다루지 않는다 — 넓은 화면은
     * [WIDE_BREAKPOINT] 가 두 칸으로 가른다.
     */
    @Test
    fun `세로 힌지는 여기서 다루지 않는다`() {
        assertNull(
            flexContentHeight(
                windowHeight = 1003.dp,
                hingeTop = 502.dp,
                halfOpened = true,
                horizontalHinge = false,
            ),
        )
    }

    /**
     * 힌지가 창 밖이면 못 믿는다.
     *
     * 자세와 힌지 자리는 **다른 데서 오는 두 값**이라 어긋난 채로 도착할 수
     * 있다. 그대로 믿고 자르면 화면이 통째로 비거나 자른 의미가 없어진다.
     */
    @Test
    fun `힌지가 창 밖이면 자르지 않는다`() {
        assertNull(
            flexContentHeight(
                windowHeight = 1003.dp,
                hingeTop = 1200.dp,
                halfOpened = true,
                horizontalHinge = true,
            ),
        )
    }

    /** 힌지가 맨 위에 붙어 있어도 마찬가지다 — 남는 자리가 없다. */
    @Test
    fun `힌지가 맨 위면 자르지 않는다`() {
        assertNull(
            flexContentHeight(
                windowHeight = 1003.dp,
                hingeTop = 0.dp,
                halfOpened = true,
                horizontalHinge = true,
            ),
        )
    }

    /**
     * 플립을 반접으면 위쪽 절반이 502dp 다. 거기서 하단바(90dp)를 빼면 본문이
     * 412dp 라 [homeScrolls] 가 true 를 돌려준다 — **커버와 같은 길로 온다.**
     * 판정을 따로 두지 않은 근거가 이 한 줄이다.
     */
    @Test
    fun `플렉스 위쪽 절반은 스크롤 갈래로 간다`() {
        val top = flexContentHeight(1003.dp, 502.dp, halfOpened = true, horizontalHinge = true)
        assertTrue(homeScrolls(top!! - 90.dp))
    }
}

/**
 * 반접기에서 **위아래 두 칸**으로 나누는 규칙.
 *
 * 두 절반은 성격이 다르다. 세워진 위쪽은 **보는 면**이고, 책상에 누운 아래쪽은
 * 손가락이 얹히는 **만지는 면**이다. 그래서 방은 위, 카드와 바는 아래로 간다 —
 * 카메라 앱이 뷰파인더를 위에 셔터를 아래에 두는 것과 같은 이유다.
 *
 * 방이 힌지 선에 **딱 맞아야** 한다. 접힌 자리를 가로지르면 방 그림이 꺾여서
 * 두 조각으로 보인다.
 */
class FlexSplitTest {

    /**
     * 플립 반접기. 힌지가 창 위에서 502dp 고 콘텐츠 상자가 상태바 아래
     * 26dp 에서 시작하면, 힌지 위에 남는 것이 476dp 다.
     *
     * **일반 폰에서 방이 받는 409dp 보다 크다** — 플렉스가 이 앱에서 방이
     * 제일 커지는 자리가 된다.
     */
    @Test
    fun `힌지까지의 높이를 방에 준다`() {
        assertEquals(476.dp, flexRoomHeight(hingeTop = 502.dp, contentTop = 26.dp))
    }

    /** 상단바가 펴져 있으면 그만큼 늦게 시작하므로 방도 그만큼 줄어든다. */
    @Test
    fun `상단바가 있으면 그만큼 줄어든다`() {
        assertEquals(412.dp, flexRoomHeight(hingeTop = 502.dp, contentTop = 90.dp))
    }

    /**
     * 힌지 위가 방 최소치도 안 되면 **두 칸으로 안 나눈다.**
     *
     * 나누면 위쪽에 띠만 한 방이 남는다. 그 경우는 한 칸 스크롤([homeScrolls])
     * 이 낫다 — 커버 화면에서 이미 그 길을 쓴다.
     */
    @Test
    fun `힌지 위가 너무 좁으면 나누지 않는다`() {
        assertNull(flexRoomHeight(hingeTop = 300.dp, contentTop = 150.dp))
    }

    /** 정확히 최소치면 나눈다 — 경계는 들어가는 쪽이다. */
    @Test
    fun `방 최소치와 같으면 나눈다`() {
        assertEquals(
            ROOM_MIN_HEIGHT,
            flexRoomHeight(hingeTop = ROOM_MIN_HEIGHT + 26.dp, contentTop = 26.dp),
        )
    }

    /**
     * 아직 못 잰 상태(콘텐츠 상자 자리가 0)에서도 답이 나와야 한다.
     *
     * 자리는 `onGloballyPositioned` 로 **한 프레임 늦게** 온다. 그 사이에
     * 터지거나 이상한 값을 내면 첫 프레임이 깨진다.
     */
    @Test
    fun `아직 못 잰 자리에서도 답이 나온다`() {
        assertEquals(502.dp, flexRoomHeight(hingeTop = 502.dp, contentTop = 0.dp))
    }

    /** 힌지가 콘텐츠 상자보다 위면 음수가 된다. 그때도 안 나눈다. */
    @Test
    fun `힌지가 콘텐츠보다 위면 나누지 않는다`() {
        assertNull(flexRoomHeight(hingeTop = 20.dp, contentTop = 90.dp))
    }
}
