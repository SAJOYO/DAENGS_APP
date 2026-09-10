package com.daengs.app.ui.game.owned

import com.daengs.app.auth.Session
import com.daengs.app.territory.owned.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnedTerritoryBrowserTest {
    private fun repository(client: OwnedTerritoryClient): OwnedTerritoryRepository {
        val auth = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        return OwnedTerritoryRepository(client, { auth }, { auth })
    }
    private fun page(items: List<com.daengs.app.territory.owned.OwnedTerritory> = listOf(previewOwnedSite()), cursor: String? = null, season: String? = "first") =
        OwnedTerritoryPage(season, 1_800_000_000_000, null, 3, items, cursor)

    @Test fun `pages merge by site id while duplicate load clicks make one request`() = runTest {
        val calls = mutableListOf<String?>()
        val later = CompletableDeferred<OwnedTerritoryPage>()
        val first = previewOwnedSite()
        val browser = OwnedTerritoryBrowser(this, repository(OwnedTerritoryClient { _, _, cursor ->
            calls += cursor
            if (cursor == null) page(cursor = "next") else later.await()
        }), "owner", null)
        browser.refresh(); runCurrent()
        browser.loadMore(); browser.loadMore(); runCurrent()
        assertEquals(listOf(null, "next"), calls)
        later.complete(page(listOf(first.copy(version = 3), previewOwnedSite(first.siteId + "0"))))
        runCurrent()
        assertEquals(2, browser.state.value.items.size)
        assertEquals(3L, browser.state.value.items.first().version)
        assertNull(browser.state.value.nextCursor)
    }

    @Test fun `refresh and leaving a screen discard noncancellable late pages`() = runTest {
        val late = CompletableDeferred<OwnedTerritoryPage>()
        var calls = 0
        val browser = OwnedTerritoryBrowser(this, repository(OwnedTerritoryClient { _, _, _ ->
            if (++calls == 1) withContext(NonCancellable) { late.await() } else page(emptyList()).copy(totalCount = 0)
        }), "owner", null)
        browser.refresh(); runCurrent()
        browser.refresh(); runCurrent()
        late.complete(page()); runCurrent()
        assertTrue(browser.state.value.items.isEmpty())
        assertEquals(0, browser.state.value.total)
        browser.stop()
    }

    @Test fun `season conflict restarts first page without mixing ownership`() = runTest {
        val calls = mutableListOf<String?>()
        val browser = OwnedTerritoryBrowser(this, repository(OwnedTerritoryClient { _, _, cursor ->
            calls += cursor
            if (cursor != null) throw OwnedTerritoryHttpException(409, "season_changed")
            if (calls.size == 1) page(cursor = "next") else page(emptyList(), season = "second").copy(totalCount = 0)
        }), "owner", null)
        browser.refresh(); runCurrent(); browser.loadMore(); runCurrent()
        assertEquals(listOf(null, "next", null), calls)
        assertEquals("second", browser.state.value.seasonId)
        assertTrue(browser.state.value.items.isEmpty())
        assertTrue(browser.state.value.message!!.contains("시즌"))
    }

    @Test fun `append failure keeps fetched items but authentication failure clears them`() = runTest {
        var error = OwnedTerritoryHttpException(503, "territory_sites_unavailable")
        val browser = OwnedTerritoryBrowser(this, repository(OwnedTerritoryClient { _, _, cursor ->
            if (cursor == null) page(cursor = "next") else throw error
        }), "owner", null)
        browser.refresh(); runCurrent(); browser.loadMore(); runCurrent()
        assertEquals(OwnedBrowserStatus.READY, browser.state.value.status)
        assertEquals(1, browser.state.value.items.size)
        assertNotNull(browser.state.value.message)
        error = OwnedTerritoryHttpException(401, null)
        browser.loadMore(); runCurrent()
        assertEquals(OwnedBrowserStatus.SIGN_IN, browser.state.value.status)
        assertTrue(browser.state.value.items.isEmpty())
    }

    @Test fun `repeated cursor does not loop or silently finish the list`() = runTest {
        val browser = OwnedTerritoryBrowser(this, repository(OwnedTerritoryClient { _, _, _ -> page(cursor = "same") }), "owner", null)
        browser.refresh(); runCurrent(); browser.loadMore(); runCurrent()
        assertEquals(1, browser.state.value.items.size)
        assertNotNull(browser.state.value.message)
        assertFalse(browser.state.value.loadingMore)
    }

    @Test fun `expired rows disappear using server time and missing locations stay in list only`() {
        val site = previewOwnedSite().copy(expiresAtMillis = 2_000)
        val value = OwnedBrowserState(OwnedBrowserStatus.READY, listOf(site, site.copy(siteId = site.siteId + "0", expiresAtMillis = null, point = null)), 2,
            serverNowMillis = 1_000, receivedAtNanos = 0)
        assertEquals(2, value.visibleItems(999_000_000).size)
        assertEquals(1, value.visibleItems(1_000_000_000).size)
        val scene = ownedTerritoryScene(value.visibleItems(0), site.siteId)
        assertEquals(site.point, scene.territorySites.single().point)
        assertTrue(scene.territorySites.single().selected)
        assertNull(scene.currentPosition)
        assertTrue(scene.places.isEmpty() && scene.moments.isEmpty() && scene.allowRegionalOverview)
        assertEquals("유지 기한 정보 없음", ownedRemaining(null, 1000))
    }
}
