package com.daengs.app.farewell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.common.TAG_ASLEEP
import com.daengs.app.ui.common.TAG_AWAKE
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 배웅 화면의 "떠난 날" 휠.
 *
 * **이 화면은 실기기로 못 봤다.** 배웅으로 들어가는 길이 삭제 확인 창 안에 있어서
 * (`MyScreen` 의 `ConfirmDelete`), 실제 계정에서 열려면 남의 강아지를 지우는 버튼 옆을
 * 짚어야 한다. 그래서 화면 자체를 여기서 띄워 같은 것을 본다 — 게다가 이쪽이 더
 * 세다. **화면이 서버로 보낼 날짜를 배웅하기 버튼으로 직접 받아 본다.**
 *
 * 날짜가 하루 밀린 채로 배웅되면 되돌리기 전까지 아무도 모른다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
open class FarewellWheelScrollTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val today: LocalDate = LocalDate.now()

    @Test fun `잠든 휠 위를 쓸면 폼이 내려가고 떠난 날은 그대로다`() {
        val sent = open()

        compose.onNodeWithTag(TAG_ASLEEP).spinYear()

        // 휠 아래의 버튼이 올라왔다 = 폼이 내려갔다.
        compose.onNodeWithText(CONFIRM).assertIsDisplayed()
        // 그리고 화면이 보낼 날은 그대로다. 이것이 본체다.
        compose.onNodeWithText(CONFIRM).performClick()
        compose.runOnIdle { assertEquals("폼을 내리는 사이 떠난 날이 바뀌었다", today, sent()) }
    }

    @Test fun `톡 누르면 깨어나고 그때는 떠난 날이 돌아간다`() {
        val sent = open()

        compose.onNodeWithTag(TAG_ASLEEP).performClick()
        // **아래로 굴린다.** 이 화면은 앞날을 못 고르게 해 두어서(`years` 가 올해에서 끝난다)
        // 오늘로 열린 휠은 이미 마지막 칸이다. 위로 굴리면 갈 곳이 없어 안 움직인다.
        compose.onNodeWithTag(TAG_AWAKE).spinYear(up = false)

        compose.onNodeWithText(CONFIRM).performClick()
        compose.runOnIdle { assertNotEquals("깨운 휠이 안 돌아갔다", today.year, sent()?.year) }
    }

    private fun open(): () -> LocalDate? {
        var sent: LocalDate? = null
        compose.setContent {
            DaengsTheme {
                FarewellScreen(
                    dogName = "Max",
                    sentOn = null,
                    onSendOff = { sent = it },
                    onUndo = {},
                    onClose = {},
                )
            }
        }
        return { sent }
    }

    /** 해 칸 복판을 위로 굴린다. 가운데는 칸 사이 이음매라 안 쓴다 — 휠을 안 건드리고 지나간다. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.spinYear(up: Boolean = true) =
        performTouchInput {
            val x = center.x + (-76).dp.toPx()
            val from = height * if (up) 0.85f else 0.15f
            val to = height * if (up) 0.15f else 0.85f
            swipe(Offset(x, from), Offset(x, to), durationMillis = 150)
        }

    private companion object {
        const val CONFIRM = "배웅하기"
    }
}

/** 폴더블 펼침 기하. 세로가 짧아 휠이 화면을 거의 다 차지하는 곳이다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w673dp-h841dp")
class FoldFarewellWheelScrollTest : FarewellWheelScrollTest()
