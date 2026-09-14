package com.daengs.app.ui.places

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
                    { queries += it; busy.value = true }, { busy.value = false },
                    searchContext = "현재 검색 지역 · 반경 3km") { Text("서버가 돌려준 답변") }
            }
        } }
        val before = compose.onNodeWithTag("place-dog-anchor").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("도우미견").assertIsDisplayed()
        compose.onNodeWithTag("place-dog-anchor").assertWidthIsAtLeast(60.dp).assertHeightIsAtLeast(48.dp)
        val portrait = compose.onNodeWithTag("place-dog-portrait", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val nameTag = compose.onNodeWithTag("place-dog-name-tag", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("인식표가 원 하단에 겹쳐 목에 달려 보여야 한다", nameTag.top < portrait.bottom)
        assertTrue("인식표가 얼굴을 답답하게 덮지 않아야 한다",
            portrait.bottom - nameTag.top <= portrait.height / 6f)
        assertTrue("인식표는 원 아래까지 이어져야 한다", nameTag.bottom > portrait.bottom)
        compose.onNodeWithContentDescription("내 주변 검색").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val location = compose.onNodeWithContentDescription("내 주변 검색")
            .fetchSemanticsNode().boundsInRoot
        assertEquals("두 원의 중심 높이가 같아야 한다", location.center.y, portrait.center.y, .5f)
        compose.onNodeWithTag("place-dog-anchor").performClick()
        val fixedTags = listOf("place-dog-bubble", "place-dog-speech", "place-dog-metadata", "place-dog-footer")
        val inputBounds = fixedTags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInWindow }
        val range = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("place-dog-search-context").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(range) }
        assertEquals("검색 범위는 스크롤 없이 한 줄로 보여야 한다", 1, range.single().lineCount)
        assertFalse(range.single().hasVisualOverflow)
        compose.onNodeWithContentDescription("말해주기").assertIsNotEnabled()
        compose.onNodeWithTag("place-dog-input").performTextInput("카페 찾아줘")
        assertTrue(queries.isEmpty())
        compose.onNodeWithContentDescription("말해주기").performClick()
        assertEquals(listOf("카페 찾아줘"), queries)
        compose.onNodeWithTag("place-dog-thinking").assertExists()
        assertEquals(inputBounds, fixedTags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInWindow })
        compose.onNodeWithTag("place-dog-input").assertDoesNotExist()
        compose.runOnIdle { available.value = true; busy.value = false }
        compose.onNodeWithText("서버가 돌려준 답변").assertExists()
        assertEquals(inputBounds, fixedTags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInWindow })
        assertEquals(before, compose.onNodeWithTag("place-dog-anchor").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("다시 말 걸기").performClick()
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
        compose.onNodeWithContentDescription("닫기").performClick()
        compose.onNodeWithTag("place-dog-input").assertDoesNotExist()
        compose.onNodeWithTag("place-dog-anchor").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(center.x, 16f))
        }
        compose.onNodeWithTag("place-dog-input").assertIsDisplayed()
    }

    @Test fun largerNameTagDoesNotMisalignTheTwoCircles() {
        compose.setContent { DaengsTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                PlaceMapControls(false, {}, {}) {
                    PlaceDogAssistant(false, false, false, {}, {}, {}) {}
                }
            }
        } }
        val portrait = compose.onNodeWithTag("place-dog-portrait", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val location = compose.onNodeWithContentDescription("내 주변 검색")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(location.center.y, portrait.center.y, .5f)
        compose.onNodeWithText("도우미견").assertIsDisplayed()
    }

    @Test fun largeFontAndLongReplyKeepCloseAndComposeReachable() {
        val available = mutableStateOf(false)
        val answer = "강아지와 갈 수 있는 카페를 찾았어. ".repeat(12)
        compose.setContent { DaengsTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                PlaceMapControls(false, {}, {}) {
                    PlaceDogAssistant(false, available.value, true, {}, { available.value = true }, {},
                        searchContext = "현재 검색 지역 · 반경 3km") {
                        DogDialogueText(answer)
                    }
                }
            }
        } }
        val before = compose.onNodeWithTag("place-dog-bubble").fetchSemanticsNode().boundsInWindow
        val range = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("place-dog-search-context").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(range) }
        assertFalse("큰 글씨에서도 검색 범위를 자르지 않는다", range.single().hasVisualOverflow)
        compose.onNodeWithTag("place-dog-input").performTextInput("카페")
        compose.onNodeWithContentDescription("말해주기").performClick()
        compose.onNodeWithText(answer).assertExists()
        compose.onNodeWithContentDescription("닫기").assertIsDisplayed()
        compose.onNodeWithText("다시 말 걸기").assertIsDisplayed().performClick()
        compose.onNodeWithTag("place-dog-input").assertTextContains("카페")
        assertEquals(before, compose.onNodeWithTag("place-dog-bubble").fetchSemanticsNode().boundsInWindow)
    }

    @Test fun cancellationDoesNotSubmitAgainAndInputSurvivesReopen() {
        val busy = mutableStateOf(false)
        val open = mutableStateOf(true)
        var submitted = 0
        var cancelled = 0
        compose.setContent { DaengsTheme {
            PlaceMapControls(false, {}, {}) {
                PlaceDogAssistant(busy.value, false, open.value, { open.value = it },
                    { submitted++; busy.value = true }, { cancelled++; busy.value = false }) {}
            }
        } }
        compose.onNodeWithTag("place-dog-input").performTextInput("주차 카페")
        compose.onNodeWithContentDescription("말해주기").performClick()
        compose.onNodeWithText("대기 그만").performClick()
        assertEquals(1, cancelled)
        assertEquals(1, submitted)
        compose.onNodeWithTag("place-dog-bubble").assertDoesNotExist()
        compose.onNodeWithTag("place-dog-anchor").performClick()
        compose.onNodeWithTag("place-dog-input").assertTextContains("주차 카페")
    }
}
