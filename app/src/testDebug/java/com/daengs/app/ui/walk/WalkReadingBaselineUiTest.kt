package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.map.layers.completedroute.RecordRouteRole
import com.daengs.app.ui.walk.review.recordContextMapFixture
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** User-approved reading baseline. Real reader/presentation/controls; native map is a test surface. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkReadingBaselineUiTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = recordContextMapFixture()
    private var input: DiaryReviewMap? = null
    private var mounts = 0

    @Composable private fun Reading() {
        WalkRouteReviewContent(fixture.detail, fixture.scenes, "열람 기준 검증", renderMap = { value ->
            SideEffect { input = value }
            DisposableEffect(Unit) { mounts++; onDispose {} }
            Box(Modifier.fillMaxSize())
        })
    }

    private fun ready(previousRevision: String? = null) {
        try {
            // A state write in runOnIdle does not itself commit the next composition's SideEffect.
            // Drain it before reading the map capture; saved-state emulation already does this.
            compose.waitForIdle()
            compose.waitUntil(15_000) {
                input?.let { it.scene.moments.isNotEmpty() && it.camera.bounds.isNotEmpty() &&
                    it.query.revisionKey != previousRevision } == true
            }
        } catch (failure: Throwable) {
            throw AssertionError("Reading not ready: previous=$previousRevision, current=${input?.query}\n" +
                compose.onRoot().printToString(), failure)
        }
    }
    private fun top() = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
    private fun fold() = compose.onNodeWithTag("diary-sheet-handle").performTouchInput {
        swipeDown(startY = 10f, endY = 600f)
    }
    private fun sceneTab() = compose.onNode(hasText("장면 ${fixture.scenes.size}") and hasClickAction())
    private fun seek(millis: Float) = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        .performSemanticsAction(SemanticsActions.SetProgress) { it(millis) }
    private fun speedAndPause() {
        compose.onNodeWithText("동선 탐색").performClick()
        compose.onNodeWithContentDescription("재생 속도").performClick()
        compose.onNodeWithText("16×").performClick()
        compose.onNodeWithText("동선 재생").performClick()
        compose.onNodeWithText("일시정지").performClick()
    }

    @Test fun `walking auxiliary gap overview replay and drawer share one reading without moving a tapped map`() {
        compose.setContent { Reading() }
        ready()
        val middle = top()
        val walking = fixture.scenes.single { it.id == "fixture/10" }
        compose.onNodeWithTag("diary-scene-list").performScrollToNode(hasText(walking.title))
        compose.onNodeWithText(walking.title).performClick()
        compose.runOnIdle { assertTrue(input!!.scene.sessionExplorer!!.highlightPaths.isNotEmpty()) }
        assertEquals(middle, top(), 1f)
        val camera = input!!.camera
        val padding = input!!.viewport.bottomPaddingPx

        // Same-screen map selection changes the story and emphasis, not the camera or height.
        compose.runOnIdle { input!!.onScene("fixture/51") }
        compose.runOnIdle {
            val selected = input!!.scene.sessionExplorer!!.observedParts.filter { it.selected }
            assertTrue(selected.isNotEmpty())
            assertTrue(selected.all { it.role == RecordRouteRole.OBSERVED_EXCLUDED })
            assertTrue(input!!.scene.sessionExplorer!!.highlightPaths.isEmpty())
            assertTrue(selected.any { it.directions.isNotEmpty() })
            assertEquals(camera, input!!.camera)
        }
        assertEquals(middle, top(), 1f)
        val gap = input!!.scene.sessionExplorer!!.recordContext!!.markers.first { it.gapBoundary }.contextId
        compose.runOnIdle { input!!.onContext(gap) }
        compose.onNodeWithText("이 사이의 이동 경로는 확인할 수 없어요.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(gap, input!!.scene.sessionExplorer!!.recordContext!!.selectedGapGuide!!.contextId)
            assertEquals(camera, input!!.camera)
            assertEquals(padding, input!!.viewport.bottomPaddingPx)
        }
        compose.onNodeWithText("전체 기록", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { input?.camera?.let { it.revision > camera.revision && it.center == null } == true }
        val whole = compose.runOnIdle { input!!.camera }
        val moments = input!!.scene.moments
        speedAndPause()
        seek(105_000f) // The fixture's 90–120 second gap has no interpolated movement.
        compose.runOnIdle {
            assertNull(input!!.scene.sessionExplorer!!.cursor)
            assertNull(input!!.scene.sessionExplorer!!.recordContext!!.selectedGapGuide)
        }
        fold()
        assertTrue(top() > middle)
        compose.onNodeWithText("16×").assertIsNotDisplayed()
        compose.onNodeWithText("동선 탐색").performClick()
        compose.onNodeWithText("16×").assertIsDisplayed()
        compose.onNodeWithText("일시정지").assertDoesNotExist()
        assertEquals(middle, top(), 1f)
        seek(125_000f) // Confirmed excluded movement supports a cursor again.
        compose.runOnIdle {
            assertNotNull(input!!.scene.sessionExplorer!!.cursor)
            assertEquals(whole, input!!.camera)
            assertEquals(padding, input!!.viewport.bottomPaddingPx)
            assertEquals(moments, input!!.scene.moments)
        }
        compose.runOnIdle { input!!.onScene("fixture/51") }
        compose.onNodeWithText("보행 제외 이동").assertIsDisplayed()
        compose.runOnIdle {
            assertNull(input!!.scene.sessionExplorer!!.cursor)
            assertEquals(whole, input!!.camera)
            assertEquals(1, mounts)
        }
    }

    @Test fun `saved composition restores a scene and settled drawer after route preparation`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Reading() }
        ready()
        compose.onNodeWithText("전체 기록", useUnmergedTree = true).performClick()
        compose.runOnIdle { input!!.onScene("fixture/51") }
        compose.onNodeWithText("보행 제외 이동").assertIsDisplayed()
        fold()
        val compact = top()
        val before = input!!
        restore.emulateSavedInstanceStateRestore()
        ready(before.query.revisionKey)
        assertEquals(compact, top(), 1f)
        compose.onNodeWithText("보행 제외 이동").assertIsNotDisplayed()
        compose.runOnIdle {
            assertEquals(before.camera, input!!.camera)
            assertTrue(input!!.scene.sessionExplorer!!.observedParts.any { it.selected })
            assertNull(input!!.scene.sessionExplorer!!.cursor)
        }
        sceneTab().performClick()
        compose.onNodeWithText("보행 제외 이동").assertIsDisplayed()
        compose.onNodeWithTag("diary-scene-body").assertIsDisplayed()
    }

    @Test fun `saved composition keeps speed but never auto plays or resurrects a gap guide`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Reading() }
        ready()
        speedAndPause()
        seek(105_000f)
        compose.onNodeWithText("동선 재생").performClick()
        fold()
        val compact = top()
        val before = input!!
        restore.emulateSavedInstanceStateRestore()
        ready(before.query.revisionKey)
        assertEquals(compact, top(), 1f)
        compose.runOnIdle {
            assertNull(input!!.scene.sessionExplorer!!.cursor)
            assertNull(input!!.scene.sessionExplorer!!.recordContext!!.selectedGapGuide)
        }
        compose.onNodeWithText("동선 탐색").performClick()
        compose.onNodeWithText("16×").assertIsDisplayed()
        compose.onNodeWithText("동선 재생").assertIsDisplayed()
        compose.onNodeWithText("일시정지").assertDoesNotExist()
        // Cursor time restoration is a future contract; do not lock its current loss as desired UX.
    }

    @Test fun `fresh entry without saved state starts at the normal reading height`() {
        var opened by mutableStateOf(true)
        compose.setContent { if (opened) Reading() }
        ready()
        val middle = top()
        compose.onNodeWithText("전체 기록", useUnmergedTree = true).performClick()
        compose.runOnIdle { input!!.onScene("fixture/51") }
        fold()
        val before = input!!
        compose.runOnIdle { opened = false }
        compose.onNodeWithTag("diary-sheet").assertDoesNotExist()
        compose.runOnIdle { opened = true }
        ready(before.query.revisionKey)
        assertEquals(middle, top(), 1f)
        compose.onNodeWithTag("diary-scene-list").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(input!!.scene.sessionExplorer!!.observedParts.any { it.selected })
            assertNull(input!!.scene.sessionExplorer!!.cursor)
        }
        // A fresh Compose entry is not a force-stop/Room/network integration test.
    }
}
