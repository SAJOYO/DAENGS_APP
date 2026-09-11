package com.daengs.app.ui.game.bookmarks

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.auth.*
import com.daengs.app.territory.bookmarks.*
import com.daengs.app.ui.game.owned.*
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
class TerritoryBookmarkActionTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun `owned star list and account switch share only the current login state`() {
        var showList by mutableStateOf(false)
        var loggedIn by mutableStateOf(true)
        var writes = 0
        val client = object : TerritoryBookmarkClient {
            var items = emptyList<TerritoryBookmark>()
            override suspend fun list(token: String) = BookmarkList(items, items.size, 20)
            override suspend fun set(token: String, siteId: String, saved: Boolean): BookmarkMutation {
                writes++
                val row = parseBookmarkList(bookmarkJson()).items.single()
                items = if (saved) listOf(row) else emptyList()
                return BookmarkMutation(siteId, saved, row.createdAtMillis.takeIf { saved }, items.size, 20)
            }
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val account = if (loggedIn) AccountScope("owner", 1) else AccountScope(null, 2)
            val currentAccount by rememberUpdatedState(account)
            val controller = remember(account) {
                TerritoryBookmarkController(scope, TerritoryBookmarkRepository(client,
                    { Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE) },
                    { currentAccount }), account)
            }
            DisposableEffect(controller) { onDispose { controller.close() } }
            CompositionLocalProvider(LocalTerritoryBookmarks provides controller) { DaengsTheme {
                if (showList) TerritoryBookmarksRoute({}, {})
                else OwnedTerritoryCard(previewOwnedSite(), null, 1_800_000_000_000, null)
            } }
        }
        compose.onNodeWithContentDescription("북마크 저장").performClick()
        compose.onNodeWithContentDescription("북마크 해제").assertIsDisplayed()
        compose.runOnIdle { showList = true }
        compose.onNodeWithText("저장한 전봇대 1 / 20곳").assertIsDisplayed()
        compose.onNodeWithContentDescription("북마크 해제").performClick()
        compose.runOnIdle { showList = false }
        compose.onNodeWithContentDescription("북마크 저장").assertIsDisplayed()
        compose.onNodeWithContentDescription("북마크 저장").performClick()
        compose.onNodeWithContentDescription("북마크 해제").assertIsDisplayed()
        compose.runOnIdle { loggedIn = false }
        compose.onNodeWithContentDescription("북마크 해제").assertDoesNotExist()
        compose.onNodeWithContentDescription("북마크 상태 확인").performClick()
        compose.onNodeWithText("로그인하면 전봇대를 저장할 수 있어요.").assertIsDisplayed()
        assertEquals(3, writes)
    }
}
