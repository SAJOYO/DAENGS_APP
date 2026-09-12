package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.walk.diary.DiaryScene
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryPlaceComparisonUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `switch retains selected scene and map geometry without requesting generation`() {
        var usePlaces by mutableStateOf(false)
        var mounts = 0
        var generations = 0
        var geometry: DiaryMapViewport? = null
        compose.setContent {
            val scene = DiaryScene("walk/note", "walk", 1000, "같은 장면",
                if (usePlaces) "주변 장소 설명\n\n메모 원문" else "메모 원문", null, "")
            WalkDiaryMapContent(listOf(scene), scene, false, null, {}, {}, {}, {}, {}, {},
                onGenerate = { generations++ },
                comparisonContent = { DiaryPlaceComparisonSwitch(usePlaces, { usePlaces = it }) },
                map = { viewport ->
                    DisposableEffect(Unit) { mounts++; onDispose {} }
                    SideEffect { geometry = viewport }
                    Box(Modifier.fillMaxSize().testTag("comparison-map"))
                })
        }
        compose.onNodeWithText("메모 원문").assertIsDisplayed()
        val before = geometry
        val bounds = compose.onNodeWithTag("comparison-map").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("장소 설명").performClick()
        compose.onNodeWithText("주변 장소 설명\n\n메모 원문").assertIsDisplayed()
        compose.onNodeWithText("같은 장면").assertIsDisplayed()
        compose.onNodeWithText("기본 설명").performClick()
        compose.onNodeWithText("메모 원문").assertIsDisplayed()
        assertEquals(1, mounts)
        assertEquals(0, generations)
        assertEquals(before, geometry)
        assertEquals(bounds, compose.onNodeWithTag("comparison-map").fetchSemanticsNode().boundsInRoot)
    }
}
