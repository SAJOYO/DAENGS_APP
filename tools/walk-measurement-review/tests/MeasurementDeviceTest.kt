package com.daengs.app.ui.walk

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Explicit invocations with external force-stop between them. Never reseed the verify phases. */
@RunWith(AndroidJUnit4::class)
class MeasurementDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MeasurementReviewActivity>
    private lateinit var activity: MeasurementReviewActivity
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val checkpoint get() = File(context.filesDir, "measurement-device-checkpoint.json")
    private fun open(reset: Boolean = false) {
        scenario = ActivityScenario.launch(Intent(context, MeasurementReviewActivity::class.java).putExtra("reset", reset))
        scenario.onActivity { activity = it }
        compose.waitUntil(30_000) { activity.failure != null || activity.ready }
        activity.failure?.let { throw AssertionError("Measurement review preparation failed", it) }
        compose.waitUntil(20_000) {
            // Stored data can become ready before setContent has produced its first hierarchy.
            try { compose.onAllNodesWithText("동선 탐색").fetchSemanticsNodes().isNotEmpty() }
            catch (_: IllegalStateException) { false }
        }
        val detail = activity.detail!!
        assertNotNull(detail.measurement)
        assertTrue(CompletedRouteReview(detail).observed.sections.isNotEmpty())
        assertTrue(requireNotNull(CompletedRouteReview(detail).timeline!!.durationMillis) > 0)
    }
    @After fun close() { if (::scenario.isInitialized) scenario.close() }
    private fun payload(): JSONObject? = runBlocking { activity.dao.exploration(activity.session, MeasurementReviewActivity.OWNER) }
        ?.payload?.let(::JSONObject)
    private fun awaitSaved(kind: String): JSONObject {
        compose.waitUntil(15_000) { payload()?.getJSONObject("selection")?.optString("kind") == kind }
        return payload()!!
    }
    private fun saveExpected(phase: String, payload: JSONObject) {
        checkpoint.writeText(JSONObject().put("phase", phase).put("process", MeasurementReviewActivity.PROCESS)
            .put("payload", payload).apply { if (phase == "scene") put("bodyScroll", bodyScroll()) }.toString())
    }
    private fun bodyScroll() = compose.onNodeWithTag("diary-scene-body").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun rangeThumbs() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        useUnmergedTree = true)
    private fun assertRange(from: Long = 5_000, until: Long = 17_000) {
        rangeThumbs().assertCountEquals(2)
        val values = rangeThumbs().fetchSemanticsNodes().map { it.config[SemanticsProperties.ProgressBarRangeInfo].current }.sorted()
        assertEquals(from.toFloat(), values[0], 1f)
        assertEquals(until.toFloat(), values[1], 1f)
        compose.onNodeWithText(formatWalkDuration(from)).assertIsDisplayed()
        compose.onNodeWithText("– " + formatWalkDuration(until)).assertIsDisplayed()
    }
    private fun previous(phase: String): JSONObject {
        check(checkpoint.isFile) { "Run the preceding phase first" }
        val expected = JSONObject(checkpoint.readText())
        assertEquals(phase, expected.getString("phase"))
        assertNotEquals("Must verify in another app process", expected.getString("process"), MeasurementReviewActivity.PROCESS)
        return expected.getJSONObject("payload")
    }

    @Test fun prepareScene() {
        open(reset = true)
        compose.onNodeWithText(MeasurementReviewActivity.TITLE).performScrollTo().performClick()
        compose.onNodeWithContentDescription("상세 패널 펼치기").performClick()
        compose.onNodeWithTag("diary-scene-body").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val scroll = bodyScroll().toInt()
        assertTrue(scroll > 0)
        compose.waitUntil(15_000) {
            val reading = payload()?.optJSONObject("reading")
            val body = reading?.optJSONObject("body")
            reading?.optString("drawer") == "Expanded" && body != null &&
                body.optInt("index") == 0 && body.optInt("offset") == scroll
        }
        saveExpected("scene", awaitSaved("scene"))
    }

    @Test fun verifySceneAndPrepareRange() {
        val expected = previous("scene")
        open()
        compose.onNodeWithTag("diary-scene-body").assertExists()
        compose.onNodeWithContentDescription("지도 넓게 보기").assertExists()
        compose.waitUntil(15_000) {
            payload()?.optJSONObject("reading")?.optJSONObject("body")?.toString() ==
                expected.getJSONObject("reading").getJSONObject("body").toString()
        }
        // The restored UI must actually be scrolled; comparing the stored JSON alone proves nothing.
        val expectedScroll = JSONObject(checkpoint.readText()).getDouble("bodyScroll").toFloat()
        assertTrue(expectedScroll > 0f)
        assertEquals(expectedScroll, bodyScroll(), 0.001f)
        assertEquals(expected.getJSONObject("selection").toString(), awaitSaved("scene").getJSONObject("selection").toString())
        compose.onNodeWithText("동선 탐색").performClick()
        compose.onNodeWithText("1분").assertIsDisplayed().performClick()
        rangeThumbs()[0].performSemanticsAction(SemanticsActions.SetProgress) { it(5_000f) }
        rangeThumbs()[1].performSemanticsAction(SemanticsActions.SetProgress) { it(17_000f) }
        assertRange()
        compose.onNodeWithText("범위 시작으로 이동").performScrollTo().assertExists()
        compose.waitUntil(15_000) {
            val selection = payload()?.optJSONObject("selection") ?: return@waitUntil false
            val time = CompletedRouteReview(activity.detail!!).timeline!!
            fun position(key: String): Long? = selection.optJSONObject(key)?.let {
                time.position(com.daengs.app.walk.routeexplorer.MeasurementTimeAddress(it.getString("epoch"),
                    it.getString("clock"), it.getLong("nanos")))
            }
            position("from") == 5_000L && position("until") == 17_000L
        }
        saveExpected("slice", awaitSaved("slice"))
    }

    @Test fun verifyRangeAndPrepareReplay() {
        val expected = previous("slice")
        open()
        compose.onNodeWithText("범위 시작으로 이동").performScrollTo().assertExists()
        assertRange()
        val saved = awaitSaved("slice")
        assertEquals(expected.getJSONObject("selection").toString(), saved.getJSONObject("selection").toString())
        assertTrue(saved.getBoolean("panel"))
        compose.onNodeWithText("범위 시작으로 이동").performClick()
        compose.onNodeWithText("동선 재생").assertIsDisplayed().performClick()
        compose.onNodeWithText("일시정지").performClick()
        compose.onNodeWithText("1×").performClick()
        compose.onNodeWithText("8×").performClick()
        compose.waitUntil(15_000) { payload()?.optString("speed") == "EIGHT" }
        saveExpected("replay", awaitSaved("replay"))
    }

    @Test fun verifyPausedReplay() {
        val expected = previous("replay")
        open()
        compose.onNodeWithText("동선 재생").assertExists()
        compose.onNodeWithText("일시정지").assertDoesNotExist()
        compose.onNodeWithText("8×").assertExists()
        val saved = awaitSaved("replay")
        assertEquals(expected.getJSONObject("selection").toString(), saved.getJSONObject("selection").toString())
        assertEquals("EIGHT", saved.getString("speed"))
        val detail = activity.detail!!
        val time = expected.getJSONObject("selection").getJSONObject("at")
        val address = com.daengs.app.walk.routeexplorer.MeasurementTimeAddress(time.getString("epoch"),
            time.getString("clock"), time.getLong("nanos"))
        val timeline = CompletedRouteReview(detail).timeline!!
        val elapsed = requireNotNull(timeline.position(address))
        val end = expected.getJSONObject("selection").optJSONObject("range")?.getJSONObject("until")?.let {
            timeline.position(com.daengs.app.walk.routeexplorer.MeasurementTimeAddress(
                it.getString("epoch"), it.getString("clock"), it.getLong("nanos")))
        } ?: requireNotNull(timeline.durationMillis)
        assertReplayTime(elapsed, end)
    }

    private fun explorerScroll() = compose.onNodeWithTag("explorer-reading").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun assertReplayTime(at: Long, end: Long) {
        compose.onNodeWithTag("explorer-range-slider").assertDoesNotExist()
        compose.onNodeWithTag("explorer-replay-slider").assertIsDisplayed()
        compose.onNodeWithText(formatWalkDuration(at)).assertIsDisplayed()
        compose.onNodeWithText("/ " + formatWalkDuration(end)).assertIsDisplayed()
    }

    private fun elapsed(selection: JSONObject): Long {
        val at = selection.getJSONObject("at")
        return requireNotNull(CompletedRouteReview(activity.detail!!).timeline!!.position(
            com.daengs.app.walk.routeexplorer.MeasurementTimeAddress(at.getString("epoch"), at.getString("clock"), at.getLong("nanos"))))
    }

    private fun capture(name: String) {
        // Frozen transport fixtures are near (0, 0), outside the normal Korean camera extent.
        // Adjust only this opt-in test map's camera; keep source bytes and production layers intact.
        fun descendants(view: android.view.View): Sequence<android.view.View> = sequence {
            yield(view)
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
        var map: com.naver.maps.map.NaverMap? = null
        scenario.onActivity { current ->
            descendants(current.window.decorView).filterIsInstance<com.naver.maps.map.MapView>().single()
                .getMapAsync { map = it }
        }
        compose.waitUntil(10_000) { map != null }
        scenario.onActivity {
            val nativeMap = requireNotNull(map)
            nativeMap.extent = null
            val bounds = com.naver.maps.geometry.LatLngBounds.from(activity.detail!!.observations.map {
                com.naver.maps.geometry.LatLng(it.lat, it.lng)
            })
            nativeMap.moveCamera(com.naver.maps.map.CameraUpdate.fitBounds(bounds, 60))
        }
        compose.waitUntil(10_000) { map?.isCameraIdlePending == false }
        // SurfaceView rendering happens outside Compose's idling loop.
        android.os.SystemClock.sleep(750)
        captureWindow(name)
    }

    private fun captureWindow(name: String) {
        // Only capture this isolated test activity, never another foreground application.
        scenario.onActivity { check(it.hasWindowFocus()) }
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    @Test fun prepareRangeScene() {
        open(reset = true)
        compose.onNodeWithText("동선 탐색").performClick()
        compose.onNodeWithText("1분").assertIsDisplayed().performClick()
        rangeThumbs()[1].performSemanticsAction(SemanticsActions.SetProgress) { it(17_000f) }
        assertRange(0)
        compose.onNodeWithText(MeasurementReviewActivity.RANGE_TITLE).assertDoesNotExist()
        compose.onNodeWithText("경로 정보 · 구간과 전후 관계").performScrollTo().performClick()
        // The pinned controls no longer contribute to this offset. Move the reading region itself.
        compose.onNodeWithTag("explorer-reading").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 8f) }
        val offset = explorerScroll()
        assertTrue(offset > 0)
        val top = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        assertExplorerSpace()
        compose.onNodeWithText("장면 3").performClick()
        compose.onNodeWithText(MeasurementReviewActivity.RANGE_TITLE).performScrollTo().performClick()
        assertTrue(awaitSaved("scene").getJSONObject("selection").has("returnRange"))
        compose.onNodeWithText("구간 복귀").assertIsDisplayed().performClick()
        assertRange(0)
        assertEquals(offset, explorerScroll(), 1f)
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("장면 3").performClick()
        compose.onNodeWithText(MeasurementReviewActivity.RANGE_TITLE).performScrollTo().performClick()
        compose.onNodeWithTag("diary-scene-body").assertExists()
        compose.waitUntil(15_000) { payload()?.optJSONObject("reading")?.optInt("explorerOffset") == offset.toInt() }
        saveExpected("range-scene", awaitSaved("scene"))
        capture("range-scene")
    }

    @Test fun verifyRangeSceneAndPlaybackEnd() {
        val expected = previous("range-scene")
        open()
        compose.onNodeWithTag("diary-scene-body").assertExists()
        assertEquals(expected.getJSONObject("selection").toString(), awaitSaved("scene").getJSONObject("selection").toString())
        compose.onNodeWithText("동선 탐색").performClick()
        assertRange(0)
        assertEquals(expected.getJSONObject("reading").getInt("explorerOffset").toFloat(), explorerScroll(), 1f)
        compose.onNodeWithText("범위 시작으로 이동").performScrollTo().performClick()
        compose.onNodeWithText("1×").assertIsDisplayed().performClick()
        compose.onNodeWithText("8×").performClick()
        compose.onNodeWithText("동선 재생").performClick()
        compose.waitUntil(15_000) {
            val selection = payload()?.optJSONObject("selection")
            selection?.optString("kind") == "replay" && elapsed(selection) == 17_000L &&
                compose.onAllNodesWithText("일시정지").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(awaitSaved("replay").getJSONObject("selection").has("range"))
        assertReplayTime(17_000, 17_000)
        capture("range-playback-end")
        // Replaying at the end starts again inside the saved range; a seek cannot discard it.
        compose.onNodeWithText("동선 재생").assertIsDisplayed().performClick()
        compose.onNodeWithText("일시정지").performClick()
        assertTrue(elapsed(awaitSaved("replay").getJSONObject("selection")) < 17_000)
        rangeThumbs().onLast().performSemanticsAction(SemanticsActions.SetProgress) { it(6_000f) }
        compose.waitUntil(15_000) { payload()?.getJSONObject("selection")?.let(::elapsed) == 6_000L }
        assertReplayTime(6_000, 17_000)
        saveExpected("bounded-replay", awaitSaved("replay"))
    }

    @Test fun verifyBoundedReplayReopened() {
        val expected = previous("bounded-replay")
        open()
        compose.onNodeWithText("일시정지").assertDoesNotExist()
        compose.onNodeWithText("동선 재생").assertIsDisplayed()
        compose.onNodeWithText("8×").assertExists()
        assertEquals(expected.getJSONObject("selection").toString(), awaitSaved("replay").getJSONObject("selection").toString())
        assertReplayTime(6_000, 17_000)
        capture("range-reopened")
        compose.onNodeWithText("장면 3").performClick()
        compose.onNodeWithText(MeasurementReviewActivity.RANGE_TITLE).performScrollTo().performClick()
        compose.onNodeWithText("이 장면 앞뒤 30초 보기").performScrollTo().performClick()
        assertRange(0, CompletedRouteReview(activity.detail!!).timeline!!.durationMillis!!)
        capture("scene-neighborhood")
    }

    private fun assertExplorerSpace() {
        val header = compose.onNodeWithTag("explorer-time-header").fetchSemanticsNode().boundsInRoot
        val reading = compose.onNodeWithTag("explorer-reading").fetchSemanticsNode().boundsInRoot
        assertTrue("Map notices consumed the reading viewport", reading.height >= 40 * context.resources.displayMetrics.density)
        assertTrue(header.bottom <= reading.top)
        compose.onNodeWithText("동선 재생").assertIsDisplayed()
    }

    @Test fun verifyExplorerActions() {
        open(reset = true)
        compose.onNodeWithText("동선 탐색").performClick()
        assertExplorerSpace()
        capture("explorer-whole")
        compose.onNodeWithText("구간 고르기").performClick()
        rangeThumbs()[1].performSemanticsAction(SemanticsActions.SetProgress) { it(17_000f) }
        assertRange(0)
        val top = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        // Exercise actual dragging, not only accessibility SetProgress.
        compose.onNodeWithTag("explorer-range-slider").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .72f, centerY),
                androidx.compose.ui.geometry.Offset(width * .5f, centerY), 500)
        }
        assertTrue(rangeThumbs().fetchSemanticsNodes().maxOf { it.config[SemanticsProperties.ProgressBarRangeInfo].current } < 17_000f)
        rangeThumbs()[1].performSemanticsAction(SemanticsActions.SetProgress) { it(17_000f) }
        compose.onNodeWithTag("explorer-range-slider").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .02f, centerY),
                androidx.compose.ui.geometry.Offset(width * .15f, centerY), 500)
        }
        val movedStart = rangeThumbs().fetchSemanticsNodes().minOf { it.config[SemanticsProperties.ProgressBarRangeInfo].current }
        assertTrue("The start handle must also respond to dragging", movedStart in 1f..16_999f)
        rangeThumbs()[0].performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertRange(0)
        compose.onNodeWithText(MeasurementReviewActivity.RANGE_TITLE).assertDoesNotExist()
        compose.onNodeWithText("경로 정보 · 구간과 전후 관계").performScrollTo().performClick()
        val offset = explorerScroll()
        capture("explorer-range")
        compose.onNodeWithText("장면 3").performClick()
        compose.onNodeWithText(MeasurementReviewActivity.RANGE_TITLE).performScrollTo().performClick()
        compose.onNodeWithText("구간 복귀").assertIsDisplayed().performClick()
        assertRange(0)
        assertEquals(offset, explorerScroll(), 1f)
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("장면 3").performClick()
        compose.onNodeWithText(MeasurementReviewActivity.RANGE_TITLE).performScrollTo().performClick()
        compose.onNodeWithText("이 장면 앞뒤 30초 보기").performScrollTo().assertIsDisplayed()
        capture("explorer-scene-actions")
        compose.onNodeWithText("이 장면 앞뒤 30초 보기").performClick()
        assertRange(0, requireNotNull(CompletedRouteReview(activity.detail!!).timeline!!.durationMillis))
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("동선 재생").performClick()
        compose.onNodeWithText("일시정지").performClick()
        compose.onNodeWithTag("explorer-range-slider").assertDoesNotExist()
        compose.onNodeWithTag("explorer-replay-slider").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * .4f, centerY))
        }
        // Confirm the touch reached the requested position, not an earlier paused checkpoint.
        compose.waitUntil(10_000) {
            rangeThumbs().fetchSemanticsNodes().single().config[SemanticsProperties.ProgressBarRangeInfo].current in 8_000f..10_000f
        }
        val cursor = rangeThumbs().fetchSemanticsNodes().single().config[SemanticsProperties.ProgressBarRangeInfo].current.toLong()
        compose.waitUntil(10_000) { payload()?.optJSONObject("selection")?.let(::elapsed) == cursor }
        capture("explorer-cursor")
        compose.onNodeWithText("구간 수정").performClick()
        compose.onNodeWithTag("explorer-replay-slider").assertDoesNotExist()
        compose.onNodeWithText("전체 산책").performClick()
        compose.onNodeWithText("전체 장면 3개").assertDoesNotExist()
        compose.onNodeWithText(MeasurementReviewActivity.RANGE_TITLE).assertDoesNotExist()
        assertEquals(top, compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals("overview", awaitSaved("overview").getJSONObject("selection").getString("kind"))
    }
}
