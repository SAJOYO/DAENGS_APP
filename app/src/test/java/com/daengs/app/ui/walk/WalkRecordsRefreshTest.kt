package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.records.WalkRecordsScreen
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.records.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRecordsRefreshTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 10)
    private val pets = listOf(Pet("dog-1", "두부", "maltese", null, null, null, null, null, isPrimary = false))

    @Test fun `repeated refreshes retain the current page and later edits still replace records`() {
        val source = GatedSource((1..8).map(::record))
        show(source)
        waitText("1 페이지")
        compose.onNodeWithText("다음 ›").performClick()
        waitText("2 페이지")
        repeat(3) {
            val gate = CompletableDeferred<Unit>()
            val before = source.reads.get()
            try {
                compose.runOnIdle { source.gate.set(gate); source.revisions.value++ }
                compose.waitUntil(10_000) { source.reads.get() > before }
                compose.onNodeWithText("산책 기록을 찾고 있어요.").assertDoesNotExist()
                compose.onNodeWithText("2 페이지").assertExists()
                compose.onNodeWithText("기록-3").assertExists()
                compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 8회")
                gate.complete(Unit)
                compose.waitUntil(10_000) { source.completed.get() > before }
                compose.waitForIdle()
                compose.onNodeWithText("2 페이지").assertExists()
                compose.onNodeWithText("산책 기록을 찾고 있어요.").assertDoesNotExist()
            } finally {
                gate.complete(Unit)
            }
        }
        // Unit-valued notifications must not be deduplicated: an actual edit still reaches the UI.
        compose.runOnIdle {
            source.gate.set(null)
            source.saved.set(source.saved.get().map {
                if (it.summary.sessionId == "record-3") it.copy(title = "바뀐 기록-3") else it
            })
            source.revisions.value++
        }
        waitText("바뀐 기록-3")
        compose.onNodeWithText("2 페이지").assertExists()
        compose.onNodeWithText("기록-3").assertDoesNotExist()
        assertEquals(5, source.reads.get())
    }

    @Test fun `equal refresh results keep loaded map traces across tab changes`() {
        val source = GatedSource(listOf(record(1)))
        show(source)
        waitText("1 페이지")
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 1회 · 표시 흔적 1개")
        compose.onNodeWithTag("records-map-sheet-toggle").performClick()
        compose.onNodeWithTag("records-map-list").performScrollToNode(hasTestTag("records-map-record-record-1"))
        compose.onNodeWithTag("records-map-record-record-1").performClick()
        assertEquals(1, source.traceReads.get())
        val gate = CompletableDeferred<Unit>()
        val before = source.reads.get()
        try {
            compose.runOnIdle { source.gate.set(gate); source.revisions.value++ }
            compose.waitUntil(10_000) { source.reads.get() > before }
            compose.onNodeWithText("산책 기록을 찾고 있어요.").assertDoesNotExist()
            compose.onNodeWithTag("records-overview-map").assertExists()
            compose.onNodeWithTag("records-map-count").assertTextEquals("선택 산책 1회 · 표시 흔적 1개")
            gate.complete(Unit)
            compose.waitUntil(10_000) { source.completed.get() > before }
            compose.waitForIdle()
            compose.onNodeWithTag("records-view-walks").performClick()
            waitText("1 페이지")
            compose.onNodeWithTag("records-view-overview").performClick()
            waitText("선택 산책 1회 · 표시 흔적 1개")
            compose.onNodeWithTag("records-map-record-record-1").assertIsSelected()
            assertEquals(1, source.traceReads.get())
        } finally {
            gate.complete(Unit)
        }
    }

    @Test fun `replacing a source clears old cards immediately and rejects its late refresh`() {
        val revisions = MutableStateFlow(0)
        val oldGate = CompletableDeferred<Unit>()
        val oldStarted = AtomicInteger()
        val old = object : WalkRecordsSource {
            override val changes = revisions.map { Unit }
            override suspend fun select(query: WalkRecordsQuery): WalkRecordsSelection {
                if (revisions.value > 0) {
                    oldStarted.incrementAndGet()
                    // Simulate a storage/provider response that ignores cancellation.
                    withContext(NonCancellable) { oldGate.await() }
                }
                return selectWalkRecords(listOf(record(1)), query)
            }
        }
        val replacement = GatedSource(listOf(record(2)))
        val nextGate = CompletableDeferred<Unit>()
        replacement.gate.set(nextGate)
        val active = mutableStateOf<WalkRecordsSource>(old)
        compose.setContent { DaengsTheme {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                WalkRecordsScreen(active.value, pets, {}, {}, today = today)
            }
        } }
        try {
            waitText("기록-1")
            compose.runOnIdle { revisions.value++ }
            compose.waitUntil(10_000) { oldStarted.get() == 1 }
            compose.onNodeWithText("기록-1").assertExists()
            compose.runOnIdle { active.value = replacement }
            // Drain the source-change recomposition before polling a background counter.
            waitText("산책 기록을 찾고 있어요.")
            compose.waitUntil(10_000) { replacement.reads.get() == 1 }
            compose.onNodeWithText("기록-1").assertDoesNotExist()
            compose.onNodeWithTag("records-count").assertTextEquals("불러오는 중")
            oldGate.complete(Unit)
            compose.waitForIdle()
            compose.onNodeWithText("기록-1").assertDoesNotExist()
            nextGate.complete(Unit)
            waitText("기록-2")
            compose.onNodeWithText("기록-1").assertDoesNotExist()
            compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 1회")
        } finally {
            oldGate.complete(Unit)
            nextGate.complete(Unit)
        }
    }

    @Test fun `a superseded refresh cannot resurrect a deleted walk`() {
        val source = GatedSource(listOf(record(1)))
        show(source)
        waitText("기록-1")
        val oldGate = CompletableDeferred<Unit>()
        try {
            compose.runOnIdle { source.gate.set(oldGate); source.revisions.value++ }
            compose.waitUntil(10_000) { source.reads.get() == 2 }
            compose.onNodeWithText("기록-1").assertExists()
            compose.runOnIdle {
                source.saved.set(emptyList())
                source.gate.set(null)
                source.revisions.value++
            }
            waitText("아직 산책 기록이 없어요.")
            assertEquals(1, source.cancelled.get())
            compose.onNodeWithText("기록-1").assertDoesNotExist()
            oldGate.complete(Unit)
            compose.waitForIdle()
            compose.onNodeWithText("기록-1").assertDoesNotExist()
            compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 0회")
            assertEquals(3, source.reads.get())
        } finally {
            oldGate.complete(Unit)
        }
    }

    /** Capture a detached snapshot before the gate so a late read really does contain old rows. */
    private class GatedSource(initial: List<WalkRecord>) : WalkRecordsSource {
        val revisions = MutableStateFlow(0)
        val saved = AtomicReference(initial)
        val gate = AtomicReference<CompletableDeferred<Unit>?>(null)
        val reads = AtomicInteger()
        val completed = AtomicInteger()
        val cancelled = AtomicInteger()
        val traceReads = AtomicInteger()
        override val changes = revisions.map { Unit }

        override suspend fun select(query: WalkRecordsQuery): WalkRecordsSelection {
            val snapshot = selectWalkRecords(saved.get(), query)
            val pending = gate.get()
            reads.incrementAndGet()
            try {
                pending?.await()
                completed.incrementAndGet()
                return snapshot
            } catch (failure: CancellationException) {
                cancelled.incrementAndGet()
                throw failure
            }
        }

        override suspend fun loadTraces(selection: WalkRecordsSelection): WalkRecordsSelection {
            traceReads.incrementAndGet()
            return WalkRecordsSelection(selection.query, selection.records.map {
                it.copy(trace = WalkTraceSheet(it.summary.sessionId,
                    cells = setOf(SpatialDiaryCellId(832649, 375728))), traceState = WalkTraceState.READY)
            })
        }
    }

    private fun show(source: WalkRecordsSource) = compose.setContent { DaengsTheme {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, pets, {}, {}, today = today)
        }
    } }

    private fun waitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun record(number: Int): WalkRecord {
        val at = today.minusDays((9 - number).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return WalkRecord(WalkSummary("record-$number", listOf("dog-1"), at, at + 600_000,
            RecordedWeather(0, true, 22f), 500.0, 600_000, emptyList(), null),
            title = "기록-$number", traceState = WalkTraceState.NOT_REQUESTED)
    }
}
