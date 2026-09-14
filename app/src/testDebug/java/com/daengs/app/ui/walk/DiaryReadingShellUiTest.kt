package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.walk.review.recordContextMapFixture
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryReadingShellUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `map controls occupy the reported overlay and opening legend leaves camera and drawer alone`() {
        val fixture = recordContextMapFixture()
        var input: DiaryReviewMap? = null
        compose.setContent { WalkRouteReviewContent(fixture.detail, fixture.scenes, "시안 02 검증",
            renderMap = { value -> SideEffect { input = value }; Box(Modifier.fillMaxSize()) }) }
        compose.waitUntil(15_000) { input?.viewport?.controlsHeightPx?.let { it > 0 } == true }
        val initial = requireNotNull(input)
        val tools = compose.onNodeWithTag("diary-map-tools").fetchSemanticsNode().boundsInRoot
        val legend = compose.onNodeWithTag("diary-map-legend").fetchSemanticsNode().boundsInRoot
        val sheet = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        assertTrue(legend.top >= tools.top && legend.bottom <= tools.bottom)
        assertEquals(tools.height, initial.viewport.controlsHeightPx.toFloat(), 1f)
        assertEquals(initial.viewport.controlsHeightPx, initial.query.topRightCoverTopPx)
        compose.onNodeWithTag("diary-map-legend").performClick()
        compose.onNodeWithTag("diary-map-legend-help").assertIsDisplayed()
        compose.runOnIdle { assertEquals(initial.camera, input!!.camera) }
        assertEquals(sheet, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `large font keeps shell controls reachable and reports wrapped toolbar height`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DiaryReadingStylePreview(DiaryReadingExample.MULTI_DOG)
            }
        }
        compose.onNodeWithText("보행 중심").assertIsDisplayed()
        compose.onNodeWithTag("diary-map-legend").assertIsDisplayed()
        compose.onNodeWithContentDescription("서랍 펼치기").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("서랍 펼치기").assertIsNotEnabled()
        compose.onNodeWithContentDescription("서랍 접기").performClick()
        compose.onNodeWithText("동선 탐색").assertIsDisplayed()
        val list = compose.onNodeWithTag("diary-scene-list").fetchSemanticsNode().boundsInRoot
        assertTrue("Reading viewport must survive the header and tabs", list.height > 100)
    }
}
