package com.daengs.app.ui.places

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h720dp")
class PlaceDogAssistantUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inputThinkingAndLatestReplyUseOneAnchorWithoutResizingMap() {
        val busy = mutableStateOf(false)
        val available = mutableStateOf(false)
        val open = mutableStateOf(false)
        val queries = mutableListOf<String>()
        compose.setContent { DaengsTheme {
            PlaceMapControls(false, {}, {}) {
                PlaceDogAssistant(busy.value, available.value, open.value, { open.value = it },
                    { queries += it; busy.value = true }, { busy.value = false }) { Text("서버가 돌려준 답변") }
            }
        } }
        val before = compose.onNodeWithTag("place-dog-anchor").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("도우미견").assertIsDisplayed()
        compose.onNodeWithTag("place-dog-anchor").assertWidthIsAtLeast(60.dp).assertHeightIsAtLeast(68.dp)
        compose.onNodeWithContentDescription("내 주변 검색").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("place-dog-anchor").performClick()
        compose.onNodeWithTag("place-dog-input").performTextInput("카페 찾아줘")
        assertTrue(queries.isEmpty())
        compose.onNodeWithText("말해주기").performScrollTo().performClick()
        assertEquals(listOf("카페 찾아줘"), queries)
        compose.onNodeWithTag("place-dog-thinking").assertExists()
        compose.onNodeWithTag("place-dog-input").assertDoesNotExist()
        compose.runOnIdle { available.value = true; busy.value = false }
        compose.onNodeWithText("서버가 돌려준 답변").assertExists()
        assertEquals(before, compose.onNodeWithTag("place-dog-anchor").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("다시 말하기").performScrollTo().performClick()
        compose.onNodeWithTag("place-dog-input").assertExists()
    }

    @Test fun nameTagAndPortraitOpenTheSameConversation() {
        val open = mutableStateOf(false)
        compose.setContent { DaengsTheme {
            PlaceMapControls(false, {}, {}) {
                PlaceDogAssistant(false, false, open.value, { open.value = it }, {}, {}) {}
            }
        } }
        // 실제 터치로 아래 인식표도 버튼 bounds 안에 포함되는지 확인한다.
        compose.onNodeWithTag("place-dog-anchor").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(center.x, height - 2f))
        }
        compose.onNodeWithTag("place-dog-input").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("place-dog-input").assertDoesNotExist()
        compose.onNodeWithTag("place-dog-anchor").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(center.x, 16f))
        }
        compose.onNodeWithTag("place-dog-input").assertIsDisplayed()
    }
}
