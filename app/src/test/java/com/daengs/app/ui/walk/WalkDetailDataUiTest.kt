package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.auth.AccountScope
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.WalkDetailActions
import com.daengs.app.walk.detail.WalkDetailSource
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.measuredSceneDetail
import com.daengs.app.walk.routeexplorer.measuredScene
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The actual detail orchestration works without DaengsApp, Room or a network client in the UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkDetailDataUiTest {
    @get:Rule val compose = createComposeRule()

    private class Source(val id: String = "s", published: Boolean = false) : WalkDetailSource {
        var loadGate: CompletableDeferred<Unit>? = null
        val detail = readCompletedRoute(RecordedSession(id, startedAtMillis = 0, endedAtMillis = 1000), emptyList())
        val diary = DiaryWalk(detail.summary, listOf(DiaryScene("$id/scene", id, 500,
            "저장된 장면 $id", "함께 걸었다.", null, "")), "", published = published)
        override val changes = flowOf(Unit)
        override val entries = flowOf(emptyList<WalkEntry>())
        override fun isCurrentAccount() = true
        override suspend fun load(): WalkSessionDetail {
            loadGate?.await()
            return detail
        }
        override fun observeDiary(detail: WalkSessionDetail) = flowOf(diary)
    }

    private class Actions : WalkDetailActions {
        var opened = 0
        var prepared = 0
        var generated = 0
        var generate: suspend () -> Unit = {}
        var save: suspend (WalkEntry) -> Unit = {}
        var saveScene: suspend (String, String) -> Unit = { _, _ -> }
        override suspend fun open() { opened++ }
        override fun prepareDiary() { prepared++ }
        override suspend fun generateDiary() { generated++; generate() }
        override suspend fun saveEntry(entry: WalkEntry) = save(entry)
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun saveScene(scene: StoryboardScene, title: String, body: String) = saveScene(title, body)
        override suspend fun deletePhoto(id: String) = Unit
        var remove: suspend (StoryboardScene) -> Unit = {}
        override suspend fun deleteScene(scene: StoryboardScene) = remove(scene)
    }

    @Composable private fun Screen(source: Source, actions: Actions) {
        DaengsTheme {
            WalkDiaryMapForAccount(source.id, source, actions, {}, Modifier, emptyList(),
                WalkSessionOrigin.RECORDS, AccountScope("owner", 1), {}, { null })
        }
    }

    private fun awaitScene(id: String = "s") {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("저장된 장면 $id").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun menu() = compose.onNodeWithContentDescription("일기 메뉴").performClick()

    @Test fun `measured detail retains selected edited scene and compact drawer through replacement and reentry`() {
        var detail = measuredSceneDetail()
        val changes = MutableStateFlow(0)
        var gate: CompletableDeferred<Unit>? = null
        val source = object : WalkDetailSource {
            override val changes = changes.map { Unit }
            override val entries = flowOf(emptyList<WalkEntry>())
            override fun isCurrentAccount() = true
            override suspend fun load() = detail
            override fun observeDiary(detail: WalkSessionDetail) = flow {
                gate?.await()
                emit(DiaryWalk(detail.summary, listOf(measuredScene(detail, title = "직접 고친 장면")
                    .copy(body = "사용자가 남긴 본문")), ""))
            }
        }
        val restore = StateRestorationTester(compose)
        restore.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { DaengsTheme {
            WalkDiaryMapForAccount("measured", source, Actions(), {}, Modifier, emptyList(),
                WalkSessionOrigin.RECORDS, AccountScope("owner", 1), {}, { null })
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("직접 고친 장면").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("직접 고친 장면").performClick()
        compose.onNodeWithText("사용자가 남긴 본문").assertIsDisplayed()
        compose.onNodeWithTag("diary-sheet-handle").performTouchInput { swipeDown(startY = 10f, endY = 600f) }
        val compact = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { gate = CompletableDeferred(); detail = measuredSceneDetail("measurement-b"); changes.value++ }
        compose.onNodeWithTag("diary-scene-body").assertExists()
        assertEquals(compact, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { gate!!.complete(Unit) }
        compose.waitForIdle()
        restore.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("diary-scene-body").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(compact, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNode(hasText("장면 1") and hasClickAction()).performClick()
        compose.onNodeWithText("사용자가 남긴 본문").assertIsDisplayed()
    }

    @Test fun `saved scene and compact drawer survive delayed reload in the actual detail screen`() {
        val source = Source(); val actions = Actions()
        val restore = StateRestorationTester(compose)
        restore.setContent { Screen(source, actions) }
        awaitScene()
        compose.onNodeWithText("저장된 장면 s").performClick()
        compose.onNodeWithText("함께 걸었다.").assertIsDisplayed()
        compose.onNodeWithTag("diary-sheet-handle").performTouchInput {
            swipeDown(startY = 10f, endY = 600f)
        }
        val compact = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        val reload = CompletableDeferred<Unit>()
        source.loadGate = reload
        restore.emulateSavedInstanceStateRestore()
        assertEquals(compact, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { reload.complete(Unit) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("diary-scene-body").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(compact, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("함께 걸었다.").assertIsNotDisplayed()
        compose.onNode(hasText("장면 1") and hasClickAction()).performClick()
        compose.onNodeWithText("함께 걸었다.").assertIsDisplayed()
        // Source is injected; this exercises production UI/read adoption, not Room or process restart.
    }

    @Test fun `injected generation keeps duplicate prevention and failure retry in the screen`() {
        val source = Source(); val actions = Actions()
        val result = CompletableDeferred<Unit>()
        actions.generate = { result.await() }
        compose.setContent { Screen(source, actions) }
        awaitScene()
        assertEquals(1, actions.opened)
        menu(); compose.onNodeWithText("일기 생성·갱신").performClick()
        menu(); compose.onNodeWithText("준비 중").assertIsNotEnabled()
        assertEquals(1, actions.generated)
        // Close the menu without changing a camera or introducing a provider call.
        compose.onNodeWithText("전체 동선 보기").performClick()
        compose.runOnIdle { result.completeExceptionally(IllegalStateException("offline")) }
        // Notices are a lazy item after the scenes and can be below the compact drawer viewport.
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(
            hasText("일기를 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요."))
        compose.onNodeWithText("일기를 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요.").assertExists()
        actions.generate = {}
        menu(); compose.onNodeWithText("일기 생성·갱신").performClick()
        compose.runOnIdle { assertEquals(2, actions.generated) }
    }

    @Test fun `published diary refresh wakes publication without regenerating`() {
        val source = Source(published = true); val actions = Actions()
        compose.setContent { Screen(source, actions) }
        awaitScene()
        menu(); compose.onNodeWithText("새로고침").performClick()
        compose.runOnIdle {
            assertEquals(1, actions.prepared)
            assertEquals(0, actions.generated)
            assertEquals(1, actions.opened)
        }
    }

    @Test fun `replacing the keyed detail cancels the old generation and clears its UI state`() {
        val first = Source(); val next = Source("next")
        val oldActions = Actions(); val nextActions = Actions()
        var cancelled = false
        oldActions.generate = { try { awaitCancellation() } finally { cancelled = true } }
        var replaced by mutableStateOf(false)
        compose.setContent {
            key(replaced) { Screen(if (replaced) next else first, if (replaced) nextActions else oldActions) }
        }
        awaitScene()
        menu(); compose.onNodeWithText("일기 생성·갱신").performClick()
        compose.runOnIdle { replaced = true }
        awaitScene("next")
        compose.onNodeWithText("저장된 장면 s").assertDoesNotExist()
        compose.runOnIdle { assertTrue(cancelled); assertEquals(1, nextActions.opened) }
        menu(); compose.onNodeWithText("일기 생성·갱신").assertIsEnabled()
    }

    @Test fun `entry draft survives a failed save and closes only after successful retry`() {
        val source = Source(); val actions = Actions()
        val submitted = mutableListOf<WalkEntry>()
        val first = CompletableDeferred<Unit>()
        actions.save = { submitted += it; first.await() }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { WalkDetailState(source, actions, scope, {}) }
            val editors = rememberWalkDiaryEditorState("s")
            LaunchedEffect(Unit) { editors.beginAdding(source.detail.summary, false) }
            if (editors.editorOpen) WalkEntryEditorContent(emptyList(), editors.entry, emptyList(), state.entryError, state.savingEntry,
                { state.saveEntry(it, editors::entrySaved) }, {}, editors::dismissEntry,
                // Same text fields and buttons; avoid Robolectric's native-dialog idle limitation.
                container = { title, body, confirm, dismiss -> Column { title(); body(); confirm(); dismiss() } })
        }
        compose.onNodeWithText("기억하고 싶은 내용을 적어 주세요").performTextReplacement("실패해도 남을 초안")
        compose.onNodeWithText("저장").performClick()
        compose.runOnIdle { first.completeExceptionally(IllegalStateException("편집 충돌")) }
        compose.onNodeWithText("편집 충돌").assertExists()
        compose.onNodeWithText("실패해도 남을 초안").assertExists()
        actions.save = { submitted += it }
        compose.onNodeWithText("저장").performClick()
        compose.onNodeWithText("실패해도 남을 초안").assertDoesNotExist()
        assertEquals(listOf("실패해도 남을 초안", "실패해도 남을 초안"), submitted.map { it.note })
    }

    @Test fun `scene title and body survive failure and duplicate clicks stay disabled during saving`() {
        val source = Source(); val actions = Actions()
        val scene = source.diary.scenes.single().copy(source = StoryboardScene("scene", 500, "제목", "내용", "", "f"))
        val submitted = mutableListOf<Pair<String, String>>()
        val first = CompletableDeferred<Unit>()
        actions.saveScene = { title, body -> submitted += title to body; first.await() }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { WalkDetailState(source, actions, scope, {}) }
            val editors = rememberWalkDiaryEditorState("s")
            LaunchedEffect(Unit) { editors.editScene(scene) }
            if (editors.editingScene != null) DiarySceneEditor(scene, state.savingScene, state.sceneError,
                { title, body -> state.saveScene(scene, title, body, editors::dismissScene) }, editors::dismissScene,
                dialog = { title, body, confirm, dismiss -> Column { title(); body(); confirm(); dismiss() } })
        }
        compose.onNodeWithText("장면 제목").performTextReplacement("바꾼 제목")
        compose.onNodeWithText("장면 내용").performTextReplacement("남길 장면 내용")
        compose.onNodeWithText("저장").performClick()
        compose.onNodeWithText("저장 중").assertIsNotEnabled()
        compose.runOnIdle { first.completeExceptionally(IllegalStateException("저장 실패")) }
        compose.onNodeWithText("저장 실패").assertExists()
        compose.onNodeWithText("바꾼 제목").assertExists()
        compose.onNodeWithText("남길 장면 내용").assertExists()
        actions.saveScene = { title, body -> submitted += title to body }
        compose.onNodeWithText("저장").performClick()
        compose.onNodeWithText("장면 수정").assertDoesNotExist()
        assertEquals(listOf("바꾼 제목" to "남길 장면 내용", "바꾼 제목" to "남길 장면 내용"), submitted)
    }
}
