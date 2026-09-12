package com.daengs.app.ui.walk.records

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRecordsMapFrameTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val sample = (1..3).map { n ->
        val summary = previewDiarySummary().copy(sessionId = "map-$n", dogIds = listOf("dog-0"))
        WalkRecord(summary, "저녁 산책 $n",
            trace = WalkTraceSheet(summary.sessionId, cells = setOf(SpatialDiaryCellId(832649, 375728))),
            entries = if (n == 3) emptyList() else listOf(WalkEntry("entry-$n", summary.sessionId,
                WalkMomentType.SNIFFING, summary.startedAtMillis + 1_000,
                point = if (n == 1) summary.segments.first().first().point else null, petId = "dog-0")))
    }
    private val source = WalkRecordsSource { query -> selectWalkRecords(sample, query) }

    @Test fun `drawer gestures preserve map size and expansion survives tabs and restoration`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, recordsPreviewPets(), {}, {})
        } } }
        openOverview()
        val map = compose.onNodeWithTag("records-overview-map").getUnclippedBoundsInRoot()
        val sheet = compose.onNodeWithTag("records-map-sheet").getUnclippedBoundsInRoot()
        assertTrue(map.bottom - map.top > (sheet.bottom - sheet.top) * 2f)
        capture("collapsed")
        compose.onNodeWithTag("records-map-sheet-toggle").performTouchInput {
            down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, -120f)); up()
        }
        sheetState("펼침")
        assertEquals(map, compose.onNodeWithTag("records-overview-map").getUnclippedBoundsInRoot())
        capture("expanded")
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithTag("records-view-overview").performClick()
        sheetState("펼침")
        restore.emulateSavedInstanceStateRestore()
        waitTag("records-map-sheet")
        sheetState("펼침")
        compose.onNodeWithTag("records-map-sheet-toggle").performClick()
        sheetState("접힘")
    }

    @Test fun `behavior overlap uses related walks once and hiding keeps its evidence and missing locations`() {
        show()
        openOverview()
        compose.onNodeWithTag("records-behavior-filter").performClick()
        compose.onNodeWithTag("records-behavior-sniffing").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitTag("records-behavior-count")
        compose.onNodeWithTag("records-behavior-count").assertTextEquals("행동 기록 2건 · 관련 산책 2회")
        compose.onNodeWithTag("records-behavior-view-overlap").performClick()
        waitText("선택 산책 2회 · 겹침 표시 2회")
        compose.onNodeWithTag("records-map-sheet-toggle").performClick()
        compose.onNodeWithTag("records-map-list").performScrollToNode(hasTestTag("records-map-record-map-1"))
        compose.onNodeWithTag("records-map-record-map-1").performClick()
        compose.onNodeWithTag("records-map-hide-map-1").performScrollTo().performClick()
        waitText("선택 산책 2회 · 겹침 표시 1회")
        capture("behavior-overlap")
        compose.onNodeWithTag("records-overlap-min-3").performClick()
        waitText("3회 이상 겹친 구간이 없어요.")
        compose.onNodeWithTag("records-overlap-empty").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-behavior-count").assertTextEquals("행동 기록 2건 · 관련 산책 2회")
        compose.onNodeWithTag("records-behavior-view-locations").performClick()
        val missing = WalkBehaviorRecord(sample[1].entries.single(), sample[1]).key
        compose.onNodeWithTag("records-behavior-list").performScrollToNode(hasTestTag("records-behavior-entry-$missing"))
        compose.onNodeWithTag("records-behavior-entry-$missing").performClick()
        compose.onNodeWithTag("records-behavior-open-$missing").performScrollTo().assertIsEnabled()
        capture("unlocated")
    }

    @Test @Config(qualifiers = "w320dp-h680dp")
    fun `small large text layout keeps icon and map controls reachable`() {
        show(1.5f)
        val search = compose.onNodeWithTag("records-search-toggle").getUnclippedBoundsInRoot()
        val conditions = compose.onNodeWithTag("records-conditions").getUnclippedBoundsInRoot()
        assertEquals(search.top, conditions.top)
        assertTrue(conditions.left >= search.right)
        compose.onNodeWithTag("records-conditions").assertContentDescriptionEquals("계절과 출발 날씨 조건")
        compose.onNodeWithText("조건", substring = false).assertDoesNotExist()
        openOverview()
        compose.onNodeWithTag("records-traces-overlap").performScrollTo().performClick()
        compose.onNodeWithTag("records-overlap-min-5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-map-sheet-toggle").performClick()
        compose.onNodeWithTag("records-map-list").performScrollToNode(hasTestTag("records-map-record-map-3"))
        compose.onNodeWithTag("records-map-record-map-3").performClick()
        compose.onNodeWithTag("records-map-open-map-3").performScrollTo().assertIsDisplayed()
        capture("small-large-text")
    }

    private fun show(scale: Float = 1f) = compose.setContent { DaengsTheme {
        CompositionLocalProvider(LocalInspectionMode provides true,
            LocalDensity provides Density(LocalDensity.current.density, scale)) {
            WalkRecordsScreen(source, recordsPreviewPets(), {}, {})
        }
    } }
    private fun openOverview() {
        waitTag("records-count")
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 3회 · 표시 흔적 3개")
    }
    private fun sheetState(value: String) = compose.onNodeWithTag("records-map-sheet")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))
    private fun waitTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        val output = File("build/outputs/records-map/$name.png").also { it.parentFile!!.mkdirs() }
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
