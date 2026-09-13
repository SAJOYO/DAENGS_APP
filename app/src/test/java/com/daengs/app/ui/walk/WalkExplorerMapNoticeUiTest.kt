package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkExplorerMapNoticeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `late map hints preserve time and reading height while their original actions remain reachable`() {
        val read = explorerPanelPreviewRead(false)
        val scenes = read.diary!!.scenes
        var hints by mutableStateOf(false)
        var zooms = 0
        var opened: String? = null
        var explorerSelected by mutableStateOf(true)
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read); selectTimeRange(0, 30_000) } }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) { DaengsTheme {
                WalkDiaryMapContent(scenes, null, false, null, { opened = it.id }, {}, {}, {}, {}, {}, map = {},
                    summaryContent = { WalkSessionSummary(read.route.detail.summary) },
                    directionNotice = hints, onZoomRoute = { zooms++ },
                    offscreenScenes = if (hints) scenes else emptyList(),
                    explorerSelected = explorerSelected, onChooseExplorer = { explorerSelected = it }, explorerPanel = { notices ->
                        WalkRouteExplorerPanel(state, {}, readingNotices = notices)
                    })
            } }
        }
        val header = compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot
        val reading = compose.onNodeWithTag("explorer-reading").fetchSemanticsNode().boundsInRoot
        val sheet = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { hints = true }
        assertEquals(header, compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot)
        assertEquals(reading, compose.onNodeWithTag("explorer-reading").fetchSemanticsNode().boundsInRoot)
        assertTrue(reading.height >= 40f)
        compose.onNodeWithText("동선 재생").assertIsDisplayed()
        compose.onNodeWithText("동선 확대").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, zooms) }
        compose.onNodeWithText("화면 밖 장면 6개").assertDoesNotExist()
        compose.onNodeWithText("장면 6").performClick()
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText("화면 밖 장면 6개"))
        compose.onNodeWithText("화면 밖 장면 6개").performScrollTo().performClick()
        compose.onNodeWithText("6 · ${scenes.last().title}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(scenes.last().id, opened) }
        assertEquals(sheet, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
