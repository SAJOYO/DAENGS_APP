package com.daengs.app.ui.places

import com.daengs.app.journey.*
import com.daengs.app.location.*
import com.daengs.app.place.*
import com.daengs.app.ui.places.lab.LabPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FacilityViewModelTest {
    @Test fun returningFromAssistantPreservesSharedCardsAndTheCurrentSelection() = runTest {
        val login = com.daengs.app.auth.Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        var searches = 0
        val repository = FacilityConversationRepository(object : ConversationClient {
            override suspend fun exchange(token: String, payload: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject {
                searches++
                return com.daengs.app.place.support.conversationFixture("manual", payload)
            }
        }, PlaceSearchRepository { error("Unexpected legacy search") }, { login }, { login })
        repository.search(PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = listOf(PlaceKind.SHOPPING)))
        val result = repository.state.value.result!!
        repository.select(result.order.last())
        val source = object : LocationSource {
            override suspend fun currentLocation() = LocationSample(result.origin, 0)
            override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
        }
        val vm = PlacesViewModel(repository, JourneyRepository { JourneyResponse("dog", emptyList()) },
            source, backgroundScope, conversationRepository = repository)
        vm.updateProfiles("owner", emptyList(), false, null)
        vm.activate(true); runCurrent()
        vm.deactivate(); runCurrent()
        assertEquals(result, repository.state.value.result)
        vm.activate(true); runCurrent()
        assertEquals(result.search, vm.state.value.visibleDiscovery().response)
        assertEquals(result.order.last(), vm.state.value.visibleDiscovery().selectedPlaceKey)
        assertEquals(1, searches)
    }

    @Test fun confirmedServerResultReachesMapWithoutLosingAiConditionsToANormalSearch() = runTest {
        var ordinaryCalls = 0
        val queries = mutableListOf<FacilityQuery>()
        val source = object : LocationSource {
            override suspend fun currentLocation() = LocationSample(facilityQuery().origin, 0)
            override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
        }
        val vm = PlacesViewModel(PlaceSearchRepository { request ->
            ordinaryCalls++
            val template = facilityResponse().lenses.first().search.groups.first()
            PlaceSearchResponse(null, request.kinds.map { template.copy(kind = it, results = emptyList()) }, request.dogs)
        }, JourneyRepository { JourneyResponse("dog", emptyList()) }, source, backgroundScope,
            object : FacilityRepository {
                override suspend fun discover(owner: String, query: FacilityQuery): FacilityResponse {
                    queries += query
                    return facilityResponse().copy(request = query)
                }
                override suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction) =
                    previous.copy(revision = 2, confirmedLensId = "lens:cafe")
            })
        vm.activate(true); vm.updateProfiles("owner", emptyList(), false, null); runCurrent()
        vm.onAction(PlacesAction.Search(null, true)); runCurrent()
        vm.onAction(PlacesAction.SetRadius(5000)); runCurrent()
        val baseline = ordinaryCalls
        val ordinaryResponse = requireNotNull(vm.state.value.visibleDiscovery().response)
        vm.onAction(PlacesAction.SetAiMode(true)); runCurrent()
        vm.onAction(PlacesAction.Discover("조용한 카페")); runCurrent()
        assertTrue(queries.single().kinds.isEmpty())
        assertEquals(5000, queries.single().radiusMeters)
        assertTrue(queries.single().parking)
        assertNull(vm.state.value.facility.confirmedLens)
        assertEquals(ordinaryResponse, vm.state.value.visibleDiscovery().response)
        vm.onAction(PlacesAction.ChooseAi(FacilityChoice.Confirm("lens:cafe"))); runCurrent()
        assertEquals(baseline, ordinaryCalls)
        val confirmed = requireNotNull(vm.state.value.facility.confirmedLens)
        val visible = vm.state.value.visibleDiscovery()
        assertEquals(confirmed.search, visible.response)
        assertEquals(confirmed.kinds, visible.requestedKinds)
        assertEquals(5000, visible.radiusMeters)
        assertTrue(visible.preferParking)
        assertEquals(LabPhase.RESULTS, vm.state.value.toConnectedSearchState("", true, null, null).phase)
        vm.onAction(PlacesAction.SetRadius(3000)); runCurrent()
        assertNull(vm.state.value.facility.response)
        assertNull(vm.state.value.facility.confirmedLens)
        val nextOrdinaryResponse = requireNotNull(vm.state.value.discovery.response)
        assertEquals(nextOrdinaryResponse, vm.state.value.visibleDiscovery().response)
        vm.onAction(PlacesAction.Discover("카페")); runCurrent()
        vm.updateProfiles(null, null, false, null); runCurrent()
        assertNull(vm.state.value.facility.response)
        assertNull(vm.state.value.facility.confirmedLens)
        assertEquals(nextOrdinaryResponse, vm.state.value.visibleDiscovery().response)
    }
}
