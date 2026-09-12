package com.daengs.app.ui.walk.review

import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import com.daengs.app.walk.sync.RemoteWalkDetail
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Opt-in replay of private #335 captures. Neither observations nor scene text enter the repo. */
class PrivateSceneBindingReplayTest {
    @Test fun `captured input preserves exact legacy results and restores only normal omitted scenes`() {
        val root = System.getenv("DAENGS_REVIEW_REPLAY_ROOT")?.let(::File)
        assumeTrue("Private captures are optional; synthetic and Room regressions always run", root?.isDirectory == true)
        val cases = root!!.walkTopDown().filter { it.name == "report.json" }.mapNotNull { file ->
            val report = JSONObject(file.readText())
            if (report.optString("app_version") != "walk-review-335" || !report.has("legacy_trace")) null
            else checkNotNull(file.parentFile) to report
        }.groupBy { it.second.getString("client_session_id") }.values.map { captures ->
            captures.maxBy { it.first.name.toLong() }
        }
        assertEquals("Replay both previously captured records", 2, cases.size)
        for ((directory, before) in cases) {
            val raw = File(directory, "detail.json").readText()
            val storyboard = File(directory, "storyboard.json").readText()
            assertEquals(before.getString("detail_sha256"), sha(raw))
            assertEquals(before.getString("storyboard_sha256"), sha(storyboard))
            val remote = RemoteWalkDetail.parse(JSONObject(raw))
            val session = remote.walk.toSession(0)
            val baseline = summarizeLegacy(session, remote.fixes, Int.MAX_VALUE)
            val read = readCompletedRoute(session, remote.fixes, explicitLegacyComparison = true)
            assertEquals(baseline, read.summary)
            assertEquals(baseline.toSessionRoute(), read.route)
            assertEquals(before.getDouble("distance_m"), read.summary.distanceMeters, 0.0)
            assertEquals(before.getLong("active_duration_ms"), read.summary.activeDurationMillis)
            val oldTrace = before.getJSONObject("legacy_trace")
            val newTrace = legacyReviewTrace(remote.fixes)
            assertEquals(oldTrace.getDouble("distance_m"), newTrace.getDouble("distance_m"), 0.0)
            assertEquals(oldTrace.getInt("retained_point_count"), newTrace.getInt("retained_point_count"))
            val oldRows = oldTrace.getJSONArray("decisions")
            val newRows = newTrace.getJSONArray("decisions")
            assertEquals(oldRows.length(), newRows.length())
            for (i in 0 until oldRows.length()) {
                val old = oldRows.getJSONObject(i); val new = newRows.getJSONObject(i)
                assertEquals(old.getInt("seq"), new.getInt("seq"))
                assertEquals(old.getString("disposition"), new.getString("disposition"))
                assertEquals(old.getDouble("distance_delta_m"), new.getDouble("distance_delta_m"), 0.0)
            }
            val bundle = GeoStoryboardBundle.parse(JSONObject(storyboard).getJSONObject("bundle").toString())
            val scenes = diaryWalk(read.summary, emptyList(), emptyList(), StoryboardDraft(),
                StoryboardAnalysisView(bundle, false, ""), remote.fixes).scenes
            val oldScenes = before.getJSONArray("scenes")
            assertEquals(oldScenes.length(), scenes.size)
            val review = CompletedRouteReview(read)
            assertEquals(before.getInt("section_count"), review.sections.size)
            val results = JSONArray()
            var recovered = 0
            scenes.forEachIndexed { i, scene ->
                val old = oldScenes.getJSONObject(i)
                val focus = review.sceneFocus(scene)
                val omitted = old.optString("observation_disposition") == "below_min_distance"
                assertEquals(old.getString("id"), scene.id)
                assertEquals("${remote.fixes.size} observations, scene ${i + 1}",
                    if (omitted) "CONNECTED" else old.getString("relation"), focus.relation.name)
                if (!omitted) assertEquals(CompletedRouteReview(read.copy(legacyRouteEvidence = null))
                    .sceneFocus(scene).paths, focus.paths)
                if (omitted) {
                    recovered++
                    assertEquals(scene.point, focus.point)
                    assertNotNull(focus.binding?.fromSeq); assertNotNull(focus.binding?.toSeq)
                }
                results.put(JSONObject().put("scene", i + 1).put("seq", scene.source?.observation?.clientSeq)
                    .put("before", old.getString("relation")).put("after", focus.relation.name)
                    .put("from_seq", focus.binding?.fromSeq ?: JSONObject.NULL)
                    .put("to_seq", focus.binding?.toSeq ?: JSONObject.NULL))
            }
            assertEquals(if (remote.fixes.size == 363) 3 else 1, recovered)
            File(directory, "binding-replay-336.json").writeText(JSONObject()
                .put("detail_sha256", sha(raw)).put("storyboard_sha256", sha(storyboard))
                .put("distance_m", read.summary.distanceMeters).put("active_duration_ms", read.summary.activeDurationMillis)
                .put("route_equals_baseline", true).put("all_decisions_equal_335", true)
                .put("scenes", results).toString(2))
        }
    }

    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
