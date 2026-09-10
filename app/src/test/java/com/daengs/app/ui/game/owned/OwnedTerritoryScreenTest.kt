package com.daengs.app.ui.game.owned

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.game.previewGamePet
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
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class OwnedTerritoryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val site = previewOwnedSite()
    private val ready = OwnedBrowserState(OwnedBrowserStatus.READY, listOf(site), 1, "first", serverNowMillis = 1_800_000_000_000)

    @Test fun `list selection opens exact map position with the dogs profile and back returns`() {
        var back = 0
        var map: OwnedMapPresentation? = null
        val photos = mutableSetOf<String>()
        compose.setContent { DaengsTheme {
            OwnedTerritoryScreen(ready, listOf(previewGamePet(site.petId)), null,
                { photos += it; ImageBitmap(8, 8) }, 0, { back++ }, {}, {}, {}, {},
                mapSurface = { value, select, _ ->
                    map = value
                    TextButton(onClick = { select(site.siteId) }) { Text("지도 전봇대") }
                })
        } }
        compose.onNodeWithTag("owned-list-tab").performClick()
        compose.onNodeWithText("두부의 점령지").assertIsDisplayed()
        compose.onNodeWithText("지도에서 보기").performClick()
        compose.onNodeWithTag("owned-map").assertIsDisplayed()
        compose.runOnIdle { assertEquals(site.point, map!!.centerOn); assertTrue(site.petId in photos) }
        compose.onNodeWithText("사진 인증 완료").assertIsDisplayed()
        compose.onNodeWithText(site.siteId).assertDoesNotExist()
        compose.onNodeWithText("산책 시작").assertDoesNotExist()
        compose.onNodeWithTag("owned-back").performClick()
        assertEquals(1, back)
    }

    @Test fun `errors no season and zero holdings remain different`() {
        var state by mutableStateOf(OwnedBrowserState(OwnedBrowserStatus.ERROR, message = "조회 실패"))
        compose.setContent { DaengsTheme { OwnedTerritoryScreen(state, emptyList(), null, { null }, 0, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("조회 실패").assertIsDisplayed()
        compose.onNodeWithText("조회 기준 0곳 보유").assertDoesNotExist()
        compose.runOnIdle { state = OwnedBrowserState(OwnedBrowserStatus.NO_SEASON, total = 0) }
        compose.onNodeWithText("아직 진행 중인 시즌이 없어요.").assertIsDisplayed()
        compose.runOnIdle { state = ready.copy(items = emptyList(), total = 0) }
        compose.onNodeWithText("현재 보유한 점령지가 없어요.").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun `large text keeps pagination reachable and missing locations cannot open map`() {
        var more = 0
        val unknown = ready.copy(items = listOf(site.copy(point = null)), total = 80, nextCursor = "next")
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
            DaengsTheme { OwnedTerritoryScreen(unknown, emptyList(), null, { null }, 0, {}, {}, {}, {}, { more++ }) }
        } }
        compose.onNodeWithTag("owned-list-tab").performClick()
        compose.onNodeWithText("위치 정보 없음").assertIsDisplayed()
        compose.onNodeWithText("지도에서 보기").assertDoesNotExist()
        compose.onNodeWithTag("owned-load-more").assertIsDisplayed().performClick()
        assertEquals(1, more)
        val list = compose.onNodeWithTag("owned-list").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("owned-load-more").fetchSemanticsNode().boundsInRoot
        assertTrue(list.bottom <= button.top)
    }

    @Test fun `selected site and list map mode survive recreation`() {
        val restore = StateRestorationTester(compose)
        var selected: String? = null
        restore.setContent { DaengsTheme {
            OwnedTerritoryScreen(ready, emptyList(), null, { null }, 0, {}, {}, {}, {}, {},
                mapSurface = { value, choose, _ ->
                    selected = value.scene.territorySites.firstOrNull { it.selected }?.id
                    TextButton(onClick = { choose(site.siteId) }, modifier = Modifier.testTag("test-pole")) { Text("전봇대") }
                })
        } }
        compose.onNodeWithTag("test-pole").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(site.siteId, selected) }
        compose.onNodeWithTag("owned-list-tab").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("owned-list").assertIsDisplayed()
    }
}
