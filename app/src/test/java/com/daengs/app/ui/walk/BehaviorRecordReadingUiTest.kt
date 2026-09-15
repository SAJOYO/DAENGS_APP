package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.records.BehaviorRecordList
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.records.*
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
class BehaviorRecordReadingUiTest {
    @get:Rule val compose = createComposeRule()
    private val summary = WalkSummary("s", emptyList(), 0, 10000, null, 0.0, 10000, emptyList(), null)
    private val a = WalkEntry("a", "s", WalkMomentType.SNIFFING, 1000)
    private val b = a.copy(id = "b", recordedAtMillis = 2000)
    private val record = WalkRecord(summary, "그날의 산책", entries = listOf(a, b))
    private val records = listOf(a, b).map { WalkBehaviorRecord(it, record) }
    private val diary = DiaryWalk(summary, listOf(a, b).map {
        DiaryScene("scene-${it.id}", "s", it.recordedAtMillis, "저장된 제목 ${it.id}",
            "직접 고친 본문 ${it.id}\n줄바꿈도 유지", null, "", entryId = it.id,
            originalNotes = listOf("원본 메모 ${it.id}"))
    }, "", sourceEntries = listOf(a, b))
    private fun await(text: String) = compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun `only selected action reads saved prose and uses the exact action when continuing`() {
        var selected by mutableStateOf<String?>(null)
        var reads = 0
        var opened: DiaryActionTarget? = null
        val updates = MutableStateFlow<DiaryWalk?>(diary)
        val source = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = WalkRecordsSelection(query, listOf(record))
            override fun observeDiary(record: WalkRecord) = flow { reads++; emitAll(updates) }
        }
        compose.setContent { DaengsTheme {
            BehaviorRecordList(records, emptyList(), selected, emptySet(), emptySet(), { selected = it }, {}, {},
                Modifier.fillMaxSize(), readingSource = source, onOpenAction = { opened = it })
        } }
        compose.runOnIdle { assertEquals(0, reads); selected = records[1].key }
        await("직접 고친 본문 b\n줄바꿈도 유지")
        compose.onNodeWithText("원본 메모 b").assertExists()
        compose.onNodeWithText("직접 고친 본문 a\n줄바꿈도 유지").assertDoesNotExist()
        compose.onNodeWithTag("records-behavior-open-${records[1].key}").performScrollTo().performClick()
        assertEquals(DiaryActionTarget("s", "b"), opened)
        compose.runOnIdle { updates.value = diary.copy(scenes = diary.scenes.map { it.copy(body = "갱신된 본문 ${it.entryId}") }) }
        await("갱신된 본문 b")
        compose.runOnIdle { updates.value = diary.copy(scenes = emptyList()) }
        await("위치 없이 남긴 행동")
        compose.onNodeWithText("갱신된 본문 b").assertDoesNotExist()
        compose.runOnIdle { updates.value = diary.copy(sourceEntries = listOf(a)) }
        await("삭제되었거나 현재 읽을 수 없는 행동이에요.")
    }

    @Test fun `late source cannot replace reading after switching source`() {
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val slow = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = WalkRecordsSelection(query, listOf(record))
            override fun observeDiary(record: WalkRecord) = flow {
                try { gate.await(); emit(diary) } finally { cancelled = true }
            }
        }
        val fast = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = WalkRecordsSelection(query, listOf(record))
            override fun observeDiary(record: WalkRecord) = flowOf(diary.copy(scenes = diary.scenes.map { it.copy(body = "새 계정의 본문") }))
        }
        var source: WalkRecordsSource by mutableStateOf(slow)
        compose.setContent { DaengsTheme {
            BehaviorRecordList(records, emptyList(), records[0].key, emptySet(), emptySet(), {}, {}, {},
                Modifier.fillMaxSize(), readingSource = source)
        } }
        await("일기를 불러오는 중…")
        compose.runOnIdle { source = fast }
        await("새 계정의 본문")
        compose.runOnIdle { gate.complete(Unit); assertTrue(cancelled) }
        compose.onNodeWithText("직접 고친 본문 a\n줄바꿈도 유지").assertDoesNotExist()
    }

    @Test fun `read failure retains original action and retry shows stored scene`() {
        var attempts = 0
        val source = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = WalkRecordsSelection(query, listOf(record))
            override fun observeDiary(record: WalkRecord) = flow {
                if (attempts++ == 0) error("read failed")
                emit(diary)
            }
        }
        compose.setContent { DaengsTheme {
            BehaviorRecordList(records, emptyList(), records[0].key, emptySet(), emptySet(), {}, {}, {},
                Modifier.fillMaxSize(), readingSource = source)
        } }
        await("다시 불러오기")
        compose.onNode(hasTestTag("records-behavior-entry-${records[0].key}") and hasText("위치 없이 남긴 행동")).assertExists()
        compose.onNodeWithText("다시 불러오기").performClick()
        await("직접 고친 본문 a\n줄바꿈도 유지")
    }
}
