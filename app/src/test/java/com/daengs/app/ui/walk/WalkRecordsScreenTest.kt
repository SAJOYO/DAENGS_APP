package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.records.WalkRecordsScreen
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.records.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRecordsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 10)
    private val records = (1..8).map { number -> record(number) }
    private val pets = listOf(pet("dog-1", "두부"), pet("dog-2", "보리"))

    @Test fun `tabs share all matching walks while list page and conditions survive restoration`() {
        val queries = Collections.synchronizedList(mutableListOf<WalkRecordsQuery>())
        val source = WalkRecordsSource { query -> queries.add(query); selectWalkRecords(records, query) }
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, pets, {}, {}, today = today)
        } } }
        waitText("1 페이지")
        compose.onNodeWithTag("records-view-overview").performClick()
        // The only trace belongs to page two, but the map uses the full selection.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-map-count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("records-map-count").assertTextEquals("선택 산책 8회 · 표시 흔적 1개")
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithText("다음 ›").performClick()
        waitText("2 페이지")
        val reads = queries.size
        compose.onNodeWithTag("records-view-overview").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 8회")
        // Only the oldest walk has a trace. It must be included even when it is not on page one.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-map-count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("records-map-count").assertTextEquals("선택 산책 8회 · 표시 흔적 1개")
        compose.onNodeWithTag("records-view-walks").performClick()
        waitText("2 페이지")
        assertEquals(reads, queries.size)
        restore.emulateSavedInstanceStateRestore()
        waitText("2 페이지")
        compose.onNodeWithTag("records-search").performTextReplacement("기록-8")
        waitText("1 페이지")
        waitCard("기록-8")
        compose.onNodeWithText("‹ 이전").assertIsNotEnabled()
    }

    @Test fun `condition edits commit together and cancel preserves the active result`() {
        val queries = Collections.synchronizedList(mutableListOf<WalkRecordsQuery>())
        val source = WalkRecordsSource { query -> queries.add(query); selectWalkRecords(records, query) }
        show(source)
        waitText("1 페이지")
        compose.onNodeWithText("다음 ›").performClick()
        waitText("2 페이지")
        val reads = queries.size
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-season-WINTER").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-cancel").performClick()
        waitText("2 페이지")
        assertEquals(reads, queries.size)
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-season-WINTER").assertIsNotSelected()
        compose.onNodeWithTag("records-dog-dog-1").performScrollTo().performClick()
        compose.onNodeWithTag("records-weather-RAIN").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitText("1 페이지")
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 4회")
        assertEquals("dog-1", queries.last().dogId)
        assertEquals(setOf(WalkDepartureWeather.RAIN), queries.last().filter.weather)
        compose.onNodeWithTag("records-view-overview").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 4회")
        compose.onNodeWithTag("records-reset").performClick()
        // Observe the rendered result so Compose can settle the reset and background selection.
        waitText("선택 산책 8회")
        assertEquals(null, queries.last().dogId)
        assertEquals(WalkHistoryFilter(), queries.last().filter)
        compose.onNodeWithTag("records-view-walks").performClick()
        waitText("1 페이지")
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 8회")
    }

    @Test fun `late previous responses cannot replace current search and retry preserves its query`() {
        val delayed = AtomicReference<Pair<WalkRecordsQuery, Continuation<WalkRecordsSelection>>?>()
        val failures = AtomicInteger()
        val source = WalkRecordsSource { query ->
            when (query.filter.keyword) {
                "느림" -> suspendCoroutine { delayed.set(query to it) }
                "실패" -> if (failures.incrementAndGet() == 1) error("source failed") else WalkRecordsSelection(query, emptyList())
                else -> selectWalkRecords(records, query)
            }
        }
        show(source)
        waitText("1 페이지")
        compose.onNodeWithTag("records-search").performTextReplacement("느림")
        compose.waitUntil(10_000) { delayed.get() != null }
        compose.onNodeWithTag("records-search").performTextReplacement("기록-8")
        waitCard("기록-8")
        compose.runOnIdle { delayed.get()!!.let { (query, continuation) ->
            continuation.resume(WalkRecordsSelection(query, listOf(record(1))))
        } }
        compose.waitForIdle()
        compose.onNode(hasText("기록-8") and !hasSetTextAction()).assertExists()
        compose.onNodeWithText("기록-1").assertDoesNotExist()
        compose.onNodeWithTag("records-search").performTextReplacement("실패")
        waitText("산책 기록을 불러오지 못했어요.")
        compose.onNodeWithTag("records-search").assertTextContains("실패")
        compose.onNodeWithText("기록-8").assertDoesNotExist()
        compose.onNodeWithText("다시 시도").performClick()
        waitText("조건에 맞는 산책이 없어요.")
        compose.onNodeWithTag("records-search").assertTextContains("실패")
        assertEquals(2, failures.get())
    }

    private fun show(source: WalkRecordsSource) = compose.setContent { DaengsTheme {
        CompositionLocalProvider(LocalInspectionMode provides true) { WalkRecordsScreen(source, pets, {}, {}, today = today) }
    } }

    private fun waitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitCard(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text) and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
    }

    private fun record(number: Int): WalkRecord {
        val at = today.minusDays((9 - number).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val id = "record-$number"
        return WalkRecord(WalkSummary(id, listOf(if (number % 2 == 0) "dog-1" else "dog-2"), at, at + 600_000,
            RecordedWeather(if (number % 2 == 0) 61 else 0, true, 22f), 500.0, 600_000,
            emptyList(), null), title = "기록-$number", notes = listOf("나무 아래"),
            trace = if (number == 1) WalkTraceSheet(id, cells = setOf(SpatialDiaryCellId(832649, 375728))) else null)
    }

    private fun pet(id: String, name: String) = Pet(id, name, "maltese", null, null, null, null, null, isPrimary = false)
}
