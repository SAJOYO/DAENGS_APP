package com.daengs.app.ui.game

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **잠긴 디자인** — `docs/design-locks.md` 3절. 점령 규칙 창의 여백은 줄이지 않는다.
 *
 * ⛔ 깨지면 **테스트를 고치지 말고 변경을 되돌린다.** 제목이 창 윗변에 붙고 글이 좌우
 *    끝에 붙어 사용자가 실기기에서 "꽉 낀다" 고 해서 넓힌 여백이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class TerritoryRulesSpacingLockTest {
    @get:Rule val compose = createComposeRule()

    private val lock = "⛔ 잠긴 디자인 위반 — docs/design-locks.md 3절. 테스트를 고치지 말고 변경을 되돌릴 것."

    private fun px(dp: Int) = with(compose.density) { dp.dp.toPx() }

    private fun show() = compose.setContent {
        DaengsTheme { TerritoryGameRulesContent(onDismiss = {}, modifier = Modifier.width(350.dp)) }
    }

    @Test
    fun `제목이 창 윗변과 왼쪽에서 떨어져 있다`() {
        show()
        val box = compose.onNodeWithTag("game-rules-dialog").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("점령 규칙").fetchSemanticsNode().boundsInRoot
        assertTrue("$lock (제목 위 여백 ${title.top - box.top}px < 18dp)", title.top - box.top >= px(18))
        assertTrue("$lock (제목 왼쪽 여백 ${title.left - box.left}px < 22dp)", title.left - box.left >= px(22))
    }

    /** 점수 카드의 글은 본문 여백(24) + 카드 안 여백(20) 만큼 들어와 있다. */
    @Test
    fun `본문 글이 좌우 끝에 붙지 않는다`() {
        show()
        val box = compose.onNodeWithTag("game-rules-dialog").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithText("점수 한눈에").fetchSemanticsNode().boundsInRoot
        assertTrue("$lock (점수 카드 글 왼쪽 여백 ${card.left - box.left}px < 40dp)", card.left - box.left >= px(40))
    }

    @Test
    fun `아래 버튼이 창 가장자리에서 떨어져 있다`() {
        show()
        val box = compose.onNodeWithTag("game-rules-dialog").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("game-rules-example-open").fetchSemanticsNode().boundsInRoot
        assertTrue("$lock (버튼 왼쪽 여백 ${button.left - box.left}px < 18dp)", button.left - box.left >= px(18))
        assertTrue("$lock (버튼 아래 여백 ${box.bottom - button.bottom}px < 18dp)", box.bottom - button.bottom >= px(18))
    }
}
