package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.records.WalkRecordsScreen
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.records.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        compose.onNodeWithTag("records-count").assertDoesNotExist()
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
        waitText("선택 산책 4회 · 표시 흔적 0개")
        compose.onNodeWithTag("records-count").assertDoesNotExist()
        compose.onNodeWithTag("records-reset").performClick()
        // Observe the rendered result so Compose can settle the reset and background selection.
        waitText("선택 산책 8회 · 표시 흔적 1개")
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

    @Test fun `map selection hide detail and recreation preserve the full query until conditions change`() {
        val sample = (1..3).map { number ->
            val entry = record(number)
            entry.copy(summary = entry.summary.copy(segments = listOf(listOf(
                LocationSample(GeoPoint(37.544, 127.037), 1_000),
                LocationSample(GeoPoint(37.545, 127.038), 2_000),
            ))), trace = if (number <= 2) WalkTraceSheet(entry.summary.sessionId,
                cells = setOf(SpatialDiaryCellId(832649, 375728))) else null)
        }
        val queries = Collections.synchronizedList(mutableListOf<WalkRecordsQuery>())
        val source = WalkRecordsSource { query -> queries.add(query); selectWalkRecords(sample, query) }
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            var opened by rememberSaveable { mutableStateOf<String?>(null) }
            WalkRecordsScreen(source, pets, {}, { opened = it }, today = today)
            if (opened != null) AlertDialog(onDismissRequest = { opened = null },
                title = { Text("상세: $opened") },
                confirmButton = { TextButton(onClick = { opened = null }) { Text("돌아가기") } })
        } } }
        waitText("1 페이지")
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 3회 · 표시 흔적 2개")
        val mapBeforeSelection = compose.onNodeWithTag("records-overview-map").getUnclippedBoundsInRoot()
        chooseMapRecord("record-2")
        compose.onNodeWithTag("records-map-record-record-2").assertIsSelected()
        assertEquals(mapBeforeSelection, compose.onNodeWithTag("records-overview-map").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("records-map-clear-selection").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("records-map-clear-selection").getUnclippedBoundsInRoot().bottom <=
            compose.onNodeWithTag("records-overview-map").getUnclippedBoundsInRoot().top)
        compose.onNodeWithText("상세: record-2").assertDoesNotExist()
        compose.onNodeWithTag("records-overview-map").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "강조한 산책: 기록-2"))
        compose.onNodeWithTag("records-map-hide-record-2").performScrollTo().performClick()
        waitText("선택 산책 3회 · 표시 흔적 1개")
        compose.onNodeWithTag("records-overview-map").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "강조한 산책 없음"))
        compose.onNodeWithTag("records-map-open-record-2").performScrollTo().performClick()
        waitText("상세: record-2")
        compose.onNodeWithText("돌아가기").performClick()
        compose.onNodeWithTag("records-map-record-record-2").assertIsSelected()
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 3회")
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 3회 · 표시 흔적 1개")
        assertEquals(1, queries.size)
        restore.emulateSavedInstanceStateRestore()
        waitText("선택 산책 3회 · 표시 흔적 1개")
        compose.onNodeWithTag("records-map-record-record-2").assertIsSelected()
        compose.onNodeWithTag("records-map-hide-record-2").assertTextEquals("지도에 다시 표시")
        chooseMapRecord("record-3")
        compose.onNodeWithTag("records-map-hide-record-3").assertIsNotEnabled()
        compose.onNodeWithTag("records-map-open-record-3").assertIsEnabled()
        compose.onNodeWithTag("records-overview-map").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "강조한 산책: 기록-3"))
        // A real condition change resets selection/hiding even if its matching population is identical.
        compose.onNodeWithTag("records-search").performTextReplacement("기록")
        waitText("선택 산책 3회 · 표시 흔적 2개")
        compose.onNodeWithTag("records-map-record-record-3").assertIsNotSelected()
        compose.onNodeWithTag("records-map-restore-all").assertDoesNotExist()
    }

    @Test fun `hiding every trace keeps the map and records available for restoration`() {
        val source = WalkRecordsSource { query -> selectWalkRecords(listOf(record(1)), query) }
        show(source)
        waitText("1 페이지")
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 1회 · 표시 흔적 1개")
        chooseMapRecord("record-1")
        compose.onNodeWithTag("records-map-hide-record-1").performScrollTo().performClick()
        waitText("산책 흔적을 모두 숨겼어요.")
        compose.onNodeWithTag("records-overview-map").assertExists()
        compose.onNodeWithTag("records-map-count").assertTextEquals("선택 산책 1회 · 표시 흔적 0개")
        compose.onNodeWithTag("records-map-record-record-1").assertIsSelected()
        compose.onNodeWithTag("records-map-open-record-1").assertIsEnabled()
        compose.onNodeWithTag("records-map-restore-all").performClick()
        waitText("선택 산책 1회 · 표시 흔적 1개")
        compose.onNodeWithTag("records-map-hide-record-1").assertTextEquals("지도에서 숨기기")
    }

    private fun chooseMapRecord(id: String) {
        compose.onNodeWithTag("records-map-list").performScrollToNode(hasTestTag("records-map-record-$id"))
        compose.onNodeWithTag("records-map-record-$id").performClick()
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
