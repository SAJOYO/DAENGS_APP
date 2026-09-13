package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.records.WalkRecordsScreen
import com.daengs.app.ui.walk.records.WalkRecordsOverview
import com.daengs.app.ui.walk.records.ReconcileWalkRecordsOverlapPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.diary.SpatialDiaryHexGrid
import com.daengs.app.walk.records.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
        waitTagText("records-map-count", "선택 산책 8회 · 표시 흔적 1개")
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithText("다음 ›").performClick()
        waitText("2 페이지")
        val reads = queries.size
        compose.onNodeWithTag("records-view-overview").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 8회")
        // Only the oldest walk has a trace. It must be included even when it is not on page one.
        waitTagText("records-map-count", "선택 산책 8회 · 표시 흔적 1개")
        compose.onNodeWithTag("records-view-walks").performClick()
        waitText("2 페이지")
        assertEquals(reads, queries.size)
        restore.emulateSavedInstanceStateRestore()
        waitText("2 페이지")
        replaceSearch("기록-8")
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
        compose.onNodeWithTag("records-extra-conditions").performScrollTo().performClick()
        compose.onNodeWithTag("records-season-WINTER").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-cancel").performClick()
        waitText("2 페이지")
        assertEquals(reads, queries.size)
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-extra-conditions").performScrollTo().performClick()
        compose.onNodeWithTag("records-season-WINTER").assertIsNotSelected()
        compose.onNodeWithTag("records-weather-RAIN").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitText("1 페이지")
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-dog-dog-1").performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitText("선택 산책 4회")
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 4회")
        assertEquals(setOf("dog-1"), queries.last().dogIds)
        assertEquals(setOf(WalkDepartureWeather.RAIN), queries.last().filter.weather)
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 4회 · 표시 흔적 0개")
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 4회")
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-conditions-reset").performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        // Observe the rendered result so Compose can settle the reset and background selection.
        waitText("선택 산책 8회 · 표시 흔적 1개")
        assertEquals(null, queries.last().dogIds)
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
        replaceSearch("느림")
        compose.waitUntil(10_000) { delayed.get() != null }
        waitText("산책 기록을 찾고 있어요.")
        compose.onNodeWithTag("records-count").assertTextEquals("불러오는 중")
        replaceSearch("기록-8")
        waitCard("기록-8")
        compose.runOnIdle { delayed.get()!!.let { (query, continuation) ->
            continuation.resume(WalkRecordsSelection(query, listOf(record(1))))
        } }
        compose.waitForIdle()
        compose.onNode(hasText("기록-8") and !hasSetTextAction()).assertExists()
        compose.onNodeWithText("기록-1").assertDoesNotExist()
        replaceSearch("실패")
        waitText("산책 기록을 불러오지 못했어요.")
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("실패", substring = true)
        compose.onNodeWithText("기록-8").assertDoesNotExist()
        compose.onNodeWithText("다시 시도").performClick()
        waitText("조건에 맞는 산책이 없어요.")
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("실패", substring = true)
        assertEquals(2, failures.get())
    }

    @Test fun `saved record refresh retains cards until replacement but clears them on failure`() {
        val revisions = MutableStateFlow(0)
        val saved = AtomicReference(records)
        val pending = AtomicReference<CompletableDeferred<Unit>?>(null)
        val fail = java.util.concurrent.atomic.AtomicBoolean(false)
        val reads = AtomicInteger()
        val source = object : WalkRecordsSource {
            override val changes = revisions.map { Unit }
            override suspend fun select(query: WalkRecordsQuery): WalkRecordsSelection {
                reads.incrementAndGet()
                pending.get()?.await()
                check(!fail.get()) { "account is no longer current" }
                return selectWalkRecords(saved.get(), query)
            }
        }
        show(source)
        waitText("1 페이지")
        replaceSearch("기록-8")
        waitCard("기록-8")

        val release = CompletableDeferred<Unit>()
        val beforeRefresh = reads.get()
        compose.runOnIdle {
            pending.set(release)
            saved.set(records.filter { it.summary.sessionId != "record-8" })
            revisions.value++
        }
        compose.waitUntil(10_000) { reads.get() > beforeRefresh }
        // An invalidation is not a replacement result: keep the current card while reading.
        compose.onNodeWithText("산책 기록을 찾고 있어요.").assertDoesNotExist()
        compose.onNode(hasText("기록-8") and !hasSetTextAction()).assertExists()
        release.complete(Unit)
        waitText("조건에 맞는 산책이 없어요.")
        compose.onNode(hasText("기록-8") and !hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("기록-8", substring = true)

        compose.runOnIdle { pending.set(null); saved.set(records); revisions.value++ }
        waitCard("기록-8")
        compose.runOnIdle { fail.set(true); revisions.value++ }
        waitText("산책 기록을 불러오지 못했어요.")
        compose.onNode(hasText("기록-8") and !hasSetTextAction()).assertDoesNotExist()
        // Recovery must not reveal a snapshot discarded after a failed/account-invalid read.
        val recovery = CompletableDeferred<Unit>()
        compose.runOnIdle { pending.set(recovery); fail.set(false); revisions.value++ }
        waitText("산책 기록을 찾고 있어요.")
        compose.onNode(hasText("기록-8") and !hasSetTextAction()).assertDoesNotExist()
        recovery.complete(Unit)
        waitCard("기록-8")
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 1회")
    }

    @Test fun `trace loading and retry preserve local behavior pins and never refetch on tab changes`() {
        val sample = behaviorRecords().map { it.copy(trace = null, traceState = WalkTraceState.NOT_REQUESTED) }
        val calls = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val source = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = selectWalkRecords(sample, query)
            override suspend fun loadTraces(selection: WalkRecordsSelection): WalkRecordsSelection {
                if (calls.incrementAndGet() == 1) {
                    release.await()
                    error("trace request failed")
                }
                return WalkRecordsSelection(selection.query, selection.records.map { record ->
                    when (record.summary.sessionId) {
                        "record-1" -> record.copy(trace = WalkTraceSheet("record-1",
                            cells = setOf(SpatialDiaryCellId(832649, 375728))), traceState = WalkTraceState.READY)
                        "record-2" -> record.copy(traceState = WalkTraceState.ANALYSIS_PENDING)
                        else -> record.copy(traceState = WalkTraceState.EMPTY)
                    }
                })
            }
        }
        show(source)
        waitText("1 페이지")
        assertEquals(0, calls.get())
        chooseBehavior("sniffing")
        expandMapList()
        waitText("흔적 불러오는 중 · 자세히")
        compose.onNodeWithTag("records-behavior-count").assertTextEquals("행동 기록 4건 · 관련 산책 3회")
        compose.onNodeWithTag("records-behavior-display-count").assertTextEquals("위치 있는 기록 3건 · 표시 3건")
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 3회")
        assertEquals(1, calls.get())
        compose.onNodeWithTag("records-view-overview").performClick()
        // Finish the pending tab round trip before releasing the background response. Otherwise
        // its failure races the next touch/measure pass in Robolectric instead of testing retention.
        waitText("흔적 불러오는 중 · 자세히")
        assertEquals(1, calls.get())
        compose.runOnIdle { release.complete(Unit) }
        waitText("3회 중 0회 흔적 준비 · 자세히")
        compose.onNodeWithTag("records-behavior-display-count").assertTextEquals("위치 있는 기록 3건 · 표시 3건")
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 3회")
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("3회 중 0회 흔적 준비 · 자세히")
        compose.onNodeWithTag("records-behavior-display-count").assertTextEquals("위치 있는 기록 3건 · 표시 3건")
        assertEquals(1, calls.get())
        compose.onNodeWithTag("records-traces-status").performScrollTo().performClick()
        compose.onNodeWithText("흔적을 불러오지 못했어요. 산책 기록과 행동 위치는 그대로 볼 수 있어요.").assertExists()
        compose.onNodeWithTag("records-traces-refresh").performClick()
        waitText("3회 중 1회 흔적이 준비됐어요.")
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithTag("records-map-display").performClick()
        compose.onNodeWithTag("records-behavior-view-traces").performClick()
        waitText("선택 산책 3회 · 표시 흔적 1개")
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 3회")
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 3회 · 표시 흔적 1개")
        assertEquals(2, calls.get())
    }

    @Test fun `late trace responses cannot replace a newer local selection`() {
        val sample = records.map { it.copy(trace = null, traceState = WalkTraceState.NOT_REQUESTED) }
        val delayed = AtomicReference<Pair<WalkRecordsSelection, Continuation<WalkRecordsSelection>>?>()
        val source = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = selectWalkRecords(sample, query)
            override suspend fun loadTraces(selection: WalkRecordsSelection): WalkRecordsSelection =
                if (selection.query.filter.keyword.isBlank()) suspendCoroutine { delayed.set(selection to it) }
                else WalkRecordsSelection(selection.query, selection.records.map { it.copy(traceState = WalkTraceState.EMPTY) })
        }
        show(source)
        waitText("1 페이지")
        compose.onNodeWithTag("records-view-overview").performClick()
        expandMapList()
        compose.onNodeWithTag("records-traces-status").performClick()
        waitText("흔적을 불러오고 있어요.")
        compose.onNodeWithText("확인").performClick()
        compose.waitUntil(10_000) { delayed.get() != null }
        replaceSearch("기록-8")
        waitText("선택 산책 1회 · 표시 흔적 0개")
        compose.runOnIdle { delayed.get()!!.let { (selection, continuation) ->
            continuation.resume(WalkRecordsSelection(selection.query, selection.records.map { record ->
                record.copy(trace = WalkTraceSheet(record.summary.sessionId,
                    cells = setOf(SpatialDiaryCellId(832649, 375728))), traceState = WalkTraceState.READY)
            }))
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("records-map-count").assertTextEquals("선택 산책 1회 · 표시 흔적 0개")
        expandMapList()
        compose.onNodeWithTag("records-map-record-record-8").assertExists()
        compose.onNodeWithTag("records-map-record-record-1").assertDoesNotExist()
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("기록-8", substring = true)
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
        assertTrue(compose.onNodeWithTag("records-map-clear-selection").getUnclippedBoundsInRoot().top >=
            compose.onNodeWithTag("records-map-sheet").getUnclippedBoundsInRoot().top)
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
        replaceSearch("기록")
        waitText("선택 산책 3회 · 표시 흔적 2개")
        expandMapList()
        compose.onNodeWithTag("records-map-list").performScrollToNode(hasTestTag("records-map-record-record-3"))
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

    @Test fun `overlap display thresholds and hidden walks survive tabs but reset with the query`() {
        val cell = SpatialDiaryCellId(832649, 375728)
        val sample = (1..3).map { n -> record(n).copy(trace = if (n <= 2)
            WalkTraceSheet("record-$n", cells = setOf(cell)) else null) }
        val queries = Collections.synchronizedList(mutableListOf<WalkRecordsQuery>())
        val source = WalkRecordsSource { query -> queries.add(query); selectWalkRecords(sample, query) }
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, pets, {}, {}, today = today)
        } } }
        waitText("1 페이지")
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 3회 · 표시 흔적 2개")
        compose.onNodeWithTag("records-overlap-legend").assertDoesNotExist()
        compose.onNodeWithTag("records-map-display").performClick()
        compose.onNodeWithTag("records-traces-overlap").performClick()
        waitText("선택 산책 3회 · 겹침 표시 2회")
        assertFixedOverlapLegend()
        compose.onNodeWithTag("records-overlap-min-2").performClick()
        chooseMapRecord("record-1")
        compose.onNodeWithTag("records-map-hide-record-1").performScrollTo().performClick()
        waitText("선택 산책 3회 · 겹침 표시 1회")
        // Hiding leaves two original walks in the overlap evidence, so this is still visible.
        compose.onNodeWithText("2회 이상 겹친 구간이 없어요.").assertDoesNotExist()
        compose.onNodeWithTag("records-map-display").performClick()
        compose.onNodeWithTag("records-overlap-min-5").performClick()
        waitText("5회 이상 겹친 구간이 없어요.")
        // A stricter display threshold never renames or rescales the fixed count strength scale.
        compose.onNodeWithTag("records-map-display").performClick()
        assertFixedOverlapLegend()
        compose.onNodeWithTag("records-overlap-min-5").performClick()
        compose.onNodeWithTag("records-overview-map").assertExists()
        compose.onNodeWithTag("records-view-walks").performClick()
        waitText("1 페이지")
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 3회")
        compose.onNodeWithTag("records-view-overview").performClick()
        compose.onNodeWithTag("records-map-display").assertTextContains("겹친 구간 · 5회 이상 ▾")
        compose.onNodeWithTag("records-map-display").performClick()
        compose.onNodeWithTag("records-overlap-min-5").assertIsSelected().performClick()
        assertEquals(1, queries.size)
        restore.emulateSavedInstanceStateRestore()
        waitText("5회 이상 겹친 구간이 없어요.")
        compose.onNodeWithTag("records-map-display").performClick()
        assertFixedOverlapLegend()
        compose.onNodeWithTag("records-overlap-min-2").performClick()
        waitText("선택 산책 3회 · 겹침 표시 1회")
        replaceSearch("기록")
        waitText("선택 산책 3회 · 표시 흔적 2개")
        compose.onNodeWithTag("records-map-display").assertTextContains("전체 흔적 ▾")
        compose.onNodeWithTag("records-overlap-legend").assertDoesNotExist()
        compose.onNodeWithTag("records-map-restore-all").assertDoesNotExist()
    }

    private fun assertFixedOverlapLegend() {
        compose.onNodeWithTag("records-overlap-legend").assertIsDisplayed()
        listOf(Triple("1", "1회", 4), Triple("2", "2회", 12), Triple("3-4", "3–4회", 22),
            Triple("5-7", "5–7회", 34), Triple("8", "8회 이상", 46)).forEach { (tag, label, opacity) ->
            compose.onNodeWithTag("records-overlap-legend-$tag").assertTextEquals(label)
                .assertContentDescriptionEquals("$label 그림자 농도 ${opacity}퍼센트")
        }
    }

    @Test fun `overlap inspection lists exact related walks including hidden evidence without changing base counts`() {
        val cell = SpatialDiaryCellId(832649, 375728)
        val sample = (1..3).map { n -> record(n).copy(trace = WalkTraceSheet("record-$n",
            cells = setOf(if (n <= 2) cell else SpatialDiaryCellId(cell.q + 10, cell.r))),
            traceState = WalkTraceState.READY) }
        val selection = WalkRecordsSelection(WalkRecordsQuery(), sample)
        val hidden = setOf("record-1")
        val stages = listOf(selection,
            WalkRecordsSelection(selection.query, sample.map { it.copy(trace = null, traceState = WalkTraceState.LOADING) }),
            WalkRecordsSelection(selection.query, sample.map { it.copy(trace = null, traceState = WalkTraceState.FAILED) }),
            WalkRecordsSelection(selection.query, sample.mapIndexed { index, record ->
                if (index == 0) record else record.copy(trace = null, traceState = WalkTraceState.ANALYSIS_PENDING)
            }),
            WalkRecordsSelection(selection.query, sample.map { it.copy(trace = null, traceState = WalkTraceState.EMPTY) }))
        val preparedStages = runBlocking { stages.map { prepareWalkRecordsTraces(it) } }
        val tileStages = runBlocking { preparedStages.map { it.compose(hidden, minimumOverlapWalks = 2, style = com.daengs.app.map.features.records.TraceDisplayPolicy(com.daengs.app.ui.theme.WalkTraceShadow.RGB)) } }
        val initialPoint = preparedStages.first().hitTestOverlap(SpatialDiaryHexGrid.center(cell, 8.0), 2)!!.point
        val stage = mutableStateOf(0)
        val selectedPoint = mutableStateOf<GeoPoint?>(initialPoint)
        val opened = AtomicReference<String>()
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val prepared = preparedStages[stage.value]
            val current = stages[stage.value]
            val hit = selectedPoint.value?.let { prepared.hitTestOverlap(it, 2, snapRadiusU = 0.0) }
            val loading = stage.value == 1
            val error = "흔적 조회 실패".takeIf { stage.value == 2 }
            ReconcileWalkRecordsOverlapPoint(current, loading, error, prepared, selectedPoint.value, hit,
                onClear = { selectedPoint.value = null })
            WalkRecordsOverview(current, pets, prepared, tileStages[stage.value], null, {}, "record-1", hidden,
                {}, {}, {}, {}, { opened.set(it) }, rememberLazyListState(), null, {}, prepared.bounds, 0,
                overlapOnly = true, overlapHit = hit, onClearOverlap = { selectedPoint.value = null },
                traceLoading = loading, traceError = error, onReloadTraces = { stage.value = 1 },
                modifier = Modifier.fillMaxSize())
        } } }
        compose.onNodeWithTag("records-map-count").assertTextEquals("선택 산책 3회 · 겹침 표시 1회")
        compose.onNodeWithTag("records-inspection-summary").assertTextContains("3회 중 2회 겹침", substring = true)
        compose.onNodeWithTag("records-map-record-record-1").assertExists()
        compose.onNodeWithTag("records-map-record-record-2").assertExists()
        compose.onNodeWithTag("records-map-record-record-3").assertDoesNotExist()
        compose.onNodeWithTag("records-map-hide-record-1").assertTextEquals("지도에 다시 표시")
        compose.onNodeWithTag("records-map-open-record-1").performScrollTo().performClick()
        assertEquals("record-1", opened.get())
        compose.onNodeWithTag("records-traces-status").performClick()
        compose.onNodeWithTag("records-traces-refresh").performClick()
        waitText("흔적을 불러오고 있어요.")
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithTag("records-overlap-clear").assertDoesNotExist()
        compose.onNodeWithTag("records-map-count").assertTextEquals("선택 산책 3회 · 겹침 표시 0회")
        compose.onNodeWithTag("records-map-list").performScrollToNode(hasTestTag("records-map-record-record-3"))
        compose.onNodeWithTag("records-map-record-record-3").assertExists()
        compose.runOnIdle { assertEquals(initialPoint, selectedPoint.value); stage.value = 2 }
        compose.onNodeWithTag("records-traces-status").performClick()
        waitText("흔적 조회 실패")
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithTag("records-overlap-clear").assertDoesNotExist()
        compose.runOnIdle { assertEquals(initialPoint, selectedPoint.value); stage.value = 3 }
        compose.onNodeWithTag("records-traces-status").performClick()
        compose.onAllNodes(hasText("서버에서 흔적 계산 중") and hasAnyAncestor(isDialog())).assertCountEquals(2)
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithTag("records-overlap-clear").assertDoesNotExist()
        // A partial response cannot disprove the remembered area. The same ready sheets restore it.
        compose.runOnIdle { assertEquals(initialPoint, selectedPoint.value); stage.value = 0 }
        waitText("선택 산책 3회 · 겹침 표시 1회")
        compose.onNodeWithTag("records-inspection-summary").assertTextContains("3회 중 2회 겹침", substring = true)
        compose.onNodeWithTag("records-map-record-record-3").assertDoesNotExist()
        // Successfully checked empty sheets do prove removal; a later load must not resurrect it.
        compose.runOnIdle { stage.value = 4 }
        compose.onNodeWithTag("records-traces-status").performClick()
        waitText("3회 중 0회 흔적이 준비됐어요.")
        compose.onNodeWithText("확인").performClick()
        compose.runOnIdle { assertEquals(null, selectedPoint.value); stage.value = 0 }
        waitText("선택 산책 3회 · 겹침 표시 1회")
        compose.onNodeWithTag("records-overlap-clear").assertDoesNotExist()
        compose.runOnIdle { selectedPoint.value = initialPoint }
        compose.onNodeWithTag("records-inspection-summary").assertTextContains("3회 중 2회 겹침", substring = true)
        compose.onNodeWithTag("records-overlap-clear").performClick()
        compose.onNodeWithTag("records-map-list").performScrollToNode(hasTestTag("records-map-record-record-3"))
        compose.onNodeWithTag("records-map-record-record-3").assertExists()
        compose.onNodeWithTag("records-map-count").assertTextEquals("선택 산책 3회 · 겹침 표시 1회")
    }

    @Test fun `selected route loads separately and a cancelled old route cannot replace the new selection`() {
        val samples = (1..3).map { n -> record(n).let { record ->
            record.copy(summary = record.summary.copy(segments = listOf(listOf(
                LocationSample(GeoPoint(37.5, 127.0), 0L),
                LocationSample(GeoPoint(37.5001, 127.0), 2_000L)))))
        } }
        val pending = CompletableDeferred<WalkSummary>()
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val source = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = selectWalkRecords(samples, query)
            override suspend fun loadRoute(record: WalkRecord): WalkSummary {
                calls += record.summary.sessionId
                return if (record.summary.sessionId == "record-1")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { pending.await() }
                else record.summary
            }
        }
        show(source)
        waitText("1 페이지")
        assertTrue(calls.isEmpty())
        compose.onNodeWithTag("records-view-overview").performClick()
        chooseMapRecord("record-1")
        compose.waitUntil(10_000) { calls.contains("record-1") }
        chooseMapRecord("record-2")
        compose.waitUntil(10_000) { calls.contains("record-2") }
        compose.waitForIdle()
        val selected = compose.onNodeWithTag("records-overview-map").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        assertEquals("강조한 산책: 기록-2", selected)
        pending.complete(samples.first { it.summary.sessionId == "record-1" }.summary)
        compose.waitForIdle()
        compose.onNodeWithTag("records-overview-map").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, selected))
        assertEquals(listOf("record-1", "record-2"), calls.toList())
    }

    @Test fun `trace preparation failure still allows selecting and displaying the original route`() {
        val sample = record(1).let { it.copy(
            summary = it.summary.copy(segments = listOf(listOf(
                LocationSample(GeoPoint(37.5, 127.0), 0L), LocationSample(GeoPoint(37.5001, 127.0), 2000L)))),
            trace = WalkTraceSheet("record-1", 8.0, (0..5000).map { SpatialDiaryCellId(it, 0) }.toSet())) }
        val reads = AtomicInteger()
        show(object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = selectWalkRecords(listOf(sample), query)
            override suspend fun loadRoute(record: WalkRecord): WalkSummary { reads.incrementAndGet(); return sample.summary }
        })
        waitText("1 페이지")
        compose.onNodeWithTag("records-view-overview").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("선택한 산책의 흔적을 표시하지 못했어요. 기간이나 조건을 좁혀 다시 확인해 주세요.").fetchSemanticsNodes().isNotEmpty() }
        chooseMapRecord("record-1")
        compose.waitUntil(10000) { reads.get() == 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("records-overview-map").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "강조한 산책: 기록-1"))
    }

    private fun chooseMapRecord(id: String) {
        expandMapList()
        compose.onNodeWithTag("records-map-list").performScrollToNode(hasTestTag("records-map-record-$id"))
        compose.onNodeWithTag("records-map-record-$id").performClick()
    }

    @Test fun `behavior conditions select the same walk subset without switching tabs or moving the header`() {
        show(WalkRecordsSource { query -> selectWalkRecords(behaviorRecords(), query) })
        waitText("1 페이지")
        val header = compose.onNodeWithTag("records-header").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-behavior-barking").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        compose.onNodeWithTag("records-view-walks").assertIsSelected()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 1회")
        compose.onNodeWithText("기록-2").assertExists()
        compose.onNodeWithText("기록-1").assertDoesNotExist()
        val count = compose.onNodeWithTag("records-count").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("행동 기록 1건 · 관련 산책 1회")
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 1회")
        assertEquals(header, compose.onNodeWithTag("records-header").getUnclippedBoundsInRoot())
        assertEquals(count, compose.onNodeWithTag("records-count").getUnclippedBoundsInRoot())
    }

    @Test fun `behavior selection commits from its draft and leaves the storage query unchanged`() {
        val reads = AtomicInteger()
        val source = WalkRecordsSource { query -> reads.incrementAndGet(); selectWalkRecords(behaviorRecords(), query) }
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, pets, {}, {}, today = today)
        } } }
        waitText("1 페이지")
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-behavior-sniffing").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-cancel").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 3회")
        chooseBehavior("sniffing")
        compose.onNodeWithTag("records-view-overview").assertIsSelected()
        compose.onNodeWithTag("records-map-display").assertTextContains("행동 위치 ▾")
        compose.onNodeWithTag("records-behavior-count").assertTextContains("4건", substring = true)
        compose.onNodeWithTag("records-behavior-count").assertTextContains("3회", substring = true)
        compose.onNodeWithTag("records-map-display").performClick()
        compose.onNodeWithTag("records-behavior-view-traces").performClick()
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 3회")
        compose.onNodeWithTag("records-view-overview").performClick()
        compose.onNodeWithTag("records-map-display").assertTextContains("전체 흔적 ▾")
        assertEquals(1, reads.get())
        restore.emulateSavedInstanceStateRestore()
        waitText("산책 기록")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-behavior-count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("records-map-display").assertTextContains("전체 흔적 ▾")
        chooseBehavior("barking")
        compose.onNodeWithTag("records-map-display").assertTextContains("전체 흔적 ▾")
        compose.onNodeWithTag("records-behavior-count").assertTextContains("1건", substring = true)
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-behavior-all").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitText("선택 산책 3회 · 표시 흔적 1개")
    }

    @Test fun `behavior records preserve missing locations and walk evidence when hidden and opened`() {
        val sample = behaviorRecords()
        val source = WalkRecordsSource { query -> selectWalkRecords(sample, query) }
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            var opened by rememberSaveable { mutableStateOf<String?>(null) }
            WalkRecordsScreen(source, pets, {}, { opened = it }, today = today)
            if (opened != null) AlertDialog(onDismissRequest = { opened = null }, title = { Text("상세: $opened") },
                confirmButton = { TextButton(onClick = { opened = null }) { Text("돌아가기") } })
        } } }
        waitText("1 페이지")
        chooseBehavior("sniffing")
        val result = selectWalkRecordBehaviors(WalkRecordsSelection(WalkRecordsQuery(), sample), WalkMomentType.SNIFFING)
        val located = result.records.first { it.entry.id == "s1" }.key
        val unlocated = result.records.first { it.entry.id == "s3" }.key
        chooseBehaviorRecord(located)
        compose.onNodeWithTag("records-behavior-hide-$located").performScrollTo().performClick()
        compose.onNodeWithTag("records-behavior-count").assertTextContains("4건", substring = true)
        compose.onNodeWithTag("records-behavior-count").assertTextContains("3회", substring = true)
        compose.onNodeWithTag("records-behavior-open-$located").performScrollTo().performClick()
        waitText("상세: record-1")
        compose.onNodeWithText("돌아가기").performClick()
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 3회")
        compose.onNodeWithTag("records-view-overview").performClick()
        compose.onNodeWithTag("records-behavior-entry-$located").assertIsSelected()
        compose.onNodeWithTag("records-behavior-hide-$located").assertTextContains("다시 표시", substring = true)
        restore.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-behavior-count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("records-behavior-entry-$located").assertIsSelected()
        compose.onNodeWithTag("records-behavior-hide-$located").assertTextContains("다시 표시", substring = true)
        chooseBehaviorRecord(unlocated)
        compose.onNodeWithTag("records-behavior-open-$unlocated").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("records-behavior-hide-$unlocated").assertIsNotEnabled()
        compose.onNodeWithTag("records-behavior-restore-all").performClick()
        compose.onNodeWithTag("records-behavior-restore-all").assertDoesNotExist()
        // A changed common query resets display state, even when its walk population is identical.
        compose.onNodeWithTag("records-map-display").performClick()
        compose.onNodeWithTag("records-behavior-view-traces").performClick()
        replaceSearch("기록")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-behavior-count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("records-map-display").assertTextContains("행동 위치 ▾")
        expandMapList()
        compose.onNodeWithTag("records-behavior-list").performScrollToNode(hasTestTag("records-behavior-entry-$unlocated"))
        compose.onNodeWithTag("records-behavior-entry-$unlocated").assertIsNotSelected()
    }

    @Test fun `behavior attribution uses the selected dog and empty behavior keeps common controls`() {
        show(WalkRecordsSource { query -> selectWalkRecords(behaviorRecords(), query) })
        waitText("1 페이지")
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-dog-dog-1").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        chooseBehavior("sniffing")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-behavior-count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("records-behavior-count").assertTextContains("2건", substring = true)
        chooseBehavior("excretion")
        compose.onNodeWithTag("records-count").assertTextEquals("선택 산책 0회")
        compose.onNodeWithText("조건에 맞는 산책이 없어요.").assertExists()
        compose.onNodeWithTag("records-conditions").assertIsEnabled()
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-behavior-all").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitText("선택 산책 3회 · 표시 흔적 1개")
    }

    private fun expandMapList() {
        if (compose.onAllNodesWithContentDescription("산책 목록 펼치기").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("records-map-sheet-toggle").performClick()
    }

    private fun chooseBehavior(code: String) {
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-behavior-$code").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        compose.onNodeWithTag("records-view-overview").performClick()
        compose.waitForIdle()
    }

    private fun chooseBehaviorRecord(key: String) {
        expandMapList()
        compose.onNodeWithTag("records-behavior-list").performScrollToNode(hasTestTag("records-behavior-entry-$key"))
        compose.onNodeWithTag("records-behavior-entry-$key").performClick()
    }

    private fun behaviorRecords() = (1..3).map { n ->
        val walk = record(n).let { it.copy(summary = it.summary.copy(dogIds = listOf("dog-1", "dog-2"))) }
        fun entry(id: String, type: WalkMomentType, petId: String?, point: GeoPoint?) = WalkEntry(id,
            walk.summary.sessionId, type, walk.summary.startedAtMillis + 1_000,
            point = point, locationCapturedAtMillis = point?.let { walk.summary.startedAtMillis + 1_000 }, petId = petId)
        val point = GeoPoint(37.544, 127.037)
        walk.copy(entries = when (n) {
            1 -> listOf(entry("s1", WalkMomentType.SNIFFING, "dog-1", point), entry("s2", WalkMomentType.SNIFFING, "dog-2", point))
            2 -> listOf(entry("s3", WalkMomentType.SNIFFING, "dog-1", null), entry("b1", WalkMomentType.BARKING, "dog-2", null))
            else -> listOf(entry("s4", WalkMomentType.SNIFFING, null, GeoPoint(37.545, 127.038)))
        })
    }

    private fun show(source: WalkRecordsSource) = compose.setContent { DaengsTheme {
        CompositionLocalProvider(LocalInspectionMode provides true) { WalkRecordsScreen(source, pets, {}, {}, today = today) }
    } }

    private fun replaceSearch(text: String) {
        if (compose.onAllNodesWithTag("records-search").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-search").performScrollTo().performTextReplacement(text)
        compose.onNodeWithTag("records-conditions-apply").performClick()
    }

    private fun waitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    /**
     * 태그가 붙은 칸이 **그 글씨가 될 때까지** 기다린다.
     *
     * 칸이 생기는 것만 기다리면 안 된다 — `records-map-count` 는 먼저 준비 중이라는 말로
     * 떴다가 나중에 숫자로 바뀐다. 생긴 것만 보고 곧바로 글씨를 재면 준비 중인 글씨를
     * 읽는다. 이 자리는 원래 그렇게 적혀 있었고 앞서 도는 테스트가 늘어 JVM 이 느려지자
     * 드러났다 (APP#277 이 테스트를 더하면서). 늘 깨지던 것이 아니라 **운으로 지나가던
     * 것이다** — 기다리는 조건을 글씨까지로 좁힌다.
     */
    private fun waitTagText(tag: String, text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
            }
        }
        compose.onNodeWithTag(tag).assertTextEquals(text)
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
