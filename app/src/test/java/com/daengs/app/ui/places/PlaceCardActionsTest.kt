package com.daengs.app.ui.places

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.journey.*
import com.daengs.app.location.*
import com.daengs.app.place.*
import com.daengs.app.map.features.places.*
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceCardActionsTest {
    @get:Rule val compose = createComposeRule()
    private fun response() = javaClass.getResourceAsStream("/place_search_lab_sample.json")!!.bufferedReader().use {
        Json.parseToJsonElement(it.readText()).jsonObject.toPlaceSearchResponse()
    }
    @Test fun loadingPreservesExpansionButRemovedCardReappearsCollapsed() {
        val original = response()
        val first = original.groups.first { it.results.isNotEmpty() }
        val hit = first.results.first().let { it.copy(place = it.place.copy(facts = it.place.facts.copy(phone = "02-123-4567"))) }
        val response = original.copy(groups = listOf(first.copy(results = listOf(hit))))
        val ready = PlacesUiState(location = PlaceLocationState.Ready(hit.place.point),
            discovery = PlaceDiscoveryState(requestedKinds = listOf(first.kind), search = PlaceSearchState.Content(response)))
        val state = mutableStateOf(ready)
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state.value, {}, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText(hit.place.name).performClick()
        compose.onNodeWithText("전화로 확인").assertExists()
        compose.runOnIdle { state.value = ready.copy(discovery = ready.discovery.copy(search = PlaceSearchState.Loading)) }
        compose.onNodeWithText("찾는 중…").assertExists()
        compose.runOnIdle { state.value = ready }
        compose.onNodeWithText("전화로 확인").assertExists()
        compose.runOnIdle { state.value = ready.copy(discovery = ready.discovery.copy(
            search = PlaceSearchState.Empty(response.copy(groups = listOf(first.copy(results = emptyList())))))) }
        compose.onNodeWithText("검색 결과가 없어요.").assertExists()
        compose.runOnIdle { state.value = ready }
        compose.onNodeWithText("전화로 확인").assertDoesNotExist()
        compose.onNodeWithText(hit.place.name).performClick()
        compose.onNodeWithText("전화로 확인").assertExists()
    }
    @Test fun cardRetryDispatchesCurrentDestination() {
        val original = response()
        val first = original.groups.first { it.results.isNotEmpty() }
        val hit = first.results.first()
        val state = PlacesUiState(location = PlaceLocationState.Ready(hit.place.point),
            discovery = PlaceDiscoveryState(requestedKinds = listOf(first.kind),
                search = PlaceSearchState.Content(original.copy(groups = listOf(first.copy(results = listOf(hit)))))),
            journey = com.daengs.app.map.features.journey.PlaceJourneyState(destinationKey = hit.place.key, error = "현재 위치 확인 필요"))
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state, actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText(hit.place.name).performClick()
        // Verify the card's action binding independently of nested-scroll touch coordinates.
        compose.onNodeWithText("길찾기 다시 시도").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(PlacesAction.LoadJourney(hit.place), actions.last()) }
    }
    @Test fun journeyFromCardAfterGpsRecoveryUsesRecoveredOrigin() = runTest {
        val hit = response().groups.flatMap { it.results }.first()
        val updates = MutableSharedFlow<LocationSample>(extraBufferCapacity = 1)
        val source = object : LocationSource {
            override suspend fun currentLocation(): LocationSample = error("GPS unavailable")
            override fun locationUpdates(config: LocationUpdateConfig) = updates
        }
        val journeys = mutableListOf<PlaceJourneyRequest>()
        val vm = PlacesViewModel(PlaceSearchRepository { response() },
            JourneyRepository { request -> journeys += request; JourneyResponse("dog", listOf(JourneyItem(
                destination = request.destination, name = request.destinationName, straightMeters = 0,
                modePriority = emptyList(), legs = emptyMap()))) }, source, backgroundScope)
        vm.activate(true); runCurrent()
        vm.onAction(PlacesAction.SearchAt(hit.place.point, hit.place.match.kind, false)); runCurrent()
        vm.onAction(PlacesAction.LoadJourney(hit.place)); runCurrent()
        assertEquals(hit.place.key, vm.state.value.journey.destinationKey)
        assertNotNull(vm.state.value.journey.error)
        val recovered = GeoPoint(37.54, 127.05)
        updates.emit(LocationSample(recovered, 0)); runCurrent()
        assertTrue(vm.state.value.location is PlaceLocationState.Ready)
        vm.onAction(PlacesAction.LoadJourney(hit.place)); runCurrent()
        assertEquals(1, journeys.size)
        assertEquals(recovered, journeys.single().origin)
        assertEquals(hit.place.key, journeys.single().destinationKey)
        assertNull(vm.state.value.journey.error)
        assertEquals(hit.place.key, vm.state.value.journey.destinationKey)
    }
}
