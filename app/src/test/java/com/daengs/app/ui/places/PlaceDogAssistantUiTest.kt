package com.daengs.app.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
            Box(Modifier.fillMaxSize()) {
                PlaceDogAssistant(Offset(160f, 300f), busy.value, available.value, open.value, { open.value = it },
                    { queries += it; busy.value = true }, { busy.value = false }) { Text("서버가 돌려준 답변") }
            }
        } }
        val before = compose.onNodeWithTag("place-dog-anchor").fetchSemanticsNode().boundsInRoot
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
}
