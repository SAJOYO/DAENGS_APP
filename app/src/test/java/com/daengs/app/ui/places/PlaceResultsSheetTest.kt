package com.daengs.app.ui.places

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.map.features.places.placeMarkerId
import com.daengs.app.ui.places.lab.*
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceResultsSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val hits = (1..20).map { index -> PreviewPlaceHit().let { sample ->
        sample.copy(place = sample.place.copy(key = sample.place.key.copy(ref = "cafe-$index"), name = "테스트 카페 $index"))
    } }

    private fun show() {
        val state = mutableStateOf(PlaceSearchLabState(phase = LabPhase.RESULTS, hits = hits))
        compose.setContent { DaengsTheme {
            PlaceSearchLabScreen(state.value, live = true, onToggle = { state.value = state.value.toggle(it) },
                categoryContent = { Text("카테고리 영역") })
        } }
    }

    @Test fun handleDragAndButtonChangeThePanelInsteadOfIndividualRows() {
        show()
        val handle = compose.onNodeWithTag("place-results-handle")
        val foldedTop = handle.fetchSemanticsNode().boundsInRoot.top
        handle.performTouchInput { swipe(center, Offset(center.x, center.y - 400f), 500) }
        compose.waitForIdle()
        assertTrue(handle.fetchSemanticsNode().boundsInRoot.top < foldedTop - 100f)
        compose.onNodeWithContentDescription("지도 보기").assertExists()
        compose.onNodeWithText("카테고리 영역").assertDoesNotExist()
        compose.onNodeWithText(hits[2].place.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("지도 보기").performClick()
        compose.onNodeWithText("카테고리 영역").assertIsDisplayed()
        assertEquals(foldedTop, handle.fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun detailButtonAndSwipeRestoreListPositionAndSheetHeight() {
        show()
        compose.onNodeWithContentDescription("목록 보기").performClick()
        val panelTop = compose.onNodeWithTag("place-results-handle").fetchSemanticsNode().boundsInRoot.top
        val target = hits[8]
        val tag = "place-result-${placeMarkerId(target.place.key)}"
        compose.onNodeWithTag("place-results-list").performScrollToIndex(8)
        val rowTop = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("place-detail-sheet").assertIsDisplayed()
        compose.onNodeWithText("추가 동반 조건").assertExists()
        compose.onNodeWithText("‹ 목록으로").performClick()
        compose.onNodeWithTag("place-detail-sheet").assertDoesNotExist()
        assertEquals(panelTop, compose.onNodeWithTag("place-results-handle").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(rowTop, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("place-detail-sheet").performTouchInput {
            swipe(Offset(center.x, 8f), Offset(center.x, height + 300f), 500)
        }
        compose.onNodeWithTag("place-detail-sheet").assertDoesNotExist()
        assertEquals(rowTop, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("목록 보기").assertExists()
    }

    @Config(qualifiers = "w320dp-h720dp")
    @Test fun narrowScreenKeepsFiltersAndFullWidthRowsReachable() {
        show()
        compose.onNodeWithText("반려견 선택 ▾").assertIsDisplayed()
        compose.onNodeWithText("주차 우선").assertIsDisplayed()
        compose.onNodeWithText(hits.first().place.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("목록 보기").performClick()
        val first = compose.onNodeWithTag("place-result-${placeMarkerId(hits[0].place.key)}").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("place-result-${placeMarkerId(hits[1].place.key)}").fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, second.left, 1f)
        assertEquals(first.right, second.right, 1f)
        assertEquals(first.height, second.height, 1f)
        assertTrue(second.top >= first.bottom)
    }
}
