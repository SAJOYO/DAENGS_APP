package com.daengs.app.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    @Test fun homeKeepsChatAboveSummaryAndIndependentMetricActions() {
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
        val distance = compose.onNodeWithContentDescription("거리 2.3km").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val speed = compose.onNodeWithContentDescription("평균 속도 4.3km/h").assertIsDisplayed()
        assertTrue(distance.right <= speed.fetchSemanticsNode().boundsInRoot.left)
        // Compare visible values: the speed button's touch bounds include extra padding.
        assertEquals(compose.onNodeWithText("2.3km", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top,
            compose.onNodeWithText("4.3km/h", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top, 1f)
        screenshot("home-pink-summary")
        territory.performClick()
        compose.runOnIdle { assertTrue(territoryOpened) }
        compose.onNodeWithText("지난 산책").performClick()
        compose.runOnIdle { assertTrue(historyOpened) }
        speed.performClick()
        compose.onNodeWithText("오늘 완료한 산책의 총 거리 ÷ 총 활동 시간이에요.", substring = true).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun smallPhoneKeepsFourMetricsVisibleWithoutInventedSpeed() {
        compose.setContent { DaengsTheme {
            HomeScreen(frameTimeMs = 400L, todayWalks = WalkDayTotals.EMPTY,
                onOpenWalkHistory = {}, gameContent = { HomeGameCard("점령 현황", "진행 중인 시즌이 없어요", {}) })
        } }
        listOf("횟수 0회", "시간 0분", "거리 0m", "평균 속도 —").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        compose.onNodeWithText("진행 중인 시즌이 없어요").assertIsDisplayed()
        screenshot("home-pink-small-empty")
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun largeTextWrapsMetricsIntoTwoRows() {
        compose.setContent { DaengsTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                WalkSummaryCard(Modifier.padding(14.dp), WalkDayTotals(2, 4_500_000, 5300.0), {},
                    territoryHeader = { HomeGameCard("점령 현황", "시즌 현황을 불러오고 있어요", {}) })
            }
        } }
        val time = compose.onNodeWithContentDescription("시간 1시간 15분").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val distance = compose.onNodeWithContentDescription("거리 5.3km").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(distance.top > time.bottom)
        compose.onNodeWithContentDescription("평균 속도 4.2km/h").assertIsDisplayed()
        screenshot("home-pink-large-text")
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
