package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.detail.WalkDiaryReadView

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.WalkDetailSource
import com.daengs.app.walk.routeexplorer.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
class WalkRangeContextUiTest {
    @get:Rule val compose = createComposeRule()
    private class Source(val read: WalkDiaryReadView) : WalkDetailSource {
        var payload: String? = null
        override val changes = flowOf(Unit)
        override val entries = flowOf(emptyList<WalkEntry>())
        override fun isCurrentAccount() = true
        override suspend fun load() = read.route.detail
        override fun observeDiary(detail: WalkSessionDetail) = flowOf(read.diary)
        override suspend fun loadExploration() = payload
        override suspend fun saveExploration(payload: String) { this.payload = payload }
    }

    @Test fun `range scene tab round trip and remount restore the explorer reading position without moving the drawer`() {
        val original = explorationRead()
        val scenes = (1..16).map { original.diary!!.scenes.single().copy(id = "measured/scene-$it", title = "관련 장면 $it") }
        val read = original.copy(diary = original.diary!!.copy(scenes = scenes),
            sceneFocus = scenes.associate { it.id to original.route.review.recordSceneFocus(it) })
        val source = Source(read).apply { payload = runBlocking {
            val initial = WalkRouteExplorerState(this, 0).apply { adopt(read); selectTimeRange(12_000, 65_000) }
            WalkExplorationBookmark.encode("owner", read, initial)
        } }
        var mounted by mutableStateOf(true)
        lateinit var state: WalkRouteExplorerState
        lateinit var reading: DiaryReadingMemory
        compose.setContent {
            if (mounted) {
                val scope = rememberCoroutineScope()
                state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read) } }
                reading = rememberDiaryReadingMemory()
                RememberWalkExplorationPersistence(source, "owner", read, state, reading)
                DaengsTheme { WalkDiaryMapContent(scenes, scenes.firstOrNull { it.id == state.selectedSceneId }, false, null,
                    onSelect = { state.selectScene(it.id) }, onClose = state::closeScene, onEdit = {}, onPhoto = {}, onRetry = {}, onAdd = {},
                    explorerSelected = state.panelOpen, onChooseExplorer = state::choosePanel, readingMemory = reading,
                    explorerPanel = { WalkRouteExplorerPanel(state, {}, reading = reading) },
                    map = {}) }
            }
        }
        compose.waitForIdle()
        val top = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("관련 장면 12", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("경로 정보 · 구간과 전후 관계").performScrollTo().performClick()
        compose.onNodeWithText("동선 1", substring = true).performScrollTo()
        var offset = 0
        compose.runOnIdle { offset = reading.explorer.value; assertTrue(offset > 0) }
        compose.onNodeWithText("장면 16").performClick()
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText("관련 장면 12"))
        compose.onNodeWithText("관련 장면 12", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(scenes[11].id, state.selectedSceneId); assertNotNull(state.returnRange) }
        compose.onNodeWithText("동선 탐색").performClick()
        compose.runOnIdle {
            assertEquals(12_000L, state.selectedSlice!!.from); assertEquals(65_000L, state.selectedSlice!!.until)
            assertEquals(offset, reading.explorer.value); assertFalse(state.playing)
        }
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("장면 16").performClick()
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText("관련 장면 12"))
        compose.onNodeWithText("관련 장면 12", useUnmergedTree = true).performClick()
        compose.runOnIdle { mounted = false }
        compose.waitForIdle()
        compose.runOnIdle { mounted = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(scenes[11].id, state.selectedSceneId); assertNotNull(state.returnRange) }
        compose.onNodeWithText("동선 탐색").performClick()
        compose.runOnIdle { assertEquals(offset, reading.explorer.value); assertEquals(65_000L, state.selectedSlice!!.until) }
        compose.onNodeWithText("관련 장면 12", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("동선 1", substring = true).assertIsDisplayed()
    }

    @Test fun `bounded playback restores paused across a new composition and gap-only control explains why it is disabled`() {
        val read = explorationRead(); val source = Source(read).apply { payload = runBlocking {
            val initial = WalkRouteExplorerState(this, 0).apply {
                adopt(read); selectTimeRange(12_000, 65_000); seek(57_000); choosePlaybackSpeed(RoutePlaybackSpeed.EIGHT)
            }
            WalkExplorationBookmark.encode("owner", read, initial)
        } }
        var mounted by mutableStateOf(true)
        lateinit var state: WalkRouteExplorerState
        compose.setContent {
            if (mounted) {
                val scope = rememberCoroutineScope()
                state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read) } }
                val reading = rememberDiaryReadingMemory()
                RememberWalkExplorationPersistence(source, "owner", read, state, reading)
                DaengsTheme { WalkRouteExplorerPanel(state, {}, reading = reading) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(57_000L, state.elapsed); assertEquals(65_000L, state.selectedSlice!!.until); assertFalse(state.playing) }
        compose.onNodeWithText("동선 재생").assertIsDisplayed().performClick()
        compose.runOnIdle { state.tick(500); assertEquals(62_000L, state.elapsed); mounted = false }
        compose.waitForIdle()
        compose.runOnIdle { mounted = true }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(62_000L, state.elapsed); assertEquals(65_000L, state.selectedSlice!!.until)
            assertFalse(state.playing); assertEquals(RoutePlaybackSpeed.EIGHT, state.playbackSpeed)
            state.selectTimeRange(30_000, 40_000)
        }
        compose.onNodeWithText("동선 재생").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("선택 범위에 재생할 이동 근거가 없어요.").performScrollTo().assertIsDisplayed()
    }
}
