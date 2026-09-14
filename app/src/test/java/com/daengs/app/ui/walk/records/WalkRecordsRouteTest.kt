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
        waitText("선택 산책 8회")
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-dog-dog-1").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitText("선택 산책 8회")
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
        waitText("선택 산책 8회")
        compose.onNodeWithTag("records-test-detail").assertDoesNotExist()
        replaceSearch("기록")
        waitText("선택 산책 8회")
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

    @Test fun `shared walks open from records as a separate read-only screen without my own walks`() {
        val reader = object : com.daengs.app.walk.shared.SharedWalkReader {
            override suspend fun list(accessToken: String, petId: String, cursor: String?, limit: Int) =
                com.daengs.app.walk.shared.SharedWalkResult.Ready(com.daengs.app.walk.shared.SharedWalkPage(petId, listOf(
                    com.daengs.app.walk.shared.SharedWalk("shared-1", 1L, 2L, 60L, null, null,
                        com.daengs.app.care.CareActor("u2", "키키"), false, listOf(petId)),
                    com.daengs.app.walk.shared.SharedWalk("mine-1", 1L, 2L, 60L, null, null,
                        com.daengs.app.care.CareActor("owner-a", "나"), true, listOf(petId)),
                ), null))
            override suspend fun detail(accessToken: String, petId: String, walkId: String) =
                com.daengs.app.walk.shared.SharedWalkResult.Unsupported
        }
        val holder = com.daengs.app.walk.shared.SharedWalksHolder(reader, accessToken = { "sample-token" },
            isCurrentAccount = { true })
        val source = WalkRecordsSource { query -> selectWalkRecords(records, query) }
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val state = rememberWalkRecordsRouteState(account)
            WalkRecordsRoute(account, source, state, pets, {}, {}, {}, { id, back -> TestDetail(id, back) },
                sharedWalks = holder)
        } } }
        waitText("1 페이지")
        compose.onNodeWithTag("records-shared-open").performClick()
        waitText("키키님이 다녀왔어요")
        compose.onNodeWithText("나님이 다녀왔어요").assertDoesNotExist()
        compose.onNodeWithText("기록-8").assertDoesNotExist()
        compose.onNodeWithTag("shared-walks-back").performClick()
        waitText("1 페이지")
    }

    @Test fun `records without a shared holder show no shared entry`() {
        val source = WalkRecordsSource { query -> selectWalkRecords(records, query) }
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val state = rememberWalkRecordsRouteState(account)
            WalkRecordsRoute(account, source, state, pets, {}, {}, {}, { id, back -> TestDetail(id, back) })
        } } }
        waitText("1 페이지")
        compose.onNodeWithTag("records-shared-open").assertDoesNotExist()
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
