package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.routeexplorer.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryReplayClockTest {
    @get:Rule val compose=createComposeRule()

    @Test fun `range ready clock matches the selected playable start`() {
        val read=explorerPanelPreviewRead()
        lateinit var state: WalkRouteExplorerState
        compose.setContent { DaengsTheme {
            val scope=rememberCoroutineScope()
            state=remember { WalkRouteExplorerState(scope,0).apply { adopt(read); choosePanel(true) } }
            DiaryReplayPlayer(state,diaryReplayTimeline(read))
        } }
        compose.runOnIdle { state.selectTimeRange(15_000,30_000) }
        assertEquals(15_000L,state.selectedSlice!!.from)
        val expected=read.route.review.timeline!!.frameAt(15_000).recordedAtMillis!!
        compose.onNodeWithTag("replay-clock").assertTextEquals(formatRouteExplorerClock(expected))
        compose.runOnIdle { state.togglePlayback() }
        compose.onNodeWithTag("replay-clock").assertTextEquals(formatRouteExplorerClock(expected))
        compose.runOnIdle { state.seek(20_000); assertTrue(state.returnToRange()) }
        compose.onNodeWithTag("replay-clock").assertTextEquals(formatRouteExplorerClock(expected))
    }

    @Test fun `end without a GPS fix keeps the actual end clock`() {
        val read=explorerPanelPreviewRead()
        lateinit var state: WalkRouteExplorerState
        compose.setContent { DaengsTheme {
            val scope=rememberCoroutineScope()
            state=remember { WalkRouteExplorerState(scope,0).apply { adopt(read); choosePanel(true) } }
            DiaryReplayPlayer(state,diaryReplayTimeline(read))
        } }
        compose.runOnIdle { state.seek(state.duration); assertTrue(state.replayFrame!!.inGap) }
        compose.onNodeWithTag("replay-clock").assertTextEquals(formatRouteExplorerClock(read.route.detail.summary.endedAtMillis!!))
    }

    @Test fun `start without a GPS fix keeps the actual start clock`() {
        val detail=measuredTimedDetail()
        lateinit var state: WalkRouteExplorerState
        compose.setContent { DaengsTheme {
            val scope=rememberCoroutineScope()
            state=remember { WalkRouteExplorerState(scope,0).apply {
                replaceRoute(RouteExplorerIndex(detail.route),CompletedRouteReview(detail),0)
                choosePanel(true)
            } }
            DiaryReplayPlayer(state,null)
        } }
        compose.onNodeWithTag("replay-clock").assertTextEquals(formatRouteExplorerClock(detail.summary.startedAtMillis))
        compose.runOnIdle { state.togglePlayback(); assertTrue(state.replayFrame!!.inGap) }
        compose.onNodeWithTag("replay-clock").assertTextEquals(formatRouteExplorerClock(detail.summary.startedAtMillis))
    }

    @Test fun `unknown middle clock stays explicit in range and replay at narrow large text`() {
        val read=explorerPanelPreviewRead()
        lateinit var state: WalkRouteExplorerState
        compose.setContent { DaengsTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                val scope=rememberCoroutineScope()
                state=remember { WalkRouteExplorerState(scope,0).apply { adopt(read); choosePanel(true) } }
                Box(Modifier.width(320.dp)) { DiaryReplayPlayer(state,diaryReplayTimeline(read)) }
            }
        } }
        compose.runOnIdle { state.selectTimeRange(60_000,90_000) }
        compose.onNodeWithTag("replay-clock").assertTextEquals("시각 정보 없음").assertIsDisplayed()
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("replay-clock").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout=layouts.single()
        // Text's own rounded pixel width can be smaller than its fractional glyph
        // width. Check the available column and complete text, not that rounding.
        assertEquals(1,layout.lineCount)
        assertEquals("시각 정보 없음".length,layout.getLineEnd(0,visibleEnd=true))
        assertTrue(layout.getLineRight(0) <= layout.layoutInput.constraints.maxWidth)
        assertFalse(layout.didOverflowHeight)
        compose.runOnIdle { state.seek(70_000) }
        compose.onNodeWithTag("replay-clock").assertTextEquals("시각 정보 없음")
        compose.runOnIdle { state.overview(); state.seek(15_000) }
        compose.onNodeWithTag("replay-clock").assertTextEquals(formatRouteExplorerClock(15_000))
    }
}

