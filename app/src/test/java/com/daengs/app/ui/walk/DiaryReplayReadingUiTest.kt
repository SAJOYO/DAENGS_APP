package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
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
class DiaryReplayReadingUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun `map inspection stays readable while playback advances and return follows the current event`() {
        val read=explorerPanelPreviewRead()
        val timeline=diaryReplayTimeline(read)
        val selectedScene=read.diary!!.scenes[2]
        lateinit var state: WalkRouteExplorerState
        lateinit var inspection: DiaryReplayInspection
        compose.setContent { DaengsTheme {
            val scope=rememberCoroutineScope()
            state=remember { WalkRouteExplorerState(scope,0).apply { adopt(read); choosePanel(true); togglePlayback() } }
            inspection=remember { DiaryReplayInspection(scope).apply { adopt(read) } }
            LaunchedEffect(state.seekRevision) { inspection.clear() }
            WalkDiaryMapContent(read.diary.scenes,null,false,null,{},{},{},{},{},{},
                explorerSelected=true,
                explorerPanel={ WalkRouteExplorerPanel(state,{},
                    replayContent={ if(inspection.active) DiaryReplayInspectionReading(inspection) else DiaryReplayReading(timeline,state) },
                    replayContentKey=if(inspection.active) inspection.markerIds else timeline.current(state.elapsed,state.duration)?.elapsed) },
                map={ Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    androidx.compose.material3.TextButton(onClick={ inspection.selectMarkers(setOf(selectedScene.id)) }) {
                        androidx.compose.material3.Text("지도의 장면 선택")
                    }
                } })
        } }
        val top=compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("지도의 장면 선택").performClick()
        compose.onNodeWithTag("diary-replay-inspection").assertExists()
        compose.onNodeWithText(selectedScene.title).assertIsDisplayed()
        compose.runOnIdle { state.tick(15_000); assertTrue(state.playing); assertEquals(15_000L,state.elapsed) }
        compose.onNodeWithText(selectedScene.title).assertIsDisplayed()
        compose.onNodeWithTag("return-to-replay").performClick()
        compose.onNodeWithText(read.diary.scenes[1].title).assertIsDisplayed()
        compose.runOnIdle { assertTrue(state.playing) }
        assertEquals(top,compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("지도의 장면 선택").performClick()
        compose.onNodeWithTag("explorer-replay-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithTag("diary-replay-inspection").assertDoesNotExist()
        compose.runOnIdle { assertFalse(state.playing) }
    }

    @Test fun `play seek and event navigation synchronize reading without remounting map or moving drawer`() {
        val base=explorerPanelPreviewRead()
        val scenes=base.diary!!.scenes.drop(1).take(2)
        val actions=listOf(WalkEntry("a",scenes[0].sessionId,WalkMomentType.SNIFFING,7_500),
            WalkEntry("b",scenes[0].sessionId,WalkMomentType.BARKING,15_000))
        val read=base.copy(diary=base.diary.copy(scenes=scenes,sourceEntries=actions))
        val timeline=diaryReplayTimeline(read)
        lateinit var state: WalkRouteExplorerState
        var mounts=0
        compose.setContent { DaengsTheme {
            val scope=rememberCoroutineScope()
            state=remember { WalkRouteExplorerState(scope,0).apply { adopt(read); choosePanel(true) } }
            WalkDiaryMapContent(scenes,null,false,null,{},{},{},{},{},{},
                explorerSelected=state.panelOpen,onChooseExplorer=state::choosePanel,
                explorerPanel={ WalkRouteExplorerPanel(state,{},
                    replayContent={ DiaryReplayReading(timeline,state) },
                    replayContentKey=timeline.at(state.elapsed)?.elapsed) },
                map={ DisposableEffect(Unit) { mounts++; onDispose {} }; Box(Modifier.fillMaxSize()) })
        } }
        val top=compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("동선 재생").performClick()
        compose.onNodeWithText("산책 시작").assertIsDisplayed()
        compose.runOnIdle { state.tick(7_500) }
        compose.onNodeWithText("킁킁").assertIsDisplayed()
        compose.onNodeWithTag("replay-next-event").performClick()
        compose.runOnIdle { assertFalse(state.playing); assertEquals(15_000L,state.elapsed) }
        compose.onNodeWithText(scenes[0].title).assertIsDisplayed()
        compose.onNodeWithText("짖기").assertExists()
        compose.onNodeWithTag("replay-previous-event").performClick()
        compose.onNodeWithText("킁킁").assertIsDisplayed()
        compose.onNodeWithTag("explorer-replay-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(30_000f) }
        compose.onNodeWithText(scenes[1].title).assertIsDisplayed()
        compose.onNodeWithText("킁킁").assertDoesNotExist()
        compose.onNodeWithTag("explorer-replay-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText("산책 시작").assertIsDisplayed()
        compose.onNodeWithText(scenes[1].title).assertDoesNotExist()
        compose.onNodeWithTag("explorer-replay-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(state.duration.toFloat()) }
        compose.onNodeWithText("산책 끝").assertIsDisplayed()
        assertEquals(top,compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        assertEquals(1,mounts)
        compose.onNodeWithText("장면 2").performClick()
        compose.onNodeWithTag("diary-replay-reading").assertDoesNotExist()
        compose.runOnIdle { assertFalse(state.playing) }
    }
}
