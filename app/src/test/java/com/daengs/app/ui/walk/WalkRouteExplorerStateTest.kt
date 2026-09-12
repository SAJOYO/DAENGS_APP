package com.daengs.app.ui.walk

import com.daengs.app.walk.routeexplorer.*
import com.daengs.app.walk.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WalkRouteExplorerStateTest {
    @Test fun `map origin survives route refresh but the next list selection clears it`() = runTest {
        // Use the actual completed reader: handmade route fixtures have no adopted observation evidence.
        val detail = readCompletedRoute(RecordedSession("map-selection", startedAtMillis = 0, endedAtMillis = 100_000),
            (0..17).map { i -> RecordedFix(i, if (i < 9) 0 else 1,
                if (i < 9) 10_000 + i * 2_000L else 60_000 + (i - 9) * 2_000L,
                0.0, i * 4.0 / 111_195, 1f, false) })
        val review = CompletedRouteReview(detail)
        val state = WalkRouteExplorerState(this, 70_000)
        state.replaceRoute(RouteExplorerIndex(detail.route), review, 70_000)
        state.selectScene("scene-7", fromMap = true)
        state.replaceRoute(RouteExplorerIndex(detail.route), review, 70_000)
        assertTrue(state.selectionFromMap)
        val gap = review.context.contexts.first { it.kind == com.daengs.app.walk.trajectory.RecordContextKind.GAP }
        state.selectContext(gap.id, openExplorer = false, fromMap = true)
        assertTrue(state.selectionFromMap)
        assertFalse(state.panelOpen)
        assertEquals(gap.id, state.selectedContext?.id)
        state.selectContext(gap.id, openExplorer = false)
        assertFalse(state.selectionFromMap)
        state.selectScene("scene-7", fromMap = true)
        state.selectScene("scene-7")
        assertFalse(state.selectionFromMap)
    }

    @Test fun `section scene passage and replay share one selection and close cannot clear another mode`() = runTest {
        val detail = reviewDetail(listOf(-10.0 to 10_000L, 0.0 to 20_000L, 10.0 to 30_000L),
            listOf(2_000.0 to 40_000L, 2_010.0 to 50_000L))
        val state = WalkRouteExplorerState(this, 70_000, StandardTestDispatcher(testScheduler))
        state.replaceRoute(RouteExplorerIndex(detail.route), CompletedRouteReview(detail), 70_000)
        state.selectSection(1)
        assertEquals(1, state.selectedSection?.index)
        assertEquals(detail.route.segments[1].points.map { it.point }, state.highlightPaths.single())
        state.closeScene() // Bottom sheet settling after a tab switch is not a new selection.
        assertEquals(RouteExplorerMode.SECTION, state.mode)
        state.selectScene("s/scene")
        assertFalse(state.panelOpen); assertNull(state.selectedSection)
        assertEquals("s/scene", state.selectedSceneId)
        state.seek(30_000)
        assertNull(state.selectedSceneId); assertEquals(RouteExplorerMode.REPLAY, state.mode)
        state.selectSection(0)
        assertNull(state.replayFrame); assertFalse(state.playing)
        state.overview()
        assertTrue(state.highlightPaths.isEmpty())
    }

    @Test fun `late passage work cannot overwrite a scene or a selected section`() = runTest {
        val detail = reviewDetail(straightExplorerPath().mapIndexed { i, p -> p.first to (i * 1_000L) })
        val state = WalkRouteExplorerState(this, 70_000, StandardTestDispatcher(testScheduler))
        state.replaceRoute(RouteExplorerIndex(detail.route), CompletedRouteReview(detail), 70_000)
        state.inspect(reviewPoint(0.0)); state.selectScene("s/one"); advanceUntilIdle()
        assertEquals("s/one", state.selectedSceneId); assertNull(state.passages)
        state.inspect(reviewPoint(0.0)); state.selectSection(0); advanceUntilIdle()
        assertEquals(0, state.selectedSection?.index); assertNull(state.passages)
        assertFalse(state.analyzing)
    }

    @Test fun `replacing a route retains only scene identity and rebuilds correspondence from latest evidence`() = runTest {
        val detail = reviewDetail(listOf(0.0 to 10_000L, 10.0 to 20_000L))
        val state = WalkRouteExplorerState(this, 70_000, StandardTestDispatcher(testScheduler))
        state.replaceRoute(RouteExplorerIndex(detail.route), CompletedRouteReview(detail), 70_000)
        state.selectScene("same-scene")
        val revision1 = reviewScene(15_000, 5.0)
        val revision2 = revision1.copy(atMillis = 60_000)
        assertFalse(state.review!!.sceneFocus(revision1).paths.isEmpty())
        assertTrue(state.review!!.sceneFocus(revision2).paths.isEmpty())
        val changed = reviewDetail(listOf(100.0 to 10_000L, 110.0 to 20_000L))
        state.replaceRoute(RouteExplorerIndex(changed.route), CompletedRouteReview(changed), 70_000)
        assertEquals("same-scene", state.selectedSceneId)
        assertTrue(state.review!!.sceneFocus(revision1).paths.isEmpty())
        state.selectSection(0)
        state.replaceRoute(RouteExplorerIndex(detail.route), CompletedRouteReview(detail), 70_000)
        assertEquals(RouteExplorerMode.OVERVIEW, state.mode)
    }

    @Test fun `switching panels and editing pause playback and reaching the end never loops`() = runTest {
        val state = WalkRouteExplorerState(this, 20_000, StandardTestDispatcher(testScheduler))
        state.index = RouteExplorerIndex(explorerRoute(straightExplorerPath()))
        state.choosePanel(true); state.togglePlayback(); state.tick(1_000)
        assertEquals(1_000L, state.elapsed)
        state.pause(); state.tick(1_000)
        assertEquals(1_000L, state.elapsed)
        state.togglePlayback(); state.choosePanel(false)
        assertFalse(state.playing)
        assertEquals(RouteExplorerMode.OVERVIEW, state.mode)
        state.choosePanel(true); state.seek(19_500); state.togglePlayback(); state.tick(1_000)
        assertEquals(20_000L, state.elapsed)
        assertFalse(state.playing)
    }
    @Test fun `pass selection and replay are exclusive and a stale selection cannot overwrite replay`() = runTest {
        val state = WalkRouteExplorerState(this, 20_000, StandardTestDispatcher(testScheduler))
        state.index = RouteExplorerIndex(explorerRoute(straightExplorerPath()))
        state.inspect(explorerPoint(0.0)); advanceUntilIdle()
        assertEquals(1, state.passages!!.passes.size)
        assertNotNull(state.selectedPass)
        state.seek(1_000)
        assertNull(state.selectedPass)
        assertEquals(RouteExplorerMode.REPLAY, state.mode)
        state.inspect(explorerPoint(0.0)); state.seek(2_000); advanceUntilIdle()
        assertEquals(RouteExplorerMode.REPLAY, state.mode)
        assertEquals(2_000L, state.elapsed)
        assertNull(state.passages)
        assertFalse(state.analyzing)
    }
}
