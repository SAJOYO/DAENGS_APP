package com.daengs.app.ui.walk.records

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.location.*
import com.daengs.app.map.features.records.*
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryActionTarget
import com.daengs.app.walk.records.*
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
class WalkRecordsPlaceCardsTest {
    @get:Rule val compose = createComposeRule()
    private val point = GeoPoint(37.5, 127.0)
    private fun walk(id: String, start: Long, count: Int) = WalkRecord(
        WalkSummary(id, listOf("dog"), start, start + 900000, null, 400.0, 900000,
            listOf(listOf(LocationSample(point,start), LocationSample(GeoPoint(37.5001,127.0), start+1000))), point),
        title = "산책 $id", entries = (0 until count).map {
            WalkEntry("action-$it", id, if (it == 0) WalkMomentType.SNIFFING else WalkMomentType.BARKING,
                start + 2000 + it*1000, point = point, petId = "dog") })
    private val records = listOf(walk("recent", 100000, 2), walk("older", 1000, 1))

    @Test fun `visits count sessions once but preserve every action even on the same day and coordinate`() {
        val actions = walkRecordsActionPins(WalkRecordsSelection(WalkRecordsQuery(), records)).records
        val visits = placeWalks(actions + actions.first())
        assertEquals(listOf("recent", "older"), visits.map { it.id })
        assertEquals(listOf(2,1), visits.map { it.actions.size })
        assertEquals(3, visits.flatMap { it.actions }.map { it.key }.distinct().size)
        assertEquals(actions.first { it.entry.type == WalkMomentType.BARKING }, visits.currentPlaceAction(actions.first { it.entry.type == WalkMomentType.BARKING }.key))
        assertEquals("older", placeWalks(actions.filter { it.walk.summary.sessionId == "older" }).currentPlaceAction("deleted")!!.walk.summary.sessionId)
    }

    @Test fun `pin inspection and next visit keep camera and drawer and open the exact action`() {
        val opened = mutableListOf<DiaryActionTarget>()
        var state: WalkRecordsActionPinState? = null
        var cameraRequests = 0
        var highlighted: String? = null
        val selection = WalkRecordsSelection(WalkRecordsQuery(), records)
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val pins = rememberWalkRecordsActionPinState()
            SideEffect { state = pins }
            var selected by remember { mutableStateOf<String?>(null) }
            SideEffect { highlighted = selected }
            WalkRecordsOverview(selection, emptyList(), null, emptyList(), null, {}, selected, emptySet(),
                { cameraRequests++ }, {}, {}, {}, {}, rememberLazyListState(), null, {}, emptyList(), 0,
                actionPinState = pins, onInspect = { selected = it }, onOpenAction = opened::add)
        } } }
        val sheet = compose.onNodeWithTag("records-map-sheet").getUnclippedBoundsInRoot()
        val map = compose.onNodeWithTag("records-overview-map").getUnclippedBoundsInRoot()
        compose.runOnIdle { state!!.inspect(walkRecordsActionPins(selection).groups.single()) }
        compose.onNodeWithTag("records-map-sheet-toggle").assertTextContains("이곳의 산책 2회 · 행동 3건")
        compose.onNodeWithTag("records-place-index").assertTextEquals("1 / 2")
        compose.onNodeWithTag("records-place-next").performClick()
        compose.onNodeWithTag("records-place-index").assertTextEquals("2 / 2")
        compose.onNodeWithTag("records-place-next").assertIsNotEnabled()
        assertEquals(sheet, compose.onNodeWithTag("records-map-sheet").getUnclippedBoundsInRoot())
        assertEquals(map, compose.onNodeWithTag("records-overview-map").getUnclippedBoundsInRoot())
        assertEquals(0, cameraRequests)
        compose.onNodeWithTag("records-place-peek-open").performClick()
        assertEquals(listOf(DiaryActionTarget("older","action-0")), opened)
        compose.onNodeWithTag("records-pins-type-barking").performClick()
        compose.runOnIdle { assertNull(highlighted); assertTrue(state!!.groupKeys.value.isEmpty()) }
    }

    @Test fun `session card groups actions and restores the chosen original action`() {
        val restore = StateRestorationTester(compose)
        val opened = mutableListOf<DiaryActionTarget>()
        var state: WalkRecordsActionPinState? = null
        val selection = WalkRecordsSelection(WalkRecordsQuery(), records)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val pins = rememberWalkRecordsActionPinState()
            var selected by rememberSaveable { mutableStateOf<String?>(null) }
            SideEffect { state = pins }
            WalkRecordsOverview(selection, emptyList(), null, emptyList(), null, {}, selected, emptySet(),
                {}, {}, {}, {}, {}, rememberLazyListState(), null, {}, emptyList(), 0, expanded = true,
                actionPinState = pins, onInspect = { selected = it }, onOpenAction = opened::add)
        } } }
        compose.runOnIdle { state!!.inspect(walkRecordsActionPins(selection).groups.single()) }
        compose.onAllNodesWithTag("records-place-walk-recent").assertCountEquals(1)
        val key = WalkBehaviorRecord(records[0].entries[1],records[0]).key
        compose.onNodeWithTag("records-behavior-entry-$key").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("records-behavior-entry-$key").assertIsSelected()
        compose.onNodeWithTag("records-behavior-open-$key").performScrollTo().performClick()
        assertEquals(listOf(DiaryActionTarget("recent", "action-1")), opened)
    }

    @Test @Config(qualifiers = "w320dp-h680dp")
    fun `compact large text keeps next and diary actions reachable`() {
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true,
            LocalDensity provides Density(LocalDensity.current.density,1.5f)) {
            WalkRecordsOverview(WalkRecordsSelection(WalkRecordsQuery(),records), emptyList(), null, emptyList(), null,
                {}, null, emptySet(), {}, {}, {}, {}, {}, rememberLazyListState(), null, {}, emptyList(), 0)
        } } }
        compose.onNodeWithTag("records-place-next").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("records-place-peek-open").performScrollTo().assertIsDisplayed()
    }

    @Test fun `empty action filter explains the result in collapsed card and can restore all actions`() {
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsOverview(WalkRecordsSelection(WalkRecordsQuery(), records), emptyList(), null, emptyList(), null,
                {}, null, emptySet(), {}, {}, {}, {}, {}, rememberLazyListState(), null, {}, emptyList(), 0)
        } } }
        compose.onNodeWithTag("records-pins-type-excretion").performClick()
        compose.onNodeWithText("배설 기록이 없어요.").assertIsDisplayed()
        compose.onNodeWithTag("records-map-sheet-toggle").performClick()
        compose.onNodeWithTag("records-actions-empty").assertIsDisplayed()
        compose.onNodeWithTag("records-actions-empty-action").assertTextContains("모든 행동 보기").performClick()
        compose.onNodeWithTag("records-place-index").assertTextEquals("1 / 2")
        compose.onNodeWithTag("records-actions-empty").assertDoesNotExist()
    }

    @Test fun `unlocated action remains explicit in compact card and opens its original diary`() {
        val unlocated = records.take(1).map { walk -> walk.copy(entries = walk.entries.map { it.copy(point = null) }) }
        val opened = mutableListOf<DiaryActionTarget>()
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsOverview(WalkRecordsSelection(WalkRecordsQuery(), unlocated), emptyList(), null, emptyList(), null,
                {}, null, emptySet(), {}, {}, {}, {}, {}, rememberLazyListState(), null, {}, emptyList(), 0,
                onOpenAction = opened::add)
        } } }
        compose.onNodeWithText("위치 없음", substring = true).assertExists()
        compose.onNodeWithTag("records-place-peek-open").assertIsDisplayed().performClick()
        assertEquals(listOf(DiaryActionTarget("recent", "action-0")), opened)
    }

    @Test fun `route failure retry remains accessible above expanded cards and preserves selected action`() {
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val selection = WalkRecordsSelection(WalkRecordsQuery(), records)
        val source = object : WalkRecordsSource {
            override suspend fun select(query: WalkRecordsQuery) = selection
            override suspend fun loadRoute(record: WalkRecord): WalkSummary {
                if (reads.incrementAndGet() == 1) error("route unavailable")
                return record.summary
            }
        }
        var pins: WalkRecordsActionPinState? = null
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val state = rememberWalkRecordsActionPinState()
            SideEffect { pins = state }
            WalkRecordsOverview(selection, emptyList(), null, emptyList(), null,
                {}, "recent", emptySet(), {}, {}, {}, {}, {}, rememberLazyListState(), null, {}, emptyList(), 0,
                routeSource = source, expanded = true, actionPinState = state)
        } } }
        compose.runOnIdle { pins!!.inspect(walkRecordsActionPins(selection).groups.single()) }
        val selected = pins!!.selectedKey.value
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-route-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("records-route-retry").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-route-error").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertEquals(selected, pins!!.selectedKey.value); assertEquals(2, reads.get()) }
        compose.onNodeWithTag("records-place-walk-recent").assertExists()
    }
}
