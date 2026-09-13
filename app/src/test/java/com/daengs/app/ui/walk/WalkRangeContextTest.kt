package com.daengs.app.ui.walk

import android.app.Application
import com.daengs.app.walk.routeexplorer.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkRangeContextTest {
    @Test fun `scene and another scene retain their originating range while the explorer tab returns paused`() = runTest {
        val original = explorationRead()
        val first = original.diary!!.scenes.single()
        val second = first.copy(id = "measured/other", title = "다음 장면")
        val read = original.copy(diary = original.diary.copy(scenes = listOf(first, second)),
            sceneFocus = original.sceneFocus + (second.id to original.route.review.recordSceneFocus(second)))
        val state = WalkRouteExplorerState(this, 0).apply { adopt(read) }
        state.selectTimeRange(12_000, 65_000)
        val range = state.timeRange
        state.choosePlaybackSpeed(RoutePlaybackSpeed.FOUR)
        state.selectScene(first.id, fromMap = true)
        assertEquals(range, state.returnRange); assertNull(state.selectedSlice)
        assertTrue(state.selectionFromMap); assertFalse(state.panelOpen)
        state.selectScene(second.id)
        assertEquals(range, state.returnRange); assertFalse(state.selectionFromMap)
        state.choosePanel(true)
        assertEquals(range, state.selection); assertTrue(state.panelOpen); assertFalse(state.playing)
        assertEquals(RoutePlaybackSpeed.FOUR, state.playbackSpeed)
        state.selectScene(first.id); state.closeScene()
        assertEquals(WalkRouteSelection.Overview, state.selection) // Explicit scene-list return still means list.
    }

    @Test fun `range replay clamps seeking skips unproven positions and stops before the rest of the walk`() = runTest {
        val read = explorationRead(); val state = WalkRouteExplorerState(this, 0).apply { adopt(read) }
        state.selectTimeRange(12_000, 65_000); val range = state.timeRange
        val highlight = state.highlightPaths
        state.seek(-1); assertEquals(12_000L, state.elapsed)
        state.togglePlayback(); state.tick(3_000)
        assertEquals(15_000L, state.elapsed); assertFalse(state.replayFrame!!.inGap)
        state.tick(4_000)
        assertTrue(state.elapsed > 19_000); assertTrue(state.elapsed <= 65_000)
        assertFalse(state.replayFrame!!.inGap); assertEquals(range, state.timeRange)
        assertEquals(highlight, state.highlightPaths)
        val layer = recordPresentationLayer(state, read.route.detail, null)
        assertFalse(layer.useOverviewDirections); assertTrue(layer.observedParts.any { it.selected })
        state.choosePlaybackSpeed(RoutePlaybackSpeed.SIXTEEN); state.tick(Long.MAX_VALUE)
        assertEquals(65_000L, state.elapsed); assertFalse(state.playing)
        state.seek(Long.MAX_VALUE); assertEquals(65_000L, state.elapsed)
        state.togglePlayback(); assertEquals(12_000L, state.elapsed)
        state.selectScene(read.diary!!.scenes.single().id)
        assertEquals(range, state.returnRange); assertFalse(state.playing)
        assertTrue(state.returnToRange()); assertEquals(range, state.selection)
        assertEquals(123.0, read.route.detail.summary.distanceMeters, 0.0)
    }

    @Test fun `gap-only range cannot replay and a trailing gap stops with no invented cursor`() = runTest {
        val read = explorationRead(); val state = WalkRouteExplorerState(this, 0).apply { adopt(read) }
        state.selectTimeRange(30_000, 40_000)
        assertFalse(state.canPlayback); state.togglePlayback()
        assertFalse(state.playing); assertEquals(RouteExplorerMode.SLICE, state.mode)
        state.seek(35_000); assertNull(state.replayFrame!!.point)
        state.togglePlayback(); assertFalse(state.playing)
        state.selectTimeRange(12_000, 30_000); state.togglePlayback(); state.tick(Long.MAX_VALUE)
        assertEquals(30_000L, state.elapsed); assertFalse(state.playing); assertNull(state.replayFrame!!.point)
        state.overview(); assertNull(state.timeRange); assertTrue(state.canPlayback)
    }

    @Test fun `same source refresh retains range but changed source or changed scene event invalidates its return`() = runTest {
        val read = explorationRead(); val state = WalkRouteExplorerState(this, 0).apply { adopt(read) }
        state.selectTimeRange(12_000, 65_000); val range = state.timeRange
        state.selectScene(read.diary!!.scenes.single().id)
        val edited = explorationRead(read.route.detail.copy(), "새 제목")
        state.adopt(edited); assertEquals(range, state.returnRange)
        val changedScene = edited.diary!!.scenes.single().copy(atMillis = 77_000)
        state.adopt(edited.copy(diary = edited.diary.copy(scenes = listOf(changedScene)),
            sceneFocus = mapOf(changedScene.id to edited.route.review.recordSceneFocus(changedScene))))
        assertNull(state.returnRange); assertEquals(changedScene.id, state.selectedSceneId)
        state.selectTimeRange(12_000, 65_000); state.selectScene(changedScene.id)
        val changed = explorationRead(read.route.detail.copy(measurement = read.route.detail.measurement!!.copy(id = "changed")))
        state.adopt(changed); assertNull(state.returnRange); assertEquals(changedScene.id, state.selectedSceneId)
        state.selectTimeRange(12_000, 65_000); state.seek(57_000)
        state.adopt(explorationRead(changed.route.detail.copy())); assertNotNull(state.timeRange)
        state.adopt(read); assertEquals(WalkRouteSelection.Overview, state.selection)
        state.selectTimeRange(12_000, 65_000); state.selectScene(changedScene.id)
        state.adopt(read.copy(diary = read.diary.copy(scenes = emptyList())))
        assertEquals(WalkRouteSelection.Overview, state.selection); assertNull(state.returnRange)
    }

    @Test fun `scene neighborhood uses the current event clock and rejects stale or unaddressable scenes`() = runTest {
        val read = explorationRead(measuredTimedDetail(clockCorrection = true))
        val state = WalkRouteExplorerState(this, 0).apply { adopt(read) }
        val scene = read.diary!!.scenes.single()
        assertTrue(state.selectSceneNeighborhood(read, scene, 10_000, 5_000))
        assertEquals(48_000L, state.selectedSlice!!.from); assertEquals(63_000L, state.selectedSlice!!.until)
        assertTrue(state.selectSceneNeighborhood(read, scene, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0L, state.selectedSlice!!.from); assertEquals(100_000L, state.selectedSlice!!.until)
        assertFalse(state.selectSceneNeighborhood(read, scene, -1, 1))
        val next = explorationRead(read.route.detail, "다른 제목"); state.adopt(next)
        assertFalse(state.selectSceneNeighborhood(read, scene))
        val noPoint = scene.copy(source = null, point = null)
        val unknown = read.copy(diary = read.diary.copy(scenes = listOf(noPoint)), sceneFocus = emptyMap())
        state.adopt(unknown); assertFalse(state.selectSceneNeighborhood(unknown, noPoint))
    }

    @Test fun `v2 scene return and bounded cursor round trip paused with explorer scroll`() = runTest {
        val read = explorationRead(); val state = WalkRouteExplorerState(this, 0).apply { adopt(read) }
        state.selectTimeRange(12_000, 65_000)
        state.selectScene(read.diary!!.scenes.single().id)
        val range = state.returnRange
        val reading = JSONObject().put("explorerOffset", 230).put("body", JSONObject().put("offset", 70))
        val payload = WalkExplorationBookmark.encode("owner", read, state, reading)!!
        assertEquals(2, JSONObject(payload).getInt("version"))
        val edited = explorationRead(read.route.detail.copy(), "바뀐 본문 제목")
        val restored = WalkExplorationBookmark.decode(payload, "owner", edited)!!
        assertEquals(range, (restored.selection as WalkRouteSelection.Scene).returnRange)
        assertEquals(230, restored.reading.getInt("explorerOffset")); assertFalse(restored.reading.has("body"))
        state.returnToRange(); state.seek(57_000); state.togglePlayback()
        val savedReplay = WalkExplorationBookmark.encode("owner", read, state)!!
        val replay = WalkExplorationBookmark.decode(savedReplay, "owner", edited)!!
        val next = WalkRouteExplorerState(this, 0).apply { adopt(edited); restoreSelection(replay.selection, replay.panel, replay.speed) }
        assertEquals(range, next.timeRange); assertEquals(57_000L, next.elapsed); assertFalse(next.playing)
        next.togglePlayback(); next.tick(Long.MAX_VALUE); assertEquals(65_000L, next.elapsed)
    }

    @Test fun `old v1 remains readable and corrupt range clocks or outside cursors are rejected`() = runTest {
        val read = explorationRead(); val state = WalkRouteExplorerState(this, 0).apply { adopt(read) }
        state.seek(57_000)
        val v1 = JSONObject(WalkExplorationBookmark.encode("owner", read, state)!!).put("version", 1)
        val old = WalkExplorationBookmark.decode(v1.toString(), "owner", read)!!
        assertEquals(WalkRouteSelection.Replay(57_000), old.selection)
        state.selectTimeRange(12_000, 65_000); state.seek(57_000)
        val payload = WalkExplorationBookmark.encode("owner", read, state)!!
        assertNull(WalkExplorationBookmark.decode(JSONObject(payload).put("version", 1).toString(), "owner", read))
        val invalid = JSONObject(payload)
        invalid.getJSONObject("selection").getJSONObject("range").getJSONObject("from").put("clock", "foreign")
        assertNull(WalkExplorationBookmark.decode(invalid.toString(), "owner", read))
        val outside = JSONObject(payload)
        outside.getJSONObject("selection").getJSONObject("at").put("nanos", 70_000_000_000L)
        assertNull(WalkExplorationBookmark.decode(outside.toString(), "owner", read))
        assertNull(WalkExplorationBookmark.decode(payload, "other", read))
        assertNull(WalkExplorationBookmark.decode(JSONObject(payload).put("version", 99).toString(), "owner", read))
        state.selectScene(read.diary!!.scenes.single().id)
        val scenePayload = WalkExplorationBookmark.encode("owner", read, state)!!
        assertNull(WalkExplorationBookmark.decode(scenePayload, "owner", read.copy(diary = read.diary.copy(scenes = emptyList()))))
        val broken = JSONObject(scenePayload)
        broken.getJSONObject("selection").getJSONObject("returnRange").getJSONObject("until").put("nanos", 0)
        assertNull(WalkExplorationBookmark.decode(broken.toString(), "owner", read))
    }
}
