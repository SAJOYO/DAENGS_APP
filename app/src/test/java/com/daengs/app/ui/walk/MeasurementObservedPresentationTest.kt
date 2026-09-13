package com.daengs.app.ui.walk

import com.daengs.app.map.layers.completedroute.*
import com.daengs.app.walk.routeexplorer.*
import com.daengs.app.walk.trajectory.RecordContextKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MeasurementObservedPresentationTest {
    @Test fun `ordinary detail adapter shows measured roles then selects scene and gap without mixing layers`() = runTest {
        val detail = measuredObservedDetail(); val review = CompletedRouteReview(detail)
        val state = WalkRouteExplorerState(this, detail.summary.activeDurationMillis)
        state.replaceRoute(RouteExplorerIndex(detail.route), review, detail.summary.activeDurationMillis)
        val overview = recordPresentationLayer(state, detail, null)
        assertEquals(5, overview.observedParts.size)
        assertEquals(setOf(RecordRouteRole.OBSERVED_EXCLUDED, RecordRouteRole.OBSERVED_UNRESOLVED),
            overview.observedParts.map { it.role }.toSet())
        assertTrue(overview.observedDirectionEdges.isNotEmpty())
        assertNull(overview.recordContext!!.selectedGapGuide)

        val scene = measuredScene(detail, 3); val focus = review.recordSceneFocus(scene)
        state.selectScene(scene.id, fromMap = true)
        val selected = recordPresentationLayer(state, detail, focus)
        val part = selected.observedParts.single { it.selected }
        assertEquals(focus.binding!!.sectionId, part.id)
        assertEquals(RecordRouteRole.OBSERVED_EXCLUDED, part.role)
        assertTrue(selected.highlightPaths.isEmpty())
        assertFalse(selected.useOverviewDirections)
        assertTrue(state.selectionFromMap)
        assertTrue(sceneRouteNotice(focus).contains("보행거리에는 포함되지"))

        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        state.selectContext(gap.id, openExplorer = false, fromMap = true)
        val gapLayer = recordPresentationLayer(state, detail, null)
        assertEquals(gap.id, gapLayer.recordContext!!.selectedGapGuide!!.contextId)
        assertTrue(gapLayer.observedParts.none { it.selected })
        assertTrue(gapLayer.observedDirectionEdges.isEmpty())
        assertTrue(gapLayer.directionPaths(listOf(detail.route.bounds)).isEmpty())
        assertTrue(gapLayer.highlightPaths.isEmpty()); assertNull(gapLayer.cursor)
        assertTrue(gapLayer.recordContext.markers.filter { it.gapBoundary }.all { it.label == "–" })
        assertFalse(state.panelOpen)
    }

    @Test fun `uncertain measured scene has geometry and an explicit missing direction notice`() = runTest {
        val detail = measuredObservedDetail(accuracy = 25f); val review = CompletedRouteReview(detail)
        val state = WalkRouteExplorerState(this, 0)
        state.replaceRoute(RouteExplorerIndex(detail.route), review, 0)
        val scene = measuredScene(detail, 5); val focus = review.recordSceneFocus(scene)
        state.selectScene(scene.id)
        val layer = recordPresentationLayer(state, detail, focus)
        assertEquals(RecordRouteRole.OBSERVED_UNRESOLVED, layer.observedParts.single { it.selected }.role)
        assertTrue(layer.observedDirectionEdges.isEmpty())
        assertTrue(sceneRouteNotice(focus).contains("이동 방향은 확인하기 어려워요"))
    }
}
