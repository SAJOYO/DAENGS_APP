package com.daengs.app.ui.walk

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.theme.DaengsTheme
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
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkDiaryCompactDrawerTest {
    @get:Rule val compose = createComposeRule()
    private val scene = DiaryScene("s/a", "s", 20_000, "벤치 옆에서", "함께 쉬었던 기억", null, "")
    private fun sceneTab() = compose.onNode(hasText("장면 1") and hasClickAction())
    private fun fold() = compose.onNodeWithTag("diary-sheet-handle").performTouchInput {
        swipeDown(startY = 10f, endY = 600f)
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `compact drawer fits both tabs with larger text and hides all details`() {
        var viewport: DiaryMapViewport? = null
        var tabChanges = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DaengsTheme { WalkDiaryMapContent(listOf(scene), null, false, null, {}, {}, {}, {}, {}, {},
                    explorerPanel = { Text("탐색 상세") }, onChooseExplorer = { tabChanges++ },
                    generationNotice = "저장한 장면을 읽었어요.", directionNotice = true,
                    offscreenScenes = listOf(scene),
                    map = { SideEffect { viewport = it }; Box(Modifier.fillMaxSize().testTag("map")) }) }
            }
        }
        val middle = viewport!!
        compose.onNodeWithText("저장한 장면을 읽었어요.").assertIsDisplayed()
        fold()
        sceneTab().assertIsDisplayed()
        compose.onNodeWithText("걸어온 길").assertIsDisplayed()
        compose.onNodeWithText(scene.title).assertIsNotDisplayed()
        compose.onNodeWithText("저장한 장면을 읽었어요.").assertIsNotDisplayed()
        compose.onNodeWithText("동선 확대").assertIsNotDisplayed()
        compose.onNodeWithText("화면 밖 장면 1개").assertIsNotDisplayed()
        val map = compose.onNodeWithTag("map").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag("diary-tabs").fetchSemanticsNode().boundsInRoot
        assertEquals(map.bottom, tabs.bottom, 1f)
        val compact = viewport!!
        sceneTab().performClick()
        compose.onNodeWithText("저장한 장면을 읽었어요.").assertIsDisplayed()
        compose.onNodeWithText("화면 밖 장면 1개").assertIsDisplayed()
        assertEquals(0, tabChanges)
        assertEquals(middle, viewport)
        assertEquals(map, compose.onNodeWithTag("map").fetchSemanticsNode().boundsInRoot)
        assertEquals(compact, viewport!!.copy(bottomOcclusionPx = compact.bottomOcclusionPx))
        assertTrue(viewport!!.bottomOcclusionPx > compact.bottomOcclusionPx)
    }

    @Test fun `drag and back preserve the selected scene and late data cannot reopen the drawer`() {
        var selected by mutableStateOf<DiaryScene?>(null)
        var pending by mutableStateOf(false)
        var back: OnBackPressedDispatcher? = null
        var closes = 0
        var mounts = 0
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            DaengsTheme { WalkDiaryMapContent(if (pending) emptyList() else listOf(scene),
                selected.takeUnless { pending }, pending, null, { selected = it },
                { closes++; selected = null }, {}, {}, {}, {}, selectionPending = pending,
                selectedRouteNotice = "확인된 동선을 함께 보여요.", offscreenScenes = listOf(scene),
                explorerPanel = { Text("탐색 상세") },
                map = { DisposableEffect(Unit) { mounts++; onDispose {} }; Box(Modifier.fillMaxSize()) }) }
        }
        val middleTop = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        sceneTab().performClick()
        compose.onNodeWithText(scene.title).performClick()
        compose.onNodeWithText(scene.body).assertIsDisplayed()
        assertTrue(compose.onNodeWithText(scene.body).getUnclippedBoundsInRoot().bottom <=
            compose.onRoot().getUnclippedBoundsInRoot().bottom)
        assertEquals(middleTop, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("diary-sheet-handle").performTouchInput { swipeDown(startY = 10f, endY = 600f) }
        val compactTop = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        assertTrue(compactTop > middleTop)
        compose.onNodeWithText(scene.body).assertIsNotDisplayed()
        assertEquals(scene, selected)
        assertEquals(compactTop, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        compose.runOnIdle { pending = true }
        compose.runOnIdle { pending = false }
        compose.onNodeWithText(scene.body).assertIsNotDisplayed()
        sceneTab().performClick()
        compose.onNodeWithText(scene.body).assertIsDisplayed()
        assertEquals(middleTop, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("diary-sheet-handle").performTouchInput { swipeUp(startY = 10f, endY = -500f) }
        assertTrue(compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top < middleTop)
        compose.runOnIdle { back!!.onBackPressed() }
        assertEquals(middleTop, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText(scene.body).assertIsDisplayed()
        compose.runOnIdle { back!!.onBackPressed() }
        compose.onNodeWithText(scene.body).assertIsNotDisplayed()
        assertEquals(scene, selected)
        assertEquals(0, closes)
        sceneTab().performClick()
        compose.onNodeWithText("‹ 장면 목록").performClick()
        compose.onNodeWithText(scene.title).assertIsDisplayed()
        assertNull(selected)
        assertEquals(1, closes)
        assertEquals(1, mounts)
    }

    @Test fun `map gap and scene selections keep the compact height and reopen the current selection`() {
        val detail = readCompletedRoute(RecordedSession("s", startedAtMillis = 0, endedAtMillis = 100_000),
            (0..17).map { i -> RecordedFix(i, if (i < 9) 0 else 1,
                if (i < 9) 10_000 + i * 2_000L else 60_000 + (i - 9) * 2_000L,
                0.0, i * 4.0 / 111_195, 1f, false) })
        val review = CompletedRouteReview(detail)
        val gap = review.context.contexts.single { it.kind == RecordContextKind.GAP }
        lateinit var state: WalkRouteExplorerState
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 100_000).apply {
                replaceRoute(RouteExplorerIndex(detail.route), review, 100_000)
            } }
            DaengsTheme { WalkDiaryMapContent(listOf(scene), scene.takeIf { state.selectedSceneId == it.id },
                false, null, { state.selectScene(it.id) }, state::closeScene, {}, {}, {}, {},
                gapContexts = review.context.contexts, selectedGap = state.selectedContext,
                explorerFocusId = state.selectedContext?.id, selectionFromMap = state.selectionFromMap,
                onContextDismiss = state::overview,
                explorerSelected = state.panelOpen, onChooseExplorer = { state.overview(); state.choosePanel(it) },
                explorerPanel = { WalkRouteExplorerPanel(state, {}) }, map = { Box(Modifier.fillMaxSize()) }) }
        }
        fold()
        val compactTop = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.selectContext(gap.id, openExplorer = false, fromMap = true) }
        compose.onNodeWithText("이 사이의 이동 경로는 확인할 수 없어요.").assertIsNotDisplayed()
        assertEquals(compactTop, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        sceneTab().performClick()
        compose.onNodeWithText("이 사이의 이동 경로는 확인할 수 없어요.").assertIsDisplayed()
        assertEquals(gap.id, state.selectedContext?.id)
        fold()
        compose.runOnIdle { state.selectScene(scene.id, fromMap = true) }
        compose.onNodeWithText(scene.body).assertIsNotDisplayed()
        assertEquals(compactTop, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        sceneTab().performClick()
        compose.onNodeWithText(scene.body).assertIsDisplayed()
        assertEquals(scene.id, state.selectedSceneId)
    }

    @Test fun `replay continues at the selected speed while collapsed and reopening the active tab does not reset it`() {
        val detail = readCompletedRoute(RecordedSession("s", startedAtMillis = 0, endedAtMillis = 100_000),
            (0..17).map { i -> RecordedFix(i, if (i < 9) 0 else 1,
                if (i < 9) 10_000 + i * 2_000L else 60_000 + (i - 9) * 2_000L,
                0.0, i * 4.0 / 111_195, 1f, false) })
        lateinit var state: WalkRouteExplorerState
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 100_000).apply {
                replaceRoute(RouteExplorerIndex(detail.route), CompletedRouteReview(detail), 100_000)
            } }
            DaengsTheme { WalkDiaryMapContent(emptyList(), null, false, null, {}, state::closeScene, {}, {}, {}, {},
                explorerSelected = state.panelOpen, onChooseExplorer = { state.overview(); state.choosePanel(it) },
                onContextDismiss = state::overview,
                explorerPanel = { WalkRouteExplorerPanel(state, {}) }, map = { Box(Modifier.fillMaxSize()) }) }
        }
        compose.onNodeWithText("걸어온 길").performClick()
        compose.onNodeWithContentDescription("재생 속도").performClick()
        compose.onNodeWithText("16×").performClick()
        compose.onNodeWithText("동선 재생").performClick()
        compose.runOnIdle {
            state.tick(1_000)
            assertNotNull(recordPresentationLayer(state, detail, null).cursor)
        }
        fold()
        compose.onNodeWithText("일시정지").assertIsNotDisplayed()
        compose.onNodeWithText("16×").assertIsNotDisplayed()
        compose.runOnIdle {
            assertTrue(state.playing)
            state.tick(1_000)
            assertEquals(32_000L, state.elapsed)
            assertTrue(state.replayFrame!!.inGap)
            val gap = recordPresentationLayer(state, detail, null)
            assertNull(gap.cursor)
            assertNull(gap.recordContext!!.selectedGapGuide)
            state.tick(2_000)
            assertEquals(64_000L, state.elapsed)
            assertNotNull(recordPresentationLayer(state, detail, null).cursor)
        }
        compose.onNodeWithText("걸어온 길").performClick()
        compose.onNodeWithText("일시정지").assertIsDisplayed()
        compose.onNodeWithText("16×").assertIsDisplayed()
        compose.runOnIdle { assertTrue(state.playing); assertEquals(64_000L, state.elapsed) }
        compose.onNodeWithText("장면 0").performClick()
        compose.runOnIdle { assertFalse(state.playing) }
    }

    @Test fun `body scrolls to its end at middle height while button and dragging still control the drawer`() {
        val longScene = scene.copy(body = "함께 쉬었던 기억. ".repeat(200))
        compose.setContent {
            DaengsTheme { WalkDiaryMapContent(listOf(longScene), longScene, false, null, {}, {}, {}, {}, {}, {},
                selectedRouteNotice = "끝까지 읽었어요.",
                explorerPanel = { Text("탐색 상세") }, map = { Box(Modifier.fillMaxSize()) }) }
        }
        fun top() = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        fun scroll() = compose.onNodeWithTag("diary-scene-body").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        val middle = top()
        compose.onNodeWithTag("diary-scene-body").performTouchInput { swipeUp() }
        assertEquals(middle, top(), 1f)
        val position = scroll()
        assertTrue(position > 0)
        fold()
        sceneTab().performClick()
        assertEquals(middle, top(), 1f)
        assertEquals(position, scroll(), .001f)
        compose.onNodeWithContentDescription("상세 패널 펼치기").performClick()
        val expanded = top()
        assertTrue(expanded < middle)
        // Lift after resting at the middle anchor, rather than flinging across it.
        compose.onNodeWithTag("diary-sheet-handle").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, middle - expanded), delayMillis = 600)
            advanceEventTime(150)
            up()
        }
        assertEquals(middle, top(), 1f)
        compose.onNodeWithText("끝까지 읽었어요.").performScrollTo().assertIsDisplayed()
        assertEquals(middle, top(), 1f)
        compose.onNodeWithContentDescription("상세 패널 펼치기").performClick()
        assertEquals(expanded, top(), 1f)
        compose.onNodeWithContentDescription("지도 넓게 보기").performClick()
        assertEquals(middle, top(), 1f)
    }

    @Test fun `scene list scrolls through the last scene without expanding or moving the map`() {
        val scenes = (1..20).map { scene.copy(id = "s/$it", title = "산책 장면 번호 $it") }
        var selected by mutableStateOf<DiaryScene?>(null)
        compose.setContent {
            DaengsTheme { WalkDiaryMapContent(scenes, selected, false, null, { selected = it },
                { selected = null }, {}, {}, {}, {}, explorerPanel = { Text("탐색 상세") },
                map = { Box(Modifier.fillMaxSize().testTag("map")) }) }
        }
        val middle = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        val map = compose.onNodeWithTag("map").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("diary-scene-list").performTouchInput { swipeUp() }
        assertTrue(compose.onNodeWithTag("diary-scene-list").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value() > 0)
        assertEquals(middle, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText(scenes.last().title))
        compose.onNodeWithText(scenes.last().title).assertIsDisplayed().performClick()
        compose.onNodeWithText(scene.body).assertIsDisplayed()
        assertEquals(middle, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        assertEquals(map, compose.onNodeWithTag("map").fetchSemanticsNode().boundsInRoot)
    }
}
