package com.daengs.app.ui.walk.records

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.auth.AccountScope
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.records.WalkBehaviorRecord
import com.daengs.app.walk.records.WalkRecord
import com.daengs.app.walk.records.WalkRecordsQuery
import com.daengs.app.walk.records.WalkRecordsSelection
import com.daengs.app.walk.records.WalkRecordsSource
import com.daengs.app.walk.records.selectWalkRecords
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
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRecordsRouteTest {
    @get:Rule val compose = createComposeRule()
    private val account = AccountScope("owner-a", 1)
    private val pets = listOf(Pet("dog-1", "두부", "maltese", null, null, null, null, null, isPrimary = true))
    private val records = (1..8).map(::record)

    @Test fun `real detail unmount and rotation retain records search page and both map views`() {
        val queryRead = AtomicReference<WalkRecordsQuery>()
        val source = WalkRecordsSource { query -> queryRead.set(query); selectWalkRecords(records, query) }
        val currentPets = mutableStateOf<List<Pet>?>(pets)
        val syncs = AtomicInteger()
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val state = rememberWalkRecordsRouteState(account)
            var atHome by rememberSaveable { mutableStateOf(false) }
            if (atHome) TextButton(onClick = { atHome = false }) { Text("기록 다시 열기") }
            else key(account) {
                WalkRecordsRoute(account, source, state, currentPets.value, { atHome = true }, {}, {
                    if (syncs.incrementAndGet() == 1) error("offline")
                }, { id, back -> TestDetail(id, back) })
            }
        } } }
        waitText("1 페이지")
        compose.onNodeWithTag("records-sync-notice").assertExists()
        replaceSearch("기록")
        waitText("산책 8회")
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-dog-dog-1").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitText("산책 8회")
        compose.onNodeWithText("다음 ›").performClick()
        waitText("2 페이지")
        compose.onNodeWithText("기록-3").performClick()
        waitText("실제 상세 자리: record-3")
        compose.onNodeWithTag("records-search").assertDoesNotExist()
        compose.runOnIdle { currentPets.value = null }
        restore.emulateSavedInstanceStateRestore()
        waitText("실제 상세 자리: record-3")
        backFromDetail()
        waitText("2 페이지")
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("기록", substring = true)
        compose.runOnIdle { assertEquals(setOf("dog-1"), queryRead.get().dogIds); currentPets.value = pets }
        compose.onNodeWithTag("records-sync-notice").assertDoesNotExist()

        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 8회 · 표시 흔적 8개")
        selectMapRecord("record-8")
        compose.onNodeWithTag("records-map-hide-record-8").performScrollTo().performClick()
        waitText("선택 산책 8회 · 표시 흔적 7개")
        compose.onNodeWithTag("records-map-open-record-8").performScrollTo().performClick()
        waitText("실제 상세 자리: record-8")
        compose.onNodeWithTag("records-overview-map").assertDoesNotExist()
        backFromDetail()
        waitText("선택 산책 8회 · 표시 흔적 7개")
        compose.onNodeWithTag("records-map-record-record-8").assertIsSelected()
        compose.onNodeWithTag("records-map-hide-record-8").assertTextEquals("지도에 다시 표시")

        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-behavior-sniffing").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitTag("records-behavior-count")
        compose.onNodeWithTag("records-map-display").performClick()
        compose.onNodeWithTag("records-behavior-view-traces").performClick()
        waitText("선택 산책 8회 · 표시 흔적 8개")
        selectMapRecord("record-8")
        compose.onNodeWithTag("records-map-hide-record-8").performScrollTo().performClick()
        compose.onNodeWithTag("records-map-open-record-8").performScrollTo().performClick()
        waitText("실제 상세 자리: record-8")
        compose.onNodeWithTag("records-behavior-count").assertDoesNotExist()
        restore.emulateSavedInstanceStateRestore()
        waitText("실제 상세 자리: record-8")
        backFromDetail()
        waitTag("records-behavior-count")
        waitText("선택 산책 8회 · 표시 흔적 7개")
        compose.onNodeWithTag("records-map-display").assertTextContains("전체 흔적 ▾")
        compose.onNodeWithTag("records-map-record-record-8").assertIsSelected()
        compose.onNodeWithTag("records-map-hide-record-8").assertTextContains("다시 표시", substring = true)
        // Ordinary navigation Back also captures the records registry before it unmounts.
        compose.onNodeWithContentDescription("뒤로").performClick()
        waitText("기록 다시 열기")
        compose.onNodeWithTag("records-search").assertDoesNotExist()
        compose.onNodeWithText("기록 다시 열기").performClick()
        waitTag("records-behavior-count")
        compose.onNodeWithTag("records-map-display").assertTextContains("전체 흔적 ▾")
        compose.onNodeWithTag("records-map-record-record-8").assertIsSelected()
        assertEquals(5, syncs.get())
    }

    @Test fun `owner and same owner login generation discard prior screens and delayed records`() {
        val currentAccount = mutableStateOf(account)
        val delayed = AtomicReference<Pair<WalkRecordsQuery, Continuation<WalkRecordsSelection>>?>()
        val ownerA = WalkRecordsSource { query ->
            if (query.filter.keyword == "느림") suspendCoroutine { delayed.set(query to it) }
            else selectWalkRecords(records, query)
        }
        val ownerB = WalkRecordsSource { query -> selectWalkRecords(listOf(record(9).copy(title = "B의 기록")), query) }
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val current = currentAccount.value
            val state = rememberWalkRecordsRouteState(current)
            val source = remember(current) { if (current.ownerId == "owner-b") ownerB else ownerA }
            key(current) { WalkRecordsRoute(current, source, state, pets, {}, {}, {}, { id, back -> TestDetail(id, back) }) }
        } } }
        waitText("1 페이지")
        replaceSearch("느림")
        waitText("산책 기록을 찾고 있어요.")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("records-search").fetchSemanticsNodes()
            delayed.get() != null
        }
        compose.runOnIdle { currentAccount.value = AccountScope("owner-b", 2) }
        waitText("B의 기록")
        assertSearchEmpty()
        compose.runOnIdle { delayed.get()!!.let { (query, continuation) ->
            continuation.resume(WalkRecordsSelection(query, records))
        } }
        compose.waitForIdle()
        compose.onNodeWithText("B의 기록").assertExists()
        compose.onNodeWithText("기록-8").assertDoesNotExist()
        compose.onNodeWithText("B의 기록").performClick()
        waitText("실제 상세 자리: record-9")
        compose.runOnIdle { currentAccount.value = AccountScope("owner-a", 3) }
        waitText("산책 8회")
        compose.onNodeWithTag("records-test-detail").assertDoesNotExist()
        replaceSearch("기록")
        waitText("산책 8회")
        compose.onNodeWithText("다음 ›").performClick()
        waitText("2 페이지")
        compose.runOnIdle { currentAccount.value = AccountScope("owner-a", 4) }
        waitText("1 페이지")
        assertSearchEmpty()
        compose.onNodeWithTag("records-view-walks").assertIsSelected()
    }

    @Test fun `missing login blocks restored detail and exposes sign in and back actions`() {
        val signedOut = AccountScope(null, 2)
        val stateRef = AtomicReference<WalkRecordsRouteState>()
        val signIns = AtomicInteger()
        val backs = AtomicInteger()
        val syncs = AtomicInteger()
        val details = AtomicInteger()
        compose.setContent { DaengsTheme {
            val state = rememberWalkRecordsRouteState(signedOut)
            stateRef.set(state)
            WalkRecordsRoute(signedOut, null, state, emptyList(), { backs.incrementAndGet() },
                { signIns.incrementAndGet() }, { syncs.incrementAndGet() }, { _, _ -> details.incrementAndGet() })
        } }
        compose.runOnIdle { stateRef.get().open("stale-walk") }
        compose.onNodeWithTag("records-login-required").assertIsDisplayed()
        compose.onNodeWithTag("records-search").assertDoesNotExist()
        compose.onNodeWithTag("records-sign-in").performClick()
        compose.onNodeWithTag("records-route-back").performClick()
        assertEquals(1, signIns.get())
        assertEquals(1, backs.get())
        assertEquals(0, syncs.get())
        assertEquals(0, details.get())
    }

    @Test fun `co-carer walks join my walks in one list with actor badges and open a read-only detail`() {
        val reader = SharedReader()
        showShared(reader)
        waitText("산책 9회")
        compose.onNodeWithTag("records-shared-open").assertDoesNotExist()
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true)
            .assertTextEquals("모든 강아지 · 모든 보호자 · 전체 기간")
        // 9/8 12:00 에 다녀온 공동 보호자 산책이 9/8 00:00 의 내 산책보다 앞선다.
        compose.onNodeWithTag("records-walk-list").onChildren().filterToOne(hasTestTag("records-day-2026-09-08")).assertExists()
        compose.onNodeWithTag("records-walk-shared-1").assertExists()
        compose.onNodeWithContentDescription("키키의 산책", useUnmergedTree = true).assertExists()
        // 목록은 화면에 보이는 카드만 그린다 — 개수 대신 내 카드에도 같은 배지가 있는지만 본다.
        compose.onNodeWithTag("records-walk-actor-record-8", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithContentDescription("내 산책", useUnmergedTree = true).fetchSemanticsNodes()
            .let { assertEquals(true, it.isNotEmpty()) }
        compose.onNodeWithText("측정 전").assertDoesNotExist()
        compose.runOnIdle { assertEquals(null, reader.queries.last().actorIds) }

        compose.onNodeWithText("다음 ›").performClick()
        waitText("2 페이지")
        compose.onNodeWithText("‹ 이전").performClick()
        waitText("1 페이지")
        compose.onNodeWithTag("records-walk-shared-1").performClick()
        waitText("키키님이 다녀왔어요")
        compose.runOnIdle { assertEquals(listOf("dog-1" to "shared-1"), reader.details) }
        compose.onNodeWithText("기록-8").assertDoesNotExist()
        compose.onNodeWithTag("shared-walk-detail-back").performClick()
        waitText("1 페이지")
        compose.onNodeWithTag("records-walk-shared-1").assertExists()

        // 모아보기는 내 산책 경로만 — 공동 보호자 산책은 들어가지 않는다.
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 8회 · 표시 흔적 8개")
        compose.onNodeWithTag("records-overview-mine-only-notice").assertExists()
        compose.onNodeWithTag("records-map-record-shared-1").assertDoesNotExist()
    }

    @Test fun `carer condition narrows walks to chosen people and overview never pretends to show them`() {
        val reader = SharedReader()
        showShared(reader)
        waitText("산책 9회")
        compose.onNodeWithTag("records-conditions").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-carer-u2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("records-carer-u2").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitText("산책 1회")
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("키키", substring = true)
        compose.onNodeWithTag("records-walk-shared-1").assertExists()
        compose.onNodeWithText("기록-8").assertDoesNotExist()
        compose.runOnIdle { assertEquals(setOf("u2"), reader.queries.last().actorIds) }

        compose.onNodeWithTag("records-view-overview").performClick()
        waitTag("records-overview-mine-only")
        compose.onNodeWithTag("records-overview-map").assertDoesNotExist()
        compose.onNodeWithText("모든 보호자 보기").performClick()
        waitText("선택 산책 8회 · 표시 흔적 8개")
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("모든 보호자", substring = true)
    }

    @Test fun `shared walk failure keeps my walks and retry brings them back`() {
        val reader = SharedReader(failFirst = true)
        showShared(reader)
        waitTag("records-shared-failed")
        waitText("산책 8회")
        compose.onNodeWithTag("records-walk-record-8").assertExists()
        compose.onNodeWithTag("records-walk-shared-1").assertDoesNotExist()
        compose.onNodeWithTag("records-shared-retry").performClick()
        waitText("산책 9회")
        compose.onNodeWithTag("records-shared-failed").assertDoesNotExist()
        compose.onNodeWithTag("records-walk-shared-1").assertExists()
    }

    @Test fun `keyword search finds only my walks and says so`() {
        val reader = SharedReader()
        showShared(reader)
        waitText("산책 9회")
        replaceSearch("기록")
        waitText("산책 8회")
        compose.onNodeWithTag("records-shared-excluded").assertExists()
        compose.onNodeWithTag("records-walk-shared-1").assertDoesNotExist()
    }

    @Test fun `records without a shared holder keep my walks only and no carer condition`() {
        val source = WalkRecordsSource { query -> selectWalkRecords(records, query) }
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val state = rememberWalkRecordsRouteState(account)
            WalkRecordsRoute(account, source, state, pets, {}, {}, {}, { id, back -> TestDetail(id, back) })
        } } }
        waitText("1 페이지")
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextEquals("모든 강아지 · 전체 기간")
        compose.onAllNodesWithContentDescription("내 산책").fetchSemanticsNodes().let { assertEquals(0, it.size) }
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-carer-all").assertDoesNotExist()
    }

    private class SharedReader(private var failFirst: Boolean = false) : com.daengs.app.walk.shared.SharedWalkReader {
        val queries = mutableListOf<com.daengs.app.walk.shared.SharedWalkFeedQuery>()
        val details = mutableListOf<Pair<String, String>>()
        private val sharedAt = LocalDate.of(2026, 9, 8).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        private val walk = com.daengs.app.walk.shared.SharedWalk("shared-1", sharedAt, sharedAt + 600_000, 600, 700, 500,
            com.daengs.app.care.CareActor("u2", "키키"), false, listOf("dog-1"), weatherCode = 0, isDay = true)
        private val carers = listOf(
            com.daengs.app.walk.shared.SharedWalkCarer("owner-a", "에이", true, listOf("dog-1")),
            com.daengs.app.walk.shared.SharedWalkCarer("u2", "키키", false, listOf("dog-1")),
        )

        override suspend fun feed(
            accessToken: String, query: com.daengs.app.walk.shared.SharedWalkFeedQuery, cursor: String?, limit: Int,
        ): com.daengs.app.walk.shared.SharedWalkResult<com.daengs.app.walk.shared.SharedWalkFeedPage> {
            if (limit > 1) queries += query
            if (limit > 1 && failFirst) {
                failFirst = false
                return com.daengs.app.walk.shared.SharedWalkResult.Failed("서버에 닿지 못했어요.")
            }
            return com.daengs.app.walk.shared.SharedWalkResult.Ready(com.daengs.app.walk.shared.SharedWalkFeedPage(
                listOf(walk), com.daengs.app.walk.shared.SharedWalkTotals(1, 700, 600), carers, null))
        }

        override suspend fun detail(accessToken: String, petId: String, walkId: String):
            com.daengs.app.walk.shared.SharedWalkResult<com.daengs.app.walk.shared.SharedWalkDetail> {
            details += petId to walkId
            return com.daengs.app.walk.shared.SharedWalkResult.Ready(com.daengs.app.walk.shared.SharedWalkDetail(walk, emptyList()))
        }
    }

    private fun showShared(reader: SharedReader) {
        val holder = com.daengs.app.walk.shared.SharedWalksHolder(reader, accessToken = { "sample-token" },
            isCurrentAccount = { true })
        val source = WalkRecordsSource { query -> selectWalkRecords(records, query) }
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val state = rememberWalkRecordsRouteState(account)
            WalkRecordsRoute(account, source, state, pets, {}, {}, {}, { id, back -> TestDetail(id, back) },
                sharedWalks = holder)
        } } }
    }

    private fun backFromDetail() = compose.onNodeWithTag("records-test-detail-back").performClick()

    private fun assertSearchEmpty() {
        compose.onNodeWithTag("records-search").assertDoesNotExist()
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-search").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("records-conditions-cancel").performClick()
    }

    private fun selectMapRecord(id: String) {
        if (compose.onAllNodesWithContentDescription("산책 목록 펼치기").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("records-map-sheet-toggle").performClick()
        compose.onNodeWithTag("records-map-list").performScrollToNode(hasTestTag("records-map-record-$id"))
        compose.onNodeWithTag("records-map-record-$id").performClick()
    }

    private fun waitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun replaceSearch(text: String) {
        if (compose.onAllNodesWithTag("records-search").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-search").performScrollTo().performTextReplacement(text)
        compose.onNodeWithTag("records-conditions-apply").performClick()
    }

    private fun waitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Composable
    private fun TestDetail(id: String, onBack: () -> Unit) {
        BackHandler(onBack = onBack)
        Column {
            Text("실제 상세 자리: $id", Modifier.testTag("records-test-detail"))
            TextButton(onClick = onBack, modifier = Modifier.testTag("records-test-detail-back")) { Text("상세에서 돌아가기") }
        }
    }

    private fun record(number: Int): WalkRecord {
        val at = LocalDate.of(2026, 9, number).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val id = "record-$number"
        return WalkRecord(WalkSummary(id, listOf("dog-1"), at, at + 600_000, null,
            500.0, 600_000, emptyList(), null), title = "기록-$number",
            trace = WalkTraceSheet(id, cells = setOf(SpatialDiaryCellId(832649, 375728))),
            entries = listOf(WalkEntry(id = "sniff-$number", sessionId = id, type = WalkMomentType.SNIFFING,
                recordedAtMillis = at + 1_000, point = GeoPoint(37.544, 127.037), petId = "dog-1")))
    }
}
