package com.daengs.app.ui.walk

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkExplorerPanelUiTest {
    @get:Rule val compose = createComposeRule()
    private var rendered: View? = null

    private fun show(example: ExplorerPanelExample, fontScale: Float = 1f) {
        compose.setContent {
            val view = LocalView.current
            SideEffect { rendered = view.rootView }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WalkExplorerPanelPreview(example)
            }
        }
    }

    private fun assertNoScenes() {
        explorerPanelPreviewRead().diary!!.scenes.forEach {
            compose.onNodeWithText(it.title, useUnmergedTree = true).assertDoesNotExist()
        }
        compose.onNodeWithText("전체 장면", substring = true).assertDoesNotExist()
        compose.onNodeWithText("이 범위의 장면", substring = true).assertDoesNotExist()
        compose.onNodeWithText("화면 밖 장면", substring = true).assertDoesNotExist()
    }

    @Test fun `overview range and replay contain route controls without a duplicate scene list`() {
        show(ExplorerPanelExample.RANGE)
        val header = compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot
        val sheet = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        assertNoScenes()
        val timeline = compose.onNodeWithTag("explorer-range-slider").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithTag("explorer-time-label").fetchSemanticsNode().boundsInRoot
        assertTrue("Both handles need the header width", timeline.width >= header.width - 1f)
        assertTrue("Elapsed times belong above the timeline", label.bottom <= timeline.top)
        compose.onNodeWithTag("explorer-range-slider").assertHeightIsAtLeast(48.dp)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(2)
        capture("range-390")
        compose.onNodeWithText("경로 정보 · 구간과 전후 관계").performScrollTo().performClick()
        compose.onNodeWithText("동선 2", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(header, compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("동선 재생").assertIsDisplayed().performClick()
        compose.onNodeWithTag("explorer-range-slider").assertDoesNotExist()
        compose.onNodeWithTag("explorer-replay-slider").assertIsDisplayed()
        assertNoScenes()
        capture("replay-390")
        compose.onNodeWithText("구간 수정").performClick()
        compose.onNodeWithTag("explorer-range-slider").assertIsDisplayed()
        compose.onNodeWithTag("explorer-replay-slider").assertDoesNotExist()
        assertEquals(sheet, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("전체 산책").performClick()
        assertNoScenes()
        capture("overview-390")
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `scene tab owns reading while the selected range survives tab and scene round trips`() {
        show(ExplorerPanelExample.RANGE, 1.3f)
        val sheet = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        val header = compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot
        val reading = compose.onNodeWithTag("explorer-reading").fetchSemanticsNode().boundsInRoot
        assertTrue("Reading region must remain usable below the controls", reading.height >= 40f)
        assertTrue(header.bottom <= reading.top)
        compose.onNodeWithTag("explorer-range-slider").assertIsDisplayed()
        capture("range-320-large-font")
        assertNoScenes()
        compose.onNodeWithText("장면 6").performClick()
        compose.onNodeWithTag("explorer-time-header").assertDoesNotExist()
        compose.onNodeWithText("함께 남긴 메모", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("구간 복귀").assertIsDisplayed().performClick()
        compose.onNodeWithText("– 00:30").assertIsDisplayed()
        assertNoScenes()
        assertEquals(sheet, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("장면 6").performClick()
        compose.onNodeWithText("함께 남긴 메모", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("이 장면 앞뒤 30초 보기").performScrollTo().performClick()
        compose.onNodeWithText("– 01:00").assertIsDisplayed()
        compose.onNodeWithTag("explorer-range-slider").assertIsDisplayed()
        assertEquals(sheet, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("동선 재생").performClick()
        compose.onNodeWithTag("explorer-replay-slider").assertIsDisplayed()
        capture("replay-320-large-font")
        compose.onNodeWithText("장면 6").performClick()
        compose.onNodeWithText("일시정지").assertDoesNotExist()
        compose.onNodeWithText("동선 탐색").performClick()
        compose.onNodeWithText("– 01:00").assertIsDisplayed()
        compose.onNodeWithText("일시정지").assertDoesNotExist()
    }

    @Test fun `gap and legacy explain route availability without scene prompts`() {
        var example by mutableStateOf(ExplorerPanelExample.GAP)
        compose.setContent { WalkExplorerPanelPreview(example) }
        compose.onNodeWithText("동선 재생").assertIsNotEnabled()
        compose.onNodeWithText("선택 범위에 재생할 이동 근거가 없어요.").performScrollTo().assertIsDisplayed()
        assertNoScenes()
        compose.runOnIdle { example = ExplorerPanelExample.LEGACY }
        compose.onNodeWithText("구간 고르기").assertDoesNotExist()
        compose.onNodeWithTag("explorer-range-slider").assertDoesNotExist()
        compose.onNodeWithText("이 산책은 시간 구간을 고를 수 있는 측정 정보가 없어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("동선 2", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("전체 동선").assertIsDisplayed().performClick()
        assertNoScenes()
        compose.onNodeWithText("장면 6").performClick()
        compose.onNodeWithText("산책을 시작했어요", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("이 장면 앞뒤 30초 보기").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `route notices stay in explorer and scene notices appear only in the scene tab`() {
        show(ExplorerPanelExample.NOTICES, 1.3f)
        val header = compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot
        val sheet = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        val reading = compose.onNodeWithTag("explorer-reading").fetchSemanticsNode().boundsInRoot
        assertTrue("A real map's notices must leave a scrollable viewport", reading.height >= 40f)
        compose.onNodeWithText("동선 확대").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("저장한 장면을 보여드려요.").assertDoesNotExist()
        assertNoScenes()
        capture("notices-320-large-font")
        assertEquals(header, compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("장면 6").performClick()
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText("다시 시도"))
        compose.onNodeWithText("다시 시도").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("화면 밖 장면 1개").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("동선 탐색").performClick()
        compose.onNodeWithText("– 00:30").assertIsDisplayed()
        assertNoScenes()
        assertEquals(sheet, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun `route details remain reachable independently of scene loading`() {
        show(ExplorerPanelExample.LOADING)
        compose.onNodeWithText("장면을 불러오고 있어요.").assertDoesNotExist()
        compose.onNodeWithText("경로 정보 · 구간과 전후 관계").performScrollTo().performClick()
        compose.onNodeWithText("동선 2", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("전체 산책").assertIsDisplayed().performClick()
        compose.onNodeWithText("경로 정보 접기").performScrollTo().assertIsDisplayed()
        assertNoScenes()
    }

    @Test fun `secondary route information still selects observed evidence and its original temporal context`() {
        val read = explorationRead()
        lateinit var state: WalkRouteExplorerState
        var observedId: String? = null
        var contextId: String? = null
        val context = read.route.review.context.contexts.first()
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read) } }
            com.daengs.app.ui.theme.DaengsTheme { WalkRouteExplorerPanel(state, {},
                onAuxiliary = { observedId = it.id }, onContext = { contextId = it.id }) }
        }
        compose.onNodeWithText("경로 정보 · 구간과 전후 관계").performScrollTo().performClick()
        compose.onNodeWithText("관측 경로 1", substring = true).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(read.route.review.observed.sections.first().id, observedId)
            assertEquals(observedId, state.selectedAuxiliary!!.id)
        }
        compose.onNodeWithText(recordContextTitle(context) + "\n" + recordContextTime(context)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(context.id, contextId); assertEquals(context, state.selectedContext) }
        compose.onNodeWithText("전체 산책").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(RouteExplorerMode.OVERVIEW, state.mode) }
    }

    @Test fun `new reading layout restores details and offsets while migrating only the old explorer offset`() {
        lateinit var memory: DiaryReadingMemory
        compose.setContent { memory = rememberDiaryReadingMemory() }
        val saved = JSONObject().put("drawer", "Browsing").put("explorerOffset", 321)
            .put("explorerDetails", true).put("explorerLayout", 4)
            .put("group", org.json.JSONArray(listOf("scene-a")))
            .put("body", JSONObject().put("id", "scene-a").put("index", 3).put("offset", 12))
        compose.runOnIdle { runBlocking { memory.restore(saved) }
            assertEquals(321, memory.pendingExplorerOffset)
            assertTrue(memory.explorerDetails)
            assertEquals(listOf("scene-a"), memory.groupIds)
            assertEquals(3, memory.restoredBody!!.getInt("index"))
            val roundTrip = memory.snapshot()
            assertEquals(4, roundTrip.getInt("explorerLayout"))
            assertEquals(321, roundTrip.getInt("explorerOffset"))
            saved.put("explorerLayout", 3); saved.remove("explorerDetails")
            runBlocking { memory.restore(saved) }
            assertEquals(0, memory.pendingExplorerOffset)
            assertFalse(memory.explorerDetails)
            assertEquals(listOf("scene-a"), memory.groupIds)
            assertEquals(12, memory.restoredBody!!.getInt("offset"))
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `hour-long time labels retain all digits with enlarged text`() {
        compose.setContent {
            val view = LocalView.current
            SideEffect { rendered = view.rootView }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DaengsTheme { Box(Modifier.width(320.dp).padding(horizontal = 20.dp)) {
                    ExplorerTimeLabel(3_600_000, 9_599_000, selection = true)
                } }
            }
        }
        capture("hours-320-large-font")
        val parent = compose.onNodeWithTag("explorer-time-label").fetchSemanticsNode().boundsInRoot
        for (text in listOf("1:00:00", "– 2:39:59", "1:39:59 선택")) {
            val node = compose.onNodeWithText(text).assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= parent.left && bounds.right <= parent.right)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertFalse(layout.didOverflowHeight)
                val lastLine = layout.lineCount - 1
                assertFalse(layout.isLineEllipsized(lastLine))
                assertEquals(text.length, layout.getLineEnd(lastLine, visibleEnd = true))
                // Text layout uses fractional glyph widths but the node size is an integer pixel.
                assertTrue("Time digits must fit: $text", layout.getLineRight(lastLine) <= layout.size.width + 1f)
            }
        }
    }

    @Test fun `range length menu preserves its start and clips to the original recording end`() {
        val read = explorerPanelPreviewRead()
        lateinit var state: WalkRouteExplorerState
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read); selectTimeRange(120_000, 150_000) } }
            DaengsTheme { WalkRouteExplorerPanel(state, {}) }
        }
        compose.onNodeWithText("길이 ▾").performScrollTo().performClick()
        compose.onNodeWithText("3분").performClick()
        compose.runOnIdle {
            assertEquals(120_000L, state.selectedSlice!!.from)
            assertEquals(180_000L, state.selectedSlice!!.until)
            assertEquals(80.0, read.route.detail.summary.distanceMeters, 0.0)
        }
        compose.onNodeWithText("01:00 선택").assertIsDisplayed()
        compose.onNodeWithText("전체 산책").performClick()
        compose.onNodeWithText("1분").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0L, state.selectedSlice!!.from); assertEquals(60_000L, state.selectedSlice!!.until) }
    }

    private fun capture(name: String) {
        val directory = System.getenv("DAENGS_EXPLORER_PREVIEW_DIR")?.let(::File) ?: return
        directory.mkdirs()
        compose.runOnIdle {
            val view = requireNotNull(rendered)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
