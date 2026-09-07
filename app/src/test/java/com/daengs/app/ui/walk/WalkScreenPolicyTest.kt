package com.daengs.app.ui.walk

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.TrailSnapshot
import com.daengs.app.walk.WalkTrackingState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class WalkScreenPolicyTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun speedAndColorAppearTogetherOnlyAfterStarting() {
        val state = mutableStateOf(WalkUiState())
        compose.setContent { DaengsTheme { WalkScreen(state.value, {}, showMap = false) } }
        compose.onNodeWithText("이동 속도 · m/s").assertDoesNotExist()
        compose.onNodeWithText("색상").assertDoesNotExist()
        compose.onNodeWithContentDescription("잠시 멈춤").assertDoesNotExist()
        compose.runOnIdle { state.value = recording() }
        compose.onNodeWithText("이동 속도 · m/s").assertIsDisplayed()
        compose.onNodeWithText("색상").assertIsDisplayed().performClick()
        compose.onNodeWithText("산책 지도 설정").assertIsDisplayed()
    }

    private fun recording() = WalkUiState(tracking = WalkTrackingState(
        trail = TrailSnapshot(state = TrackingState.RECORDING)))

    private fun checkLayout() {
        val actions = mutableListOf<WalkAction>()
        compose.setContent { DaengsTheme { WalkScreen(recording(), actions::add, showMap = false) } }
        val time = compose.onNodeWithText("산책 시간").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val pause = compose.onNodeWithContentDescription("잠시 멈춤").assertIsDisplayed()
        val pauseBounds = pause.fetchSemanticsNode().boundsInRoot
        val home = compose.onNodeWithContentDescription("홈으로").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithContentDescription("행동 기록").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val speed = compose.onNodeWithText("이동 속도 · m/s").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val color = compose.onNodeWithText("색상").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(time.left < home.left)
        assertTrue(pauseBounds.right <= home.left)
        assertTrue(pauseBounds.bottom < action.top)
        assertTrue(kotlin.math.abs(speed.center.y - color.center.y) < speed.height)
        compose.onNodeWithText("쉼").assertDoesNotExist()
        pause.performClick()
        assertEquals(WalkAction.Pause, actions.last())
        compose.onNodeWithContentDescription("홈으로").performClick()
        assertEquals(WalkAction.Home, actions.last())
    }

    @Test fun portraitGroupsRelatedControls() = checkLayout()
    @Test fun pausedMenuKeepsHomeOnTheRightAndReturnsToTimerControl() {
        val state = recording().copy(tracking = WalkTrackingState(trail = TrailSnapshot(state = TrackingState.PAUSED)))
        compose.setContent { DaengsTheme { WalkScreen(state, {}, showMap = false) } }
        val home = compose.onAllNodesWithContentDescription("홈으로").fetchSemanticsNodes().last().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(home.center.x > root.center.x)
        compose.onNodeWithText("지도 둘러보기").performClick()
        compose.onNodeWithContentDescription("산책 재개 메뉴").assertIsDisplayed().performClick()
        compose.onNodeWithText("이어서 걷기").assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w320dp-h844dp")
    fun narrowScreenKeepsControlsApart() = checkLayout()
    @Test @Config(qualifiers = "w891dp-h411dp")
    fun landscapeGroupsRelatedControls() = checkLayout()
}
