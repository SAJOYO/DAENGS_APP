package com.daengs.app.ui.places

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
class PlaceBookmarkUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(model: PlaceBookmarkLabModel = PlaceBookmarkLabModel()): PlaceBookmarkLabModel {
        compose.setContent { DaengsTheme { PlaceBookmarkLabScreen(model) } }
        return model
    }
    private fun capture(name: String) {
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val directory = java.io.File("build/reports/place-bookmarks").apply { mkdirs() }
            java.io.File(directory, name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
    @Test fun savedTabKeepsConditionsAndAllSavedCanFindFacilitiesOutsideTheSearch() {
        val model = show()
        val searchFilters = model.session.current.filters
        compose.onNodeWithTag("place-tab-BOOKMARKS").performClick().assertIsSelected()
        compose.onNodeWithText("조건에 맞는 1곳 · 저장 3곳").assertExists()
        assertEquals(searchFilters, model.session.current.filters)
        compose.onNodeWithText("찜한 시설 · 지도에 1곳").assertExists()
        capture("filtered-390.png")
        compose.onNodeWithText("전체 찜 보기").performClick()
        compose.onNodeWithText("지역 전체 ▾").assertIsDisplayed()
        compose.onNodeWithText("조건에 맞는 3곳 · 저장 3곳").assertExists()
        compose.onNodeWithContentDescription("목록 보기").performClick()
        compose.onNodeWithTag("place-results-list").performScrollToNode(hasText("바람숲 스테이"))
        assertTrue(model.visible().any { it.place.name == "바람숲 스테이" })
        capture("all-saved-390.png")
        compose.onNodeWithTag("place-tab-SEARCH").performClick().assertIsSelected()
        assertEquals(searchFilters, model.session.current.filters)
        compose.onNodeWithText("반경 3km ▾").assertIsDisplayed()
    }

    @Test fun noMatchingSavedFacilitiesIsDifferentFromNeverSavedAndFromLoadFailure() {
        val model = show(PlaceBookmarkLabModel().apply { tab(PlaceBrowseTab.BOOKMARKS); filter { it.copy(name = "없는이름") } })
        compose.onNodeWithText("이 조건에 맞는 찜이 없어요.").assertExists()
        compose.onNodeWithText("전체 찜 보기").assertExists()
        compose.runOnIdle { model.saved = emptySet() }
        compose.onNodeWithText("아직 찜한 시설이 없어요.").assertExists()
        compose.onNodeWithText("전체 찜 보기").assertDoesNotExist()
        capture("empty-390.png")
        compose.runOnIdle { model.phase = PlaceBookmarkPhase.FAILED }
        compose.onNodeWithText("아직 찜한 시설이 없어요.").assertDoesNotExist()
        compose.onNodeWithText("찜한 시설을 불러오지 못했어요.").assertExists()
        compose.onNodeWithText("다시 불러오기").performClick()
        compose.onNodeWithText("아직 찜한 시설이 없어요.").assertExists()
        compose.runOnIdle { model.phase = PlaceBookmarkPhase.LOADING }
        compose.onNodeWithText("찜한 시설을 불러오는 중…").assertExists()
        compose.onNodeWithText("아직 찜한 시설이 없어요.").assertDoesNotExist()
    }

    @Test fun rowAndDetailHeartsShareStateWithoutOpeningTheDetailOnSave() {
        val model = show()
        compose.onNodeWithContentDescription("목록 보기").performClick()
        compose.onNodeWithContentDescription("느티나무 카페 찜하기").performClick()
        compose.onNodeWithTag("place-detail-sheet").assertDoesNotExist()
        val hit = model.catalog.first()
        assertTrue(hit.place.key in model.saved)
        compose.onNodeWithTag("place-result-${placeMarkerId(hit.place.key)}").performClick()
        compose.onNodeWithTag("place-detail-sheet").assertIsDisplayed()
        capture("detail-390.png")
        compose.onNode(hasContentDescription("느티나무 카페 찜 해제") and
            hasAnyAncestor(hasTestTag("place-detail-sheet"))).performClick()
        assertFalse(hit.place.key in model.saved)
        compose.onNodeWithText("‹ 목록으로").performClick()
        compose.onNodeWithContentDescription("느티나무 카페 찜하기").assertExists()
        compose.onNodeWithText("실행 취소").performClick()
        assertTrue(hit.place.key in model.saved)
        compose.onNodeWithTag("place-result-${placeMarkerId(hit.place.key)}").performClick()
        compose.onNodeWithText("지도에서 보기").performClick()
        compose.onNodeWithTag("place-detail-sheet").assertDoesNotExist()
        compose.onNodeWithContentDescription("목록 보기").assertIsDisplayed()
        assertEquals(hit.place.key, model.session.current.selected)
    }

    @Test fun eachTabRestoresItsOwnListPosition() {
        val sample = bookmarkLabHits().first()
        val hits = (0..25).map { n -> sample.copy(place = sample.place.copy(key = sample.place.key.copy(ref = "row-$n"), name = "카페 $n")) }
        show(PlaceBookmarkLabModel(hits))
        compose.onNodeWithContentDescription("목록 보기").performClick()
        compose.onNodeWithTag("place-results-list").performScrollToIndex(12)
        val tag = "place-result-${placeMarkerId(hits[12].place.key)}"
        val before = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("place-tab-BOOKMARKS").performClick()
        compose.onNodeWithTag("place-results-list").performScrollToIndex(4)
        compose.onNodeWithTag("place-tab-SEARCH").performClick()
        assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Config(qualifiers = "w320dp-h720dp")
    @Test fun narrowLayoutKeepsTabsAndTouchTargets() {
        show()
        compose.onNodeWithTag("place-tab-SEARCH").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("place-tab-BOOKMARKS").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("검색 실행").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        capture("search-320.png")
        compose.onNodeWithTag("place-tab-BOOKMARKS").performClick()
        compose.onNodeWithText("전체 찜 보기").assertIsDisplayed()
        capture("filtered-320.png")
        compose.onNodeWithContentDescription("목록 보기").performClick()
        compose.onNodeWithContentDescription("오후의 정원 찜 해제").assertIsDisplayed().assertWidthIsEqualTo(48.dp)
        capture("expanded-320.png")
    }
}
