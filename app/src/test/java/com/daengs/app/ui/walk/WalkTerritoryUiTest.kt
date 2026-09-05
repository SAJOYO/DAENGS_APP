package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.territory.*
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.territory.*
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class WalkTerritoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val site = TerritorySite("A", GeoPoint(37.5,127.0),0.0)
    private fun game(phase: TerritoryWalkPhase) = TerritoryGameState(
        enabled = true, phase = phase, targetId = "A", representativeLabel = "보리",
        guidance = "점령 준비 · 영역표시할 수 있어요",
        claimingPetId = "p1", eligiblePets = linkedMapOf("p1" to "보리", "p2" to "두부"),
        canMark = phase == TerritoryWalkPhase.WALKING, canPhotograph = phase == TerritoryWalkPhase.WALKING,
        sites = listOf(TerritoryGameSite(site, TerritoryClaimSite("A"), "", null, 5.0,false)),
    )
    private fun screen(phase: TerritoryWalkPhase) = WalkUiState(
        map = WalkMapUiState(purpose = MapPurpose.TERRITORY),
        location = WalkLocationUiState(permissionGranted = true, precisePermission = true),
        territory = TerritoryBoardState(sites = listOf(site), selectedSiteId = "A"), territoryGame = game(phase),
        tracking = WalkTrackingState(activeSessionId = if (phase == TerritoryWalkPhase.BROWSING) null else "walk",
            activeDogIds = listOf("p1", "p2"), trail = TrailSnapshot(state = when (phase) {
                TerritoryWalkPhase.BROWSING -> TrackingState.OFF
                TerritoryWalkPhase.WALKING -> TrackingState.RECORDING
                TerritoryWalkPhase.PAUSED -> TrackingState.PAUSED
            })),
    )

    private fun screenshot(name: String) {
        val output = java.io.File("build/reports/walk-territory/$name.png")
        checkNotNull(output.parentFile).mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun browsingThenWalkingThenPausedOnlyExposesEligibleActions() {
        val state = mutableStateOf(screen(TerritoryWalkPhase.BROWSING))
        val actions = mutableListOf<WalkAction>()
        compose.setContent { DaengsTheme { WalkScreen(state.value, actions::add, showMap = false) } }
        compose.onNodeWithText("미점유").assertExists()
        compose.onNodeWithText("영역표시할 강아지").assertDoesNotExist()
        compose.onNodeWithContentDescription("영역표시 인증 촬영").assertDoesNotExist()
        compose.onNodeWithText("산책 시작").assertExists()
        screenshot("browsing")
        compose.runOnIdle { state.value = screen(TerritoryWalkPhase.WALKING) }
        compose.onNodeWithText("영역표시할 강아지").assertExists()
        screenshot("walking")
        compose.onNodeWithText("보리").performClick()
        compose.onNodeWithText("두부").performClick()
        assertEquals(WalkAction.SelectClaimingPet("A","p2"), actions.last())
        compose.onNodeWithContentDescription("산책 기록 목록").performClick()
        assertEquals(WalkAction.OpenEntries, actions.last())
        compose.runOnIdle { state.value = screen(TerritoryWalkPhase.PAUSED) }
        compose.onNodeWithText("지도 둘러보기").performClick()
        compose.onNodeWithText("산책을 재개하면 영역표시할 수 있어요").assertIsDisplayed()
        compose.onNodeWithText("영역표시할 강아지").assertDoesNotExist()
        screenshot("paused")
        compose.onNodeWithContentDescription("산책 재개 메뉴").performClick()
        compose.onNodeWithText("지도 둘러보기").assertIsDisplayed()
    }

    @Test fun compactPhotoSummaryOpensExistingRetry() {
        var retried: String? = null
        compose.setContent { DaengsTheme { TerritoryPhotoStatus(listOf(
            TerritoryPhotoJob("attempt", "A", "capture", java.io.File("sample.jpg"), ClaimPhotoStatus.RETRY_PENDING)
        ), { retried = it }) } }
        compose.onNodeWithText("판정 재시도 (페이크 성공)").assertDoesNotExist()
        compose.onNodeWithText("사진 인증 연습 · 결과 보기 ›").performClick()
        compose.onNodeWithText("판정 재시도 (페이크 성공)").performClick()
        assertEquals("attempt", retried)
    }

    @Test fun closingSelectionAndTogglingDisplayHaveSeparateActions() {
        val actions = mutableListOf<WalkAction>()
        compose.setContent { DaengsTheme { WalkScreen(screen(TerritoryWalkPhase.BROWSING), actions::add, showMap = false) } }
        compose.onNodeWithContentDescription("점령지 선택 닫기").performClick()
        assertEquals(WalkAction.ClearTerritory, actions.last())
        compose.onNodeWithContentDescription("점령지 숨기기").performClick()
        assertEquals(WalkAction.ChangeMapPurpose(MapPurpose.WALK), actions.last())
        compose.onNodeWithContentDescription("GPS 불안정").performClick()
        compose.onNodeWithText("정확한 새 위치를 기다리고 있어요").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h844dp")
    fun narrowScreenKeepsClaimAndWalkControlsSeparate() {
        compose.setContent { DaengsTheme { WalkScreen(screen(TerritoryWalkPhase.WALKING), {}, showMap = false) } }
        val claim = compose.onNodeWithText("영역표시").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithContentDescription("행동 기록").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(claim.bottom <= dock.top)
        compose.onNodeWithContentDescription("GPS 불안정").assertIsDisplayed()
        screenshot("narrow")
        compose.onNodeWithContentDescription("영역표시 인증 촬영").assertIsDisplayed()
        compose.onNodeWithContentDescription("내 위치").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w844dp-h390dp")
    fun landscapeSeparatesCardFromDock() {
        compose.setContent { DaengsTheme { WalkScreen(screen(TerritoryWalkPhase.WALKING), {}, showMap = false) } }
        val card = compose.onNodeWithText("영역표시").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithContentDescription("행동 기록").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(card.right <= dock.left)
        screenshot("landscape")
    }
}
