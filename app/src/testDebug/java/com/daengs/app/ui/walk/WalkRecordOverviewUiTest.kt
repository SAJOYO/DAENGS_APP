package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.map.shell.MapVisibilityResult
import com.daengs.app.ui.walk.review.recordContextMapFixture
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRecordOverviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `whole walking offscreen scene and map gap actions share the real camera contract`() {
        val fixture = recordContextMapFixture()
        val far = fixture.scenes[6].copy(id = "far", title = "먼 공원에서", source = null, content = null,
            point = fixture.scenes[6].point!!.copy(longitude = fixture.scenes[6].point!!.longitude + .5))
        val scenes = fixture.scenes + far
        var input: DiaryReviewMap? = null
        var mounts = 0
        compose.setContent { WalkRouteReviewContent(fixture.detail, scenes, "가상 기록", renderMap = { value ->
            SideEffect { input = value }
            DisposableEffect(Unit) { mounts++; onDispose {} }
            Box(Modifier.fillMaxSize())
        }) }
        compose.waitUntil(15_000) { input?.scene?.moments?.any { it.id == "far" } == true && input?.camera?.bounds?.isNotEmpty() == true }
        val walking = input!!.camera.bounds
        compose.onNodeWithText("전체 기록", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertTrue(input!!.camera.bounds.last().longitude > walking.last().longitude)
            val query = input!!.query
            input!!.onVisibility(MapVisibilityResult(query, query.targets.map { it.id }.toSet() - "far"))
        }
        compose.onNodeWithText("화면 밖 장면 1개").performClick()
        compose.onNodeWithText("${scenes.size} · 먼 공원에서").performClick()
        compose.runOnIdle { assertEquals(far.point, input!!.camera.center) }
        val selectedCamera = input!!.camera
        val padding = input!!.viewport.bottomPaddingPx
        val gapId = input!!.scene.sessionExplorer!!.recordContext!!.markers.first { it.gapBoundary }.contextId
        compose.runOnIdle { input!!.onContext(gapId) }
        compose.runOnIdle {
            assertEquals(selectedCamera, input!!.camera)
            assertEquals(padding, input!!.viewport.bottomPaddingPx)
            assertEquals(gapId, input!!.scene.sessionExplorer!!.recordContext!!.selectedGapGuide!!.contextId)
            input!!.onScene(fixture.scenes[6].id)
        }
        compose.runOnIdle {
            assertEquals(selectedCamera, input!!.camera)
            assertNull(input!!.scene.sessionExplorer!!.recordContext!!.selectedGapGuide)
        }
        compose.onNodeWithText("보행 중심", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(walking, input!!.camera.bounds)
            assertNull(input!!.camera.center)
            assertTrue(input!!.scene.moments.any { it.id == "far" })
            assertEquals(1, mounts)
        }
    }

    @Test fun `late scene update and stale viewport result cannot move the map or restore old offscreen counts`() {
        val fixture = recordContextMapFixture()
        var scenes by mutableStateOf(fixture.scenes)
        var input: DiaryReviewMap? = null
        compose.setContent { WalkRouteReviewContent(fixture.detail, scenes, "가상 기록", renderMap = { value ->
            SideEffect { input = value }; Box(Modifier.fillMaxSize())
        }) }
        compose.waitUntil(15_000) { input?.scene?.moments?.isNotEmpty() == true && input?.camera?.bounds?.isNotEmpty() == true }
        compose.onNodeWithText("전체 기록", useUnmergedTree = true).performClick()
        compose.runOnIdle { input!!.onGesture() }
        val old = input!!
        compose.runOnIdle { scenes = scenes.mapIndexed { index, scene -> if (index == 6) scene.copy(title = "바뀐 장면 제목") else scene } }
        compose.waitForIdle()
        try {
            compose.waitUntil(15_000) { input!!.query.revisionKey != old.query.revisionKey && input!!.query.targets.isNotEmpty() }
        } catch (failure: Throwable) {
            throw AssertionError("Scene update did not finish: old=${old.query.revisionKey}, current=${input!!.query}\n" +
                compose.onRoot().printToString(), failure)
        }
        compose.runOnIdle {
            assertEquals(old.camera, input!!.camera)
            input!!.onVisibility(MapVisibilityResult(old.query, emptySet()))
            old.onVisibility(MapVisibilityResult(old.query, emptySet()))
        }
        compose.onNodeWithText("화면 밖 장면", substring = true).assertDoesNotExist()
        compose.runOnIdle {
            val current = input!!
            current.onVisibility(MapVisibilityResult(current.query, current.query.targets.map { it.id }.toSet()))
            assertEquals(old.camera, current.camera)
        }
    }
}
