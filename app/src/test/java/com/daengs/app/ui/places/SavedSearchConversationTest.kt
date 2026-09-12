package com.daengs.app.ui.places

import com.daengs.app.auth.*
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.*
import com.daengs.app.place.bookmarks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedSearchConversationTest {
    private val account = AccountScope("owner", 1)
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val original = PlaceBrowseSnapshot(PlaceBrowseFilters(origin = GeoPoint(37.5, 127.0)), draft = "입력 중")
    private class Client : PlaceBookmarkClient {
        val page = SavedPlacePage(emptyList(), 200)
        var gate: suspend () -> Unit = {}
        var searchGate: suspend () -> Unit = {}
        var change: (JsonObject) -> JsonObject = { it }
        var action = "search"
        var searches = 0
        override suspend fun list(token: String) = page
        override suspend fun set(token: String, key: PlaceKey, saved: Boolean): SavedPlacePage = error("No write permission")
        override suspend fun search(token: String, filters: JsonObject): SavedPlaceResults {
            searches++; searchGate()
            return SavedPlaceResults(page, emptyList(), emptySet(), filters["lat"] != JsonNull)
        }
        override suspend fun interpret(token: String, query: String, filters: JsonObject): SavedSearchPlan {
            gate()
            return SavedSearchPlan(action, "조건을 알려 주세요", change(filters).takeIf { action == "search" })
        }
    }
    private fun controller(scope: CoroutineScope, client: Client) = PlaceBookmarkController(scope,
        PlaceBookmarkRepository(client, { session }, { account }), account)

    @Test fun appliedSavedConversationKeepsSearchAndSurvivesTabRoundTripUntilExplicitCopy() = runTest {
        val client = Client().apply { change = { JsonObject(it + ("radius_m" to JsonNull) + ("parking" to JsonPrimitive(true))) } }
        val controller = controller(this, client)
        controller.enter(original, emptyList()); advanceUntilIdle()
        controller.chat("멀어도 돼 주차 우선"); advanceUntilIdle()
        assertNull(controller.state.value.session.current.filters.radiusMeters)
        assertTrue(controller.state.value.session.current.filters.parkingFirst)
        assertEquals(original, controller.state.value.session.search)
        assertTrue(controller.state.value.aiAnswer!!.contains("0곳"))
        controller.returnToSearch(); advanceUntilIdle()
        val other = original.copy(filters = original.filters.copy(kinds = setOf(PlaceKind.HOSPITAL)))
        controller.enter(other, emptyList()); advanceUntilIdle()
        assertEquals(setOf(PlaceKind.CAFE), controller.state.value.session.current.filters.kinds)
        controller.copySearchConditions(); advanceUntilIdle()
        assertEquals(other.filters, controller.state.value.session.current.filters)
        controller.close()
    }

    @Test fun clarificationAndFailedSearchLeaveCurrentResultsAndFiltersUntouched() = runTest {
        val client = Client()
        val controller = controller(this, client)
        controller.enter(original, emptyList()); advanceUntilIdle()
        val before = controller.state.value.session
        client.action = "clarify"
        controller.chat("이거 빼줘"); advanceUntilIdle()
        assertEquals(1, client.searches)
        assertEquals(before, controller.state.value.session)
        client.action = "search"; client.searchGate = { throw java.io.IOException() }
        client.change = { JsonObject(it + ("radius_m" to JsonNull)) }
        controller.chat("멀어도 돼"); advanceUntilIdle()
        assertEquals(before, controller.state.value.session)
        assertEquals(PlaceBookmarkPhase.READY, controller.state.value.phase)
        assertFalse(controller.state.value.aiBusy)
        controller.close()
    }

    @Test fun changedFiltersOrTabInvalidatePendingInterpretationWithoutSearch() = runTest {
        for (leave in listOf(false, true)) {
            val client = Client()
            val controller = controller(this, client)
            controller.enter(original, emptyList()); advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            client.gate = { withContext(NonCancellable) { gate.await() } }
            controller.chat("주차 우선"); runCurrent()
            if (leave) controller.returnToSearch() else controller.filters { it.copy(name = "새 조건") }
            runCurrent()
            val before = controller.state.value.session
            val searches = client.searches
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(before, controller.state.value.session)
            assertEquals(searches, client.searches)
            assertFalse(controller.state.value.aiBusy)
            controller.close()
        }
    }

    @Test fun interpretationCannotChangeCoordinates() = runTest {
        val client = Client().apply { change = { JsonObject(it + ("lat" to JsonPrimitive(38.0))) } }
        val controller = controller(this, client)
        controller.enter(original, emptyList()); advanceUntilIdle()
        controller.chat("제주로"); advanceUntilIdle()
        assertEquals(original.filters, controller.state.value.session.current.filters)
        assertEquals(1, client.searches)
        controller.close()
    }

    @Test fun handoffWaitsForSearchAndDoesNotSwitchTabAfterAConflictingChange() = runTest {
        val client = Client()
        val controller = controller(this, client)
        val turn = controller.captureTurn(original)
        val gate = CompletableDeferred<Unit>()
        client.searchGate = { gate.await() }
        var current = true
        val job = async { turn.search(original.filters.savedQuery(emptyList())) { current } }
        runCurrent()
        assertEquals(PlaceBrowseTab.SEARCH, controller.state.value.session.tab)
        current = false; gate.complete(Unit); runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(PlaceBrowseTab.SEARCH, controller.state.value.session.tab)
        controller.close()
    }
}
