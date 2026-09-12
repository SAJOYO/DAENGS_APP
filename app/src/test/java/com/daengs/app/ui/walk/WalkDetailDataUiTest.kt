package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.auth.AccountScope
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.WalkDetailActions
import com.daengs.app.walk.detail.WalkDetailSource
import com.daengs.app.walk.diary.*
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
        val detail = readCompletedRoute(RecordedSession(id, startedAtMillis = 0, endedAtMillis = 1000), emptyList())
        val diary = DiaryWalk(detail.summary, listOf(DiaryScene("$id/scene", id, 500,
            "저장된 장면 $id", "함께 걸었다.", null, "")), "", published = published)
        override val changes = flowOf(Unit)
        override val entries = flowOf(emptyList<WalkEntry>())
        override fun isCurrentAccount() = true
        override suspend fun load() = detail
        override fun observeDiary(detail: WalkSessionDetail) = flowOf(diary)
    }

    private class Actions : WalkDetailActions {
        var opened = 0
        var prepared = 0
        var generated = 0
        var generate: suspend () -> Unit = {}
        override suspend fun open() { opened++ }
        override fun prepareDiary() { prepared++ }
        override suspend fun generateDiary() { generated++; generate() }
        override suspend fun saveEntry(entry: WalkEntry) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun saveScene(scene: StoryboardScene, title: String, body: String) = Unit
        override suspend fun deletePhoto(id: String) = Unit
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
}
