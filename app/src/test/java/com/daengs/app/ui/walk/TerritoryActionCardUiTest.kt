package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.daengs.app.map.features.territory.*
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h844dp")
class TerritoryActionCardUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun `map preparation dismisses the card and restores pet selection without starting a walk`() {
        val game = cardGame()
        val state = mutableStateOf(WalkUiState(
            map = WalkMapUiState(purpose = com.daengs.app.map.shell.MapPurpose.TERRITORY),
            territory = TerritoryBoardState(sites = listOf(game.target!!.site), selectedSiteId = "A"),
            territoryGame = game))
        val actions = mutableListOf<WalkAction>()
        compose.setContent { DaengsTheme { WalkScreen(state.value, onAction = {
            actions += it
            if (it == WalkAction.ClearTerritory) state.value = state.value.copy(
                territory = state.value.territory.copy(selectedSiteId = null))
        }, showMap = false) } }
        compose.onNodeWithText("산책을 시작할까요?").assertDoesNotExist()
        compose.onNodeWithText("산책 준비하기").performScrollTo().performClick()
        compose.onNodeWithText("산책을 시작할까요?").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(actions.contains(WalkAction.ClearTerritory))
            assertFalse(actions.contains(WalkAction.StartRequested))
        }
    }

    @Test fun `browsing leads to preparation without submitting a claim`() {
        var prepared = false
        compose.setContent { DaengsTheme { TerritoryActionCard(cardGame(),
            onMark = { error("Must not claim") }, onPrepareWalk = { prepared = true }) } }
        compose.onNodeWithText("아직 주인이 없어요").assertIsDisplayed()
        compose.onNodeWithText("산책 준비하기").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(prepared) }
    }
    @Test fun `photo and ordinary actions keep distinct site callbacks`() {
        var photo: String? = null
        var mark: String? = null
        compose.setContent { DaengsTheme { TerritoryActionCard(cardGame().copy(
            phase = TerritoryWalkPhase.WALKING, canMark = true, canPhotograph = true),
            onMark = { mark = it }, onPhotograph = { photo = it }) } }
        compose.onNodeWithText("강아지 인증하고 점령").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("A", photo); assertNull(mark) }
        compose.onNodeWithText("사진 없이 일반 점령").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("A", mark) }
    }
    @Test fun `read only cannot expose write buttons even with stale capabilities`() {
        compose.setContent { DaengsTheme { TerritoryActionCard(cardGame().copy(
            phase = TerritoryWalkPhase.WALKING, readOnly = true, canMark = true, canPhotograph = true,
            guidance = "게임을 준비하고 있어요"), {}) } }
        compose.onNodeWithText("게임을 준비하고 있어요").assertIsDisplayed()
        compose.onNodeWithText("강아지 인증하고 점령").assertDoesNotExist()
        compose.onNodeWithText("사진 없이 일반 점령").assertDoesNotExist()
    }
    @Test fun `short card remains scrollable and preserves pending status`() {
        compose.setContent { DaengsTheme { TerritoryActionCard(cardGame(occupied = true).copy(
            phase = TerritoryWalkPhase.WALKING, guidance = "사진 확인 중 · 산책을 계속해도 돼요"), {},
            modifier = Modifier.width(280.dp).heightIn(max = 240.dp)) } }
        compose.onNodeWithText("사진 확인 중 · 산책을 계속해도 돼요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("강아지 인증하고 도전").assertDoesNotExist()
        compose.onNodeWithText("점령 정보 자세히").performScrollTo().performClick()
        compose.onNodeWithText("사진 인증은 현재 위치와 강아지를 확인해요. 전봇대를 사진에 담을 필요는 없어요.").assertIsDisplayed()
    }
}
