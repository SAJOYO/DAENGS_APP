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
