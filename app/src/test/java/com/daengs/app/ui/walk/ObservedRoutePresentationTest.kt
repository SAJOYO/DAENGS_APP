package com.daengs.app.ui.walk

import com.daengs.app.map.layers.completedroute.*
import com.daengs.app.walk.*
import com.daengs.app.walk.routeexplorer.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ObservedRoutePresentationTest {
    @Test fun `selecting an observed section preserves rails role and source direction ids`() = runTest {
        val raw = (0..12).map { RecordedFix(it, 0, 10_000L + it * 1_000L, 0.0, it * 12.0 / 111_195, 1f, false) }
        val detail = readCompletedRoute(RecordedSession("presentation", startedAtMillis = 0, endedAtMillis = 30_000), raw)
        val review = CompletedRouteReview(detail)
        val state = WalkRouteExplorerState(this, 30_000)
        state.replaceRoute(RouteExplorerIndex(detail.route), review, 30_000)
        val overview = recordPresentationLayer(state, detail, null)
        val base = overview.observedParts.single()
        state.selectAuxiliary(base.id)
        val selection = recordPresentationLayer(state, detail, null)
        val chosen = selection.observedParts.single { it.selected }
        assertEquals(base.id, chosen.id); assertEquals(base.path, chosen.path); assertEquals(base.role, chosen.role)
        assertEquals(base.directions, chosen.directions)
        assertTrue(selection.highlightPaths.isEmpty()); assertFalse(selection.useOverviewDirections)
        for (selected in listOf(false, true)) {
            val style = RecordPresentationPolicy.stroke(chosen.role, selected)
            assertTrue(style.railWidthDp > 0); assertNotEquals(style.color, style.centerColor)
        }
        state.selectScene("new")
        assertNull(state.selectedAuxiliary)
        assertTrue(recordPresentationLayer(state, detail, null).observedDirectionEdges.isEmpty())
        state.selectAuxiliary(base.id); state.seek(1_000)
        assertNull(state.selectedAuxiliary)
        state.selectAuxiliary(base.id); state.replaceRoute(RouteExplorerIndex(detail.route), review, 30_000)
        assertEquals(RouteExplorerMode.OVERVIEW, state.mode)
        assertTrue(recordPresentationLayer(state, detail.copy(), null).observedParts.isEmpty())
    }

    @Test fun `excluded and unresolved sections keep different labels in both selection states`() {
        assertNotEquals(RecordPresentationPolicy.label(RecordRouteRole.OBSERVED_EXCLUDED),
            RecordPresentationPolicy.label(RecordRouteRole.OBSERVED_UNRESOLVED))
        for (selected in listOf(false, true)) assertNotEquals(
            RecordPresentationPolicy.stroke(RecordRouteRole.OBSERVED_EXCLUDED, selected),
            RecordPresentationPolicy.stroke(RecordRouteRole.OBSERVED_UNRESOLVED, selected))
    }
}
