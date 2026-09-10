package com.daengs.app.ui.places

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.places.PlaceDiscoveryState
import com.daengs.app.map.features.places.PlaceSearchState
import com.daengs.app.place.*
import com.daengs.app.place.support.filteredConversationFixture
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
    private fun captureWindow(name: String) {
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val directory = java.io.File("build/reports/conversation").apply { mkdirs() }
            java.io.File(directory, name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
    @Config(qualifiers = "w320dp-h844dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun filtersRemainVisibleWithAiOffAndRemovalPreservesOrGrouping() {
        val result = filteredConversationFixture().toConversationResult()
        val actions = mutableListOf<PlacesAction>()
        val state = ready().copy(conversationAvailable = true,
            conversation = ConversationUiState(result = result),
            discovery = PlaceDiscoveryState(requestedKinds = result.kinds, origin = result.origin,
                search = PlaceSearchState.Content(result.search!!)))
        compose.setContent { DaengsTheme {
            ConnectedPlaceSearchScreen(state, actions::add, {}, {}, {}, {}, {}, showMap = false)
        } }
        compose.onNodeWithContentDescription("검색 필터").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("place-search-queue").assertIsDisplayed()
        captureWindow("filters-screen.png")
        compose.onNodeWithContentDescription("검색 필터").performClick()
        compose.onNodeWithText("위 조건을 충족하면서, 다음 조합 중 하나").assertIsDisplayed()
        compose.onNodeWithText("또는").assertIsDisplayed()
        compose.onNodeWithText("주차 우선: 꺼짐 · 주차 필수 조건의 적용 여부와는 별개예요.").assertExists()
        captureWindow("filters-dialog.png")
        compose.onNodeWithContentDescription("주차 가능한 곳만 해제").performClick()
        assertEquals(PlacesAction.ApplyFilters(ConversationFilterEdit(result.sessionId, result.revision, removeAll = listOf("parking"))), actions.single())
        // No optimistic removal: the dialog continues showing the committed condition.
        compose.onNodeWithContentDescription("주차 가능한 곳만 해제").assertExists()
        compose.onNodeWithText("조합 조건 해제").performClick()
        assertEquals(PlacesAction.ApplyFilters(ConversationFilterEdit(result.sessionId, result.revision, removeAny = listOf("shop", "pet"))), actions.last())
    }

    @Test fun staleResultCanBeRefreshedWithoutAiAndFilterRetryKeepsItsOperation() {
        val result = filteredConversationFixture().toConversationResult().let {
            it.copy(receipt = JsonObject(it.receipt + ("result_matches_filters" to JsonPrimitive(false))))
        }
        val retry = ConversationFilterEdit(result.sessionId, result.revision, removeAll = listOf("parking"))
        val actions = mutableListOf<PlacesAction>()
        val state = ready().copy(conversationAvailable = true,
            conversation = ConversationUiState(result = result, error = "해제 실패", filterRetry = retry))
        compose.setContent { DaengsTheme {
            ConnectedPlaceSearchScreen(state, actions::add, {}, {}, {}, {}, {}, showMap = false)
        } }
        compose.onNodeWithText("현재 목록은 변경 전 조건의 결과예요.").assertIsDisplayed()
        compose.onNodeWithText("현재 조건으로 검색").performClick()
        assertEquals(PlacesAction.ApplyFilters(ConversationFilterEdit(result.sessionId, result.revision)), actions.last())
        compose.onNodeWithText("검색 다시 시도").performClick()
        assertEquals(PlacesAction.ApplyFilters(retry), actions.last())
    }
    @Test fun recoveryNoticeAndRetryRemainVisibleWithAiOff() {
        val result = conversationFixture("manual").toConversationResult()
        val actions = mutableListOf<PlacesAction>()
        val message = "검색 상태를 확인하지 못했어요."
        val state = ready().copy(conversationAvailable = true,
            conversation = ConversationUiState(result = result, error = message),
            discovery = PlaceDiscoveryState(requestedKinds = result.kinds, origin = result.origin,
                search = PlaceSearchState.Content(result.search!!)))
        compose.setContent { DaengsTheme {
            ConnectedPlaceSearchScreen(state, actions::add, {}, {}, {}, {}, {}, showMap = false)
        } }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText("검색 다시 시도").performClick()
        assertEquals(PlacesAction.RetrySearch, actions.single())
        compose.onNodeWithContentDescription("AI 조건 검색 전환").assertDoesNotExist()
    }
    @Test fun conversationAnswerComesFromMapDogAndSelectedCardStays() {
        val result = conversationFixture("picked").toConversationResult()
        val state = ready().copy(conversationAvailable = true,
            facility = FacilityUiState(enabled = true),
            conversation = ConversationUiState(result = result, selected = result.selected),
            discovery = PlaceDiscoveryState(requestedKinds = result.kinds, origin = result.origin,
                selectedPlaceKey = result.selected, search = PlaceSearchState.Content(result.search!!)))
        compose.setContent { DaengsTheme {
            ConnectedPlaceSearchScreen(state, {}, {}, {}, {}, {}, {}, showMap = false)
        } }
        compose.onNodeWithText(result.answer!!).assertDoesNotExist()
        compose.onNodeWithContentDescription("강아지에게 검색 조건 말하기").performClick()
        compose.onNodeWithText(result.answer!!).assertExists()
        compose.onNodeWithContentDescription("AI 조건 검색 전환").assertDoesNotExist()
        assertEquals(result.selected, state.toConnectedSearchState("", true, result.selected, null).selected)
    }
    @Test fun fixedDogRemainsAvailableWithoutGpsAndWhenSearchOriginMovesFarAway() {
        val state = androidx.compose.runtime.mutableStateOf(ready().copy(
            location = PlaceLocationState.Failed(PlaceLocationFailure.UNAVAILABLE, null)))
        compose.setContent { DaengsTheme {
            ConnectedPlaceSearchScreen(state.value, {}, {}, {}, {}, {}, {}, showMap = false)
        } }
        compose.onNodeWithTag("place-dog-anchor").assertIsDisplayed().performClick()
        compose.onNodeWithText("검색 지역을 먼저 정해 줘").assertExists()
        compose.onNodeWithText("닫기").performClick()
        val before = compose.onNodeWithTag("place-dog-anchor").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            state.value = state.value.copy(discovery = state.value.discovery.copy(
                origin = GeoPoint(35.16, 129.16), radiusMeters = 500))
        }
        compose.onNodeWithTag("place-dog-anchor").assertIsDisplayed().performClick()
        compose.onNodeWithText("현재 검색 지역 · 반경 500m").assertExists()
        assertEquals(before, compose.onNodeWithTag("place-dog-anchor").fetchSemanticsNode().boundsInRoot)
    }

    private fun ready() = PlacesUiState(location = PlaceLocationState.Ready(GeoPoint(37.54,127.05)),
        discovery = PlaceDiscoveryState(requestedKinds = listOf(PlaceKind.CAFE)))

    @Config(qualifiers = "w320dp-h844dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun compactHeaderAndFloatingCategoriesDoNotMoveTheMap() {
        var backs = 0
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(ready(), actions::add, { backs++ }, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithTag("place-search-field").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithContentDescription("AI 조건 검색 전환").assertDoesNotExist()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        assertEquals(1, backs)
        assertTrue(actions.isEmpty())
        compose.onNodeWithContentDescription("내 주변 검색").performClick()
        assertEquals(PlacesAction.Locate(PlaceKind.CAFE, false), actions.single())
        val before = compose.onNodeWithContentDescription("내 주변 검색").fetchSemanticsNode().boundsInRoot
        expandCategories()
        compose.onNodeWithTag("place-purpose-grid").assertExists()
        // Popup belongs to another window; compare the original map control's position.
        assertEquals(before, compose.onNodeWithContentDescription("내 주변 검색").fetchSemanticsNode().boundsInRoot)
        compose.onNode(hasText("문화") and hasAnyAncestor(hasTestTag("place-purpose-grid"))).performClick()
        assertEquals(before, compose.onNodeWithContentDescription("내 주변 검색").fetchSemanticsNode().boundsInRoot)
        assertEquals(1, actions.size)
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
    @Test fun typingInDogBubbleDispatchesOnlyOnSubmission() {
        val actions = mutableListOf<PlacesAction>()
        val state = androidx.compose.runtime.mutableStateOf(ready())
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state.value, { action ->
            actions += action
            if (action is PlacesAction.SetAiMode) state.value = state.value.copy(facility = FacilityUiState(enabled = action.enabled))
        }, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithTag("place-search-field").assertExists()
        compose.onNodeWithContentDescription("강아지에게 검색 조건 말하기").performClick()
        assertTrue(actions.isEmpty())
        compose.onNodeWithTag("place-dog-input").performTextInput("주차 가능한 카페")
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("말해주기").performScrollTo().performClick()
        assertEquals(listOf(PlacesAction.SetAiMode(true), PlacesAction.Discover("주차 가능한 카페")), actions)
    }
    /**
     * 카테고리 격자를 편다. **격자는 기본이 접혀 있고 고르면 도로 접힌다** — 지도를
     * 가리지 않으려는 것이라, 격자를 두 번 만지는 시험은 그 사이에 다시 펴야 한다.
     */
    private fun expandCategories() = compose.onNodeWithTag("place-category-bar").performClick()

    @Test fun globalAllIsOnHoldAndRadiusCanBeSelected() {
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(ready(), actions::add, {}, {}, {}, {}, {}, showMap = false) } }
        expandCategories()
        compose.onNode(hasText("전체") and hasAnyAncestor(hasTestTag("place-purpose-grid"))).assertIsNotEnabled()
        compose.onNode(hasText("식사·카페") and hasAnyAncestor(hasTestTag("place-purpose-grid"))).performClick()
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("반경 3km ▾").performClick()
        compose.onNodeWithText("5km").performClick()
        assertEquals(PlacesAction.SetRadius(5000), actions.single())
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
    @Test fun browsingPurposeKeepsQueryAndLeavesToggleAcrossParents() {
        val state = androidx.compose.runtime.mutableStateOf(ready())
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state.value, { action ->
            actions += action
            if (action is PlacesAction.Search) state.value = state.value.copy(discovery = state.value.discovery.copy(requestedKinds = action.category.kinds))
        }, {}, {}, {}, {}, {}, showMap = false) } }
        expandCategories()
        listOf("전체", "진료", "돌봄", "쇼핑", "식사·카페", "나들이", "문화", "숙박", "기타").forEach {
            compose.onNode(hasText(it) and hasAnyAncestor(hasTestTag("place-purpose-grid"))).assertIsDisplayed()
        }
        compose.onNode(hasText("진료") and hasAnyAncestor(hasTestTag("place-purpose-grid"))).performClick()
        assertTrue(actions.isEmpty())
        compose.onNodeWithContentDescription("진료 전체").assertIsNotSelected()
        compose.onNode(hasText("카페") and hasAnyAncestor(hasTestTag("place-search-queue"))).assertExists()
        compose.onNodeWithContentDescription("동물병원").performClick()
        assertEquals(listOf(PlaceKind.CAFE, PlaceKind.HOSPITAL), (actions.last() as PlacesAction.Search).category.kinds)
        compose.onNodeWithContentDescription("동물병원").performClick()
        assertEquals(listOf(PlaceKind.CAFE), (actions.last() as PlacesAction.Search).category.kinds)
        compose.onNode(hasText("카페") and hasAnyAncestor(hasTestTag("place-search-queue"))).performClick()
        assertEquals(PlaceCategorySelection.None, (actions.last() as PlacesAction.Search).category)
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
        expandCategories()
        compose.onNode(hasText("숙박") and hasAnyAncestor(hasTestTag("place-purpose-grid"))).performClick()
        compose.onNodeWithContentDescription("숙박 전체").assertIsSelected()
        compose.onNode(hasText("숙박") and hasAnyAncestor(hasTestTag("place-subcategory-panel"))).performClick()
        assertEquals(PlaceCategorySelection.Kind(PlaceKind.STAY), selection.value)
    }
    @Config(qualifiers = "w320dp-h720dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun cultureLeavesScrollInOneQuietRowAndToggleIndependently() {
        val selection = androidx.compose.runtime.mutableStateOf<PlaceCategorySelection>(PlaceCategorySelection.Purpose(PlacePurpose.CULTURE))
        compose.setContent { DaengsTheme {
            Box(Modifier.width(288.dp)) { PlacePurposeMenu(selection.value) { selection.value = it } }
        } }
        compose.onNodeWithTag("place-subcategory-panel").assertHeightIsEqualTo(44.dp)
        compose.onNodeWithContentDescription("박물관").performClick().assertIsNotSelected()
        assertFalse(PlaceKind.MUSEUM in selection.value.kinds)
        compose.onNodeWithContentDescription("문화 전체").performClick().assertIsSelected()
        assertEquals(PlacePurpose.CULTURE.kinds, selection.value.kinds)
        compose.onNodeWithContentDescription("문화 전체").performClick().assertIsNotSelected()
        assertEquals(PlaceCategorySelection.None, selection.value)
    }

}
