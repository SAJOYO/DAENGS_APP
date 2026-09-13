package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.diary.DiaryScene
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiarySceneRemovalUiTest {
    @get:Rule val compose = createComposeRule()
    private val first = DiaryScene("s/first", "s", 0, "함께 쉬어 간 길", "잠깐 쉬었어요.", null, "")
    private val second = first.copy(id = "s/second", title = "집으로 가는 길")

    @Test fun `list removal targets its row without selecting it or moving the drawer`() {
        var requested: DiaryScene? = null
        val scenes = mutableStateOf(listOf(first, second))
        compose.setContent { DaengsTheme {
            WalkDiaryMapContent(scenes.value, null, false, null,
                { fail("removal must not select the row") }, {}, {}, {}, {}, {},
                onDelete = { requested = it }, explorerPanel = {},
                map = { Box(Modifier.fillMaxSize()) })
        } }
        val top = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithContentDescription("장면 1 삭제").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(first, requested) }
        // Requesting confirmation leaves both scenes intact. Only an adopted successful write removes it.
        compose.onNodeWithText(first.title).assertExists()
        compose.runOnIdle { scenes.value = listOf(second) }
        compose.onNodeWithText(first.title).assertDoesNotExist()
        compose.onNodeWithText(second.title).assertExists()
        compose.onNodeWithContentDescription("장면 1 삭제").assertExists()
        compose.onNodeWithContentDescription("장면 2 삭제").assertDoesNotExist()
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun `opened scene offers removal while preserving its body and edit access`() {
        var requested: DiaryScene? = null
        compose.setContent { DaengsTheme {
            WalkDiaryMapContent(listOf(first, second), first, false, null, {}, {}, {}, {}, {}, {},
                onDelete = { requested = it }, explorerPanel = {},
                map = { Box(Modifier.fillMaxSize()) })
        } }
        compose.onNodeWithContentDescription("장면 삭제").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(first, requested) }
        compose.onNodeWithText(first.body).assertExists()
        compose.onNodeWithContentDescription("장면 수정").assertExists()
    }
}
