package com.daengs.app.ui.places

import com.daengs.app.auth.Session
import com.daengs.app.journey.*
import com.daengs.app.location.*
import com.daengs.app.place.*
import com.daengs.app.ui.places.lab.LabPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationConnectedTest {
    @Test fun existingScreenKeepsShoppingResultsAcrossAiToggleAndAppliesSelectedPlace() = runTest {
        val calls = mutableListOf<String>()
        val login = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val repository = FacilityConversationRepository(ConversationClient { _, payload ->
            val mode = payload.getValue("mode").jsonPrimitive.content
            calls += mode
            conversationFixture(if (mode == "manual") "manual" else "picked", payload)
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
        assertNotNull(vm.state.value.conversation.result!!.answer)
        assertNull(vm.state.value.facility.error)
    }
}
