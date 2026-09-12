package com.daengs.app.ui.walk

import com.daengs.app.map.layers.completedroute.*
import com.daengs.app.walk.*
import com.daengs.app.walk.routeexplorer.*
import com.daengs.app.walk.trajectory.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecordContextPresentationTest {
    private fun read() = readCompletedRoute(RecordedSession("context-ui", startedAtMillis = 0, endedAtMillis = 100_000),
        (0..17).map { i -> RecordedFix(i, if (i < 9) 0 else 1,
            if (i < 9) 10_000 + i * 2_000L else 60_000 + (i - 9) * 2_000L,
            0.0, i * 4.0 / 111_195, 1f, false) })
    @Test fun `guide selection is exclusive and never adds a route direction or cursor`() = runTest {
        val detail = read(); val review = CompletedRouteReview(detail)
        val state = WalkRouteExplorerState(this, detail.summary.activeDurationMillis)
        state.replaceRoute(RouteExplorerIndex(detail.route), review, detail.summary.activeDurationMillis)
        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        assertNull(recordPresentationLayer(state, detail, null).recordContext!!.selectedGapGuide)
        state.selectContext(gap.id)
        val selected = recordPresentationLayer(state, detail, null)
        assertEquals(gap.id, selected.recordContext!!.selectedGapGuide!!.contextId)
        assertTrue(selected.highlightPaths.isEmpty()); assertTrue(selected.observedDirectionEdges.isEmpty())
        assertTrue(selected.directionPaths(detail.route.bounds.let(::listOf)).isEmpty()); assertNull(selected.cursor)
        state.closeScene(); assertEquals(gap, state.selectedContext)
        state.selectScene("s"); assertNull(recordPresentationLayer(state, detail, null).recordContext!!.selectedGapGuide)
        state.selectContext(gap.id); state.seek(40_000)
        assertNull(recordPresentationLayer(state, detail, null).recordContext!!.selectedGapGuide); assertNull(state.replayFrame!!.point)
        state.selectContext(gap.id); state.choosePanel(false); assertNull(state.selectedContext)
        state.selectContext(gap.id); state.replaceRoute(RouteExplorerIndex(detail.route), review, 0); assertNull(state.selectedContext)
        assertNull(recordPresentationLayer(state, detail.copy(), null).recordContext)
    }
    @Test fun `missing endpoint and event selections cannot produce a relation line`() {
        val review = CompletedRouteReview(read()); val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        assertNull(recordContextLayer(review, gap.copy(after = null))!!.selectedGapGuide)
        assertNull(recordContextLayer(review, review.context.contexts.last())!!.selectedGapGuide)
        assertTrue(recordContextDescription(gap.copy(after = null)).contains("양 끝 위치"))
        assertTrue(recordContextTime(gap.copy(durationMillis = null)).contains("미확정"))
    }
    @Test fun `coincident start and end share one stamp but keep both event addresses`() {
        val detail = readCompletedRoute(RecordedSession("same-point", startedAtMillis = 0, endedAtMillis = 30_000),
            (0..8).map { RecordedFix(it, 0, 10_000 + it * 1_000L, 0.0, 0.0, 1f, false) })
        val review = CompletedRouteReview(detail)
        val start = review.context.contexts.first(); val end = review.context.contexts.last()
        assertNotEquals(start.id, end.id)
        assertEquals(RouteEndpointKind.START_END, recordContextLayer(review, null)!!.endpoints.single().kind)
        assertEquals(start.id, recordContextLayer(review, start)!!.endpoints.single().id)
        assertEquals(end.id, recordContextLayer(review, end)!!.endpoints.single().id)
    }

    @Test fun `gap at the last location keeps the same marker and hit target across selections`() {
        val detail = readCompletedRoute(RecordedSession("gap-end", startedAtMillis = 0, endedAtMillis = 100_000),
            (0..17).map { i -> RecordedFix(i, if (i < 9) 0 else 1,
                if (i < 9) 10_000 + i * 2_000L else 60_000 + (i - 9) * 2_000L,
                0.0, minOf(i, 9) * 4.0 / 111_195, 1f, false) })
        val review = CompletedRouteReview(detail)
        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        val end = review.context.contexts.last { it.kind == RecordContextKind.END }
        val normal = recordContextLayer(review, null)!!
        val selected = recordContextLayer(review, gap)!!
        val eventSelected = recordContextLayer(review, end)!!
        assertEquals(2, normal.markers.count { it.gapBoundary })
        assertTrue(normal.markers.filter { it.gapBoundary }.all { it.label == "–" })
        assertEquals(normal.markers, selected.markers.map { it.copy(selected = false) })
        assertEquals(normal.markers, eventSelected.markers)
        assertEquals(normal.endpoints, selected.endpoints)
        assertEquals(normal.endpoints, eventSelected.endpoints)
        assertTrue(normal.endpoints.none { it.id == end.id })
        assertEquals(end, review.context.context(end.id)) // Event access stays in the list.
        val scene = com.daengs.app.walk.diary.DiaryScene("seventh", detail.summary.sessionId, 70_000,
            "일곱 번째 장면", "", gap.after!!.point, "")
        assertTrue(diarySceneMarkers(listOf(scene), null).single().aboveRouteEndpoints)
        assertEquals(diarySceneMarkers(listOf(scene), null).single().point,
            diarySceneMarkers(listOf(scene), scene.id).single().point)
    }
}
