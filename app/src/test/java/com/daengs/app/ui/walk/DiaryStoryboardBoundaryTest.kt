package com.daengs.app.ui.walk

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.diary.DiaryScene
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], qualifiers="w390dp-h844dp", application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryStoryboardBoundaryTest {
    @get:Rule val compose=createComposeRule()
    private val scenes=(1..3).map { DiaryScene("s/$it","s",it*30_000L,"기록 $it","장면 본문",null,"") }

    @Test fun `scene tab reads start scenes and end with session times and unchanged numbering`() {
        var selected: DiaryScene? = null
        var mounts=0
        compose.setContent { DaengsTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                WalkDiaryMapContent(scenes,null,false,null,{ selected=it },{},{},{},{},{},
                    walkStartedAtMillis=1_000,walkEndedAtMillis=300_000,
                    explorerPanel={ Text("걸어온 길 내용") },
                    map={ DisposableEffect(Unit) { mounts++; onDispose {} }; Box(Modifier.fillMaxSize()) })
            }
        } }
        val top=compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("장면 3").assertIsDisplayed()
        compose.onNodeWithTag("storyboard-start-time").assertTextEquals(formatRouteExplorerClock(1_000)).assertIsDisplayed()
        compose.onNodeWithTag("storyboard-start").assertHasNoClickAction()
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText("기록 1"))
        compose.onNodeWithText("기록 1").performClick()
        assertEquals(scenes.first(),selected)
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasTestTag("storyboard-end"))
        compose.onNodeWithText("산책 끝").assertIsDisplayed()
        compose.onNodeWithTag("storyboard-end-time").assertTextEquals(formatRouteExplorerClock(300_000)).assertIsDisplayed()
        assertEquals(top,compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top)
        assertEquals(1,mounts)
    }

    @Test fun `a walk without ordinary scenes still has both storyboard bookends`() {
        compose.setContent { DaengsTheme {
            WalkDiaryMapContent(emptyList(),null,false,null,{},{},{},{},{},{},
                walkStartedAtMillis=1_000,walkEndedAtMillis=300_000,
                explorerPanel={ Text("걸어온 길 내용") },map={ Box(Modifier.fillMaxSize()) })
        } }
        compose.onNodeWithText("장면 0").assertIsDisplayed()
        compose.onNodeWithText("산책 시작").assertIsDisplayed()
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasTestTag("storyboard-end"))
        compose.onNodeWithTag("storyboard-end-time").assertTextEquals(formatRouteExplorerClock(300_000)).assertIsDisplayed()
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText("기록 남기기"))
        compose.onNodeWithText("기록 남기기").assertHasClickAction()
    }

    @Test fun `saved scene position still resolves after the start row is inserted`() {
        lateinit var memory: DiaryReadingMemory
        val longStoryboard=(1..8).map { DiaryScene("s/$it","s",it*30_000L,"기록 $it","장면 본문",null,"") }
        compose.setContent { DaengsTheme {
            memory=rememberDiaryReadingMemory()
            var prepared by remember { mutableStateOf(false) }
            if (!prepared) {
                memory.pendingList=JSONObject().put("key","scene:s/2").put("offset",0)
                prepared=true
            }
            WalkDiaryMapContent(longStoryboard,null,false,null,{},{},{},{},{},{},
                walkStartedAtMillis=1_000,walkEndedAtMillis=300_000,readingMemory=memory,
                explorerPanel={ Text("걸어온 길 내용") },map={ Box(Modifier.fillMaxSize()) })
        } }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(memory.pendingList); assertEquals("scene:s/2",memory.list.layoutInfo.visibleItemsInfo.first().key) }
        compose.onNodeWithText("기록 2").assertIsDisplayed()
    }
}
