package com.daengs.app.ui.walk

import android.app.Application
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryWalk
import com.daengs.app.walk.routeexplorer.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal fun explorationRead(detail: WalkSessionDetail = measuredTimedDetail(), title: String = "장면"): WalkDiaryReadView {
    val route = PreparedDiaryRoute(detail); val scene = measuredScene(detail, 9, title)
    return WalkDiaryReadView(route, DiaryWalk(detail.summary, listOf(scene), ""), mapOf(scene.id to route.review.recordSceneFocus(scene)))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkExplorationBookmarkTest {
    @Test fun `scene section observed gap time slice and replay reopen against a new prepared read`() = runTest {
        val view = explorationRead(); val state = WalkRouteExplorerState(this, 0); state.adopt(view)
        val reopened = explorationRead(view.route.detail.copy())
        val selections = listOf<() -> Unit>(
            { state.selectScene(view.diary!!.scenes.single().id) }, { state.selectSection(0) },
            { state.selectAuxiliary(view.route.review.observed.sections.first().id) },
            { state.selectContext(view.route.review.context.contexts.first { it.kind == com.daengs.app.walk.trajectory.RecordContextKind.GAP }.id) },
            { state.selectTimeRange(12_000, 60_000) }, { state.seek(57_000); state.togglePlayback() })
        for (select in selections) {
            select(); state.choosePlaybackSpeed(RoutePlaybackSpeed.EIGHT)
            val payload = WalkExplorationBookmark.encode("owner", view, state)!!
            assertTrue(payload.toByteArray().size < 2_000)
            val decoded = WalkExplorationBookmark.decode(payload, "owner", reopened)!!
            val next = WalkRouteExplorerState(this, 0); next.adopt(reopened)
            next.restoreSelection(decoded.selection, decoded.panel, decoded.speed)
            assertEquals(state.selection, next.selection)
            assertEquals(RoutePlaybackSpeed.EIGHT, next.playbackSpeed); assertFalse(next.playing)
            assertEquals(state.replayFrame, next.replayFrame)
        }
    }
    @Test fun `foreign owner changed measurement removed scene and invalid clock never restore`() = runTest {
        val view = explorationRead(); val state = WalkRouteExplorerState(this, 0); state.adopt(view)
        state.selectScene(view.diary!!.scenes.single().id)
        val payload = WalkExplorationBookmark.encode("owner", view, state)!!
        assertNull(WalkExplorationBookmark.decode(payload, "other", view))
        assertNull(WalkExplorationBookmark.decode(payload, "owner", view.copy(diary = view.diary.copy(scenes = emptyList()))))
        val detail = view.route.detail.copy(measurement = view.route.detail.measurement!!.copy(id = "new"))
        assertNull(WalkExplorationBookmark.decode(payload, "owner", explorationRead(detail)))
        state.seek(57_000)
        val altered = JSONObject(WalkExplorationBookmark.encode("owner", view, state)!!)
        altered.getJSONObject("selection").getJSONObject("at").put("clock", "old-boot")
        assertNull(WalkExplorationBookmark.decode(altered.toString(), "owner", view))
        assertNull(WalkExplorationBookmark.decode("broken", "owner", view))
    }
    @Test fun `body edit retains event selection while discarding old text scroll offset`() = runTest {
        val view = explorationRead(); val state = WalkRouteExplorerState(this, 0); state.adopt(view)
        state.selectScene(view.diary!!.scenes.single().id)
        val reading = JSONObject().put("body", JSONObject().put("index", 3).put("offset", 42))
        val payload = WalkExplorationBookmark.encode("owner", view, state, reading)!!
        val edited = explorationRead(view.route.detail, "고친 제목")
        val restored = WalkExplorationBookmark.decode(payload, "owner", edited)!!
        assertEquals(state.selection, restored.selection); assertFalse(restored.reading.has("body"))
    }
}
