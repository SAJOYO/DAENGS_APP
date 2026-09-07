package com.daengs.app.ui.places

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.places.PlaceDiscoveryState
import com.daengs.app.map.features.places.PlaceSearchState
import com.daengs.app.place.*
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectedPlaceSearchUiTest {
    @get:Rule val compose = createComposeRule()
    private fun ready() = PlacesUiState(location = PlaceLocationState.Ready(GeoPoint(37.54,127.05)),
        discovery = PlaceDiscoveryState(requestedKinds = listOf(PlaceKind.CAFE)))
    @Test fun failedGpsOffersRetryInsteadOfEmptyResults() {
        val state = ready().copy(
            location = PlaceLocationState.Failed(PlaceLocationFailure.UNAVAILABLE, null),
            waitingForSearchLocation = true,
        )
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state, actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText("검색 결과가 없어요.").assertDoesNotExist()
        compose.onNodeWithText("다시 확인").performClick()
        assertEquals(PlacesAction.RetrySearch, actions.single())
    }
    @Test fun realProfileNamesDispatchIndependentDogSelection() {
        val dog = com.daengs.app.pet.Pet("real-id", "콩이", "mix", null, null, 9f, null, null, isPrimary = true)
        val state = ready().copy(profiles = PlaceProfiles().receive("owner", listOf(dog), false, null))
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state, actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText("🐾 반려견 ▾").performClick()
        compose.onNodeWithText("콩이").assertExists()
        compose.onNodeWithContentDescription("콩이 선택").performClick()
        assertEquals(PlacesAction.ToggleDog("real-id"), actions.single())
    }

    @Test fun changedProfilesHidePreviousEvaluationBeforeNewResponse() {
        val response = javaClass.getResourceAsStream("/place_search_lab_sample.json")!!.bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject.toPlaceSearchResponse()
        }
        val dog = com.daengs.app.pet.Pet("a", "콩이", "mix", null, null, 9f, null, null, isPrimary = false)
        val state = ready().copy(profiles = PlaceProfiles().receive("owner", listOf(dog), false, null).toggle("a"),
            discovery = ready().discovery.copy(search = PlaceSearchState.Content(response)))
        assertTrue(state.toConnectedSearchState("", false, null, null).hits.isEmpty())
    }
    @Test fun submitDispatchesNameButTypingAndAiDoNotSearch() {
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(ready(), actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNode(hasSetTextAction()).performTextInput("구욱희씨")
        assertTrue(actions.isEmpty())
        compose.onNodeWithContentDescription("AI 조건 검색 전환").performClick()
        compose.onNodeWithContentDescription("검색 실행").performClick()
        assertTrue(actions.isEmpty())
        compose.onNodeWithContentDescription("AI 조건 검색 전환").performClick()
        compose.onNodeWithContentDescription("검색 실행").performClick()
        assertEquals(PlacesAction.Search(PlaceKind.CAFE, false, "구욱희씨"), actions.single())
    }
    @Test fun allDispatchesAllScopeAndRadiusCanBeSelected() {
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(ready(), actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText("전체보기").performClick()
        assertEquals(PlacesAction.Search(null, false, null), actions.single())
        compose.onNodeWithText("3km ▾").performClick()
        compose.onNodeWithText("5km").performClick()
        assertEquals(PlacesAction.SetRadius(5000), actions.last())
    }
    @Test fun projectionPreservesTruncationAndDoesNotExposeStaleCardsWhileLoading() {
        val response = javaClass.getResourceAsStream("/place_search_lab_sample.json")!!.bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject.toPlaceSearchResponse()
        }
        val clipped = response.copy(groups = response.groups.map { it.copy(truncated = true) })
        val content = ready().copy(discovery = ready().discovery.copy(search = PlaceSearchState.Content(clipped)))
        val ui = content.toConnectedSearchState("", false, null, null)
        assertTrue(ui.truncated)
        assertEquals(response.groups.sumOf { it.results.size }, ui.hits.size)
        val loading = content.copy(discovery = content.discovery.copy(search = PlaceSearchState.Loading))
        assertTrue(loading.toConnectedSearchState("", false, null, null).hits.isEmpty())
    }
}
