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
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-dog-dog-0").performClick()
        compose.onNodeWithTag("records-dog-dog-1").performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitDogs(setOf("dog-0", "dog-1"))
        compose.runOnIdle { loaded.value = false; pets.value = emptyList() }
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("2마리", substring = true)
        assertEquals(setOf("dog-0", "dog-1"), query.get().dogIds)
        compose.runOnIdle { pets.value = recordsPreviewPets().filter { it.id != "dog-0" }; loaded.value = true }
        waitDogs(setOf("dog-1"))
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("콩이", substring = true)
        compose.runOnIdle { pets.value = recordsPreviewPets().filter { it.id !in setOf("dog-0", "dog-1") } }
        waitDogs(null)
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("모든 강아지", substring = true)
    }

    @Test fun `unified dog and period draft survives recreation and cancels without applying`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, recordsPreviewPets(), {}, {}, today = today)
        } } }
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-dog-dog-0").performScrollTo().performClick()
        compose.onNodeWithTag("records-dog-dog-4").performScrollTo().performClick()
        compose.onNodeWithTag("records-period-7").performScrollTo().performClick()
        assertEquals(WalkRecordsQuery(), query.get())
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("records-period-7").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitDogs(setOf("dog-0", "dog-4"))
        assertEquals(today.minusDays(6), query.get().filter.from)
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("2마리", substring = true)
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-dog-dog-0").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("records-dog-dog-4").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("records-conditions-apply").assertIsNotEnabled()
        compose.onNodeWithTag("records-conditions-cancel").performClick()
        assertEquals(setOf("dog-0", "dog-4"), query.get().dogIds)
    }

    @Test fun `keyword draft cancels and applied keyword persists across tabs until reset`() {
        show()
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-search").performScrollTo().performTextReplacement("강변")
        compose.onNodeWithTag("records-conditions-cancel").performClick()
        assertEquals("", query.get().filter.keyword)
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-search").performScrollTo().performTextReplacement("강변")
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitQuery { it.filter.keyword == "강변" }
        val header = compose.onNodeWithTag("records-header").getUnclippedBoundsInRoot()
        val count = compose.onNodeWithTag("records-count").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("records-view-overview").performClick()
        assertEquals(header, compose.onNodeWithTag("records-header").getUnclippedBoundsInRoot())
        assertEquals(count, compose.onNodeWithTag("records-count").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true).assertTextContains("강변", substring = true)
        compose.onNodeWithTag("records-conditions").performClick()
        compose.onNodeWithTag("records-search").performScrollTo().assertTextContains("강변")
        compose.onNodeWithTag("records-conditions-reset").performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        waitQuery { it.filter == WalkHistoryFilter() }
    }

    @Test @Config(qualifiers = "w320dp-h680dp")
    fun `narrow large text keeps all draft fields and actions reachable`() {
        show(1.5f)
        compose.onNodeWithTag("records-conditions").assertIsDisplayed().performClick()
        compose.onNodeWithTag("records-dog-dog-4").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("records-behavior-sniffing").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("records-search").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-extra-conditions").performScrollTo().performClick()
        compose.onNodeWithTag("records-season-WINTER").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-weather-RAIN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("records-conditions-cancel").assertIsDisplayed().performClick()
        compose.onNodeWithTag("records-view-walks").assertIsSelected()
        assertEquals(WalkRecordsQuery(), query.get())
    }

    @Test fun `carer checkboxes follow all and individual rules and candidates follow the chosen dogs`() {
        val carers = listOf(
            com.daengs.app.walk.shared.SharedWalkCarer("me", "롱롱씨 메인", true, listOf("dog-0", "dog-1")),
            com.daengs.app.walk.shared.SharedWalkCarer("u2", "키키", false, listOf("dog-0")),
            com.daengs.app.walk.shared.SharedWalkCarer("u3", "보리아빠", false, listOf("dog-1")),
        )
        val reader = object : com.daengs.app.walk.shared.SharedWalkReader {
            override suspend fun feed(accessToken: String, query: com.daengs.app.walk.shared.SharedWalkFeedQuery, cursor: String?, limit: Int) =
                com.daengs.app.walk.shared.SharedWalkResult.Ready(com.daengs.app.walk.shared.SharedWalkFeedPage(
                    emptyList(), com.daengs.app.walk.shared.SharedWalkTotals(0, 0, 0), carers, null))
            override suspend fun detail(accessToken: String, petId: String, walkId: String) =
                com.daengs.app.walk.shared.SharedWalkResult.Unsupported
        }
        val holder = com.daengs.app.walk.shared.SharedWalksHolder(reader, { "sample-token" }, { true })
        compose.setContent { DaengsTheme { CompositionLocalProvider(LocalInspectionMode provides true) {
            WalkRecordsScreen(source, recordsPreviewPets(), {}, {}, today = today, sharedWalks = holder, myId = "me")
        } } }
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true)
            .assertTextContains("모든 강아지 · 모든 보호자 · 전체 기간", substring = true)
        compose.onNodeWithTag("records-conditions").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records-carer-u2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("보호자 조건만 산책별에 적용되고, 나머지 조건은 산책별과 모아보기에 함께 적용돼요.").assertExists()
        compose.onNodeWithText("산책별과 모아보기에 함께 적용돼요.").assertDoesNotExist()
        compose.onNodeWithTag("records-carer-all").performScrollTo().assertIsOn()
        compose.onNodeWithText("나").performScrollTo().assertExists()

        compose.onNodeWithTag("records-carer-u2").performScrollTo().performClick()
        compose.onNodeWithTag("records-carer-all").assertIsOff()
        compose.onNodeWithTag("records-carer-u2").assertIsOn()
        compose.onNodeWithTag("records-carer-u3").performScrollTo().performClick()
        compose.onNodeWithTag("records-carer-u2").assertIsOn()
        compose.onNodeWithTag("records-carer-u3").assertIsOn()
        compose.onNodeWithTag("records-carer-all").performScrollTo().performClick()
        compose.onNodeWithTag("records-carer-all").assertIsOn()
        compose.onNodeWithTag("records-carer-u2").performScrollTo().assertIsOff()
        compose.onNodeWithTag("records-carer-u3").assertIsOff()
        compose.onNodeWithTag("records-carer-me").performScrollTo().performClick()
        compose.onNodeWithTag("records-carer-me").performClick()
        compose.onNodeWithTag("records-carer-all").assertIsOn()

        // 보리(dog-1)만 고르면 보리를 함께 돌보는 사람만 후보다.
        compose.onNodeWithTag("records-dog-dog-1").performScrollTo().performClick()
        compose.onNodeWithTag("records-carer-u3").performScrollTo().assertExists()
        compose.onNodeWithTag("records-carer-u2").assertDoesNotExist()
        compose.onNodeWithTag("records-carer-u3").performClick()
        compose.onNodeWithTag("records-carer-me").performScrollTo().performClick()
        compose.onNodeWithTag("records-conditions-apply").performClick()
        compose.onNodeWithTag("records-active-filters", useUnmergedTree = true)
            .assertTextContains("콩이 · 나 외 1명 · 전체 기간", substring = true)
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
