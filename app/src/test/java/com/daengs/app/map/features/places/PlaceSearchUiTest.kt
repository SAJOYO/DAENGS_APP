package com.daengs.app.map.features.places

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.place.PlaceKind
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.journey.PlaceJourneyState
import com.daengs.app.ui.places.PlaceLocationState
import com.daengs.app.ui.places.PlacesAction
import com.daengs.app.ui.places.PlacesScreen
import com.daengs.app.ui.places.PlacesUiState
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaceSearchUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `all categories opens a picker without searching and extra selection stays visible`() {
        val selections = mutableListOf<PlaceKind>()
        compose.setContent {
            DaengsTheme {
                var selected by remember { mutableStateOf(PlaceKind.CAFE) }
                PlaceCategoryMenu(selected, true, { selected = it; selections += it })
            }
        }
        // 격자는 접혀 있는 것이 기본이라 먼저 편다.
        compose.onNodeWithTag("place-kind-bar").performClick()
        compose.onNodeWithText("전체 보기").performClick()
        assertTrue(selections.isEmpty())
        compose.onNodeWithText("전체 카테고리").assertIsDisplayed()
        compose.onNodeWithTag("place-category-list").performScrollToNode(hasText("문예회관"))
        compose.onNodeWithText("문예회관").performClick()
        assertEquals(listOf(PlaceKind.ARTS_CENTER), selections)
        // 고르면 격자가 도로 접힌다. 무엇으로 보고 있는지는 막대에 남아야 한다 —
        // 안 남으면 접힌 뒤에 "왜 이것만 나오지" 를 알 길이 없다.
        compose.onNodeWithText("전체 보기").assertDoesNotExist()
        compose.onNodeWithTag("place-kind-bar").assertTextContains("문예회관")
    }

    @Test fun `category grid stays folded until the bar is tapped`() {
        compose.setContent { DaengsTheme { PlaceCategoryMenu(PlaceKind.CAFE, true, {}) } }
        compose.onNodeWithText("펫샵").assertDoesNotExist()
        compose.onNodeWithTag("place-kind-bar").performClick()
        compose.onNodeWithText("펫샵").assertIsDisplayed()
        compose.onNodeWithTag("place-kind-bar").performClick()
        compose.onNodeWithText("펫샵").assertDoesNotExist()
    }

    @Test fun `busy category cannot dispatch another search`() {
        var calls = 0
        compose.setContent { DaengsTheme { PlaceCategoryMenu(PlaceKind.CAFE, false, { calls++ }) } }
        compose.onNodeWithTag("place-kind-bar").performClick()
        compose.onNodeWithText("펫샵").assertIsNotEnabled().performClick()
        assertEquals(0, calls)
    }

    @Test fun `ordinary search and AI inputs dispatch different events without mutating each other`() {
        var query = ""
        var question = ""
        val events = mutableListOf<String>()
        compose.setContent {
            DaengsTheme {
                var q by remember { mutableStateOf("") }
                var ai by remember { mutableStateOf("") }
                Column {
                    PlaceNameSearchField(q, { q = it; query = it }, { events += "search:$q" })
                    PlaceAiQuestionPanel(ai, { ai = it; question = it }, { events += "ask:$ai" },
                        listOf(PlaceAiSuggestion("walk", "가볍게산책")), { events += "suggestion:$it" })
                }
            }
        }
        compose.onNodeWithContentDescription("장소명 검색").performTextInput("홍대")
        compose.onNodeWithContentDescription("장소 검색 실행").performClick()
        compose.onNodeWithContentDescription("AI에게 질문").performTextInput("강아지랑 놀고 싶어")
        compose.onNodeWithText("질문").performClick()
        compose.onNodeWithText("#가볍게산책").performClick()
        assertEquals("홍대", query)
        assertEquals("강아지랑 놀고 싶어", question)
        assertEquals(listOf("search:홍대", "ask:강아지랑 놀고 싶어", "suggestion:walk"), events)
    }

    @Test fun `blank AI question cannot submit`() {
        compose.setContent { PlaceAiQuestionPanel("   ", {}, { error("blank submitted") }, emptyList(), {}) }
        compose.onNodeWithText("질문").assertIsNotEnabled()
    }

    @Test fun `live screen exposes categories and name input but keeps AI unconnected`() {
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme {
            PlacesScreen(PlacesUiState(), {}, {}, {}, { actions += it }, {}, {}, showMap = false)
        } }
        compose.onNodeWithTag("place-kind-bar").performClick()
        compose.onNodeWithText("펫샵").performClick()
        assertEquals(listOf(PlacesAction.Search(PlaceKind.PET_SHOP, false)), actions)
        compose.onNodeWithContentDescription("장소명 검색").assertExists().assertIsNotEnabled()
        compose.onNodeWithContentDescription("AI에게 질문").assertDoesNotExist()
        compose.onNodeWithText("위치 권한").assertIsDisplayed()
    }

    @Test fun `typing does not search and submit clear and categories dispatch distinct name intent`() {
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme {
            PlacesScreen(PlacesUiState(
                location = PlaceLocationState.Ready(GeoPoint(37.556, 126.923)),
                discovery = PlaceDiscoveryState(requestedKinds = listOf(PlaceKind.CAFE), nameQuery = "홍대"),
            ), {}, {}, {}, { actions += it }, {}, {}, showMap = false)
        } }
        compose.onNodeWithContentDescription("장소명 검색").performTextReplacement("  새이름  ")
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("이름 조건: 홍대").assertIsDisplayed()
        compose.onNodeWithTag("place-kind-bar").performClick()
        compose.onNodeWithText("펫샵").performClick()
        assertEquals(PlacesAction.Search(PlaceKind.PET_SHOP, false), actions.last())
        compose.onNodeWithContentDescription("장소명 검색").performImeAction()
        assertEquals(PlacesAction.Search(PlaceKind.CAFE, false, "새이름"), actions.last())
        compose.onNodeWithText("이름 지우기").performClick()
        assertEquals(PlacesAction.Search(PlaceKind.CAFE, false, ""), actions.last())
    }

    @Test fun `overlong name remains editable but cannot submit`() {
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme {
            PlacesScreen(PlacesUiState(location = PlaceLocationState.Ready(GeoPoint(37.556, 126.923))),
                {}, {}, {}, { actions += it }, {}, {}, showMap = false)
        } }
        compose.onNodeWithContentDescription("장소명 검색").performTextInput("가".repeat(121))
        compose.onNodeWithContentDescription("장소 검색 실행").assertIsNotEnabled()
        compose.onNodeWithContentDescription("장소명 검색").performImeAction()
        assertTrue(actions.isEmpty())
        compose.onNodeWithContentDescription("장소명 검색").performTextReplacement("홍대")
        compose.onNodeWithContentDescription("장소 검색 실행").assertIsEnabled().performClick()
        assertEquals("홍대", (actions.single() as PlacesAction.Search).nameQuery)
    }

    @Test fun `result panel folds and unfolds by its handle`() {
        compose.setContent {
            DaengsTheme {
                PlaceDiscoveryPanel(
                    state = PlaceDiscoveryState(
                        requestedKinds = listOf(PlaceKind.CAFE),
                        origin = GeoPoint(37.557, 126.924),
                    ),
                    journey = PlaceJourneyState(),
                    onSearch = { _, _ -> }, onRetry = {}, onSelect = {}, onJourney = {},
                    onRetryJourney = {}, onOpenHandoff = {}, onCall = {},
                )
            }
        }
        // 접어도 "무엇을 어디서" 는 남고, 목록 쪽만 사라진다.
        val title = compose.onAllNodes(hasText("주차 가능 우선"))
        title.assertCountEquals(1)
        compose.onNodeWithTag("place-panel-handle").performClick()
        compose.onAllNodes(hasText("주차 가능 우선")).assertCountEquals(0)
        compose.onNodeWithTag("place-panel-handle").performClick()
        compose.onAllNodes(hasText("주차 가능 우선")).assertCountEquals(1)
    }

}
