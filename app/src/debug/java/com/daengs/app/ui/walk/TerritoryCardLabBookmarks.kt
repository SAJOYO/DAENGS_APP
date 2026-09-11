package com.daengs.app.ui.walk

import androidx.compose.runtime.*
import com.daengs.app.auth.AccountScope
import com.daengs.app.auth.Session
import com.daengs.app.territory.bookmarks.*
import com.daengs.app.ui.game.bookmarks.*

/** In-memory bookmarks for the clearly labelled card lab, never a network client. */
@Composable
internal fun TerritoryCardLabBookmarks(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember {
        val account = AccountScope("card-fixture", 1)
        val session = Session("card-fixture", "fixture-token", "fixture-refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val client = object : TerritoryBookmarkClient {
            var items = emptyList<TerritoryBookmark>()
            override suspend fun list(token: String) = BookmarkList(items, items.size, 20)
            override suspend fun set(token: String, siteId: String, saved: Boolean): BookmarkMutation {
                val row = TerritoryBookmark(siteId, 1_800_000_000_000, null, BookmarkLocationStatus.NOT_FOUND)
                items = if (saved) listOf(row) else emptyList()
                return BookmarkMutation(siteId, saved, row.createdAtMillis.takeIf { saved }, items.size, 20)
            }
        }
        TerritoryBookmarkController(scope, TerritoryBookmarkRepository(client, { session }, { account }), account)
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    CompositionLocalProvider(LocalTerritoryBookmarks provides controller, content = content)
}
