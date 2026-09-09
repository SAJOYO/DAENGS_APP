package com.daengs.app.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.WalkDayTotals
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeSummaryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun territoryInTopBarPreservesOriginalMiniroomSpaceAndWalkCard() {
        var territoryOpened = false
        var historyOpened = false
        val showTerritory = mutableStateOf(false)
        compose.setContent { DaengsTheme {
            HomeScreen(frameTimeMs = 400L, dateLabel = "9월 9일 수요일",
                todayWalks = WalkDayTotals(2, 1_920_000, 2300.0),
                onOpenWalkHistory = { historyOpened = true },
                gameContent = if (showTerritory.value) {
                    { HomeGameCard("보리의 이번 시즌", "3곳 · 320점 · 순위 —", { territoryOpened = true }) }
                } else null)
        } }
        val originalRoom = compose.onNodeWithTag("home-miniroom").fetchSemanticsNode().boundsInRoot
        val originalChat = compose.onNodeWithText(HomeDemoData.CHAT_TITLE).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { showTerritory.value = true }
        assertEquals(originalRoom, compose.onNodeWithTag("home-miniroom").fetchSemanticsNode().boundsInRoot)
        assertEquals(originalChat, compose.onNodeWithText(HomeDemoData.CHAT_TITLE).fetchSemanticsNode().boundsInRoot)
        val chat = compose.onNodeWithText(HomeDemoData.CHAT_TITLE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val territory = compose.onNodeWithContentDescription("보리의 이번 시즌", substring = true).assertIsDisplayed()
        val territoryBounds = territory.fetchSemanticsNode().boundsInRoot
        assertTrue(territoryBounds.bottom <= originalRoom.top)
        val summary = compose.onNodeWithText(HomeDemoData.WALK_TITLE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(chat.bottom < summary.top)
        listOf("2회", "32분", "2.3km", HomeDemoData.DAILY_WORD_TITLE).forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("평균 속도").assertDoesNotExist()
        screenshot("home-top-territory")
        territory.performClick()
        compose.runOnIdle { assertTrue(territoryOpened) }
        compose.onNodeWithText("지난 산책").performClick()
        compose.runOnIdle { assertTrue(historyOpened) }
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun smallPhoneKeepsOriginalMetricsAndDailyWordVisible() {
        val showTerritory = mutableStateOf(false)
        compose.setContent { DaengsTheme {
            HomeScreen(frameTimeMs = 400L, todayWalks = WalkDayTotals.EMPTY,
                onOpenWalkHistory = {}, gameContent = if (showTerritory.value) {
                    { HomeGameCard("점령 현황", "3곳 · 320점 · 순위 —", {}) }
                } else null)
        } }
        val originalRoom = compose.onNodeWithTag("home-miniroom").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { showTerritory.value = true }
        assertEquals(originalRoom, compose.onNodeWithTag("home-miniroom").fetchSemanticsNode().boundsInRoot)
        listOf("0회", "0분", "0m", HomeDemoData.DAILY_WORD_TITLE).forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("3곳 · 320점 · 순위 —").assertIsDisplayed()
        compose.onNodeWithText("평균 속도").assertDoesNotExist()
        screenshot("home-top-territory-small")
    }

    private fun screenshot(name: String) {
        val output = File("build/reports/home-summary/$name.png")
        output.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
