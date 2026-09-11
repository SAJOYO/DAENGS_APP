package com.daengs.app.ui.places

import com.daengs.app.auth.*
import com.daengs.app.location.*
import com.daengs.app.journey.*
import com.daengs.app.pet.Pet
import com.daengs.app.place.*
import com.daengs.app.place.bookmarks.*
import com.daengs.app.place.support.conversationFixture
import com.daengs.app.place.support.filteredConversationFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchPolicyTest {
    private val account = AccountScope("owner", 1)
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val snapshot = PlaceBrowseSnapshot(PlaceBrowseFilters(origin = GeoPoint(37.5, 127.0)), draft = "이전 입력")
    private val candidate = filteredConversationFixture().getValue("filters").jsonObject
    private class SavedClient(val candidate: JsonObject) : PlaceBookmarkClient {
        val page = SavedPlacePage(emptyList(), 200)
        var navigation = false
        override suspend fun list(token: String) = page
        override suspend fun set(token: String, key: PlaceKey, saved: Boolean): SavedPlacePage = error("No writes")
        override suspend fun search(token: String, filters: JsonObject) = SavedPlaceResults(page, emptyList(), emptySet(), true)
        override suspend fun interpret(token: String, query: String, filters: JsonObject) =
            if (navigation) SavedSearchPlan("return_search", "", null)
            else SavedSearchPlan("search_places", "", null, candidate)
    }
    private fun controller(scope: CoroutineScope, client: SavedClient) = PlaceBookmarkController(scope,
        PlaceBookmarkRepository(client, { session }, { account }), account)
    private fun repo(live: () -> Session? = { session }, client: ConversationClient) =
        FacilityConversationRepository(client, PlaceSearchRepository { error("No fallback") }, { session }, live)
    private fun restored(payload: JsonObject) = JsonObject(conversationFixture("manual", payload) + mapOf(
        "filters" to payload.getValue("restore_filters"), "session_id" to JsonPrimitive("new-search-session")))

    @Test fun sharedPlanKeepsHardOrAndDoesNotInventOriginOrDogs() {
        val accepted = snapshot.filters.withSearchPlan(candidate, emptyList())
        assertEquals(candidate["hard"], accepted.requiredConditions)
        assertEquals(setOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING), accepted.kinds)
        assertEquals(snapshot.filters.origin, accepted.origin)
        val changedOrigin = JsonObject(candidate + ("spatial" to JsonObject(candidate.getValue("spatial").jsonObject + ("lat" to JsonPrimitive(38.0)))))
        assertTrue(runCatching { snapshot.filters.withSearchPlan(changedOrigin, emptyList()) }.isFailure)
        val unbounded = JsonObject(candidate + ("spatial" to JsonObject(candidate.getValue("spatial").jsonObject + ("radius_m" to JsonNull))))
        assertTrue(runCatching { snapshot.filters.withSearchPlan(unbounded, emptyList()) }.isFailure)
    }

    @Test fun savedToOrdinaryWaitsForActualLookupAndPreservesSavedConditions() = runTest {
        val saved = controller(this, SavedClient(candidate))
        saved.enter(snapshot, emptyList()); advanceUntilIdle()
        val before = saved.state.value.session.bookmarks
        val gate = CompletableDeferred<Unit>()
        val repository = repo { _, payload -> gate.await(); restored(payload) }
        saved.chat("찜 제한 풀고 같은 조건") { transfer ->
            launch {
                repository.applySearchPlan(transfer)
                transfer.completion.complete(Unit)
            }
        }
        runCurrent()
        assertEquals(PlaceBrowseTab.BOOKMARKS, saved.state.value.session.tab)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(PlaceBrowseTab.SEARCH, saved.state.value.session.tab)
        assertEquals(before, saved.state.value.session.bookmarks)
        assertEquals(candidate, repository.state.value.result!!.filters)
        assertEquals(candidate["hard"], saved.state.value.session.search.filters.requiredConditions)
        assertEquals(1, saved.state.value.searchTransfer)
        saved.close()
    }

    @Test fun screenRestoreUsesStoredScreenWithoutAnotherSearchPlan() = runTest {
        val client = SavedClient(candidate).apply { navigation = true }
        val saved = controller(this, client)
        saved.enter(snapshot, emptyList()); advanceUntilIdle()
        saved.chat("이전 검색으로 돌아가") { error("No search handoff") }
        advanceUntilIdle()
        assertEquals(snapshot, saved.state.value.session.search)
        assertEquals(PlaceBrowseTab.SEARCH, saved.state.value.session.tab)
        assertEquals(0, saved.state.value.searchTransfer)
        saved.close()
    }

    @Test fun failedHandoffKeepsBothWorkspacesAndOldOrdinaryResult() = runTest {
        val saved = controller(this, SavedClient(candidate))
        saved.enter(snapshot, emptyList()); advanceUntilIdle()
        val before = saved.state.value.session
        val repository = repo { _, payload ->
            if (payload["mode"]?.jsonPrimitive?.content == "restore") throw FacilityException(503)
            conversationFixture("manual", payload)
        }
        repository.search(PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = listOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING)))
        val old = repository.state.value.result
        saved.chat("찜 제한 풀어") { transfer ->
            launch { runCatching { repository.applySearchPlan(transfer) }
                .onSuccess { transfer.completion.complete(Unit) }
                .onFailure { transfer.completion.completeExceptionally(it) } }
        }
        advanceUntilIdle()
        assertEquals(before, saved.state.value.session)
        assertEquals(old, repository.state.value.result)
        assertNotNull(saved.state.value.aiAnswer)
        assertFalse(saved.state.value.aiBusy)
        saved.close()
    }

    @Test fun staleSourceCannotPublishEvenIfTransportIgnoresCancellation() = runTest {
        for (change in listOf("tab", "filters", "cancel")) {
            val saved = controller(this, SavedClient(candidate))
            saved.enter(snapshot, emptyList()); advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            val repository = repo { _, payload -> withContext(NonCancellable) { gate.await() }; restored(payload) }
            saved.chat("찜 제한 풀어") { transfer ->
                launch { runCatching { repository.applySearchPlan(transfer) }
                    .onSuccess { transfer.completion.complete(Unit) }
                    .onFailure { transfer.completion.completeExceptionally(it) } }
            }
            runCurrent()
            when (change) {
                "tab" -> saved.returnToSearch()
                "filters" -> saved.filters { it.copy(name = "다른 조건") }
                else -> saved.cancelConversation()
            }
            runCurrent()
            val before = saved.state.value.session
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(before, saved.state.value.session)
            assertNull(repository.state.value.result)
            saved.close()
        }
    }

    @Test fun changedAccountOrOrdinaryIntentRejectsLateTransfer() = runTest {
        for (accountChanged in listOf(false, true)) {
            var live = session
            val gate = CompletableDeferred<Unit>()
            val repository = repo({ live }, ConversationClient { _, payload -> gate.await(); restored(payload) })
            val work = async { repository.applySearchPlan(SearchPlanTransfer(candidate, "owner") { true }) }
            runCurrent()
            if (accountChanged) live = session.copy(appUserId = "other") else repository.cancelPending()
            gate.complete(Unit); advanceUntilIdle()
            assertTrue(work.isCancelled)
            assertNull(repository.state.value.result)
        }
    }

    @Test fun responseMustEchoEveryCompiledConditionBeforePublication() = runTest {
        val repository = repo { _, payload -> JsonObject(restored(payload) +
            ("filters" to conversationFixture("manual").getValue("filters"))) }
        assertTrue(runCatching { repository.applySearchPlan(SearchPlanTransfer(candidate, "owner") { true }) }.isFailure)
        assertNull(repository.state.value.result)
    }

    @Test fun viewModelCommitsTransferredDogSelectionAndResultsTogether() = runTest {
        val pet = Pet("dog", "보리", "mix", null, null, 9f, null, null,
            isPrimary = true, updatedAt = "v1")
        val profiles = PlaceProfiles().receive("owner", listOf(pet), false, null).toggle("dog")
        val dogs = profiles.snapshots()
        val filters = JsonObject(candidate + ("dogs" to JsonArray(dogs.map { it.toJson() })))
        val repository = repo { _, payload ->
            val body = restored(payload)
            val search = body.getValue("search").jsonObject
            val groups = search.getValue("groups").jsonArray.map { group ->
                val value = group.jsonObject
                JsonObject(value + ("results" to JsonArray(value.getValue("results").jsonArray.map { hit ->
                    JsonObject(hit.jsonObject + ("evaluations" to buildJsonObject {
                        put("dogs", buildJsonArray { add(buildJsonObject {
                            put("ref", "dog")
                            put("dog_access", buildJsonObject { put("state", "unknown"); put("reason", "synthetic fixture") })
                            put("restrictions", buildJsonObject {})
                        }) })
                    }))
                })))
            }
            JsonObject(body + ("search" to JsonObject(search + mapOf(
                "dogs" to filters.getValue("dogs"), "groups" to JsonArray(groups)))))
        }
        val model = PlacesViewModel(repository, JourneyRepository { JourneyResponse("dog", emptyList()) },
            object : LocationSource {
                override suspend fun currentLocation() = LocationSample(GeoPoint(37.5, 127.0), 0)
                override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
            }, backgroundScope, conversationRepository = repository)
        model.updateProfiles("owner", listOf(pet), false, null)
        runCurrent()
        val transfer = SearchPlanTransfer(filters, "owner") { true }
        model.onAction(PlacesAction.ApplySearchPlan(transfer))
        runCurrent()
        assertTrue(transfer.completion.isCompleted)
        transfer.completion.await()
        assertEquals(setOf("dog"), model.state.value.profiles.selectedIds)
        assertEquals(dogs, model.state.value.discovery.response!!.dogs)
        assertEquals(filters, model.state.value.conversation.result!!.filters)
    }

    @Test fun capturedOwnerMustMatchFreshAuthenticationBeforeAnyLookup() = runTest {
        val repository = repo { _, _ -> error("A different owner's plan cannot be sent") }
        assertTrue(runCatching { repository.applySearchPlan(SearchPlanTransfer(candidate, "previous-owner") { true }) }
            .exceptionOrNull() is CancellationException)
        assertNull(repository.state.value.result)
    }
}
