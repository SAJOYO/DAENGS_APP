package com.daengs.app.ui.places

import com.daengs.app.auth.*
import com.daengs.app.place.*
import com.daengs.app.place.bookmarks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkCommandTest {
    private val account = AccountScope("owner", 1)
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val a = PlaceKey("kcisa", "a")
    private val b = PlaceKey("kcisa", "b")
    private class Client : PlaceBookmarkClient {
        var page = SavedPlacePage(emptyList(), 200)
        var writes = 0
        var onWrite: suspend () -> Unit = {}
        var onRead: suspend () -> Unit = {}
        override suspend fun list(token: String): SavedPlacePage { onRead(); return page }
        override suspend fun set(token: String, key: PlaceKey, saved: Boolean): SavedPlacePage {
            writes++
            page = page.copy(items = page.items.filterNot { it.key == key } +
                if (saved) listOf(SavedPlace(key, "시설", Instant.EPOCH)) else emptyList())
            onWrite()
            return page
        }
        override suspend fun search(token: String, filters: JsonObject) =
            SavedPlaceResults(list(token), emptyList(), emptySet(), false)
    }
    private fun controller(scope: CoroutineScope, client: Client) = PlaceBookmarkController(scope,
        PlaceBookmarkRepository(client, { session }, { account }), account)

    @Test fun samePlaceDirectIntentSupersedesOldAiButOtherPlaceAndRefreshDoNot() = runTest {
        val client = Client()
        val controller = controller(this, client)
        val old = controller.captureTurn()
        controller.set(a, false).await()
        assertEquals(BookmarkCompletion.SUPERSEDED, old.execute("old", a, true).completion)
        assertTrue(client.page.items.isEmpty())
        val next = controller.captureTurn()
        controller.set(b, true).await()
        controller.refresh()
        assertEquals(BookmarkCompletion.CONFIRMED, next.execute("next", a, true).completion)
        assertEquals(setOf(a, b), client.page.items.map { it.key }.toSet())
        controller.close()
    }

    @Test fun laterAiIntentWinsAndRecoveredRequestDoesNotRepeatWrite() = runTest {
        val client = Client()
        val controller = controller(this, client)
        val old = controller.captureTurn()
        val recent = controller.captureTurn()
        recent.execute("recent", a, false)
        assertEquals(BookmarkCompletion.SUPERSEDED, old.execute("old", a, true).completion)
        recent.execute("recent", a, false)
        assertEquals(1, client.writes)
        controller.close()
    }

    @Test fun resultWaitsForCommitAndLostReplyCanBeConfirmedByRead() = runTest {
        val client = Client()
        val controller = controller(this, client)
        val gate = CompletableDeferred<Unit>()
        client.onWrite = { gate.await(); throw java.io.IOException() }
        val result = async { controller.captureTurn().execute("one", a, true) }
        runCurrent()
        assertFalse(result.isCompleted)
        gate.complete(Unit)
        assertEquals(BookmarkCompletion.CONFIRMED, result.await().completion)
        assertTrue(client.page.items.any { it.key == a })
        controller.close()
    }

    @Test fun unknownWriteResultDoesNotBecomeSuccessAndCancelledTurnDoesNotWrite() = runTest {
        val client = Client()
        val controller = controller(this, client)
        client.onWrite = { throw java.io.IOException() }
        client.onRead = { throw java.io.IOException() }
        assertEquals(BookmarkCompletion.UNKNOWN, controller.captureTurn().execute("one", a, true).completion)
        val cancelled = controller.captureTurn()
        cancelled.cancel()
        assertEquals(BookmarkCompletion.SUPERSEDED, cancelled.execute("two", b, true).completion)
        assertEquals(1, client.writes)
        controller.close()
    }

    @Test fun successfulResponseWithOppositeStateDoesNotAnnounceSuccess() = runTest {
        val client = Client()
        val controller = controller(this, client)
        client.onWrite = { client.page = SavedPlacePage(emptyList(), 200) }
        assertEquals(BookmarkCompletion.UNKNOWN, controller.captureTurn().execute("one", a, true).completion)
        assertNull(controller.state.value.undo)
        controller.close()
    }

    @Test fun timedOutWriteWithOppositeReadIsStillUnknownAndClosedControllerCannotWrite() = runTest {
        val client = Client()
        val controller = controller(this, client)
        client.onWrite = { client.page = SavedPlacePage(emptyList(), 200); throw java.io.IOException() }
        assertEquals(BookmarkCompletion.UNKNOWN, controller.captureTurn().execute("one", a, true).completion)
        val old = controller.captureTurn()
        controller.close()
        assertEquals(BookmarkCompletion.SUPERSEDED, old.execute("two", b, true).completion)
        assertEquals(1, client.writes)
    }
}
