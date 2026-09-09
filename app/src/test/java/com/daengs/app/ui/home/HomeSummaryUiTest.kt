package com.daengs.app.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
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

    @Test fun homeKeepsOriginalWalkCardAndAddsTerritoryBelowChat() {
        var territoryOpened = false
        var historyOpened = false
        compose.setContent { DaengsTheme {
            HomeScreen(frameTimeMs = 400L, dateLabel = "9월 9일 수요일",
                todayWalks = WalkDayTotals(2, 1_920_000, 2300.0),
                onOpenWalkHistory = { historyOpened = true },
                gameContent = { HomeGameCard("보리의 이번 시즌", "3곳 · 320점 · 순위 —", { territoryOpened = true }) })
        } }
        val chat = compose.onNodeWithText(HomeDemoData.CHAT_TITLE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val territory = compose.onNodeWithContentDescription("보리의 이번 시즌").assertIsDisplayed()
        val territoryBounds = territory.fetchSemanticsNode().boundsInRoot
        assertTrue(chat.bottom < territoryBounds.top)
        val summary = compose.onNodeWithText(HomeDemoData.WALK_TITLE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(territoryBounds.bottom < summary.top)
        listOf("2회", "32분", "2.3km", HomeDemoData.DAILY_WORD_TITLE).forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("평균 속도").assertDoesNotExist()
        screenshot("home-original-territory")
        territory.performClick()
        compose.runOnIdle { assertTrue(territoryOpened) }
        compose.onNodeWithText("지난 산책").performClick()
        compose.runOnIdle { assertTrue(historyOpened) }
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun smallPhoneKeepsOriginalMetricsAndDailyWordVisible() {
        compose.setContent { DaengsTheme {
            HomeScreen(frameTimeMs = 400L, todayWalks = WalkDayTotals.EMPTY,
                onOpenWalkHistory = {}, gameContent = { HomeGameCard("점령 현황", "진행 중인 시즌이 없어요", {}) })
        } }
        listOf("0회", "0분", "0m", HomeDemoData.DAILY_WORD_TITLE).forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("진행 중인 시즌이 없어요").assertIsDisplayed()
        compose.onNodeWithText("평균 속도").assertDoesNotExist()
        screenshot("home-original-territory-small")
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
