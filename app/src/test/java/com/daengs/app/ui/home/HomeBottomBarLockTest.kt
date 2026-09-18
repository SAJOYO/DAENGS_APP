package com.daengs.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **사용자가 실기기에서 정한 하단 규격** — `docs/design-locks.md`.
 *
 * ⛔ 이 테스트가 깨지면 테스트를 고치지 말고 변경을 되돌린다. 여기 적힌 것은 사용자가
 *    Pixel 3 XL 에서 보고 정한 화면이다. 바꾸려면 사람이 문서부터 고친다.
 *
 * 무엇이 잘못돼 있었나: 가운데 발바닥 버튼 지름 58dp 중 **22dp(38%)** 가 흰 바 위로
 * 나와 있어서, 바의 일부가 아니라 바 위에 얹힌 별개의 물체로 읽혔다. 그리고 시즌 행과
 * 흰 바 사이가 **47.1dp** 비어 있었다 — 내용 영역 안 25.1dp + 투명한 띠 22dp 다.
 *
 * 여기 드는 dp 는 **합성 안 dp** 다 (`DaengsTheme` 이 `LocalDensity` 를 갈아끼운다).
 * 실기기에서 잴 때 `wm density` 를 쓰면 안 맞는다 — Pixel 3 XL 은 `1dp = 3.504px` 다.
 */
class HomeBottomBarLockTest {

    @Test
    fun `가운데 버튼 지름은 52dp 다`() {
        assertEquals(52.dp, FabSize)
    }

    /** 지름의 12%. 38% 였을 때 "바 위에 얹힌 별개의 물체" 로 읽혔다. */
    @Test
    fun `가운데 버튼이 흰 바 위로 나오는 양은 6dp 다`() {
        assertEquals(6.dp, FabLift)
    }

    /**
     * ⚠️ **50dp 로 줄이면 탭 라벨 아래가 깎인다** (2026-09-18 실기기 확인).
     * 홈에서 방을 키우는 것은 이 값이 아니라 [FabLift] 와 카드 쪽에서 한다.
     */
    @Test
    fun `바 높이 56dp 는 지킨다`() {
        assertEquals(56.dp, BarHeight)
    }

    @Test
    fun `가운데 버튼은 터치 최소치를 넘는다`() {
        assertTrue("$FabSize 는 48dp 미만이다", FabSize >= 48.dp)
    }

    /**
     * 글자가 17.4dp 인데 `ButtonDefaults.MinHeight` 때문에 띠가 58dp 를 먹고 있었다
     * (실측). `LocalMinimumInteractiveComponentSize` 를 풀어 둔 것만으로는 안 줄었다 —
     * 그건 48dp 터치 타깃을 푸는 것이고 버튼 최소 높이는 별개다.
     */
    @Test
    fun `시즌 행 높이는 28dp 다`() {
        assertEquals(28.dp, HomeGameRowHeight)
    }

    /** 글자 17.4dp 가 들어가고 위아래로 숨 쉴 만큼은 남아야 한다. */
    @Test
    fun `시즌 행에 글자 한 줄이 들어간다`() {
        assertTrue("$HomeGameRowHeight 에 17.4dp 글자가 안 들어간다", HomeGameRowHeight >= 24.dp)
    }
}
