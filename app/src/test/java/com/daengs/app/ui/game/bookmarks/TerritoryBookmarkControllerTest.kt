package com.daengs.app.ui.game.bookmarks

import com.daengs.app.auth.*
import com.daengs.app.territory.bookmarks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TerritoryBookmarkControllerTest {
    private val account = AccountScope("owner", 1)
    private class Client : TerritoryBookmarkClient {
        var items = emptyList<TerritoryBookmark>()
        var sets = 0
        var getError: Exception? = null
        var setError: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun list(token: String): BookmarkList {
            getError?.let { throw it }
            return BookmarkList(items, items.size, 20)
        }
        override suspend fun set(token: String, siteId: String, saved: Boolean): BookmarkMutation {
            sets++; gate?.await()
            val row = parseBookmarkList(bookmarkJson()).items.single()
            items = if (saved) listOf(row) else emptyList()
            setError?.let { throw it } // The server may commit before a timeout is observed.
            return BookmarkMutation(siteId, saved, row.createdAtMillis.takeIf { saved }, items.size, 20)
        }
    }
    private fun repository(client: Client) = TerritoryBookmarkRepository(client,
        { Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE) }, { account })

    @Test fun `rapid taps and refresh do not race an inflight write`() = runTest {
        val client = Client()
        val controller = TerritoryBookmarkController(this, repository(client), account)
        controller.ensureLoaded(); runCurrent()
        client.gate = CompletableDeferred()
        controller.toggle(BOOKMARK_SITE); controller.toggle(BOOKMARK_SITE); controller.refresh(); runCurrent()
        assertEquals(1, client.sets); assertFalse(controller.state.value.saved(BOOKMARK_SITE))
        client.gate!!.complete(Unit); runCurrent()
        assertTrue(controller.state.value.saved(BOOKMARK_SITE))
        controller.toggle(BOOKMARK_SITE); runCurrent()
        assertFalse(controller.state.value.saved(BOOKMARK_SITE)); assertEquals(2, client.sets)
    }
    @Test fun `timeout after commit reconciles before permitting another toggle`() = runTest {
        val client = Client()
        val controller = TerritoryBookmarkController(this, repository(client), account)
        controller.refresh(); runCurrent()
        client.setError = IOException()
        controller.toggle(BOOKMARK_SITE); runCurrent()
        assertTrue(controller.state.value.saved(BOOKMARK_SITE))
        assertNotNull(controller.state.value.message)
        client.getError = IOException()
        controller.toggle(BOOKMARK_SITE); runCurrent()
        assertEquals(BookmarkStatus.ERROR, controller.state.value.status)
        assertTrue(controller.state.value.items.isEmpty())
        controller.toggle(BOOKMARK_SITE); runCurrent(); assertEquals(2, client.sets)
    }
    @Test fun `unauthorized and unavailable endpoint do not appear as an empty collection`() = runTest {
        val client = Client()
        val controller = TerritoryBookmarkController(this, repository(client), account)
        client.getError = BookmarkHttpException(404, null)
        controller.refresh(); runCurrent()
        assertEquals(BookmarkStatus.ERROR, controller.state.value.status)
        assertTrue(controller.state.value.message!!.contains("준비"))
        client.getError = BookmarkHttpException(401, null)
        controller.refresh(); runCurrent()
        assertEquals(BookmarkStatus.SIGN_IN, controller.state.value.status)
        assertTrue(BookmarkHttpException(409, "bookmark_limit_reached", 20).bookmarkMessage().contains("20개"))
    }
    @Test fun `closing controller discards non cancellable late response`() = runTest {
        val late = CompletableDeferred<BookmarkList>()
        val client = object : TerritoryBookmarkClient {
            override suspend fun list(token: String) = withContext(NonCancellable) { late.await() }
            override suspend fun set(token: String, siteId: String, saved: Boolean): BookmarkMutation = error("unused")
        }
        val repo = TerritoryBookmarkRepository(client, { Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE) }, { account })
        val controller = TerritoryBookmarkController(this, repo, account)
        controller.refresh(); runCurrent(); controller.close()
        late.complete(parseBookmarkList(bookmarkJson())); runCurrent()
        assertFalse(controller.state.value.saved(BOOKMARK_SITE))
    }
}
