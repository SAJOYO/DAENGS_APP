package com.daengs.app.ui.game.bookmarks

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import com.daengs.app.territory.bookmarks.*
import com.daengs.app.ui.game.*
import com.daengs.app.ui.game.owned.OwnedMapPresentation
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.territory.support.bookmarkJson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class TerritoryBookmarksScreenTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val item = parseBookmarkList(bookmarkJson()).items.single()
    private val ready = BookmarkState(BookmarkStatus.READY, listOf(item), 20)

    @Test fun `saved list opens exact coordinate without walking and back restores list`() {
        var map: OwnedMapPresentation? = null
        var backs = 0
        compose.setContent { DaengsTheme {
            TerritoryBookmarksScreen(ready, { backs++ }, {}, {}, {}, mapSurface = { value, _, _ -> map = value; Text("지도") })
        } }
        compose.onNodeWithText("저장한 전봇대 1 / 20곳").assertIsDisplayed()
        compose.onNodeWithText("지도에서 보기").performClick()
        compose.runOnIdle {
            assertEquals(item.point, map!!.centerOn)
            assertEquals(item.siteId, map!!.scene.territorySites.single().id)
            assertNull(map!!.scene.currentPosition)
            assertFalse(map!!.scene.territorySites.single().occupancyKnown)
        }
        compose.onNodeWithText("산책 시작").assertDoesNotExist()
        compose.onNodeWithText(item.siteId).assertDoesNotExist()
        compose.onNodeWithTag("bookmarks-back").performClick()
        compose.onNodeWithTag("bookmarks-list").assertIsDisplayed()
        assertEquals(0, backs)
        compose.onNodeWithTag("bookmarks-back").performClick()
        assertEquals(1, backs)
    }
    @Test fun `removed and temporarily unavailable locations can still be deleted`() {
        var removed: String? = null
        val missing = ready.copy(items = listOf(item.copy(point = null, locationStatus = BookmarkLocationStatus.NOT_FOUND)))
        compose.setContent { DaengsTheme { TerritoryBookmarksScreen(missing, {}, {}, {}, { removed = it }) } }
        compose.onNodeWithText("지도에서 보기").assertIsNotEnabled()
        compose.onNodeWithContentDescription("북마크 해제").performClick()
        assertEquals(item.siteId, removed)
    }
    @Test fun `busy action blocks duplicate taps and failures are not empty`() {
        var state by mutableStateOf(ready.copy(busySite = item.siteId))
        compose.setContent { DaengsTheme { TerritoryBookmarksScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("북마크 확인 중").assertIsNotEnabled()
        compose.runOnIdle { state = BookmarkState(BookmarkStatus.ERROR, message = "북마크 기능 준비 중") }
        compose.onNodeWithText("북마크 기능 준비 중").assertIsDisplayed()
        compose.onNodeWithText("저장한 전봇대 0 / 20곳").assertDoesNotExist()
    }
    @Test @Config(qualifiers = "w320dp-h720dp")
    fun `large text and recreation preserve map selection and accessible removal`() {
        val restoration = StateRestorationTester(compose)
        var map: OwnedMapPresentation? = null
        restoration.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) { DaengsTheme {
            TerritoryBookmarksScreen(ready, {}, {}, {}, {}, mapSurface = { value, _, _ -> map = value; Text("지도") })
        } } }
        compose.onNodeWithText("지도에서 보기").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(item.point, map!!.centerOn) }
        compose.onNodeWithContentDescription("북마크 해제").performScrollTo().assertIsDisplayed()
    }
    @Test fun `bookmark menu is independent of having a pet or active game`() {
        var calls = 0
        compose.setContent { DaengsTheme {
            TerritoryGameScreen(TerritoryGameOverview(GameOverviewStatus.NO_PET), null, emptyList(), { null }, 0,
                {}, {}, {}, {}, onOpenBookmarks = { calls++ })
        } }
        compose.onNodeWithTag("game-bookmarks-open").performClick()
        assertEquals(1, calls)
    }
}
