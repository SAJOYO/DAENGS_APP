package com.daengs.app.ui.places

import com.daengs.app.journey.*
import com.daengs.app.location.*
import com.daengs.app.place.*
import com.daengs.app.map.features.places.*
import com.daengs.app.ui.places.lab.LabPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchRecoveryTest {
    private val point = GeoPoint(37.54, 127.05)
    private fun response(request: PlaceSearchRequest) = PlaceSearchResponse(null, request.kinds.map { kind ->
        PlaceSearchGroup(kind, PlaceSort(PlaceSortType.DISTANCE, emptyList(), emptyList(), null, emptyMap()), 50, false, emptyList())
    }, request.dogs)
    private fun source(locate: suspend () -> LocationSample) = object : LocationSource {
        override suspend fun currentLocation() = locate()
        override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
    }
    private fun TestScope.vm(source: LocationSource, repo: PlaceSearchRepository) = PlacesViewModel(
        repo, JourneyRepository { JourneyResponse("dog", emptyList()) }, source, backgroundScope)

    @Test fun locationFailureShowsErrorAndRetryLocatesAgain() = runTest {
        var fixes = 0
        var searches = 0
        val vm = vm(source {
            fixes++
            if (fixes == 1) error("GPS unavailable")
            LocationSample(point, 0)
        }, PlaceSearchRepository { searches++; response(it) })
        vm.activate(true); runCurrent()
        assertTrue(vm.state.value.location is PlaceLocationState.Failed)
        assertEquals(LabPhase.ERROR, vm.state.value.toConnectedSearchState("", false, null, null).phase)
        assertEquals("현재 위치를 확인하지 못했습니다.", vm.state.value.toConnectedSearchState("", false, null, null).errorText)
        vm.onAction(PlacesAction.RetrySearch); runCurrent()
        assertEquals(2, fixes)
        assertEquals(1, searches)
        assertEquals(LabPhase.EMPTY, vm.state.value.toConnectedSearchState("", false, null, null).phase)
    }

    @Test fun obsoleteGpsWaitDoesNotMaskCompletedCategorySearch() = runTest {
        var fixes = 0
        val requests = mutableListOf<PlaceSearchRequest>()
        val vm = vm(source {
            if (++fixes > 1) awaitCancellation()
            LocationSample(point, 0)
        }, PlaceSearchRepository { requests += it; response(it) })
        vm.activate(true); runCurrent()
        vm.onAction(PlacesAction.Locate(PlaceKind.CAFE, false)); runCurrent()
        vm.onAction(PlacesAction.Search(PlaceKind.RESTAURANT, false, "새이름")); runCurrent()
        assertEquals("새이름", requests.last().nameQuery)
        assertTrue(vm.state.value.discovery.search is PlaceSearchState.Empty)
        assertEquals(LabPhase.EMPTY, vm.state.value.toConnectedSearchState("", false, null, null).phase)
    }

    @Test fun returningDuringSearchRestoresNameCategoryParkingAndPinnedOrigin() = runTest {
        val requests = mutableListOf<PlaceSearchRequest>()
        val vm = vm(source { LocationSample(point, 0) }, PlaceSearchRepository { request ->
            requests += request
            if (request.nameQuery == "보리" && requests.count { it.nameQuery == "보리" } == 1) awaitCancellation()
            response(request)
        })
        vm.activate(true); runCurrent()
        val pinned = GeoPoint(37.55, 127.06)
        vm.onAction(PlacesAction.SearchAt(pinned, PlaceKind.RESTAURANT, true, "보리")); runCurrent()
        assertTrue(vm.state.value.discovery.loading)
        vm.deactivate(); runCurrent()
        vm.activate(true); runCurrent()
        assertEquals("보리", requests.last().nameQuery)
        assertEquals(listOf(PlaceKind.RESTAURANT), requests.last().kinds)
        assertTrue(requests.last().preferParking)
        assertEquals(pinned, requests.last().origin)
        assertEquals(requests[requests.lastIndex - 1], requests.last())
        assertEquals(LabPhase.EMPTY, vm.state.value.toConnectedSearchState("", false, null, null).phase)
    }

    @Test fun returningDuringGpsWaitRestoresLatestConditions() = runTest {
        var fixes = 0
        val requests = mutableListOf<PlaceSearchRequest>()
        val vm = vm(source {
            if (++fixes < 3) awaitCancellation()
            LocationSample(point, 0)
        }, PlaceSearchRepository { requests += it; response(it) })
        vm.activate(true); runCurrent()
        vm.onAction(PlacesAction.Search(PlaceKind.RESTAURANT, true, "보리")); runCurrent()
        vm.deactivate(); runCurrent()
        vm.activate(true); runCurrent()
        assertEquals(3, fixes)
        assertEquals("보리", requests.single().nameQuery)
        assertEquals(listOf(PlaceKind.RESTAURANT), requests.single().kinds)
        assertTrue(requests.single().preferParking)
    }

    @Test fun networkRetryRepeatsRequestWithoutAnotherGpsFix() = runTest {
        var fixes = 0
        val requests = mutableListOf<PlaceSearchRequest>()
        val vm = vm(source { fixes++; LocationSample(point, 0) }, PlaceSearchRepository {
            requests += it
            if (requests.size == 1) throw java.net.UnknownHostException()
            response(it)
        })
        vm.activate(true); runCurrent()
        assertEquals(LabPhase.ERROR, vm.state.value.toConnectedSearchState("", false, null, null).phase)
        vm.onAction(PlacesAction.RetrySearch); runCurrent()
        assertEquals(1, fixes)
        assertEquals(2, requests.size)
        assertEquals(requests.first(), requests.last())
        assertEquals(LabPhase.EMPTY, vm.state.value.toConnectedSearchState("", false, null, null).phase)
    }
}
