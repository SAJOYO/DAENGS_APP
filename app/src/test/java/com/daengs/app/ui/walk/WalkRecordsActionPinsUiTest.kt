package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.location.*
import com.daengs.app.map.features.records.*
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.records.*
import com.daengs.app.walk.*
import com.daengs.app.walk.records.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRecordsActionPinsUiTest {
    @get:Rule val compose = createComposeRule()
    private val point = GeoPoint(37.5, 127.0)
    private fun record(id: String, types: List<WalkMomentType>, located: Boolean = true): WalkRecord {
        val summary = WalkSummary(id, listOf("dog"), 1000, 20000L, null, 10.0, 19000,
            listOf(listOf(LocationSample(point, 1000), LocationSample(GeoPoint(37.5001,127.0),3000))), point)
        return WalkRecord(summary, title = id, entries = types.mapIndexed { index, type ->
            WalkEntry("$id-$index", id, type, 4000L + index, point.takeIf { located }, 4000L.takeIf { located }, petId = "dog")
        })
    }
    private val records = listOf(record("first", listOf(WalkMomentType.SNIFFING, WalkMomentType.BARKING)),
        record("second", listOf(WalkMomentType.EXCRETION), false))
    private val source = WalkRecordsSource { query -> WalkRecordsSelection(query, records) }
    private fun await(tag: String) = compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun `pin types change pins only and unlocated action can open its original walk`() {
        val opened = mutableListOf<com.daengs.app.walk.diary.DiaryActionTarget>()
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, emptyList(), {}, { error("Lost action identity") }, today = LocalDate.of(2026,9,13), onOpenAction = opened::add)
        } } }
        await("records-count")
        compose.onNodeWithTag("records-view-overview").performClick()
        await("records-pins-browse")
        compose.onNodeWithTag("records-count").assertTextEquals("산책 2회")
        compose.onNodeWithTag("records-pins-browse").assertTextContains("액션 3건").performClick()
        compose.onNodeWithTag("records-pins-summary").assertTextEquals("핀 표시 2건 · 위치 없음 1건")
        val key = WalkBehaviorRecord(records[1].entries.single(), records[1]).key
        compose.onNodeWithTag("records-behavior-list").performScrollToNode(hasTestTag("records-behavior-entry-$key"))
        compose.onNodeWithTag("records-behavior-entry-$key").performClick()
        compose.onNodeWithTag("records-behavior-open-$key").performScrollTo().performClick()
        assertEquals(listOf(com.daengs.app.walk.diary.DiaryActionTarget("second", "second-0")), opened)
        compose.onNodeWithTag("records-pins-type-barking").performScrollTo().performClick()
        compose.onNodeWithTag("records-count").assertTextEquals("산책 2회")
        compose.onNodeWithTag("records-map-sheet-toggle").assertTextContains("액션 기록 1건 · 접기")
    }

    @Test fun `native group inspection lists every action and preserves the selected action after restoration`() {
        val restore = StateRestorationTester(compose)
        var pinState: WalkRecordsActionPinState? = null
        var highlighted: String? = null
        val selection = WalkRecordsSelection(WalkRecordsQuery(), records)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val state = rememberWalkRecordsActionPinState()
            var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
            SideEffect { pinState = state; highlighted = selected }
            WalkRecordsOverview(selection, emptyList(), null, emptyList(), null, {}, selected, emptySet(),
                { selected = it }, {}, {}, { selected = null }, {}, androidx.compose.foundation.lazy.rememberLazyListState(),
                null, {}, emptyList(), 0, expanded = true, actionPinState = state)
        } } }
        compose.runOnIdle { pinState!!.inspect(walkRecordsActionPins(selection).groups.single()) }
        compose.onNodeWithTag("records-pins-summary").assertTextEquals("이 위치의 액션 2건")
        val key = WalkBehaviorRecord(records[0].entries[1], records[0]).key
        compose.onNodeWithTag("records-behavior-entry-$key").performClick()
        compose.runOnIdle { assertEquals("first", highlighted) }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("records-pins-summary").assertTextEquals("이 위치의 액션 2건")
        compose.onNodeWithTag("records-behavior-entry-$key").assertIsSelected()
        compose.onNodeWithTag("records-behavior-open-$key").assertExists()
    }

    @Test fun `nearby group keeps its record membership through restoration and offers explicit expansion`() {
        val near = record("near", listOf(WalkMomentType.SNIFFING)).let { walk ->
            walk.copy(entries = walk.entries.map { it.copy(point = GeoPoint(37.5001,127.0)) })
        }
        val selection = WalkRecordsSelection(WalkRecordsQuery(), listOf(records.first(), near))
        val restore = StateRestorationTester(compose)
        var pinState: WalkRecordsActionPinState? = null
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val state = rememberWalkRecordsActionPinState()
            SideEffect { pinState = state }
            WalkRecordsOverview(selection, emptyList(), null, emptyList(), null, {}, null, emptySet(), {}, {}, {}, {}, {},
                androidx.compose.foundation.lazy.rememberLazyListState(), null, {}, emptyList(), 0,
                expanded = true, actionPinState = state)
        } } }
        compose.runOnIdle {
            pinState!!.type.value = WalkMomentType.SNIFFING
            val entries = walkRecordsActionPins(selection, setOf(WalkMomentType.SNIFFING)).records
            pinState!!.inspect(WalkActionPinGroup(point, entries))
        }
        compose.onNodeWithTag("records-pins-summary").assertTextEquals("이 구간의 액션 2건")
        compose.onNodeWithTag("records-pins-expand").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("records-pins-summary").assertTextEquals("이 구간의 액션 2건")
        compose.runOnIdle { assertEquals(2, pinState!!.groupKeys.value.size) }
        compose.onNodeWithTag("records-pins-type-barking").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(pinState!!.groupKeys.value.isEmpty()) }
        compose.onNodeWithTag("records-pins-expand").assertDoesNotExist()
    }

    @Test fun `hide and refreshed removal clear markers and stale selected action without losing other records`() {
        val current = mutableStateOf(records)
        val hidden = mutableStateOf(emptySet<String>())
        var pinState: WalkRecordsActionPinState? = null
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            val state = rememberWalkRecordsActionPinState()
            SideEffect { pinState = state }
            WalkRecordsOverview(WalkRecordsSelection(WalkRecordsQuery(), current.value), emptyList(), null, emptyList(), null,
                {}, null, hidden.value, {}, { id -> hidden.value = hidden.value + id }, { hidden.value = emptySet() }, {}, {},
                androidx.compose.foundation.lazy.rememberLazyListState(), null, {}, emptyList(), 0,
                expanded = true, actionPinState = state)
        } } }
        compose.onNodeWithTag("records-pins-browse").performClick()
        val key = WalkBehaviorRecord(records[0].entries[1], records[0]).key
        compose.onNodeWithTag("records-behavior-entry-$key").performClick()
        compose.onNodeWithTag("records-behavior-hide-$key").performScrollTo().performClick()
        compose.onNodeWithTag("records-pins-summary").assertTextEquals("핀 표시 0건 · 위치 없음 1건")
        compose.onNodeWithTag("records-behavior-entry-$key").assertIsNotSelected()
        compose.onNodeWithTag("records-map-restore-all").performClick()
        compose.onNodeWithTag("records-pins-summary").assertTextEquals("핀 표시 2건 · 위치 없음 1건")
        compose.onNodeWithTag("records-behavior-entry-$key").performClick()
        compose.runOnIdle { current.value = listOf(records[1]) }
        compose.onNodeWithTag("records-behavior-entry-$key").assertDoesNotExist()
        compose.runOnIdle { assertNull(pinState!!.selectedKey.value) }
        compose.onNodeWithTag("records-pins-summary").assertTextEquals("핀 표시 0건 · 위치 없음 1건")
    }
}
