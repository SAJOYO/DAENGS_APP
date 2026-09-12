package com.daengs.app.ui.places

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * **잠긴 디자인** — `docs/design-locks.md` 2절. 내 주변 카테고리 선택은 넉넉하게 연다.
 *
 * ⛔ 깨지면 **테스트를 고치지 말고 변경을 되돌린다.** 사용자가 실기기에서 보고
 *    "옹졸하다" 고 해서 넓힌 화면이다. 줄이려면 사람이 문서부터 고친다.
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

    @Test
    fun `카테고리 시트는 화면 폭을 다 쓴다`() {
        open()
        val sheet = compose.onNodeWithTag("place-purpose-sheet").fetchSemanticsNode()
        // 막대 밑 드롭다운으로 돌아가면 폭이 화면의 절반 남짓이 된다.
        assertTrue("$lock (시트 폭 ${sheet.size.width}px < 화면의 95%)", sheet.size.width >= px(390) * 0.95f)
    }

    @Test
    fun `칸은 넉넉하다`() {
        open()
        val tiles = compose.onAllNodesWithTag("place-purpose-tile").fetchSemanticsNodes()
        assertEquals("갈래 아홉 개가 다 떠야 한다", 9, tiles.size)
        tiles.forEach { tile ->
            assertTrue("$lock (칸 높이 ${tile.size.height}px < 84dp)", tile.size.height >= px(84))
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
}
