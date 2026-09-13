package com.daengs.app.ui.walk

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
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

/** Four explicit invocations, with external force-stop between them. Never reseed the verify phases. */
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
        compose.waitUntil(20_000) { compose.onAllNodesWithText("동선 탐색").fetchSemanticsNodes().isNotEmpty() }
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
        compose.onNodeWithText("1분").performScrollTo().performClick()
        compose.onNodeWithText("범위 시작으로 이동").performScrollTo().assertExists()
        saveExpected("slice", awaitSaved("slice"))
    }

    @Test fun verifyRangeAndPrepareReplay() {
        val expected = previous("slice")
        open()
        compose.onNodeWithText("범위 시작으로 이동").performScrollTo().assertExists()
        val saved = awaitSaved("slice")
        assertEquals(expected.getJSONObject("selection").toString(), saved.getJSONObject("selection").toString())
        assertTrue(saved.getBoolean("panel"))
        compose.onNodeWithText("범위 시작으로 이동").performClick()
        compose.onNodeWithText("동선 재생").performScrollTo().performClick()
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
        compose.onNodeWithText(formatWalkDuration(elapsed) + " / " + formatWalkDuration(requireNotNull(timeline.durationMillis)))
            .performScrollTo().assertExists()
    }
}
