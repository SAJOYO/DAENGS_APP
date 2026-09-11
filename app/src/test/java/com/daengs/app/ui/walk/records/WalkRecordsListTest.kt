package com.daengs.app.ui.walk.records

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.ui.walk.formatWalkDay
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.records.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRecordsListTest {
    @get:Rule val compose = createComposeRule()
    private val pets = recordsPreviewPets()

    @Test fun `date grouping follows local start date including midnight and year boundaries`() {
        val before = record("before", at = Instant.parse("2025-12-31T14:59:00Z").toEpochMilli())
        val after = record("after", at = Instant.parse("2025-12-31T15:01:00Z").toEpochMilli())
        val groups = groupWalkRecordDays(listOf(after, before), ZoneId.of("Asia/Seoul"))
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31)), groups.keys.toList())
        assertEquals(listOf("before"), groups.getValue(LocalDate.of(2025, 12, 31)).map { it.summary.sessionId })
        assertEquals(1, groupWalkRecordDays(listOf(after, before), ZoneId.of("UTC")).size)
    }

    @Test fun `same day is headed once and each card opens its own record`() {
        val opened = AtomicReference<String>()
        val records = listOf(record("evening", "저녁 바람 따라"), record("morning", "아침 동네 한 바퀴"))
        compose.setContent { DaengsTheme {
            WalkRecordsList(records, 1, 1, {}, {}, opened::set, pets, Modifier.fillMaxSize())
        } }
        compose.onAllNodesWithTag("records-day-2026-09-11").assertCountEquals(1)
        compose.onNodeWithText("‹ 이전").assertIsNotEnabled()
        compose.onNodeWithText("다음 ›").assertIsNotEnabled()
        compose.onNodeWithTag("records-walk-morning").performClick()
        assertEquals("morning", opened.get())
        capture("cards")
    }

    @Test fun `missing title route and profile remain explicit without losing summary values`() {
        val original = record("missing", "   ").let { it.copy(summary = it.summary.copy(
            dogIds = listOf("dog-0", "removed"), segments = emptyList(), weather = null)) }
        compose.setContent { DaengsTheme {
            WalkRecordsList(listOf(original), 1, 1, {}, {}, {}, pets, Modifier.fillMaxSize())
        } }
        compose.onNodeWithText("${formatWalkDay(original.summary.startedAtMillis)} 산책").assertExists()
        compose.onNodeWithText("경로 미기록").assertIsDisplayed()
        compose.onNodeWithText("두부 · 이름 미확인").assertExists()
        compose.onNodeWithText("출발 날씨 정보 없음", substring = true).assertExists()
        compose.onNodeWithText("750m").assertExists()
        compose.onNodeWithText("15:00").assertExists()
        capture("missing-data")
    }

    @Test fun `page and list position survive tabs and restoration without showing another page on map`() {
        val records = (1..7).map { record("r$it", "기록 $it", at = atStart + it * 60_000) }
        val source = WalkRecordsSource { query -> selectWalkRecords(records, query) }
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, pets, {}, {})
        } } }
        waitText("1 페이지")
        capture("screen")
        compose.onNodeWithText("다음 ›").performClick()
        waitText("2 페이지")
        compose.onNodeWithTag("records-walk-r2").assertExists()
        compose.onNodeWithTag("records-walk-r7").assertDoesNotExist()
        compose.onNodeWithText("‹ 이전").performClick()
        waitText("1 페이지")
        compose.onNodeWithTag("records-walk-list").performScrollToNode(hasTestTag("records-walk-r3"))
        val before = compose.onNodeWithTag("records-walk-r3").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("records-view-overview").performClick()
        waitText("선택 산책 7회 · 표시 흔적 0개")
        compose.onNodeWithTag("records-view-walks").performClick()
        compose.onNodeWithTag("records-walk-r3").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("records-walk-r3").getUnclippedBoundsInRoot())
        restore.emulateSavedInstanceStateRestore()
        waitText("1 페이지")
        assertEquals(before, compose.onNodeWithTag("records-walk-r3").getUnclippedBoundsInRoot())
    }

    @Test @Config(qualifiers = "w320dp-h680dp")
    fun `large text long titles and five dogs keep metrics and page controls reachable`() {
        val long = record("long", "다섯 아이들과 골목을 지나 나무 그늘을 찾아 천천히 걸었던 아주 긴 오후의 산책")
            .let { it.copy(summary = it.summary.copy(dogIds = pets.map { pet -> pet.id })) }
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true,
            LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
            WalkRecordsScreen(WalkRecordsSource { query -> WalkRecordsSelection(query, listOf(long)) }, pets, {}, {})
        } } }
        waitText("1 페이지")
        compose.onNodeWithTag("records-walk-list").performScrollToNode(hasTestTag("records-walk-long"))
        compose.onNodeWithText("750m").assertIsDisplayed()
        compose.onNodeWithText("15:00").assertIsDisplayed()
        compose.onNodeWithText("다음 ›").assertIsDisplayed()
        compose.onNodeWithContentDescription("동행견: 두부, 콩이, 보리, 호두, 이름이 아주 긴 우리집 설기").assertExists()
        capture("large-text")
    }

    private fun record(id: String, title: String? = "동네 한 바퀴", at: Long = atStart): WalkRecord =
        WalkRecord(previewDiarySummary().copy(sessionId = id, dogIds = listOf("dog-0", "dog-1"),
            startedAtMillis = at, endedAtMillis = at + 900_000, weather = RecordedWeather(0, true, 23f)), title)

    private fun waitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(name: String) {
        val file = File("build/outputs/records-list/$name.png")
        file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private val atStart = LocalDate.of(2026, 9, 11).atTime(18, 40).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
