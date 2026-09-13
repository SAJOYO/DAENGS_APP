package com.daengs.app.ui.walk

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.*
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

    @Test fun `range controls stay visible while related scenes scroll and replay has only a cursor`() {
        show(ExplorerPanelExample.RANGE)
        val header = compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot
        val sheet = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("이 범위의 장면 3개").assertIsDisplayed()
        compose.onNodeWithText("다시 걸어간 길", useUnmergedTree = true).assertDoesNotExist()
        capture("range-390")
        compose.onNodeWithText("함께 남긴 메모", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals(header, compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("동선 재생").assertIsDisplayed().performClick()
        compose.onNodeWithTag("explorer-range-slider").assertDoesNotExist()
        compose.onNodeWithTag("explorer-replay-slider").assertIsDisplayed()
        capture("replay-390")
        compose.onNodeWithText("구간 수정").performClick()
        compose.onNodeWithTag("explorer-range-slider").assertIsDisplayed()
        compose.onNodeWithTag("explorer-replay-slider").assertDoesNotExist()
        assertEquals(sheet, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("전체 산책").performClick()
        compose.onNodeWithText("전체 장면 6개").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("집 앞에서 마무리", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `small screen enlarged text retains fixed controls scrollable scenes and explicit scene round trip`() {
        show(ExplorerPanelExample.RANGE, 1.3f)
        val sheet = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        val header = compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot
        val reading = compose.onNodeWithTag("explorer-reading").fetchSemanticsNode().boundsInRoot
        assertTrue("Reading region must remain usable below the controls", reading.height >= 40f)
        assertTrue(header.bottom <= reading.top)
        compose.onNodeWithTag("explorer-range-slider").assertIsDisplayed()
        compose.onNodeWithText("동선 재생").assertIsDisplayed()
        capture("range-320-large-font")
        compose.onNodeWithText("함께 남긴 메모", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("구간 복귀").assertIsDisplayed().performClick()
        compose.onNodeWithText("– 00:30").assertIsDisplayed()
        assertEquals(sheet, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("함께 남긴 메모", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("이 장면 앞뒤 30초 보기").performScrollTo().performClick()
        compose.onNodeWithText("– 01:00").assertIsDisplayed()
        compose.onNodeWithTag("explorer-range-slider").assertIsDisplayed()
        assertEquals(sheet, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("동선 재생").performClick()
        compose.onNodeWithTag("explorer-replay-slider").assertIsDisplayed()
        capture("replay-320-large-font")
    }

    @Test fun `gap-only range explains disabled playback and legacy keeps sections without a false time control`() {
        var example by mutableStateOf(ExplorerPanelExample.GAP)
        compose.setContent { WalkExplorerPanelPreview(example) }
        compose.onNodeWithText("동선 재생").assertIsNotEnabled()
        compose.onNodeWithText("선택 범위에 재생할 이동 근거가 없어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이 시간 범위에 확인된 장면이 없어요.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { example = ExplorerPanelExample.LEGACY }
        compose.onNodeWithText("구간 고르기").assertDoesNotExist()
        compose.onNodeWithTag("explorer-range-slider").assertDoesNotExist()
        compose.onNodeWithText("이 산책은 시간 구간을 고를 수 있는 측정 정보가 없어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("동선 2", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("전체 동선").assertIsDisplayed().performClick()
        compose.onNodeWithText("산책을 시작했어요", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("이 장면 앞뒤 30초 보기").assertDoesNotExist()
    }

    @Test fun `route details stay reachable and loading has its own explanation`() {
        show(ExplorerPanelExample.LOADING)
        compose.onNodeWithText("장면을 불러오고 있어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("경로 정보 · 구간과 전후 관계").performScrollTo().performClick()
        compose.onNodeWithText("동선 2", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("전체 산책").assertIsDisplayed().performClick()
        compose.onNodeWithText("경로 정보 접기").performScrollTo().assertIsDisplayed()
    }

    @Test fun `unaddressable scenes remain in whole reading and are explained when a range filters them out`() {
        val read = explorerPanelPreviewRead()
        val scenes = read.diary!!.scenes
        lateinit var state: WalkRouteExplorerState
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read); selectTimeRange(0, 30_000) } }
            com.daengs.app.ui.theme.DaengsTheme {
                WalkRouteExplorerPanel(state, {}, allScenes = scenes, sliceScenes = scenes.take(3), unknownTimeScenes = 1)
            }
        }
        compose.onNodeWithText("시각을 확인하지 못한 장면 1개는 전체 장면에서 볼 수 있어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("전체 산책").performClick()
        compose.onNodeWithText("전체 장면 6개").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("집 앞에서 마무리", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("시각을 확인하지 못한 장면 1개는 전체 장면에서 볼 수 있어요.").assertDoesNotExist()
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
            .put("explorerDetails", true).put("explorerLayout", 2)
            .put("group", org.json.JSONArray(listOf("scene-a")))
            .put("body", JSONObject().put("id", "scene-a").put("index", 3).put("offset", 12))
        compose.runOnIdle { runBlocking { memory.restore(saved) }
            assertEquals(321, memory.pendingExplorerOffset)
            assertTrue(memory.explorerDetails)
            assertEquals(listOf("scene-a"), memory.groupIds)
            assertEquals(3, memory.restoredBody!!.getInt("index"))
            val roundTrip = memory.snapshot()
            assertEquals(2, roundTrip.getInt("explorerLayout"))
            assertEquals(321, roundTrip.getInt("explorerOffset"))
            saved.remove("explorerLayout"); saved.remove("explorerDetails")
            runBlocking { memory.restore(saved) }
            assertEquals(0, memory.pendingExplorerOffset)
            assertFalse(memory.explorerDetails)
            assertEquals(listOf("scene-a"), memory.groupIds)
            assertEquals(12, memory.restoredBody!!.getInt("offset"))
        }
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
