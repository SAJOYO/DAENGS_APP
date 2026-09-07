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
        compose.onNodeWithText("다시 확인").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(PlacesAction.RetrySearch, actions.single())
    }
    @Test fun realProfileNamesDispatchIndependentDogSelection() {
        val dog = com.daengs.app.pet.Pet("real-id", "콩이", "mix", null, null, 9f, null, null, isPrimary = true)
        val state = ready().copy(profiles = PlaceProfiles().receive("owner", listOf(dog), false, null))
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state, actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText("반려견 선택 ▾").performClick()
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
    @Test fun typingDoesNotSearchAndAiSubmissionDispatchesTheNaturalLanguageQuery() {
        val actions = mutableListOf<PlacesAction>()
        val state = androidx.compose.runtime.mutableStateOf(ready())
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state.value, { action ->
            actions += action
            if (action is PlacesAction.SetAiMode) state.value = state.value.copy(facility = FacilityUiState(enabled = action.enabled))
        }, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNode(hasSetTextAction()).performTextInput("구욱희씨")
        assertTrue(actions.isEmpty())
        compose.onNodeWithContentDescription("AI 조건 검색 전환").performClick()
        assertEquals(PlacesAction.SetAiMode(true), actions.single())
        compose.onNodeWithContentDescription("검색 실행").performClick()
        assertEquals(PlacesAction.Discover("구욱희씨"), actions.last())
        compose.onNodeWithContentDescription("AI 조건 검색 전환").performClick()
        compose.onNodeWithContentDescription("검색 실행").performClick()
        assertEquals(PlacesAction.Search(PlaceKind.CAFE, false, "구욱희씨"), actions.last())
    }
    @Test fun allDispatchesAllScopeAndRadiusCanBeSelected() {
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(ready(), actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText("전체").performClick()
        assertEquals(PlacesAction.Search(null, false, null), actions.single())
        compose.onNodeWithText("반경 3km ▾").performClick()
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

    @Config(qualifiers = "w320dp-h844dp")
    @Test fun purposeSearchShowsOnlyItsChildrenAndChangingPurposeResetsChild() {
        val state = androidx.compose.runtime.mutableStateOf(ready())
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state.value, { action ->
            actions += action
            if (action is PlacesAction.Search) state.value = state.value.copy(
                discovery = state.value.discovery.copy(requestedKinds = action.category.kinds))
        }, {}, {}, {}, {}, {}, showMap = false) } }
        listOf("전체", "진료", "돌봄", "쇼핑", "식사·카페", "나들이", "문화", "숙박", "기타").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("식사·카페").performClick()
        assertEquals(PlaceCategorySelection.Purpose(PlacePurpose.DINING), (actions.last() as PlacesAction.Search).category)
        compose.onNodeWithText("식사·카페 전체").assertIsSelected()
        compose.onNodeWithText("음식점").performClick()
        compose.onNodeWithText("음식점").assertIsSelected()
        compose.onNodeWithText("진료").assertIsDisplayed()
        compose.onNodeWithText("진료").performClick()
        compose.onNodeWithText("진료 전체").assertIsSelected()
        compose.onNodeWithText("동물병원").assertExists()
        compose.onNodeWithText("음식점").assertDoesNotExist()
        assertEquals(PlacePurpose.HEALTHCARE.kinds, (actions.last() as PlacesAction.Search).category.kinds)
        compose.onNodeWithText("전체").assertIsDisplayed()
        compose.onNodeWithText("전체").performClick()
        compose.onNodeWithText("진료 전체").assertDoesNotExist()
        assertEquals(PlaceKind.entries, (actions.last() as PlacesAction.Search).category.kinds)
        compose.onNodeWithText("기타").assertIsDisplayed()
        compose.onNodeWithText("기타").performClick()
        assertEquals(listOf(PlaceKind.ETC), (actions.last() as PlacesAction.Search).category.kinds)
    }

    @Test fun submittingNameAndParkingPreserveTheSelectedPurpose() {
        val actions = mutableListOf<PlacesAction>()
        val state = ready().copy(discovery = ready().discovery.copy(requestedKinds = PlacePurpose.DINING.kinds))
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state, actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText("식사·카페 0곳").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("보리")
        compose.onNodeWithContentDescription("검색 실행").performClick()
        assertEquals(PlacesAction.Search(PlaceCategorySelection.Purpose(PlacePurpose.DINING), false, "보리"), actions.last())
        compose.onNodeWithText("주차 우선").performClick()
        assertEquals(PlacesAction.Search(PlaceCategorySelection.Purpose(PlacePurpose.DINING), true), actions.last())
    }
}
