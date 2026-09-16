package com.daengs.app.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.places.lab.PlaceResultsPosition
import com.daengs.app.ui.places.lab.PlaceResultsScaffold
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class PlaceAssistantKeyboardOverlayTest {
    @get:Rule val compose = createComposeRule()

    private fun checkOverlay(initial: PlaceResultsPosition, finish: String) {
        val ime = mutableStateOf(false)
        val open = mutableStateOf(true)
        val busy = mutableStateOf(false)
        val list = LazyListState(8, 7)
        compose.setContent { DaengsTheme {
            val keyboardHeight = with(LocalDensity.current) { 300.dp.roundToPx() }
            Box(Modifier.height(780.dp)) {
                PlaceResultsScaffold(false, { Text("검색") }, { Text("카테고리") }, { Text("조건") },
                    map = {
                        PlaceMapControls(false, {}, {}) {
                            PlaceDogAssistant(busy.value, false, open.value, { open.value = it },
                                { busy.value = true }, { busy.value = false }, searchContext = "반경 3km") {}
                        }
                    }, listState = list, initialPosition = initial,
                    imeInsets = WindowInsets(bottom = if (ime.value) keyboardHeight else 0),
                ) { items(40) { Text("카페 $it", Modifier.height(60.dp)) } }
            }
        } }
        val sheet = compose.onNodeWithTag("place-results-sheet")
        val previousDescription = sheet.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        val previousTop = sheet.fetchSemanticsNode().boundsInRoot.top
        val previousMapHeight = compose.onNodeWithTag("place-map-controls").fetchSemanticsNode().boundsInRoot.height
        // 도우미견 입력에 포커스가 없으면 기존 화면의 IME 회피 배치를 유지한다.
        compose.runOnIdle { ime.value = true }
        assertTrue(compose.onNodeWithTag("place-map-controls").fetchSemanticsNode().boundsInRoot.height < previousMapHeight)
        compose.runOnIdle { ime.value = false }
        compose.onNodeWithTag("place-dog-input").performClick()
        val mapBounds = compose.onNodeWithTag("place-map-controls").fetchSemanticsNode().boundsInRoot
        val previousIndex = list.firstVisibleItemIndex
        val previousOffset = list.firstVisibleItemScrollOffset
        compose.runOnIdle { ime.value = true }
        fun assertBackgroundUnchanged() {
            sheet.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, previousDescription))
            assertEquals(previousTop, sheet.fetchSemanticsNode().boundsInRoot.top, 1f)
            assertEquals(mapBounds, compose.onNodeWithTag("place-map-controls").fetchSemanticsNode().boundsInRoot)
            assertEquals(previousIndex, list.firstVisibleItemIndex)
            assertEquals(previousOffset, list.firstVisibleItemScrollOffset)
        }
        assertBackgroundUnchanged()
        compose.onNodeWithTag("place-dog-input").performTextInput("카페")
        if (finish == "send") {
            compose.onNodeWithContentDescription("말해주기").performClick()
            compose.onNodeWithTag("place-dog-thinking").assertExists()
        } else if (finish == "dismiss") {
            compose.onNodeWithContentDescription("닫기").performClick()
        }
        assertBackgroundUnchanged()
        compose.runOnIdle { ime.value = false }
        assertBackgroundUnchanged()
        if (finish == "hide") compose.onNodeWithTag("place-dog-input").assertTextContains("카페")
    }

    @Test fun previewDoesNotMoveWhenKeyboardOpensOrHides() = checkOverlay(PlaceResultsPosition.Preview, "hide")
    @Test fun expandedAndScrolledListRemainAfterSending() = checkOverlay(PlaceResultsPosition.Expanded, "send")
    @Test fun collapsedRemainsCollapsedAfterSending() = checkOverlay(PlaceResultsPosition.Collapsed, "send")
    @Test fun closingDialogueWaitsForKeyboardWithoutMovingCards() = checkOverlay(PlaceResultsPosition.Preview, "dismiss")
}
