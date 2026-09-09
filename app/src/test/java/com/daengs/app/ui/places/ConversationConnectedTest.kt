package com.daengs.app.ui.places

import com.daengs.app.auth.Session
import com.daengs.app.journey.*
import com.daengs.app.location.*
import com.daengs.app.place.*
import com.daengs.app.place.support.filteredConversationFixture
import com.daengs.app.ui.places.lab.LabPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationConnectedTest {
    @Test fun directFilterRemovalUpdatesTheExistingSessionAndMapWithoutAi() = runTest {
        val calls = mutableListOf<JsonObject>()
        val login = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val repository = FacilityConversationRepository(ConversationClient { _, payload ->
            calls += payload
            if (payload.getValue("mode").jsonPrimitive.content == "manual") filteredConversationFixture(payload)
            else conversationFixture("manual", payload)
        }, PlaceSearchRepository { error("Unexpected fallback") }, { login }, { login })
        val vm = PlacesViewModel(repository, JourneyRepository { JourneyResponse("dog", emptyList()) },
            object : LocationSource {
                override suspend fun currentLocation() = LocationSample(GeoPoint(37.5, 127.0), 0)
                override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
            }, externalScope = backgroundScope, conversationRepository = repository)
        vm.updateProfiles("owner", emptyList(), false, null)
        vm.updatePermission(true, false)
        runCurrent()
        vm.onAction(PlacesAction.SearchAt(GeoPoint(37.5, 127.0), PlaceKind.SHOPPING, false))
        runCurrent()
        val before = vm.state.value.conversation.result!!
        assertEquals(2, before.appliedPlaceFilters().count)
        vm.onAction(PlacesAction.ApplyFilters(ConversationFilterEdit(before.sessionId, before.revision,
            removeAll = listOf("parking"), removeAny = listOf("shop", "pet"))))
        runCurrent()
        assertEquals(listOf("manual", "filters"), calls.map { it.getValue("mode").jsonPrimitive.content })
        assertFalse(vm.state.value.facility.enabled)
        assertEquals(0, vm.state.value.conversation.result!!.appliedPlaceFilters().count)
        assertEquals(vm.state.value.conversation.result!!.search, vm.state.value.discovery.response)
        assertEquals(LabPhase.RESULTS, vm.state.value.toConnectedSearchState("", false, null, null).phase)
    }
    @Test fun existingScreenKeepsShoppingResultsAcrossAiToggleAndAppliesSelectedPlace() = runTest {
        val calls = mutableListOf<String>()
        val answerReady = kotlinx.coroutines.CompletableDeferred<Unit>()
        val login = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val repository = FacilityConversationRepository(object : ConversationClient {
            override suspend fun answer(token: String, payload: JsonObject): JsonObject {
                answerReady.await()
                return conversationFixture("picked", payload)
            }
            override suspend fun exchange(token: String, payload: JsonObject): JsonObject {
            val mode = payload.getValue("mode").jsonPrimitive.content
            calls += mode
            return conversationFixture(if (mode == "manual") "manual" else "picked", payload)
            }
        }, PlaceSearchRepository { error("No legacy search in this slice") }, { login }, { login })
        val vm = PlacesViewModel(repository, JourneyRepository { JourneyResponse("dog", emptyList()) },
            object : LocationSource {
                override suspend fun currentLocation() = LocationSample(GeoPoint(37.5, 127.0), 0)
                override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
            }, externalScope = backgroundScope, conversationRepository = repository)
        vm.updateProfiles("owner", emptyList(), false, null)
        vm.updatePermission(true, false)
        runCurrent()
        vm.onAction(PlacesAction.SearchAt(GeoPoint(37.5, 127.0), PlaceCategorySelection.fromKinds(listOf(PlaceKind.SHOPPING, PlaceKind.PET_SHOP)), false))
        runCurrent()
        val before = vm.state.value.toConnectedSearchState("", false, null, null)
        assertEquals(2, before.hits.size)
        vm.onAction(PlacesAction.SetAiMode(true))
        runCurrent()
        assertEquals(before.hits, vm.state.value.toConnectedSearchState("", true, null, null).hits)
        vm.onAction(PlacesAction.Discover(""))
        runCurrent()
        assertNotNull(vm.state.value.facility.error)
        vm.onAction(PlacesAction.Discover("아무 데나 하나 골라줘"))
        runCurrent()
        val after = vm.state.value.toConnectedSearchState("", true, null, null)
        assertEquals(LabPhase.RESULTS, after.phase)
        assertEquals(before.hits, after.hits)
        assertEquals(PlaceKey("test:facility", "first"), after.selected)
        assertEquals(listOf("manual", "chat"), calls)
        assertEquals(2, vm.state.value.conversation.result!!.revision)
        assertNull(vm.state.value.conversation.result!!.answer)
        assertTrue(vm.state.value.conversation.answerBusy)
        answerReady.complete(Unit)
        runCurrent()
        assertNotNull(vm.state.value.conversation.result!!.answer)
        assertNull(vm.state.value.facility.error)
    }

    @Test fun manualFailureRestoresCanonicalCriteriaAndResultsThroughTheExistingScreen() = runTest {
        val calls = mutableListOf<JsonObject>()
        val login = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val repository = FacilityConversationRepository(ConversationClient { _, payload ->
            calls += payload
            val response = conversationFixture("manual", payload)
            if (calls.size == 1) response else JsonObject(response + ("receipt" to
                JsonObject(response.getValue("receipt").jsonObject + mapOf(
                    "execution" to JsonPrimitive("failed"), "code" to JsonPrimitive("search_failed")))))
        }, PlaceSearchRepository { error("Unexpected fallback") }, { login }, { login })
        val vm = PlacesViewModel(repository, JourneyRepository { JourneyResponse("dog", emptyList()) },
            object : LocationSource {
                override suspend fun currentLocation() = LocationSample(GeoPoint(37.5, 127.0), 0)
                override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
            }, externalScope = backgroundScope, conversationRepository = repository)
        vm.updateProfiles("owner", emptyList(), false, null)
        vm.updatePermission(true, false)
        runCurrent()
        vm.onAction(PlacesAction.SearchAt(GeoPoint(37.5, 127.0), PlaceKind.SHOPPING, false))
        runCurrent()
        val before = vm.state.value.discovery
        vm.onAction(PlacesAction.SetRadius(1000))
        runCurrent()
        assertEquals(3000, vm.state.value.discovery.radiusMeters)
        assertEquals(before.response, vm.state.value.discovery.response)
        assertNotNull(vm.state.value.conversation.notice)
        assertFalse(vm.state.value.facility.enabled)
        vm.onAction(PlacesAction.Search(PlaceKind.CAFE, true, "새 이름"))
        runCurrent()
        assertEquals(before.requestedKinds, vm.state.value.discovery.requestedKinds)
        assertEquals(before.nameQuery, vm.state.value.discovery.nameQuery)
        assertFalse(vm.state.value.discovery.preferParking)
        // A subsequent action is based on the restored 3km scope, not the rejected 1km intent.
        assertEquals(3000, calls.last().getValue("manual").jsonObject.getValue("radius_m").jsonPrimitive.int)
        val screen = vm.state.value.toConnectedSearchState("", false, null, null)
        assertEquals(LabPhase.RESULTS, screen.phase)
        assertEquals(2, screen.hits.size)
        vm.onAction(PlacesAction.Locate(PlaceKind.CAFE, false))
        runCurrent()
        assertEquals(before.originMode, vm.state.value.discovery.originMode)
        assertEquals(before.origin, vm.state.value.discovery.origin)
    }
}
