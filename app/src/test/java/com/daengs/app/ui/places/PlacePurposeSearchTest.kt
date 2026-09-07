package com.daengs.app.ui.places

import com.daengs.app.journey.*
import com.daengs.app.location.*
import com.daengs.app.map.features.places.PlaceDiscoveryController
import com.daengs.app.place.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlacePurposeSearchTest {
    private val dining = PlaceCategorySelection.Purpose(PlacePurpose.DINING)
    private val point = GeoPoint(37.54, 127.05)
    private fun sample() = javaClass.getResourceAsStream("/place_search_lab_sample.json")!!.bufferedReader().use {
        Json.parseToJsonElement(it.readText()).jsonObject.toPlaceSearchResponse()
    }
    private fun response(request: PlaceSearchRequest) = sample().copy(dogs = request.dogs,
        groups = request.kinds.map { sample().groups.first().copy(kind = it, results = emptyList()) })
    private fun source(locate: suspend () -> LocationSample) = object : LocationSource {
        override suspend fun currentLocation() = locate()
        override fun locationUpdates(config: LocationUpdateConfig) = emptyFlow<LocationSample>()
    }

    @Test fun groupScopeSurvivesRadiusMapLocateRetryAndDogChanges() = runTest {
        val requests = mutableListOf<PlaceSearchRequest>()
        val vm = PlacesViewModel(PlaceSearchRepository { requests += it; response(it) },
            JourneyRepository { JourneyResponse("dog", emptyList()) }, source { LocationSample(point, 0) }, backgroundScope)
        vm.activate(true); runCurrent()
        vm.onAction(PlacesAction.Search(dining, true, "보리")); runCurrent()
        vm.onAction(PlacesAction.SetRadius(5000)); runCurrent()
        requests.clear()
        vm.onAction(PlacesAction.SearchAt(GeoPoint(37.55, 127.06), dining, true)); runCurrent()
        vm.onAction(PlacesAction.Locate(dining, true)); runCurrent()
        vm.onAction(PlacesAction.RetrySearch); runCurrent()
        val dog = com.daengs.app.pet.Pet("a", "콩이", "mix", null, null, 9f, null, null, isPrimary = false)
        vm.updateProfiles("owner", listOf(dog), false, null); runCurrent()
        vm.onAction(PlacesAction.ToggleDog("a")); runCurrent()
        assertEquals(4, requests.size)
        assertTrue(requests.all { it.kinds == dining.kinds && it.nameQuery == "보리" && it.radiusMeters == 5000 && it.preferParking })
        assertEquals(listOf("a"), requests.last().dogs.map { it.ref })
        assertEquals(dining.kinds, vm.state.value.discovery.requestedKinds)
    }

    @Test fun pendingGpsPublishesNewGroupAndKeepsItWhenRadiusChanges() = runTest {
        val fix = CompletableDeferred<LocationSample>()
        val requests = mutableListOf<PlaceSearchRequest>()
        val vm = PlacesViewModel(PlaceSearchRepository { requests += it; response(it) },
            JourneyRepository { JourneyResponse("dog", emptyList()) }, source { fix.await() }, backgroundScope)
        vm.activate(true); runCurrent()
        vm.onAction(PlacesAction.Search(dining, true, "새이름")); runCurrent()
        assertEquals(dining.kinds, vm.state.value.discovery.requestedKinds)
        assertEquals("새이름", vm.state.value.discovery.nameQuery)
        assertTrue(vm.state.value.waitingForSearchLocation)
        vm.onAction(PlacesAction.SetRadius(5000)); runCurrent()
        fix.complete(LocationSample(point, 0)); runCurrent()
        assertEquals(dining.kinds, requests.single().kinds)
        assertEquals(5000, requests.single().radiusMeters)
    }

    @Test fun lateGroupResponseCannotReplaceNewSubcategory() = runTest {
        val old = CompletableDeferred<PlaceSearchResponse>()
        val controller = PlaceDiscoveryController(PlaceSearchRepository {
            if (it.kinds.size > 1) withContext(NonCancellable) { old.await() } else response(it)
        }, null, backgroundScope)
        controller.search(point, dining.kinds); runCurrent()
        controller.search(point, listOf(PlaceKind.RESTAURANT)); runCurrent()
        val latest = controller.state.value.response
        old.complete(response(PlaceSearchRequest(point, kinds = dining.kinds))); runCurrent()
        assertSame(latest, controller.state.value.response)
        assertEquals(listOf(PlaceKind.RESTAURANT), controller.state.value.requestedKinds)
    }

    @Test fun groupResultsDeduplicateAndSelectFirstPlaceUsingSharedDistanceParkingOrder() = runTest {
        val template = sample().groups.first()
        val hit = template.results.first()
        fun hit(ref: String, distance: Int, parking: Boolean) = hit.copy(place = hit.place.copy(
            key = PlaceKey("test", ref), distanceMeters = distance, facts = hit.place.facts.copy(parking = parking)))
        val near = hit("near", 100, false)
        val parking = hit("parking", 400, true)
        val far = hit("far", 650, true)
        val result = sample().copy(groups = listOf(
            template.copy(kind = PlaceKind.CAFE, results = listOf(far, parking)),
            template.copy(kind = PlaceKind.RESTAURANT, results = listOf(near, parking)),
        ))
        assertEquals(listOf(near, parking, far), result.overviewHits(false))
        assertEquals(listOf(parking, near, far), result.overviewHits(true))
        val controller = PlaceDiscoveryController(PlaceSearchRepository { result }, null, backgroundScope)
        controller.search(point, dining.kinds, preferParking = true); runCurrent()
        assertEquals(parking.place.key, controller.state.value.selectedPlaceKey)
    }
}
