package com.daengs.app.map.features.places

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.place.PlaceKind
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
        compose.onNodeWithText("전체 보기").performClick()
        assertTrue(selections.isEmpty())
        compose.onNodeWithText("전체 카테고리").assertIsDisplayed()
        compose.onNodeWithTag("place-category-list").performScrollToNode(hasText("문예회관"))
        compose.onNodeWithText("문예회관").performClick()
        assertEquals(listOf(PlaceKind.ARTS_CENTER), selections)
        compose.onNodeWithText("문예회관").assertIsDisplayed().assertIsSelected()
    }

    @Test fun `busy category cannot dispatch another search`() {
        var calls = 0
        compose.setContent { DaengsTheme { PlaceCategoryMenu(PlaceKind.CAFE, false, { calls++ }) } }
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

    @Test fun `live screen exposes working categories but no unconnected name or AI inputs`() {
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme {
            PlacesScreen(PlacesUiState(), {}, {}, {}, { actions += it }, {}, {}, showMap = false)
        } }
        compose.onNodeWithText("펫샵").performClick()
        assertEquals(listOf(PlacesAction.Search(PlaceKind.PET_SHOP, false)), actions)
        compose.onNodeWithContentDescription("장소명 검색").assertDoesNotExist()
        compose.onNodeWithContentDescription("AI에게 질문").assertDoesNotExist()
        compose.onNodeWithText("위치 권한").assertIsDisplayed()
    }
}
