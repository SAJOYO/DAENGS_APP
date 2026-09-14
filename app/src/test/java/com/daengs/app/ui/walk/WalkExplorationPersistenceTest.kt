package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.detail.WalkDiaryReadView

import android.app.Application
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.*
import androidx.compose.material3.Text
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.WalkDetailSource
import com.daengs.app.walk.routeexplorer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkExplorationPersistenceTest {
    @get:Rule val compose = createComposeRule()
    private val view = explorationRead()
    private class Source(val read: WalkDiaryReadView) : WalkDetailSource {
        var payload: String? = null
        var gate: CompletableDeferred<Unit>? = null
        var current = true
        var writes = 0
        override val changes = flowOf(Unit)
        override val entries = flowOf(emptyList<WalkEntry>())
        override fun isCurrentAccount() = current
        override suspend fun load() = read.route.detail
        override fun observeDiary(detail: WalkSessionDetail) = flowOf(read.diary)
        override suspend fun loadExploration(): String? { gate?.await(); return payload }
        override suspend fun saveExploration(payload: String) { if (current) { this.payload = payload; writes++ } }
    }
    private fun savedReplay(): String = runBlocking {
        val state = WalkRouteExplorerState(this, 0); state.adopt(view)
        state.seek(57_000); state.choosePlaybackSpeed(RoutePlaybackSpeed.EIGHT)
        WalkExplorationBookmark.encode("owner", view, state, JSONObject().put("drawer", "Compact"))!!
    }
    @Test fun `scene body scroll and drawer survive leaving the real reading layout`() {
        val scene = view.diary!!.scenes.single().copy(body = "함께 걷다가 잠시 쉬었다가 다시 걸었어요.\n".repeat(120))
        val read = view.copy(diary = view.diary.copy(scenes = listOf(scene)),
            sceneFocus = mapOf(scene.id to view.route.review.recordSceneFocus(scene)))
        val source = Source(read).apply { payload = runBlocking {
            val state = WalkRouteExplorerState(this, 0); state.adopt(read); state.selectScene(scene.id)
            WalkExplorationBookmark.encode("owner", read, state, JSONObject().put("drawer", "Expanded"))
        } }
        var mounted by mutableStateOf(true)
        lateinit var reading: DiaryReadingMemory
        compose.setContent {
            if (mounted) {
                val scope = rememberCoroutineScope()
                val state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read) } }
                reading = rememberDiaryReadingMemory()
                RememberWalkExplorationPersistence(source, "owner", read, state, reading)
                DaengsTheme { WalkDiaryMapContent(listOf(scene), scene.takeIf { state.selectedSceneId == it.id }, false, null,
                    onSelect = { state.selectScene(it.id) }, onClose = state::closeScene, onEdit = {}, onPhoto = {},
                    onRetry = {}, onAdd = {}, map = {}, explorerPanel = { Text("동선 탐색") }, readingMemory = reading) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("diary-scene-body").performTouchInput { swipeUp() }
        compose.waitForIdle()
        var position = 0 to 0
        compose.runOnIdle {
            position = reading.bodyIndex to reading.bodyOffset
            assertTrue(position.first > 0 || position.second > 0)
            mounted = false
        }
        compose.waitForIdle()
        compose.runOnIdle { mounted = true }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(DiaryDrawerValue.Expanded, reading.drawer.targetValue)
            assertEquals(position, reading.bodyIndex to reading.bodyOffset)
        }
    }
    @Test fun `saved composition state does not suppress durable cursor restoration`() {
        val source = Source(view).apply { payload = savedReplay() }
        val restoration = StateRestorationTester(compose)
        lateinit var state: WalkRouteExplorerState
        restoration.setContent {
            state = rememberWalkRouteExplorer("measured", null)
            SideEffect { state.adopt(view) }
            RememberWalkExplorationPersistence(source, "owner", view, state, rememberDiaryReadingMemory())
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(57_000L, state.elapsed) }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(57_000L, state.elapsed); assertFalse(state.playing) }
    }
    @Test fun `recreated detail restores a paused cursor and speed then flushes the last position on exit`() {
        val source = Source(view).apply { payload = savedReplay() }
        var mounted by mutableStateOf(true)
        lateinit var state: WalkRouteExplorerState
        lateinit var reading: DiaryReadingMemory
        compose.setContent {
            if (mounted) {
                val scope = rememberCoroutineScope()
                state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(view) } }
                reading = rememberDiaryReadingMemory()
                RememberWalkExplorationPersistence(source, "owner", view, state, reading)
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(57_000L, state.elapsed); assertFalse(state.playing)
            assertEquals(RoutePlaybackSpeed.EIGHT, state.playbackSpeed)
            assertEquals(DiaryDrawerValue.Compact, reading.drawer.targetValue)
            assertNotNull(state.replayFrame!!.point)
            state.seek(65_000); mounted = false
        }
        compose.waitForIdle()
        compose.runOnIdle { mounted = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(65_000L, state.elapsed); assertFalse(state.playing) }
    }
    @Test fun `late checkpoint cannot overwrite a newer time selection or drawer gesture`() {
        val gate = CompletableDeferred<Unit>()
        val source = Source(view).apply { payload = savedReplay(); this.gate = gate }
        lateinit var state: WalkRouteExplorerState
        lateinit var reading: DiaryReadingMemory
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(view) } }
            reading = rememberDiaryReadingMemory()
            RememberWalkExplorationPersistence(source, "owner", view, state, reading)
        }
        compose.runOnIdle { state.selectTimeRange(10_000, 22_000) }
        runBlocking { reading.drawer.drag.snapTo(DiaryDrawerValue.Expanded) }
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(RouteExplorerMode.SLICE, state.mode)
            assertEquals(10_000L, state.selectedSlice!!.from)
            assertEquals(DiaryDrawerValue.Expanded, reading.drawer.targetValue)
            assertEquals(RoutePlaybackSpeed.ONE, state.playbackSpeed)
        }
    }
    @Test fun `mismatched measurement waits without destroying the saved address or changing the camera`() {
        val source = Source(view).apply { payload = savedReplay() }
        val oldPayload = source.payload
        var current by mutableStateOf(explorationRead(view.route.detail.copy(measurement = view.route.detail.measurement!!.copy(id = "other"))))
        lateinit var state: WalkRouteExplorerState
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(current) } }
            SideEffect { state.adopt(current) }
            RememberWalkExplorationPersistence(source, "owner", current, state, rememberDiaryReadingMemory())
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(RouteExplorerMode.OVERVIEW, state.mode)
            assertEquals(oldPayload, source.payload); assertEquals(0, source.writes)
            current = view
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(57_000L, state.elapsed) }
    }
}
