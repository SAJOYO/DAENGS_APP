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
class PlaceFilterAiUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun protectedEditShowsBeforeAfterAndExplicitApply() {
        var confirmed = 0
        val response = filterEditFixture(confirmation = true)
        compose.setContent { DaengsTheme { PlaceFilterAiPanel(PlaceFilterAiState(enabled = true, response = response), { confirmed++ }, {}) } }
        compose.onNodeWithText("현재:", substring = true).assertExists()
        compose.onNodeWithText("변경 후:", substring = true).assertExists()
        compose.onNodeWithText("이 변경 적용").performScrollTo().performClick()
        assertEquals(1, confirmed)
    }

    @Test fun aiModeKeepsAppliedResultsAndManualEditorAvailableWhenUnsupported() {
        val base = filterRequestFixture()
        val request = PlaceFilterRequest(base.request.copy(dogs = emptyList()), base.criteria, 7)
        val response = filterResponseFixture(request, withHits = true)
        val state = PlacesUiState(location = PlaceLocationState.Ready(GeoPoint(37.5, 127.0)),
            discovery = PlaceDiscoveryState(requestedKinds = request.criteria.kinds, origin = request.request.origin,
                filters = request.criteria, filterResponse = response, search = PlaceSearchState.Content(response.results()), filterCapabilities = filterCapabilitiesFixture()),
            filterAiAvailable = true, filterAi = PlaceFilterAiState(enabled = true, response = filterEditFixture(PlaceFilterEditQuery("조용한 곳", request), unresolved = true)))
        val actions = mutableListOf<PlacesAction>()
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state, { actions += it }, {}, {}, {}, {}, {}, showMap = false) } }
        compose.onNodeWithText("지원하지 않는 조건이에요.").assertExists()
        compose.onNodeWithText("먼 주차 카페").assertExists()
        compose.onNodeWithText("이 변경 적용").assertDoesNotExist()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/filter-screenshots").mkdirs()
            File("build/filter-screenshots/ai-unsupported.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("적용 조건", substring = true).performClick()
        compose.onNodeWithText("장소 조건").assertExists()
        assertTrue(actions.contains(PlacesAction.LoadFilterCapabilities))
    }

    @Test fun everyExcludedPurposeConditionCanBeRemovedIndependently() {
        val criteria = PlaceFilterCriteria(listOf(PlaceKind.CAFE, PlaceKind.RESTAURANT), all = listOf(
            PlaceFilterAtom("exclude", "purpose.kind", "not_in", JsonArray(listOf(JsonPrimitive("cafe")))),
            PlaceFilterAtom("parking", "operations.parking", "eq", JsonPrimitive(true))))
        var applied: PlaceFilterCriteria? = null
        compose.setContent { DaengsTheme { PlaceFilterEditor(PlaceDiscoveryState(filters = criteria, requestedKinds = criteria.kinds, filterCapabilities = filterCapabilitiesFixture()), true, { applied = it }, {}, {}) } }
        compose.onNodeWithText("카페 제외").performScrollTo().assertExists()
        compose.onNodeWithText("업종 조건 삭제").performScrollTo().performClick()
        compose.onNodeWithText("조건 적용").performClick()
        assertEquals(listOf("parking"), applied!!.all.map { it.id })
    }
}
