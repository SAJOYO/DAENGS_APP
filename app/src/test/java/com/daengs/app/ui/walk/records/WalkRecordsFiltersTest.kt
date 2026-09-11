package com.daengs.app.ui.walk.records

import android.app.Application
import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.WalkHistoryFilter
import com.daengs.app.walk.records.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRecordsFiltersTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val query = AtomicReference(WalkRecordsQuery())
    private val source = WalkRecordsSource { value -> query.set(value); WalkRecordsSelection(value, emptyList()) }
    private val today = LocalDate.of(2026, 9, 11)

    @Test fun `loading preserves selected dogs and deleted profiles reconcile only after loading`() {
        val pets = mutableStateOf(recordsPreviewPets())
        val loaded = mutableStateOf(true)
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, pets.value, {}, {}, petsLoaded = loaded.value)
        } } }
        compose.onNodeWithTag("records-dog-filter").performClick()
        compose.onNodeWithTag("records-dog-dog-0").performClick()
        compose.onNodeWithTag("records-dog-dog-1").performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitDogs(setOf("dog-0", "dog-1"))
        compose.runOnIdle { loaded.value = false; pets.value = emptyList() }
        compose.onNodeWithTag("records-dog-filter").assertTextContains("2마리")
        assertEquals(setOf("dog-0", "dog-1"), query.get().dogIds)
        compose.runOnIdle { pets.value = recordsPreviewPets().filter { it.id != "dog-0" }; loaded.value = true }
        waitDogs(setOf("dog-1"))
        compose.onNodeWithTag("records-dog-filter").assertTextContains("콩이")
        compose.runOnIdle { pets.value = recordsPreviewPets().filter { it.id !in setOf("dog-0", "dog-1") } }
        waitDogs(null)
        compose.onNodeWithTag("records-dog-filter").assertTextContains("모든 강아지")
    }

    @Test fun `five dog selector is independent and applied subset survives recreation`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, recordsPreviewPets(), {}, {}, today = today)
        } } }
        compose.onNodeWithTag("records-search").assertDoesNotExist()
        capture("records-header", "records-header")
        compose.onNodeWithTag("records-dog-filter").performClick()
        compose.onNodeWithTag("records-dog-all").assertIsOn()
        compose.onNodeWithTag("records-behavior-sniffing").assertDoesNotExist()
        compose.onNodeWithTag("records-period-7").assertDoesNotExist()
        compose.onNodeWithTag("records-dog-dog-0").performScrollTo().performClick()
        compose.onNodeWithTag("records-dog-dog-4").performScrollTo().performClick()
        compose.onNodeWithTag("records-dog-dog-0").assertIsOn()
        compose.onNodeWithTag("records-dog-dog-4").assertIsOn()
        capture("records-dogs", "records-filter-sheet")
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitDogs(setOf("dog-0", "dog-4"))
        compose.onNodeWithTag("records-dog-filter").assertTextContains("2마리")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("records-dog-filter").assertTextContains("2마리")
        compose.onNodeWithTag("records-dog-filter").performClick()
        compose.onNodeWithTag("records-dog-dog-0").assertIsOn().performClick()
        compose.onNodeWithTag("records-dog-dog-4").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("records-conditions-apply").assertIsNotEnabled()
        compose.onNodeWithTag("records-conditions-cancel").performClick()
        assertEquals(setOf("dog-0", "dog-4"), query.get().dogIds)
        compose.onNodeWithTag("records-period-filter").performClick()
        compose.onNodeWithTag("records-dog-all").assertDoesNotExist()
        compose.onNodeWithTag("records-period-7").performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitQuery { it.filter.from == today.minusDays(6) }
        assertEquals(setOf("dog-0", "dog-4"), query.get().dogIds)
        compose.onNodeWithTag("records-dog-filter").performClick()
        compose.onNodeWithTag("records-dog-all").performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitDogs(null)
        assertEquals(today.minusDays(6), query.get().filter.from)
    }

    @Test fun `closing search retains its active keyword across tabs until explicit reset`() {
        show()
        compose.onNodeWithTag("records-search-toggle").performClick()
        compose.onNodeWithTag("records-search").performTextReplacement("강변")
        waitQuery { it.filter.keyword == "강변" }
        compose.onNodeWithTag("records-search-toggle").performClick()
        compose.onNodeWithTag("records-search").assertDoesNotExist()
        compose.onNodeWithTag("records-active-filters").assertTextContains("검색: 강변")
        compose.onNodeWithTag("records-view-overview").performClick()
        compose.onNodeWithTag("records-search-toggle").performClick()
        compose.onNodeWithTag("records-search").assertTextContains("강변")
        compose.onNodeWithTag("records-reset").performClick()
        waitQuery { it.filter == WalkHistoryFilter() }
        compose.onNodeWithTag("records-search").assertTextContains("")
    }

    @Test @Config(qualifiers = "w320dp-h680dp")
    fun `narrow screen with large text keeps every filter and long dog row reachable`() {
        show(1.5f)
        listOf("records-dog-filter", "records-period-filter", "records-behavior-filter", "records-conditions")
            .forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        capture("records-header-large-text", "records-header")
        compose.onNodeWithTag("records-dog-filter").performClick()
        compose.onNodeWithTag("records-dog-dog-4").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("records-conditions-apply").assertIsDisplayed().performClick()
        waitDogs(setOf("dog-4"))
        compose.onNodeWithTag("records-behavior-filter").performClick()
        compose.onNodeWithTag("records-dog-all").assertDoesNotExist()
        compose.onNodeWithTag("records-behavior-sniffing").performScrollTo().performClick()
        capture("records-behavior-large-text", "records-filter-sheet")
        compose.onNodeWithTag("records-conditions-cancel").performClick()
        compose.onNodeWithTag("records-view-walks").assertIsSelected()
    }

    @Suppress("DEPRECATION")
    private fun show(fontScale: Float = 1f) {
        // Use the actual resource configuration so the separate bottom-sheet window inherits it too.
        compose.runOnUiThread {
            val resources = compose.activity.resources
            resources.updateConfiguration(Configuration(resources.configuration).apply { this.fontScale = fontScale },
                resources.displayMetrics)
        }
        compose.setContent { DaengsTheme {
            // The test rule created its host view before the configuration update.
            CompositionLocalProvider(LocalInspectionMode provides true,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                WalkRecordsScreen(source, recordsPreviewPets(), {}, {}, today = today)
            }
        } }
    }

    private fun waitDogs(ids: Set<String>?) = waitQuery { it.dogIds == ids }

    private fun waitQuery(predicate: (WalkRecordsQuery) -> Boolean) = compose.waitUntil(10_000) {
        compose.onNodeWithTag("records-header").fetchSemanticsNode()
        predicate(query.get())
    }

    private fun capture(name: String, tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val file = File("build/outputs/records-filters/$name.png")
        file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
