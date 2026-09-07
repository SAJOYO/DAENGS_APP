package com.daengs.app.ui.places

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectedPlaceSearchUiTest {
    @get:Rule val compose = createComposeRule()
    private fun ready() = PlacesUiState(location = PlaceLocationState.Ready(GeoPoint(37.54,127.05)),
        discovery = PlaceDiscoveryState(requestedKinds = listOf(PlaceKind.CAFE)))

    @Config(qualifiers = "w320dp-h844dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun compactHeaderAndMapControlsKeepNavigationSeparateFromSearching() {
        var backs = 0
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(ready(), actions::add, { backs++ }, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithTag("place-search-field").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithContentDescription("AI 조건 검색 전환").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val field = compose.onNodeWithTag("place-search-field").fetchSemanticsNode().boundsInRoot
        val submit = compose.onNodeWithContentDescription("검색 실행").fetchSemanticsNode().boundsInRoot
        val robot = compose.onNodeWithContentDescription("AI 조건 검색 전환").fetchSemanticsNode().boundsInRoot
        assertTrue(submit.left >= field.left && submit.right <= robot.left)
        assertEquals(field.right, robot.right, 1f)
        assertEquals(field.top, robot.top, 1f)
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        assertEquals(1, backs)
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("이 주변 검색").assertDoesNotExist()
        compose.onNodeWithText("내 주변 검색").performClick()
        assertEquals(PlacesAction.Locate(PlaceKind.CAFE, false), actions.single())
        val category = compose.onNode(hasText("전체") and hasAnyAncestor(hasTestTag("place-purpose-grid"))).fetchSemanticsNode().boundsInRoot
        val conditions = compose.onNodeWithText("반려견 선택 ▾").fetchSemanticsNode().boundsInRoot
        assertTrue(conditions.top > category.bottom)
        compose.onAllNodesWithText("주차 우선").assertCountEquals(1)
        compose.onNodeWithText("주차 우선").assertIsNotSelected()
        val grid = compose.onNodeWithTag("place-purpose-grid").fetchSemanticsNode().boundsInRoot
        val radius = compose.onNodeWithText("반경 3km ▾").fetchSemanticsNode().boundsInRoot
        val parking = compose.onNodeWithText("주차 우선").fetchSemanticsNode().boundsInRoot
        val count = compose.onNodeWithText("카페 0곳").fetchSemanticsNode().boundsInRoot
        assertEquals(grid.left, count.left, 1f)
        assertTrue(conditions.left > grid.left)
        assertEquals(conditions.top, radius.top, 1f)
        assertEquals(radius.top, parking.top, 1f)
        assertEquals(radius.left - conditions.right, parking.left - radius.right, 1f)
        assertEquals(grid.right, parking.right, 1f)
        compose.onNodeWithText("지도 중심 기준").assertDoesNotExist()
    }
    @Test fun failedGpsOffersRetryInsteadOfEmptyResults() {
        val state = ready().copy(
            location = PlaceLocationState.Failed(PlaceLocationFailure.UNAVAILABLE, null),
            waitingForSearchLocation = true,
        )
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state, actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText("검색 결과가 없어요.").assertDoesNotExist()
        compose.onNode(hasText("현재 위치를 확인하지 못했습니다.") and hasAnyAncestor(hasScrollAction())).assertExists()
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
        compose.onNodeWithText("장소명 검색").assertExists()
        compose.onNodeWithContentDescription("AI 조건 검색 전환").performClick().assertIsOn()
        compose.onNodeWithText("AI에게 원하는 장소를 말해보세요").assertIsDisplayed()
        compose.onNodeWithContentDescription("AI 조건 검색 전환").performClick().assertIsOff()
        compose.onNodeWithText("장소명 검색").assertExists()
        actions.clear()
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
        compose.onNode(hasText("전체") and hasAnyAncestor(hasTestTag("place-purpose-grid"))).performClick()
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
            compose.onNode(hasText(it) and hasAnyAncestor(hasTestTag("place-purpose-grid"))).assertIsDisplayed()
        }
        compose.onNodeWithText("식사·카페").performClick()
        assertEquals(PlaceCategorySelection.Purpose(PlacePurpose.DINING), (actions.last() as PlacesAction.Search).category)
        compose.onNodeWithContentDescription("식사·카페 전체").assertIsSelected().assertTextEquals("전체")
        compose.onNodeWithTag("place-subcategory-panel").assertIsDisplayed()
        compose.onNodeWithText("음식점").performClick()
        compose.onNodeWithText("음식점").assertIsSelected()
        compose.onNodeWithText("진료").assertIsDisplayed()
        compose.onNodeWithText("진료").performClick()
        compose.onNodeWithContentDescription("진료 전체").assertIsSelected()
        compose.onNodeWithText("동물병원").assertExists()
        compose.onNodeWithText("음식점").assertDoesNotExist()
        assertEquals(PlacePurpose.HEALTHCARE.kinds, (actions.last() as PlacesAction.Search).category.kinds)
        compose.onNode(hasText("전체") and hasAnyAncestor(hasTestTag("place-purpose-grid"))).assertIsDisplayed()
        compose.onNode(hasText("전체") and hasAnyAncestor(hasTestTag("place-purpose-grid"))).performClick()
        compose.onNodeWithTag("place-subcategory-panel").assertDoesNotExist()
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

    @Test fun lodgingParentAndChildCanShareTheirDisplayName() {
        val selection = androidx.compose.runtime.mutableStateOf<PlaceCategorySelection>(PlaceCategorySelection.All)
        compose.setContent { DaengsTheme { PlacePurposeMenu(selection.value) { selection.value = it } } }
        compose.onNodeWithText("숙박").performClick()
        compose.onNodeWithContentDescription("숙박 전체").assertIsSelected()
        compose.onNode(hasText("숙박") and hasAnyAncestor(hasTestTag("place-subcategory-panel"))).performClick()
        assertEquals(PlaceCategorySelection.Kind(PlaceKind.STAY), selection.value)
    }
    @Config(qualifiers = "w320dp-h720dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun cultureTabsWrapAndOnlyOneSubcategoryRemainsSelected() {
        val selection = androidx.compose.runtime.mutableStateOf<PlaceCategorySelection>(PlaceCategorySelection.Purpose(PlacePurpose.CULTURE))
        compose.setContent { DaengsTheme { PlacePurposeMenu(selection.value) { selection.value = it } } }
        val panel = compose.onNodeWithTag("place-subcategory-panel").fetchSemanticsNode().boundsInRoot
        val tabs = PlacePurpose.CULTURE.kinds.map { kind ->
            compose.onNode(hasText(com.daengs.app.map.features.places.categoryLabel(kind)) and
                hasAnyAncestor(hasTestTag("place-subcategory-panel")))
        }
        val bounds = tabs.map { it.assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds.all { it.left >= panel.left && it.right <= panel.right && it.bottom <= panel.bottom })
        assertTrue(bounds.last().top > bounds.first().top)
        tabs[0].performClick().assertIsSelected()
        tabs[1].performClick().assertIsSelected()
        tabs[0].assertIsNotSelected()
        assertEquals(listOf(PlaceKind.GALLERY), selection.value.kinds)
        tabs[1].performClick().assertIsSelected()
        compose.onNodeWithContentDescription("문화 전체").performClick().assertIsSelected()
        tabs[1].assertIsNotSelected()
        assertEquals(PlacePurpose.CULTURE.kinds, selection.value.kinds)
    }

}
