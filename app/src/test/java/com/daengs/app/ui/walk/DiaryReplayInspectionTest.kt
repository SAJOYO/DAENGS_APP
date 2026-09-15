package com.daengs.app.ui.walk

import com.daengs.app.walk.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiaryReplayInspectionTest {
    @Test fun `scene action route and context inspection never replace the running clock`() = runTest {
        val base=explorerPanelPreviewRead()
        val scene=base.diary!!.scenes[1]
        val action=WalkEntry("a",scene.sessionId,WalkMomentType.SNIFFING,7_500,scene.point,7_500)
        val read=base.copy(diary=base.diary.copy(sourceEntries=listOf(action)))
        val player=WalkRouteExplorerState(this,0).apply { adopt(read); choosePanel(true); togglePlayback() }
        val inspect=DiaryReplayInspection(this,StandardTestDispatcher(testScheduler)).apply { adopt(read) }
        inspect.selectMarkers(setOf(scene.id)); player.tick(2_000)
        assertTrue(player.playing); assertEquals(2_000L,player.elapsed)
        assertEquals(scene.id,inspect.events().single().scene!!.id)
        inspect.selectMarkers(setOf(diaryActionKey(action))); player.tick(2_000)
        assertTrue(player.playing); assertEquals(4_000L,player.elapsed)
        assertEquals(action,inspect.events().single().action)
        inspect.inspect(requireNotNull(scene.point)); advanceUntilIdle(); player.tick(2_000)
        assertTrue(player.playing); assertEquals(6_000L,player.elapsed)
        assertEquals(RouteExplorerMode.PASSAGE,inspect.explorer.mode)
        inspect.selectContext(read.route.review.context.contexts.first().id); player.tick(2_000)
        assertTrue(player.playing); assertEquals(8_000L,player.elapsed)
        assertNotNull(inspect.explorer.selectedContext)
        inspect.clear()
        assertFalse(inspect.active); assertTrue(player.playing); assertEquals(8_000L,player.elapsed)
        assertEquals(RouteExplorerMode.REPLAY,player.mode)
    }

    @Test fun `only a time change resets inspection follow mode while ticks speed and pause do not`() = runTest {
        val state=WalkRouteExplorerState(this,0).apply { adopt(explorerPanelPreviewRead()); choosePanel(true); togglePlayback() }
        val revision=state.seekRevision
        state.tick(2_000)
        state.choosePlaybackSpeed(com.daengs.app.walk.routeexplorer.RoutePlaybackSpeed.FOUR)
        state.pause(); state.togglePlayback()
        assertEquals(revision,state.seekRevision)
        state.seek(10_000)
        assertTrue(state.seekRevision>revision); assertFalse(state.playing)
    }
}
