package com.daengs.app.ui.walk.review

import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.*
import com.daengs.app.walk.sync.RemoteWalkDetail
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PrivateObservedRouteMapReplayTest {
    @Test fun `same actual records keep walking and bind the adopted observed range with distinct event time`() {
        val root = System.getenv("DAENGS_REVIEW_REPLAY_ROOT")?.let(::File)
        assumeTrue(root?.isDirectory == true)
        val cases = root!!.walkTopDown().filter { it.name == "report.json" }.mapNotNull { file ->
            val r = JSONObject(file.readText())
            if (r.optString("app_version") == "walk-review-338") checkNotNull(file.parentFile) to r else null
        }.groupBy { it.second.getString("client_session_id") }.values.map { it.maxBy { p -> p.first.name.toLong() } }
        assertEquals(2, cases.size)
        for ((directory, before) in cases) {
            val raw = File(directory, "detail.json").readText(); val storyboard = File(directory, "storyboard.json").readText()
            assertEquals(before.getString("detail_sha256"), sha(raw)); assertEquals(before.getString("storyboard_sha256"), sha(storyboard))
            val remote = RemoteWalkDetail.parse(JSONObject(raw)); val session = remote.walk.toSession(0)
            val detail = readCompletedRoute(session, remote.fixes, explicitLegacyComparison = true)
            val review = CompletedRouteReview(detail)
            val bundle = GeoStoryboardBundle.parse(JSONObject(storyboard).getJSONObject("bundle").toString())
            val scenes = diaryWalk(detail.summary, emptyList(), emptyList(), StoryboardDraft(),
                StoryboardAnalysisView(bundle, false, ""), remote.fixes).scenes
            assertEquals(before.getDouble("distance_m"), detail.summary.distanceMeters, 0.0)
            assertEquals(before.getLong("active_duration_ms"), detail.summary.activeDurationMillis)
            assertEquals(summarizeLegacy(session, remote.fixes, Int.MAX_VALUE).toSessionRoute(), detail.route)
            scenes.forEachIndexed { i, scene ->
                assertEquals(before.getJSONArray("scenes").getJSONObject(i).getString("relation"), review.sceneFocus(scene).relation.name)
            }
            if (remote.fixes.size == 363) {
                val part = review.observed.sections.single()
                assertEquals(340, part.fixes.first().clientSeq); assertEquals(362, part.fixes.last().clientSeq)
                for (i in listOf(4, 5)) {
                    val focus = review.recordSceneFocus(scenes[i])
                    assertEquals(SceneRouteRelation.OBSERVED_EXCLUDED, focus.relation)
                    assertTrue(focus.paths.isEmpty()); assertEquals(scenes[i].point, focus.point)
                    assertTrue(focus.observedParts.single().directions.isNotEmpty())
                    assertTrue(focus.observedParts.single().fixes.none { it.clientSeq <= 339 })
                }
                val end = review.recordSceneFocus(scenes[5]).binding!!
                assertEquals(1407L, end.eventAtMillis - end.locationAtMillis)
                for (i in 1..3) assertEquals(review.sceneFocus(scenes[i]), review.recordSceneFocus(scenes[i]))
            } else {
                assertEquals(174, remote.fixes.size); assertTrue(review.observed.sections.isEmpty())
                scenes.forEach { assertEquals(review.sceneFocus(it), review.recordSceneFocus(it)) }
            }
            File(directory, "observed-map-replay-342.json").writeText(JSONObject()
                .put("detail_sha256", sha(raw)).put("storyboard_sha256", sha(storyboard))
                .put("distance_m", detail.summary.distanceMeters).put("active_duration_ms", detail.summary.activeDurationMillis)
                .put("record_presentation", observedRouteMapReport(review, scenes)).toString(2))
        }
    }
    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
