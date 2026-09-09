package com.daengs.app.ui.places

import com.daengs.app.journey.JourneyRepository
import com.daengs.app.journey.JourneyResponse
import com.daengs.app.location.*
import com.daengs.app.place.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceFilterAiViewModelTest {
    @Test fun manualFiltersAndAiShareOneAppliedStateAndEditorOpeningCancelsPendingAi() = runTest {
        val proposals = mutableListOf<PlaceFilterEditQuery>()
        var wait: CompletableDeferred<Unit>? = null
        val editor = object : PlaceFilterEditRepository {
            override suspend fun propose(owner: String, query: PlaceFilterEditQuery): PlaceFilterEditResponse {
                proposals += query
                wait?.await()
                return filterEditFixture(query)
            }
            override suspend fun apply(owner: String, action: PlaceFilterEditAction) = appliedFilterEditFixture(action)
        }
        val repo = object : PlaceSearchRepository {
            override suspend fun search(request: PlaceSearchRequest) = PlaceSearchResponse(null, emptyList(), request.dogs)
            override suspend fun filterCapabilities() = filterCapabilitiesFixture()
            override suspend fun searchFiltered(request: PlaceFilterRequest) = filterResponseFixture(request)
        }
        val vm = PlacesViewModel(repo, JourneyRepository { JourneyResponse("dog", emptyList()) }, object : LocationSource {
            override suspend fun currentLocation() = LocationSample(GeoPoint(37.5, 127.0), 0)
            override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
        }, backgroundScope, filterEditRepository = editor)
        vm.updateProfiles("owner", emptyList(), false, null)
        vm.activate(true); runCurrent()
        vm.onAction(PlacesAction.LoadFilterCapabilities); runCurrent()
        val manual = PlaceFilterCriteria(listOf(PlaceKind.CAFE), all = listOf(PlaceFilterAtom("parking", "operations.parking", "eq", JsonPrimitive(true))))
        vm.onAction(PlacesAction.ApplyFilters(manual)); runCurrent()
        vm.onAction(PlacesAction.SetAiMode(true)); runCurrent()
        assertTrue(vm.state.value.filterAi.enabled)
        vm.onAction(PlacesAction.Discover("주차 빼줘")); runCurrent()
        assertEquals(manual, proposals.single().base.criteria)
        assertTrue(vm.state.value.discovery.filters!!.all.isEmpty())
        assertNotNull(vm.state.value.discovery.filterResponse)
        assertNotNull(vm.state.value.filterAi.response!!.result)
        wait = CompletableDeferred()
        vm.onAction(PlacesAction.Discover("전용도 추가")); runCurrent()
        vm.onAction(PlacesAction.LoadFilterCapabilities); runCurrent()
        wait!!.complete(Unit); runCurrent()
        assertNull(vm.state.value.filterAi.response)
        assertTrue(vm.state.value.discovery.filters!!.all.isEmpty())
    }
}
