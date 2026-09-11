package com.daengs.app.territory.bookmarks

import com.daengs.app.activity.*
import com.daengs.app.auth.*
import com.daengs.app.territory.support.BOOKMARK_SITE
import com.daengs.app.territory.support.bookmarkJson
import com.daengs.app.territory.support.mutationJson
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TerritoryBookmarkRepositoryTest {
    private val account = AccountScope("owner", 1)
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    @Test fun `logout other member and same member relogin reject late reads and writes`() = runBlocking {
        listOf(AccountScope(null, 2), AccountScope("other", 2), AccountScope("owner", 2)).forEach { next ->
            listOf(false, true).forEach { write ->
                var current = account
                val repo = TerritoryBookmarkRepository(object : TerritoryBookmarkClient {
                    override suspend fun list(token: String): BookmarkList { current = next; return parseBookmarkList(bookmarkJson()) }
                    override suspend fun set(token: String, siteId: String, saved: Boolean): BookmarkMutation {
                        current = next; return parseBookmarkMutation(mutationJson(saved), siteId, saved)
                    }
                }, { session }, { current })
                val result = if (write) repo.set(account, BOOKMARK_SITE, true) else repo.list(account)
                assertTrue(result.exceptionOrNull() is ActivitySessionChanged)
            }
        }
    }
    @Test fun `missing session and stale account never reach server`() = runBlocking {
        val client = object : TerritoryBookmarkClient {
            override suspend fun list(token: String): BookmarkList = error("must not call")
            override suspend fun set(token: String, siteId: String, saved: Boolean): BookmarkMutation = error("must not call")
        }
        assertTrue(TerritoryBookmarkRepository(client, { null }, { account }).list(account).exceptionOrNull() is ActivityAuthenticationRequired)
        assertTrue(TerritoryBookmarkRepository(client, { session }, { account.copy(generation = 2) })
            .set(account, BOOKMARK_SITE, true).exceptionOrNull() is ActivitySessionChanged)
    }
    @Test fun `normal refresh is accepted but cancellation propagates`() = runBlocking {
        val client = object : TerritoryBookmarkClient {
            override suspend fun list(token: String): BookmarkList { assertEquals("new-access", token); return parseBookmarkList(bookmarkJson()) }
            override suspend fun set(token: String, siteId: String, saved: Boolean): BookmarkMutation = throw CancellationException()
        }
        val repo = TerritoryBookmarkRepository(client, { session.copy(accessToken = "new-access", refreshToken = "new-refresh") }, { account })
        assertTrue(repo.list(account).isSuccess)
        assertTrue(runCatching { repo.set(account, BOOKMARK_SITE, true) }.exceptionOrNull() is CancellationException)
    }
}
