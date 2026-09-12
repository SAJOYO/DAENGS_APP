package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.map.layers.completedroute.directionPaths
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.routeexplorer.*
import com.daengs.app.walk.trajectory.RecordContextKind
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkDiaryGapListTest {
    @get:Rule val compose = createComposeRule()
    private val detail = readCompletedRoute(RecordedSession("gap-list", startedAtMillis = 0, endedAtMillis = 100_000),
        (0..17).map { i -> RecordedFix(i, if (i < 9) 0 else 1,
            if (i < 9) 10_000 + i * 2_000L else 60_000 + (i - 9) * 2_000L,
            0.0, i * 4.0 / 111_195, 1f, false) })
    private val review = CompletedRouteReview(detail)
    private val scenes = listOf(20_000L, 50_000L, 65_000L).mapIndexed { i, at ->
        DiaryScene("scene-$i", "gap-list", at, "장면 제목 ${i + 1}", "함께 쉬었던 기억", null, "")
    }

    @Test fun `map scene and gap selection preserve sheet height while list selection still expands`() {
        lateinit var state: WalkRouteExplorerState
        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        var viewport: DiaryMapViewport? = null
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 100_000).apply {
                replaceRoute(RouteExplorerIndex(detail.route), this@WalkDiaryGapListTest.review, 100_000)
            } }
            WalkDiaryMapContent(scenes, scenes.firstOrNull { it.id == state.selectedSceneId }, false, null,
                { state.selectScene(it.id) }, state::closeScene, {}, {}, {}, {},
                gapContexts = review.context.contexts, selectedGap = state.selectedContext,
                onSelectGap = { state.selectContext(it.id, openExplorer = false) },
                explorerFocusId = state.selectedContext?.id, onContextDismiss = state::overview,
                selectionFromMap = state.selectionFromMap,
                map = { SideEffect { viewport = it }; Box(Modifier.fillMaxSize()) })
        }
        val initial = compose.onNodeWithTag("diary-sheet-handle").fetchSemanticsNode().boundsInRoot
        val initialViewport = viewport
        repeat(2) {
            compose.runOnIdle { state.selectContext(gap.id, openExplorer = false, fromMap = true) }
            compose.onNodeWithText("이 사이의 이동 경로는 확인할 수 없어요.").assertIsDisplayed()
            assertEquals(initial, compose.onNodeWithTag("diary-sheet-handle").fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { state.selectScene(scenes.last().id, fromMap = true) }
            compose.onNodeWithText(scenes.last().title).assertIsDisplayed()
            assertEquals(initial, compose.onNodeWithTag("diary-sheet-handle").fetchSemanticsNode().boundsInRoot)
            assertEquals(initialViewport, viewport)
        }
        // Selecting the same scene from the list is a new intent and must still open the page.
        compose.runOnIdle { state.selectScene(scenes.last().id) }
        val expanded = compose.onNodeWithTag("diary-sheet-handle").fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.top < initial.top)
        compose.runOnIdle { state.selectContext(gap.id, openExplorer = false, fromMap = true) }
        assertEquals(expanded, compose.onNodeWithTag("diary-sheet-handle").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.selectScene(scenes.last().id, fromMap = true) }
        assertEquals(expanded, compose.onNodeWithTag("diary-sheet-handle").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(scenes.last().id, state.selectedSceneId) }
    }

    @Test fun `gap row selects only the guide and returns to unchanged scene numbering`() {
        lateinit var state: WalkRouteExplorerState
        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 100_000).apply {
                replaceRoute(RouteExplorerIndex(detail.route), this@WalkDiaryGapListTest.review, 100_000)
            } }
            WalkDiaryMapContent(scenes, scenes.firstOrNull { it.id == state.selectedSceneId }, false, null,
                { state.selectScene(it.id) }, state::closeScene, {}, {}, {}, {},
                gapContexts = review.context.contexts, selectedGap = state.selectedContext,
                onSelectGap = { state.selectContext(it.id, openExplorer = false) },
                explorerFocusId = state.selectedContext?.id, onContextDismiss = state::overview,
                map = { Box(Modifier.fillMaxSize()) })
        }
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText(diaryGapTitle(gap)))
        compose.onNodeWithText(diaryGapTitle(gap)).performClick()
        compose.runOnIdle {
            assertFalse(state.panelOpen)
            assertNull(state.selectedSceneId)
            val presentation = recordPresentationLayer(state, detail, null)
            assertEquals(gap.id, presentation.recordContext!!.selectedGapGuide!!.contextId)
            assertTrue(presentation.directionPaths(emptyList()).isEmpty())
            assertNull(presentation.cursor)
        }
        compose.onNodeWithText("이 사이의 이동 경로는 확인할 수 없어요.").assertIsDisplayed()
        compose.onNodeWithText("‹ 장면 목록").performClick()
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText("장면 제목 2"))
        compose.onNodeWithContentDescription("장면 2 수정").assertExists()
        compose.onNodeWithText("장면 제목 2").performClick()
        compose.onNodeWithText("함께 쉬었던 기억").assertIsDisplayed()
        compose.onAllNodesWithText(formatWalkClock(scenes[1].atMillis)).assertCountEquals(1)
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.onNodeWithText("기록 시각", substring = true).assertDoesNotExist()
        compose.onNodeWithText("이전 관측", substring = true).assertDoesNotExist()
        compose.onNodeWithText("앞 장면", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertNull(recordPresentationLayer(state, detail, null).recordContext!!.selectedGapGuide) }
    }

    @Test fun `gaps do not consume scene numbers or guess an unknown time position`() {
        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        assertEquals(mapOf(1 to listOf(gap)), diaryGapSlots(scenes, review.context.contexts))
        assertEquals(listOf(gap), diaryGapSlots(emptyList(), listOf(gap)).getValue(0))
        val unknown = gap.copy(durationMillis = null)
        assertEquals(listOf(unknown), diaryGapSlots(scenes, listOf(unknown)).getValue(scenes.size))
        assertEquals("시간 미확인 · 경로 공백", diaryGapTitle(unknown))
    }

    @Test fun `gap selection changes color but preserves its drawn footprint`() {
        for (density in listOf(1f, 2.75f)) {
            val normal = com.daengs.app.map.provider.naver.diaryGapPinBitmap(false, density)
            val selected = com.daengs.app.map.provider.naver.diaryGapPinBitmap(true, density)
            assertEquals(normal.width, selected.width)
            assertEquals(normal.height, selected.height)
            for (y in 0 until normal.height) for (x in 0 until normal.width) {
                assertEquals(android.graphics.Color.alpha(normal.getPixel(x, y)),
                    android.graphics.Color.alpha(selected.getPixel(x, y)))
            }
            assertNotEquals(normal.getPixel(normal.width / 2, normal.height / 3),
                selected.getPixel(selected.width / 2, selected.height / 3))
        }
    }
}
