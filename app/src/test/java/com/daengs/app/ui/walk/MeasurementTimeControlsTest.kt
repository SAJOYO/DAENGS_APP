package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MeasurementTimeControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `real explorer panel selects a time window without scene duplication and seeks without changing metrics`() {
        val read = explorationRead(); lateinit var state: WalkRouteExplorerState
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read) } }
            DaengsTheme { WalkRouteExplorerPanel(state, {}) }
        }
        compose.onNodeWithText("1분").performClick()
        compose.runOnIdle {
            assertEquals(RouteExplorerMode.SLICE, state.mode)
            assertEquals(60_000L, state.selectedSlice!!.until)
            assertEquals(123.0, read.route.detail.summary.distanceMeters, 0.0)
            val layer = recordPresentationLayer(state, read.route.detail, null)
            assertTrue(layer.observedParts.any { it.selected })
            assertEquals(1, layer.highlightPaths.size)
        }
        compose.onNodeWithText("이 범위의 장면 1개").assertDoesNotExist()
        compose.onNodeWithText("범위 시작으로 이동").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(RouteExplorerMode.REPLAY, state.mode); assertEquals(0L, state.elapsed) }
        compose.onNodeWithTag("explorer-range-slider").assertDoesNotExist()
        compose.onNodeWithTag("explorer-replay-slider").assertExists()
        compose.onNodeWithText("구간 수정").performClick()
        compose.onNodeWithTag("explorer-range-slider").assertExists()
        compose.onNodeWithTag("explorer-replay-slider").assertDoesNotExist()
        compose.onNodeWithText("장면", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertNull(state.selectedSceneId) }
    }
}
