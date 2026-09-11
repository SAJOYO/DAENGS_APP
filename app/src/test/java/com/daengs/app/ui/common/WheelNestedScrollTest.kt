package com.daengs.app.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * 스크롤 폼 안에 놓인 휠의 제스처 주인.
 *
 * **본체는 "폼이 안 움직인다" 가 아니라 "그 사이 값이 조용히 바뀐다" 이다.** 폼이 안 내려간
 * 것은 눈에 보이지만, 생일이 하루 옮겨간 것은 안 보인다. 그래서 스크롤과 값을 **같이** 본다.
 *
 * 고치기 전에는 위로 한 번 쓰는 것만으로 2023-05-14 가 2023-12-14 가 됐다.
 *
 * 폴더블 기하는 [FoldWheelNestedScrollTest] 에서 같은 것을 다시 본다 — 휠이 화면을 거의
 * 다 차지해 휠 바깥을 잡을 자리가 없는 곳이라 여기가 제일 아프다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
open class WheelNestedScrollTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val startDay = LocalDate.of(2023, 5, 14)
    private val startTime = LocalTime.of(8, 0)

    @Test fun `잠든 날짜 휠 위를 쓸면 폼이 내려가고 날짜는 그대로다`() {
        val form = dateForm()

        asleep().spin(YEAR)

        compose.runOnIdle {
            assertTrue("휠 위를 쓸었는데 폼이 안 내려갔다", form.scroll.value > 0)
            assertEquals("폼을 내리는 사이 날짜가 바뀌었다", startDay, form.day)
        }
    }

    @Test fun `잠든 시각 휠 위를 쓸면 폼이 내려가고 시각은 그대로다`() {
        val form = timeForm()

        asleep().spin(HOUR)

        compose.runOnIdle {
            assertTrue("휠 위를 쓸었는데 폼이 안 내려갔다", form.scroll.value > 0)
            assertEquals("폼을 내리는 사이 시각이 바뀌었다", startTime, form.time)
        }
    }

    @Test fun `톡 누르면 깨어나고 그때는 날짜가 돌아간다`() {
        val form = dateForm()

        asleep().performClick()
        awake().spin(YEAR)

        compose.runOnIdle {
            assertNotEquals("깨운 휠이 안 돌아갔다", startDay.year, form.day.year)
            // 해가 끝에 닿아도 폼은 안 딸려간다 — KeepScrollInside 가 남은 스크롤을 먹는다.
            assertEquals("휠을 돌리는 사이 폼이 딸려 움직였다", 0, form.scroll.value)
        }
    }

    @Test fun `톡 누르면 깨어나고 그때는 시각이 돌아간다`() {
        val form = timeForm()

        asleep().performClick()
        awake().spin(HOUR)

        compose.runOnIdle {
            assertNotEquals("깨운 휠이 안 돌아갔다", startTime.hour, form.time.hour)
        }
    }

    @Test fun `깨어난 휠은 폼이 움직이면 도로 잠든다`() {
        val form = dateForm()

        asleep().performClick()
        compose.onNodeWithTag(TAG_AWAKE).assertExists()

        // 휠 바깥을 끌어 폼을 내린 것과 같다. 카드가 제 자리를 옮긴 것이 신호다.
        // **부호를 조심한다** — 폼은 0 에서 시작하므로 음수로 밀면 제자리에 붙어 있고,
        // 그러면 카드가 안 움직여 이 테스트가 아무것도 안 보고 지나간다.
        // **조금만 민다.** 많이 밀면 카드가 위로 잘려 나가서 뒤의 쓸기가 화면 밖을
        // 짚고, 폼이 안 내려간 것이 고침 탓인지 카드가 안 보인 탓인지 안 갈린다.
        compose.runOnIdle { form.scroll.dispatchRawDelta(NUDGE) }
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_ASLEEP).assertExists()

        // 그리고 그 뒤로는 다시 폼이 움직이고 값은 안 바뀐다.
        val parked = compose.runOnIdle { form.scroll.value }
        asleep().spin(YEAR)
        compose.runOnIdle {
            assertTrue("도로 잠든 휠 위에서 폼이 안 내려갔다 parked=$parked now=${form.scroll.value} max=${form.scroll.maxValue}", form.scroll.value > parked)
            assertEquals("도로 잠든 휠에서 날짜가 바뀌었다", startDay, form.day)
        }
    }

    // --- 거들기 ------------------------------------------------------------

    private fun asleep() = compose.onNodeWithTag(TAG_ASLEEP)
    private fun awake() = compose.onNodeWithTag(TAG_AWAKE)

    /**
     * 한 칸을 위로 굴린다.
     *
     * **가운데를 쓸면 안 된다.** 카드는 폭을 다 쓰지만 칸들은 가운데 248dp 에만 모여
     * 있어서, 폭이 넓은 기하에서는 카드 한가운데가 칸과 칸 사이 이음매이거나 아예 빈
     * 자리다. 거기를 쓸면 휠을 안 건드리고도 테스트가 지나간다 — 처음에 그렇게 새어
     * 나갔다. 그래서 칸 복판을 [dx] 로 짚는다.
     */
    private fun SemanticsNodeInteraction.spin(dx: Dp) = performTouchInput {
        val x = center.x + dx.toPx()
        swipe(
            start = Offset(x, height * 0.85f),
            end = Offset(x, height * 0.15f),
            durationMillis = 150,
        )
    }

    private class DateForm {
        lateinit var scroll: ScrollState
        var day by mutableStateOf(LocalDate.of(2023, 5, 14))
    }

    private class TimeForm {
        lateinit var scroll: ScrollState
        var time by mutableStateOf(LocalTime.of(8, 0))
    }

    private fun dateForm(): DateForm {
        val form = DateForm()
        compose.setContent {
            DaengsTheme {
                form.scroll = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(form.scroll)) {
                    Spacer(Modifier.fillMaxWidth().height(TOP))
                    DateWheel(value = form.day, onChange = { form.day = it })
                    Spacer(Modifier.fillMaxWidth().height(BOTTOM))
                }
            }
        }
        return form
    }

    private fun timeForm(): TimeForm {
        val form = TimeForm()
        compose.setContent {
            DaengsTheme {
                form.scroll = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(form.scroll)) {
                    Spacer(Modifier.fillMaxWidth().height(TOP))
                    TimeWheel(value = form.time, onChange = { form.time = it })
                    Spacer(Modifier.fillMaxWidth().height(BOTTOM))
                }
            }
        }
        return form
    }

    private companion object {
        /** 휠이 화면 안에 다 보이되 위로 스크롤할 자리도 남는 높이. */
        val TOP = 120.dp

        /** 폼이 내려갈 자리. 이만큼 없으면 스크롤이 0 에서 안 움직여 테스트가 늘 지나간다. */
        val BOTTOM = 1200.dp

        /** 폼을 살짝 미는 양. 카드가 제 자리를 옮기되 화면 안에 다 남는 만큼이다. */
        const val NUDGE = 60f

        /** 카드 한가운데에서 각 칸 복판까지. 칸은 96·76·76dp 이고 가운데로 모여 있다. */
        val YEAR = (-76).dp
        val HOUR = (-42).dp
    }
}

/**
 * 같은 것을 폴더블 펼침 기하(673dp × 841dp)에서 다시 본다.
 *
 * PR 본문이 짚은 대로 **이 기하가 문제를 제일 잘 드러낸다** — 세로가 짧아 휠이 화면
 * 아래를 거의 다 차지하고, 가로가 넓어 카드 한가운데는 칸이 아니라 빈 자리다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w673dp-h841dp")
class FoldWheelNestedScrollTest : WheelNestedScrollTest()
