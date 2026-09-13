package com.daengs.app.ui.walk

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.auth.AccountScope
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.WalkDetailSource
import com.daengs.app.walk.detail.WalkDetailActions
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryScenePresentationTest {
    @get:Rule val compose = createComposeRule()
    private var rendered: android.view.View? = null
    private val scene = DiaryScene("s/a", "s", 1000, "물 한 모금, 잠깐의 쉼", "물을 마시고 잠깐 쉬었어요.", null, "", entryId = "a")
    private val summary = WalkSummary("s", emptyList(), 0, 2000, null, 0.0, 2000, emptyList(), null)

    @Test fun `production screen uses diary snapshot rather than a newer independent entry stream`() {
        val detail = readCompletedRoute(RecordedSession("s", startedAtMillis = 0, endedAtMillis = 2000), emptyList())
        val note = WalkEntry("a", "s", WalkMomentType.NOTE, 1000, note = "물 한 모금")
        val diary = MutableStateFlow(DiaryWalk(detail.summary, listOf(scene), "", sourceEntries = listOf(note)))
        val data = object : WalkDetailSource, WalkDetailActions {
            override val changes = flowOf(Unit)
            override val entries = flowOf(listOf(note.copy(type = WalkMomentType.BARKING)))
            override fun isCurrentAccount() = true
            override suspend fun load() = detail
            override fun observeDiary(detail: WalkSessionDetail) = diary
            override suspend fun open() {}
            override fun prepareDiary() {}
            override suspend fun generateDiary() {}
            override suspend fun saveEntry(entry: WalkEntry) {}
            override suspend fun deleteEntry(id: String) {}
            override suspend fun saveScene(scene: StoryboardScene, title: String, body: String) {}
            override suspend fun deleteScene(scene: StoryboardScene) {}
            override suspend fun deletePhoto(id: String) {}
        }
        compose.setContent { DaengsTheme {
            WalkDiaryMapForAccount("s", data, data, {}, Modifier, emptyList(), WalkSessionOrigin.RECORDS,
                AccountScope("owner", 1), {}, { null })
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("직접 남긴 메모").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("짖기 기록").assertDoesNotExist()
        compose.onNodeWithText(scene.title).performClick()
        compose.runOnIdle { diary.value = diary.value.copy(sourceEntries = listOf(note.copy(type = WalkMomentType.BARKING))) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("짖기 기록").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(scene.title).assertExists()
        compose.onNodeWithText(scene.body).assertExists()
        compose.onNodeWithText("직접 남긴 메모").assertDoesNotExist()
    }

    @Test fun `same source presentation follows list detail and source changes without moving drawer`() {
        var walk by mutableStateOf(DiaryWalk(summary, listOf(scene), "", sourceEntries = listOf(
            WalkEntry("a", "s", WalkMomentType.NOTE, 1000, note = "물 한 모금"))))
        var selected by mutableStateOf<DiaryScene?>(null)
        var removal: DiaryScene? = null
        compose.setContent { DaengsTheme {
            val view = LocalView.current
            SideEffect { rendered = view.rootView }
            val kinds = remember(walk) { walk.sceneKinds() }
            WalkDiaryMapContent(walk.scenes, selected, false, null, { selected = it }, { selected = null }, {}, {}, {}, {},
                sceneKinds = kinds, onDelete = { removal = it }, explorerPanel = {}, map = { Box(Modifier.fillMaxSize()) })
        } }
        val top = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("직접 남긴 메모").assertExists()
        compose.onNodeWithText(scene.title).performClick()
        compose.onNodeWithText(scene.body).assertExists()
        compose.onNodeWithContentDescription("직접 남긴 메모").assertExists()
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithContentDescription("장면 삭제").performClick()
        compose.runOnIdle { assertEquals(scene, removal); walk = walk.copy(sourceEntries = emptyList()) }
        compose.onNodeWithText("산책 장면").assertExists()
        compose.onNodeWithText("직접 남긴 메모").assertDoesNotExist()
        compose.onNodeWithText(scene.body).assertExists()
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        capture("scene-detail")
    }

    @Test fun `range scenes use the same badge and remain selectable`() {
        val read = explorationRead(); lateinit var state: WalkRouteExplorerState
        var clicked: DiaryScene? = null
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read); selectTimeRange(0, 60_000) } }
            DaengsTheme { MeasurementTimeControls(state, listOf(scene), { clicked = it }, mapOf(scene.id to DiarySceneKind.NOTE)) }
        }
        compose.onNodeWithContentDescription("직접 남긴 메모").assertExists()
        compose.onNodeWithText(scene.title).performClick()
        compose.runOnIdle { assertEquals(scene, clicked) }
    }

    @Test @Config(qualifiers = "w320dp-h1200dp")
    fun `all source badges render in a narrow layout with enlarged font`() {
        compose.setContent {
            val view = LocalView.current
            SideEffect { rendered = view.rootView }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DiarySceneKindsPreview()
            }
        }
        compose.onNodeWithContentDescription("킁킁 기록").assertExists()
        compose.onNodeWithContentDescription("배설 기록").assertExists()
        compose.onNodeWithContentDescription("사진 기록").assertExists()
        capture("scene-kinds-320")
    }

    private fun capture(name: String) {
        val directory = System.getenv("DAENGS_SCENE_PREVIEW_DIR")?.let(::File) ?: return
        directory.mkdirs()
        compose.runOnIdle {
            val view = requireNotNull(rendered)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
