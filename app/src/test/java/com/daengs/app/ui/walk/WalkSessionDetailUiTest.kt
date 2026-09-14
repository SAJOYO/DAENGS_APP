package com.daengs.app.ui.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.routeexplorer.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkSessionDetailUiTest {
    @get:Rule val compose = createComposeRule()
    private var rendered: android.view.View? = null

    private fun capture(name: String) = compose.runOnIdle {
        val view = requireNotNull(rendered)
        val file = java.io.File("build/outputs/" + name + ".png")
        file.parentFile?.mkdirs()
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun `summary and explorer share one map and returning to scenes pauses replay`() {
        var mounts = 0
        lateinit var explorer: WalkRouteExplorerState
        compose.setContent {
            val view = androidx.compose.ui.platform.LocalView.current
            SideEffect { rendered = view.rootView }
            val scope = rememberCoroutineScope()
            explorer = remember { WalkRouteExplorerState(scope, 20_000).apply {
                index = RouteExplorerIndex(explorerRoute(straightExplorerPath()))
            } }
            val summary = WalkSummary("saved", emptyList(), 0, 20_000, null, 100.0, 20_000, emptyList(), null)
            DaengsTheme {
                WalkDiaryMapContent(emptyList(), null, false, null, {}, {}, {}, {}, {}, {},
                    title = "저장된 산책", backLabel = WalkSessionOrigin.COMPLETION.backLabel,
                    summaryContent = { WalkSessionSummary(summary) },
                    backupAction = { WalkRouteBackupIcon(com.daengs.app.walk.sync.WalkRouteBackupState.PENDING, onRequest = {}) },
                    explorerSelected = explorer.panelOpen, onChooseExplorer = explorer::choosePanel,
                    explorerPanel = { WalkRouteExplorerPanel(explorer, {}) },
                    map = {
                        DisposableEffect(Unit) { mounts++; onDispose {} }
                        Box(Modifier.fillMaxSize().background(Color.LightGray).testTag("session-map"))
                    })
            }
        }
        compose.onNodeWithContentDescription("걸은 시간", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("평균 속도", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("홈으로").assertExists()
        capture("walk-session-overview")
        compose.onNodeWithText("동선 탐색").performClick()
        capture("walk-session-explorer")
        compose.onNodeWithContentDescription("재생 속도").performClick()
        compose.onNodeWithText("4×").performClick()
        compose.onNodeWithText("동선 재생").performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress)).assertIsDisplayed()
        capture("walk-session-replay")
        compose.runOnIdle {
            assertTrue(explorer.playing)
            explorer.tick(1_000)
            assertEquals(4_000L, explorer.elapsed)
        }
        compose.onNodeWithContentDescription("재생 속도").performClick()
        compose.onNodeWithText("8×").performClick()
        compose.runOnIdle {
            assertTrue(explorer.playing)
            assertEquals(4_000L, explorer.elapsed)
            assertEquals(RoutePlaybackSpeed.EIGHT, explorer.playbackSpeed)
        }
        compose.onNodeWithText("장면 0").performClick()
        compose.runOnIdle { assertFalse(explorer.playing); assertEquals(1, mounts) }
        compose.onNodeWithTag("session-map").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `speed menu and playback stay reachable with larger text on a small screen`() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { WalkRouteExplorerState(scope, 20_000).apply {
                index = RouteExplorerIndex(explorerRoute(straightExplorerPath()))
            } }
            DaengsTheme {
                val density = androidx.compose.ui.platform.LocalDensity.current
                CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(density.density, 1.3f)) {
                    WalkRouteExplorerPanel(state, {})
                }
            }
        }
        compose.onNodeWithText("전체 동선").assertIsDisplayed()
        compose.onNodeWithText("동선 재생").assertIsDisplayed()
        compose.onNodeWithContentDescription("재생 속도").performClick()
        listOf("1×", "2×", "4×", "8×", "16×").forEach { label ->
            compose.onAllNodesWithText(label).onLast().assertIsDisplayed()
        }
        compose.onNodeWithText("16×").performClick()
        compose.onNodeWithText("16×").assertIsDisplayed()
        compose.onNodeWithText("동선 재생").performClick()
        compose.onNodeWithText("일시정지").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `large font on a small screen keeps summary map and panel choices reachable`() {
        compose.setContent {
            val view = androidx.compose.ui.platform.LocalView.current
            SideEffect { rendered = view.rootView }
            DaengsTheme {
                val density = androidx.compose.ui.platform.LocalDensity.current
                CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(density.density, 1.3f)) {
                    WalkDiaryMapContent(emptyList(), null, false, null, {}, {}, {}, {}, {}, {},
                        title = "두부와 함께 남긴 저녁 산책",
                        summaryContent = { WalkSessionSummary(WalkSummary("s", emptyList(), 0, 1_800_000,
                            null, 1_200.0, 1_800_000, emptyList(), null), listOf("두부")) },
                        backupAction = { WalkRouteBackupIcon(com.daengs.app.walk.sync.WalkRouteBackupState.NEEDS_RETRY, onRequest = {}) },
                        explorerPanel = { androidx.compose.material3.Text("동선 탐색") },
                        map = { Box(Modifier.fillMaxSize().background(Color.LightGray).testTag("session-map")) })
                }
            }
        }
        compose.onNodeWithContentDescription("걸은 시간", substring = true).assertIsDisplayed()
        compose.onNodeWithText("장면 0").assertIsDisplayed()
        compose.onNodeWithText("동선 탐색").assertIsDisplayed()
        compose.onNodeWithTag("session-map").assertIsDisplayed()
        compose.onNodeWithTag("route-backup-request").assertIsDisplayed()
        capture("walk-session-small")
    }

    @Test fun `reading scene edits preserve summary and origin only changes the back destination`() {
        val scene = DiaryScene("saved/note", "saved", 0, "같은 장면", "저장한 내용", null, "")
        var origin by mutableStateOf(WalkSessionOrigin.COMPLETION)
        var edited = false
        compose.setContent {
            DaengsTheme {
                WalkDiaryMapContent(listOf(scene), scene, false, null, {}, {}, { edited = true }, {}, {}, {},
                    title = "저장된 산책", backLabel = origin.backLabel,
                    map = { Box(Modifier.fillMaxSize()) })
            }
        }
        compose.onNodeWithText("저장한 내용").assertIsDisplayed()
        compose.onNodeWithContentDescription("장면 수정").performScrollTo().performClick()
        assertTrue(edited)
        compose.runOnIdle { origin = WalkSessionOrigin.RECORDS }
        compose.onNodeWithContentDescription("산책 목록으로").assertExists()
        compose.onNodeWithText("저장한 내용").assertIsDisplayed()
    }
}
