package com.daengs.app.ui.places

import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.map.features.places.PlaceDiscoveryState
import com.daengs.app.place.*
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FacilityConnectedUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun onlyConfirmedResultsBecomeCardsAndMarkers() {
        val result = facilityResponse()
        val state = mutableStateOf(PlacesUiState(location = PlaceLocationState.Ready(result.request.origin),
            discovery = PlaceDiscoveryState(requestedKinds = PlacePurpose.DINING.kinds, origin = result.request.origin),
            profiles = PlaceProfiles(ownerId = "fixture", ready = true, message = null),
            facility = FacilityUiState(enabled = true, response = result)))
        val actionLabel = "이 방향으로 검색 · ${result.lenses.single().search.overviewHits(true).size}곳"
        compose.setContent { DaengsTheme { ConnectedPlaceSearchScreen(state.value, { action ->
            if (action is PlacesAction.ChooseAi) {
                assertEquals(FacilityChoice.Confirm("lens:cafe"), action.choice)
                state.value = state.value.copy(facility = state.value.facility.copy(response = result.copy(confirmedLensId = "lens:cafe", revision = 2)))
            }
        }, {}, {}, {}, {}, {}, showMap = false) } }
        val name = result.lenses.single().search.overviewHits(true).first().place.name
        compose.onNodeWithText(name).assertDoesNotExist()
        compose.onNodeWithContentDescription("강아지에게 검색 조건 말하기").performClick()
        capture("nearby-ai-interpretation.png")
        compose.onNodeWithText(actionLabel).performScrollTo().performClick()
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText("#카페 · 적용한 검색 방향").assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("조건 ▾").performClick()
        compose.onNodeWithText("검색 방향 · 카페").performClick()
        compose.onNodeWithText("#카페 · 적용한 검색 방향").assertExists()
        capture("nearby-ai-confirmed.png")
    }

    private fun capture(name: String) {
        val directory = System.getenv("DAENGS_UI_CAPTURE_DIR") ?: return
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File(directory, name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
