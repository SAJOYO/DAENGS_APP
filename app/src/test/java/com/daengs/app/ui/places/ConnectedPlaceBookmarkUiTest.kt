package com.daengs.app.ui.places

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.auth.*
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.*
import com.daengs.app.place.bookmarks.*
import com.daengs.app.ui.places.lab.PlaceBookmarkLabModel
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectedPlaceBookmarkUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val hits = PlaceBookmarkLabModel().catalog
    private val missing = PlaceKey("kto", "missing")
    private var page = SavedPlacePage(listOf(SavedPlace(hits.first().place.key, hits.first().place.name, Instant.EPOCH),
        SavedPlace(missing, "사라진 시설", Instant.EPOCH)), 200)
    private val client = object : PlaceBookmarkClient {
        override suspend fun list(token: String) = page
        override suspend fun set(token: String, key: PlaceKey, saved: Boolean): SavedPlacePage {
            page = page.copy(items = page.items.filterNot { it.key == key }); return page
        }
        override suspend fun search(token: String, filters: JsonObject) = SavedPlaceResults(page,
            hits.filter { hit -> page.items.any { it.key == hit.place.key } },
            setOf(missing).filter { key -> page.items.any { it.key == key } }.toSet(), filters["lat"] != JsonNull)
    }
    @Test fun `connected saved tab removes missing source and preserves normal draft`() {
        lateinit var controller: PlaceBookmarkController
        compose.setContent {
            val scope = rememberCoroutineScope()
            controller = remember { PlaceBookmarkController(scope, PlaceBookmarkRepository(client,
                { Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE) }, { AccountScope("owner", 1) }), AccountScope("owner", 1)) }
            LaunchedEffect(Unit) { controller.ensureLoaded() }
            DaengsTheme { ConnectedPlaceSearchScreen(
                PlacesUiState(location = PlaceLocationState.Ready(GeoPoint(37.54, 127.05)),
                    discovery = com.daengs.app.map.features.places.PlaceDiscoveryState(requestedKinds = listOf(PlaceKind.CAFE), origin = GeoPoint(37.54, 127.05)),
                    profiles = PlaceProfiles(ready = true, message = null)),
                {}, {}, {}, {}, {}, {}, showMap = false, bookmarkController = controller) }
        }
        compose.onNode(hasSetTextAction()).performTextInput("보존할 입력")
        compose.onNodeWithTag("place-tab-BOOKMARKS").performClick()
        compose.onNodeWithText("전체 찜 보기").performClick()
        compose.onNodeWithText("지역 전체 ▾").assertExists()
        compose.onNodeWithContentDescription("목록 보기").performClick()
        compose.onNodeWithText("사라진 시설").assertExists()
        compose.onNodeWithText("찜 해제").performClick()
        compose.onNodeWithText("사라진 시설").assertDoesNotExist()
        compose.onNodeWithText("거리 미확인", substring = true).assertExists()
        capture()
        compose.onNodeWithTag("place-tab-SEARCH").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("보존할 입력")
        compose.runOnIdle { controller.close() }
    }
    private fun capture() {
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().first { it.width > 0 && it.height > 400 }
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val directory = java.io.File("build/reports/place-bookmarks").apply { mkdirs() }
            java.io.File(directory, "connected-saved-390.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
