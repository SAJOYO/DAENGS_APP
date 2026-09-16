package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.daengs.app.auth.AccountScope
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.*
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryActionEntryUiTest {
    @get:Rule val compose = createComposeRule()
    private class Data(val linked: Boolean) : WalkDetailSource, WalkDetailActions {
        val gate = CompletableDeferred<Unit>()
        var generated = 0
        val detail = readCompletedRoute(RecordedSession("s", startedAtMillis = 0, endedAtMillis = 1000), emptyList())
        val originals = listOf(WalkEntry("a", "s", WalkMomentType.SNIFFING, 100),
            WalkEntry("b", "s", WalkMomentType.SNIFFING, 200))
        val revision = MutableStateFlow(0)
        override val changes = revision.map { Unit }
        override val entries = flowOf(originals)
        override fun isCurrentAccount() = true
        override suspend fun load() = detail
        override fun observeDiary(detail: WalkSessionDetail) = flow {
            gate.await()
            emit(DiaryWalk(detail.summary, if (!linked) emptyList() else originals.map {
                DiaryScene("scene-${it.id}", "s", it.recordedAtMillis, "장면 ${it.id}", "본문 ${it.id}", null, "", entryId = it.id)
            }, "", sourceEntries = originals))
        }
        override suspend fun open() = Unit
        override fun prepareDiary() = Unit
        override suspend fun generateDiary() { generated++ }
        override suspend fun saveEntry(entry: WalkEntry) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun saveScene(scene: StoryboardScene, title: String, body: String) = Unit
        override suspend fun deleteScene(scene: StoryboardScene) = Unit
        override suspend fun deletePhoto(id: String) = Unit
    }
    @Composable private fun Screen(data: Data, entry: String) {
        CompositionLocalProvider(LocalInspectionMode provides true) { DaengsTheme {
            WalkDiaryMapForAccount("s", data, data, {}, Modifier, emptyList(), WalkSessionOrigin.RECORDS,
                AccountScope("owner", 1), {}, { null }, initialAction = DiaryActionTarget("s", entry))
        } }
    }
    @Test fun `delayed diary opens selected second sniff`() {
        val data = Data(true)
        compose.setContent { Screen(data, "b") }
        compose.runOnIdle { data.gate.complete(Unit) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("diary-scene-body").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("본문 b").assertIsDisplayed()
        compose.onNodeWithText("본문 a").assertDoesNotExist()
        compose.onNodeWithText("‹ 장면 목록").performClick()
        compose.onNodeWithText("장면 a").performClick()
        compose.onNodeWithText("본문 a").assertIsDisplayed()
        compose.runOnIdle { data.revision.value++ }
        compose.waitForIdle()
        compose.onNodeWithText("본문 a").assertIsDisplayed()
        assertEquals(0, data.generated)
    }
    @Test fun `unlinked action opens original record without generating a scene`() {
        val data = Data(false)
        val restore = StateRestorationTester(compose)
        restore.setContent { Screen(data, "b") }
        compose.runOnIdle { data.gate.complete(Unit) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("행동 기록 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("위치 없이 남긴 행동").assertIsDisplayed()
        compose.onNodeWithTag("diary-scene-body").assertDoesNotExist()
        restore.emulateSavedInstanceStateRestore()
        compose.waitUntil(10000) { compose.onAllNodesWithText("행동 기록 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("위치 없이 남긴 행동").assertIsDisplayed()
        assertEquals(0, data.generated)
    }
}
