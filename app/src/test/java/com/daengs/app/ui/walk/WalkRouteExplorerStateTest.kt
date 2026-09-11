package com.daengs.app.ui.walk

import com.daengs.app.walk.routeexplorer.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WalkRouteExplorerStateTest {
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
