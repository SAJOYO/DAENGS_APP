package com.daengs.app.ui.places

import com.daengs.app.activity.ActivitySessionChanged
import com.daengs.app.auth.*
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.*
import com.daengs.app.place.bookmarks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceBookmarkControllerTest {
    private val account = AccountScope("owner", 1)
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val key = PlaceKey("kcisa", "ref")
    private inner class Client : PlaceBookmarkClient {
        var page = SavedPlacePage(emptyList(), 200)
        var read: suspend () -> Unit = {}
        var write: suspend () -> Unit = {}
        var calls = 0
        var writes = 0
        val filters = mutableListOf<JsonObject>()
        override suspend fun list(token: String): SavedPlacePage { calls++; read(); return page }
        override suspend fun set(token: String, key: PlaceKey, saved: Boolean): SavedPlacePage {
            writes++; page = page.copy(items = if (saved) listOf(SavedPlace(key, "시설", Instant.EPOCH)) else emptyList())
            write(); return page
        }
        override suspend fun search(token: String, filters: JsonObject): SavedPlaceResults {
            this.filters += filters; read()
            return SavedPlaceResults(page, emptyList(), emptySet(), filters["lat"] != JsonNull)
        }
    }
    private fun repository(client: Client) = PlaceBookmarkRepository(client, { session }, { account })

    @Test fun `full saved query and edits preserve the normal search snapshot`() = runTest {
        val client = Client()
        val controller = PlaceBookmarkController(this, repository(client), account)
        val search = PlaceBrowseSnapshot(PlaceBrowseFilters(origin = GeoPoint(37.5, 127.0), name = "카페"), draft = "입력 중")
        controller.enter(search, emptyList()); advanceUntilIdle()
        assertEquals(3000, client.filters.last().getValue("radius_m").jsonPrimitive.int)
        controller.all(); advanceUntilIdle()
        assertEquals(JsonNull, client.filters.last()["radius_m"])
        assertTrue(client.filters.last().getValue("kinds").jsonArray.isEmpty())
        controller.filters { it.copy(name = "제주") }; advanceUntilIdle()
        controller.returnToSearch(); advanceUntilIdle()
        assertEquals(search, controller.state.value.session.current)
        controller.enter(search, emptyList()); advanceUntilIdle()
        assertEquals("제주", controller.state.value.session.current.filters.name)
        controller.close()
    }

    @Test fun `uncertain committed write reconciles and never enables stale heart on tab return`() = runTest {
        val client = Client()
        val controller = PlaceBookmarkController(this, repository(client), account)
        controller.ensureLoaded(); advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        client.write = { client.read = { gate.await() }; throw java.io.IOException("lost response") }
        controller.toggle(key); runCurrent()
        assertEquals(PlaceBookmarkPhase.LOADING, controller.state.value.phase)
        controller.returnToSearch(); runCurrent()
        assertEquals(PlaceBookmarkPhase.LOADING, controller.state.value.phase)
        controller.toggle(key); runCurrent()
        assertEquals(1, client.writes)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(key, controller.state.value.page!!.items.single().key)
        assertEquals(PlaceBookmarkPhase.READY, controller.state.value.phase)
        controller.close()
    }

    @Test fun `write serialization and remove undo use authoritative saved state`() = runTest {
        val client = Client()
        val controller = PlaceBookmarkController(this, repository(client), account)
        controller.ensureLoaded(); advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        client.write = { gate.await() }
        controller.toggle(key); controller.toggle(key); runCurrent()
        assertEquals(1, client.writes)
        gate.complete(Unit); advanceUntilIdle()
        controller.toggle(key); advanceUntilIdle()
        assertTrue(controller.state.value.page!!.items.isEmpty())
        assertEquals(key, controller.state.value.undo)
        controller.undo(); advanceUntilIdle()
        assertEquals(key, controller.state.value.page!!.items.single().key)
        controller.close()
    }

    @Test fun `late reads and writes cannot cross logout account switch or relogin`() = runTest {
        for (next in listOf(AccountScope(null, 2), AccountScope("other", 2), account.copy(generation = 2))) {
            for (write in listOf(false, true)) {
                var current = account
                val client = Client().apply { read = { current = next }; this.write = { current = next } }
                val repo = PlaceBookmarkRepository(client, { session }, { current })
                val failure = runCatching { if (write) repo.set(account, key, true) else repo.list(account) }.exceptionOrNull()
                assertTrue(failure is ActivitySessionChanged)
            }
        }
    }

    @Test fun `failed read keeps unknown state and retry recovers`() = runTest {
        val client = Client().apply { read = { throw java.io.IOException() } }
        val controller = PlaceBookmarkController(this, repository(client), account)
        controller.ensureLoaded(); advanceUntilIdle()
        assertEquals(PlaceBookmarkPhase.FAILED, controller.state.value.phase)
        assertNull(controller.state.value.page)
        client.read = {}; controller.refresh(); advanceUntilIdle()
        assertEquals(PlaceBookmarkPhase.READY, controller.state.value.phase)
        controller.close()
    }
}
