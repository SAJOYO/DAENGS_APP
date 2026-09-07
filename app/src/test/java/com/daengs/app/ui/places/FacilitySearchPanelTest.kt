package com.daengs.app.ui.places

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.place.*
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FacilitySearchPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun proxyTagIsSelectableButUnavailablePriceIsNot() {
        val choices = mutableListOf<FacilityChoice>()
        val response = facilityResponse().copy(lenses = emptyList(), signals = listOf(
            FacilitySignal("cost", "비용", "needs_selection", true, "가격 정보가 없어요.", listOf(
                FacilityOption("near", "가까운 곳", "proxy", "실제 가격이 아니라 거리 기준이에요."),
                FacilityOption("price", "상품 가격", "unavailable", "상품 가격은 지원하지 않아요.")), null)))
        compose.setContent { DaengsTheme { FacilitySearchPanel(FacilityUiState(enabled = true, response = response), choices::add, {}) } }
        compose.onNodeWithText("#상품 가격").assertIsNotEnabled()
        compose.onNodeWithText("#가까운 곳").performClick()
        assertEquals(listOf(FacilityChoice.Refine("cost", "near")), choices)
        compose.onNodeWithText("가까운 곳: 실제 가격이 아니라 거리 기준이에요.").assertExists()
    }

    @Test fun confirmationDispatchesServerLensIdAndLoadingDisablesIt() {
        val choices = mutableListOf<FacilityChoice>()
        val loading = androidx.compose.runtime.mutableStateOf(false)
        val response = facilityResponse()
        val label = "이 방향으로 검색 · ${response.lenses.single().search.overviewHits(true).size}곳"
        compose.setContent { DaengsTheme { FacilitySearchPanel(FacilityUiState(enabled = true, loading = loading.value, response = response), choices::add, {}) } }
        compose.onNodeWithText(label).performScrollTo().performClick()
        assertEquals(listOf(FacilityChoice.Confirm("lens:cafe")), choices)
        compose.runOnIdle { loading.value = true }
        compose.onNodeWithText(label).assertIsNotEnabled()
    }
}
