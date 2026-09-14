package com.daengs.app.ui.walk.review

import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import com.daengs.app.walk.sync.RemoteWalkDetail
import com.daengs.app.walk.trajectory.RecordContextKind
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PrivateRecordContextReplayTest {
    @Test fun `same A B source keeps prior route scene and display results while exposing event context`() {
        val root = System.getenv("DAENGS_REVIEW_REPLAY_ROOT")?.let(::File)
        assumeTrue(root?.isDirectory == true)
        val cases = root!!.walkTopDown().filter { it.name == "report.json" }.mapNotNull { file ->
            val data = JSONObject(file.readText())
            if (data.optString("app_version") == "walk-review-342") checkNotNull(file.parentFile) to data else null
        }.groupBy { it.second.getString("client_session_id") }.values.map { it.maxBy { (folder, _) -> folder.name.toLong() } }
        assertEquals(2, cases.size)
        for ((folder, before) in cases) {
            val raw = File(folder, "detail.json").readText(); val story = File(folder, "storyboard.json").readText()
            assertEquals(before.getString("detail_sha256"), sha(raw)); assertEquals(before.getString("storyboard_sha256"), sha(story))
            val remote = RemoteWalkDetail.parse(JSONObject(raw)); val session = remote.walk.toSession(0)
            val detail = readCompletedRoute(session, remote.fixes, explicitLegacyComparison = true)
            val review = CompletedRouteReview(detail)
            val bundle = GeoStoryboardBundle.parse(JSONObject(story).getJSONObject("bundle").toString())
            val scenes = diaryWalk(detail.summary, emptyList(), emptyList(), StoryboardDraft(),
                StoryboardAnalysisView(bundle, false, ""), remote.fixes).scenes
            assertEquals(before.getDouble("distance_m"), detail.summary.distanceMeters, 0.0)
            assertEquals(before.getLong("active_duration_ms"), detail.summary.activeDurationMillis)
            assertEquals(summarizeLegacy(session, remote.fixes, Int.MAX_VALUE).toSessionRoute(), detail.route)
            val actual = observedRouteMapReport(review, scenes)
            // Normalize object key order without assigning a numerical tolerance.
            assertEquals(normalize(before.getJSONObject("record_presentation")), normalize(JSONObject(actual.toString())))
            val start = review.context.contexts.first(); val end = review.context.contexts.last()
            assertEquals(session.startedAtMillis, start.fromMillis); assertEquals(session.endedAtMillis, end.fromMillis)
            assertEquals(session.endedAtMillis!! - session.startedAtMillis, review.context.durationMillis)
            assertNull(review.context.frameAt(review.context.durationMillis!!).point)
            if (remote.fixes.size == 363) {
                assertEquals(39_463L, end.fromMillis - end.walkingEndpoint!!.capturedAtMillis)
                assertEquals(1_407L, end.fromMillis - end.before!!.atMillis)
                assertTrue(review.context.contexts.any { it.kind == RecordContextKind.GAP && it.toSeq == 340 })
            }
            File(folder, "record-context-replay-343.json").writeText(JSONObject()
                .put("detail_sha256", sha(raw)).put("storyboard_sha256", sha(story))
                .put("distance_m", detail.summary.distanceMeters).put("active_duration_ms", detail.summary.activeDurationMillis)
                .put("record_context", recordContextReport(review)).toString(2))
        }
    }
    @Test fun `phone fixture includes positionless start gap note and end without synthetic guide anchors`() {
        val record = recordContextMapFixture(); val review = CompletedRouteReview(record.detail)
        val unlocated = record.scenes.filter { it.point == null }
        assertEquals(3, unlocated.size)
        unlocated.forEach { assertNull(review.recordSceneFocus(it).point) }
        assertEquals(2, unlocated.count { review.context.eventFor(it) != null })
        assertEquals(1, unlocated.count { review.context.gapAt(it.atMillis) != null })
    }
    private fun normalize(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().associateWith { normalize(value.get(it)) }
        is org.json.JSONArray -> (0 until value.length()).map { normalize(value.get(it)) }
        else -> value
    }
    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
