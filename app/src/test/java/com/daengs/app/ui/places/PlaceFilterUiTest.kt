package com.daengs.app.ui.places

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceFilterUiTest {
    @get:Rule val compose = createComposeRule()
    private fun state() = PlaceDiscoveryState(requestedKinds = listOf(PlaceKind.CAFE),
        origin = GeoPoint(37.5, 127.0), filterCapabilities = filterCapabilitiesFixture())

    @Test fun editorDistinguishesExplicitFalseFromClearAndAppliesBothAttributesTogether() {
        var applied: PlaceFilterCriteria? = null
        compose.setContent { DaengsTheme { PlaceFilterEditor(state(), true, { applied = it }, {}, {}) } }
        compose.onNodeWithText("불가").performScrollTo().performClick()
        compose.onNodeWithText("전용").performScrollTo().performClick()
        compose.onNodeWithText("조건 적용").performClick()
        assertEquals(2, applied!!.all.size)
        assertEquals(JsonPrimitive(false), applied!!.all.first { it.capability == "operations.parking" }.value)
        assertEquals(JsonPrimitive(true), applied!!.all.first { it.capability == "pet_access.exclusive" }.value)
        compose.runOnIdle {
            // PixelCopy waits forever on a separate dialog surface in Windows Robolectric.
            val view = WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File("build/filter-screenshots").mkdirs()
            File("build/filter-screenshots/editor.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun unsupportedServerDoesNotShowSelectableAttributes() {
        compose.setContent { DaengsTheme { PlaceFilterEditor(state().copy(filterCapabilities = null, filterError = "이 서버는 아직 조건 검색을 지원하지 않아요."), true, {}, {}, {}) } }
        compose.onNodeWithText("조건 적용").assertIsNotEnabled()
        compose.onNodeWithText("불가").assertDoesNotExist()
    }

    @Test fun emptyAlternativeMustBeFilledOrRemoved() {
        compose.setContent { DaengsTheme { PlaceFilterEditor(state(), true, {}, {}, {}) } }
        compose.onNodeWithText("대안 묶음 추가").performScrollTo().performClick()
        compose.onNodeWithText("조건 적용").assertIsNotEnabled()
        compose.onNodeWithText("묶음 삭제").performScrollTo().performClick()
        compose.onNodeWithText("조건 적용").assertIsEnabled()
    }

    @Test fun uncertainResultsAreOptInAndNeverCountedAsConfirmed() {
        val response = filterResponseFixture(filterRequestFixture(), withHits = true)
        val ui = PlacesUiState(location = PlaceLocationState.Ready(GeoPoint(37.5, 127.0)),
            discovery = state().copy(filters = response.request.criteria, filterResponse = response,
                search = PlaceSearchState.Content(response.results())),
            profiles = PlaceProfiles())
        // Projection order is asserted independently of the profile loading guard.
        assertEquals("먼 주차 카페", ui.visibleDiscovery().response!!.groups.single().results.single().place.name)
        assertEquals("가까운 정보 미상 카페", ui.visibleDiscovery(true).response!!.groups.single().results.single().place.name)
        assertEquals(response.request.criteria, ui.visibleDiscovery(true).filters)
    }

    @Test fun connectedScreenSwitchesBetweenBucketsAndKeepsTheAppliedConditionsVisible() {
        val base = filterRequestFixture()
        val request = PlaceFilterRequest(base.request.copy(dogs = emptyList()), base.criteria, 7, "screen-7")
        val response = filterResponseFixture(request, withHits = true)
        val ui = PlacesUiState(location = PlaceLocationState.Ready(GeoPoint(37.5, 127.0)),
            discovery = state().copy(filters = request.criteria, filterResponse = response,
                search = PlaceSearchState.Content(response.results())))
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(ui, {}, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText("조건 충족 1곳").assertExists()
        compose.onNodeWithText("먼 주차 카페").assertExists()
        compose.onNodeWithText("가까운 정보 미상 카페").assertDoesNotExist()
        compose.onNodeWithText("확인 필요").performClick()
        compose.onNodeWithText("확인 필요 1곳").assertExists()
        compose.onNodeWithText("가까운 정보 미상 카페").assertExists()
        compose.onNodeWithText("먼 주차 카페").assertDoesNotExist()
        compose.onNodeWithText("적용 조건", substring = true).assertExists()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File("build/filter-screenshots").mkdirs()
            File("build/filter-screenshots/results.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("반경 3km ▾").performClick()
        compose.onNodeWithText("현재 적용 조건:", substring = true).assertExists()
        compose.onNodeWithText("주차는 필수 조건이 아닌 우선 정렬입니다.", substring = true).assertDoesNotExist()
    }
}
