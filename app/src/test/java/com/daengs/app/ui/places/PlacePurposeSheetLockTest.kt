package com.daengs.app.ui.places

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.daengs.app.place.PlaceCategorySelection
import com.daengs.app.place.PlaceKind
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/**
 * **잠긴 디자인** — `docs/design-locks.md` 2절. 내 주변 카테고리 판.
 *
 * ⛔ 깨지면 **테스트를 고치지 말고 변경을 되돌린다.** 사용자와 두 번 맞춘 자리다 —
 *    작은 드롭다운은 "옹졸하다", 큰 아래 시트는 "너무 크고 위가 낫다" 였다.
 *    그래서 **아래 한계와 위 한계를 둘 다** 잡는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class PlacePurposeSheetLockTest {
    @get:Rule val compose = createComposeRule()

    private val lock = "⛔ 잠긴 디자인 위반 — docs/design-locks.md 2절. 테스트를 고치지 말고 변경을 되돌릴 것."

    private fun px(dp: Int) = with(compose.density) { dp.dp.toPx() }

    private fun open() {
        compose.setContent {
            DaengsTheme {
                var selection by remember {
                    mutableStateOf<PlaceCategorySelection>(PlaceCategorySelection.Kind(PlaceKind.CAFE))
                }
                PlacePurposeMenu(selection) { selection = it }
            }
        }
        compose.onNodeWithTag("place-category-bar").performClick()
        compose.waitForIdle()
    }

    /** 막대 밑 좁은 드롭다운(화면 절반)으로 돌아가면 걸린다. */
    @Test
    fun `판은 화면 폭으로 뜬다`() {
        open()
        val sheet = compose.onNodeWithTag("place-purpose-sheet").fetchSemanticsNode()
        assertTrue("$lock (판 폭 ${sheet.size.width}px < 화면의 90%)", sheet.size.width >= px(390) * 0.90f)
    }

    /** 큰 시트로 돌아가도 걸린다 — 판이 화면 세로의 40% 를 넘지 않는다. */
    @Test
    fun `판이 너무 크지 않다`() {
        open()
        val sheet = compose.onNodeWithTag("place-purpose-sheet").fetchSemanticsNode()
        assertTrue("$lock (판 높이 ${sheet.size.height}px > 화면의 40%)", sheet.size.height <= px(844) * 0.40f)
    }

    @Test
    fun `칸은 작지도 크지도 않다`() {
        open()
        val tiles = compose.onAllNodesWithTag("place-purpose-tile").fetchSemanticsNodes()
        assertEquals("갈래 아홉 개가 다 떠야 한다", 9, tiles.size)
        tiles.forEach { tile ->
            assertTrue("$lock (칸 높이 ${tile.size.height}px < 60dp — 옹졸)", tile.size.height >= px(60))
            assertTrue("$lock (칸 높이 ${tile.size.height}px > 80dp — 너무 큼)", tile.size.height <= px(80))
            assertTrue("$lock (칸 폭 ${tile.size.width}px < 100dp)", tile.size.width >= px(100))
        }
    }

    /** 다섯 칸으로 되돌리면 칸이 좁아진다 — 그게 "옹졸" 의 뿌리였다. */
    @Test
    fun `한 줄에 세 칸이다`() {
        open()
        val rows = compose.onAllNodesWithTag("place-purpose-tile").fetchSemanticsNodes()
            .groupBy { it.boundsInRoot.top.roundToInt() }
        assertEquals("$lock (줄 수)", 3, rows.size)
        rows.values.forEach { assertEquals("$lock (한 줄의 칸 수)", 3, it.size) }
    }

    /** **위에 뜬다** — 막대 바로 아래. 화면 아래 시트로 돌아가면 걸린다. */
    @Test
    fun `판은 막대 바로 아래에 붙는다`() {
        val bar = IntRect(40, 200, 1000, 260)
        val offset = purposePanelOffset(bar, IntSize(1080, 2400), IntSize(1020, 600), margin = 30)
        assertEquals("$lock (판의 자리)", IntOffset(30, 260), offset)
    }

    @Test
    fun `화면 아래로 넘치면 넘치는 만큼만 올린다`() {
        val bar = IntRect(40, 2000, 1000, 2060)
        val offset = purposePanelOffset(bar, IntSize(1080, 2400), IntSize(1020, 600), margin = 30)
        assertEquals(IntOffset(30, 1800), offset)
    }
}
