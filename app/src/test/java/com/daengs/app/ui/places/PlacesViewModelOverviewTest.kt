package com.daengs.app.ui.places

import com.daengs.app.journey.JourneyRepository
import com.daengs.app.journey.JourneyResponse
import com.daengs.app.location.*
import com.daengs.app.place.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModelOverviewTest {
    @Test fun radiusAndAllScopeSurviveMapMoveLocateAndRetry() = runTest {
        val requests = mutableListOf<PlaceSearchRequest>()
        val sample = javaClass.getResourceAsStream("/place_search_lab_sample.json")!!.bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject.toPlaceSearchResponse()
        }
        val point = GeoPoint(37.54,127.05)
        val source = object : LocationSource {
            override suspend fun currentLocation() = LocationSample(point, 0)
            override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
        }
        val vm = PlacesViewModel(PlaceSearchRepository { request ->
            requests += request
            sample.copy(groups = request.kinds.map { sample.groups.first().copy(kind = it) })
        }, JourneyRepository { JourneyResponse("dog", emptyList()) }, source, backgroundScope)
        runCurrent(); vm.activate(true); runCurrent()
        vm.onAction(PlacesAction.Search(null, true, "카페")); runCurrent()
        vm.onAction(PlacesAction.SetRadius(5000)); runCurrent()
        requests.clear()
        vm.onAction(PlacesAction.SearchAt(GeoPoint(37.55,127.06), null, true)); runCurrent()
        vm.onAction(PlacesAction.Locate(null, true)); runCurrent()
        vm.onAction(PlacesAction.RetrySearch); runCurrent()
        assertEquals(9, requests.size)
        assertTrue(requests.all { it.radiusMeters == 5000 && it.nameQuery == "카페" && it.preferParking })
        assertEquals(PlaceKind.entries, vm.state.value.discovery.requestedKinds)
        assertEquals(5000, vm.state.value.discovery.radiusMeters)
        vm.onAction(PlacesAction.Search(PlaceKind.ETC, true)); runCurrent()
        assertEquals(listOf(PlaceKind.ETC), requests.last().kinds)
        assertEquals(5000, requests.last().radiusMeters)
    }
}
