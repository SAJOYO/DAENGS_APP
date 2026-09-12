package com.daengs.app.ui.walk.review

import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import com.daengs.app.walk.sync.RemoteWalkDetail
import com.daengs.app.walk.trajectory.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PrivateObservationConnectivityReplayTest {
    @Test fun `actual records produce review evidence without adopting it or changing legacy scene bindings`() {
        val root = System.getenv("DAENGS_REVIEW_REPLAY_ROOT")?.let(::File)
        assumeTrue(root?.isDirectory == true)
        val cases = root!!.walkTopDown().filter { it.name == "report.json" }.mapNotNull { file ->
            val report = JSONObject(file.readText())
            if (report.optString("app_version") == "walk-review-336") checkNotNull(file.parentFile) to report else null
        }.groupBy { it.second.getString("client_session_id") }.values.map { it.maxBy { pair -> pair.first.name.toLong() } }
        assertEquals(2, cases.size)
        for ((directory, before) in cases) {
            val raw = File(directory, "detail.json").readText(); val storyboard = File(directory, "storyboard.json").readText()
            assertEquals(before.getString("detail_sha256"), sha(raw)); assertEquals(before.getString("storyboard_sha256"), sha(storyboard))
            val remote = RemoteWalkDetail.parse(JSONObject(raw))
            val session = remote.walk.toSession(0)
            val detail = readCompletedRoute(session, remote.fixes, explicitLegacyComparison = true)
            val baseline = summarizeLegacy(session, remote.fixes, Int.MAX_VALUE)
            val result = evaluateLegacyObservationConnections(detail)
            assertNull(result.unsupportedReason); assertTrue(result.matches(detail))
            assertEquals(baseline, detail.summary); assertEquals(baseline.toSessionRoute(), detail.route)
            assertEquals(before.getDouble("distance_m"), detail.summary.distanceMeters, 0.0)
            assertEquals(before.getLong("active_duration_ms"), detail.summary.activeDurationMillis)
            assertEquals(detail.summary.distanceMeters, result.owners.sumOf { it.distanceContributionMeters }, 0.0)
            val covered = result.intervals.filter { it.ownerToSeq != null }
            assertTrue(covered.none { it.auxiliaryUse == AuxiliaryUse.REVIEW_CANDIDATE })
            assertEquals(remote.fixes.size - 1, result.intervals.size)
            val bundle = GeoStoryboardBundle.parse(JSONObject(storyboard).getJSONObject("bundle").toString())
            val scenes = diaryWalk(detail.summary, emptyList(), emptyList(), StoryboardDraft(),
                StoryboardAnalysisView(bundle, false, ""), remote.fixes).scenes
            val review = CompletedRouteReview(detail)
            val oldScenes = before.getJSONArray("scenes")
            scenes.forEachIndexed { i, scene -> assertEquals(oldScenes.getJSONObject(i).getString("relation"), review.sceneFocus(scene).relation.name) }
            // No expected A5/A6 connectivity here: the policy is tested on independent fixtures.
            // This report is an observation of the candidate, not a target used to tune parameters.
            val report = observationConnectivityReport(detail, scenes)
            assertEquals("review_candidate", report.getString("stage"))
            assertEquals(10, report.getJSONArray("sensitivity").length())
            File(directory, "observation-replay-338.json").writeText(JSONObject()
                .put("detail_sha256", sha(raw)).put("storyboard_sha256", sha(storyboard))
                .put("distance_m", detail.summary.distanceMeters).put("active_duration_ms", detail.summary.activeDurationMillis)
                .put("observation_connectivity", report).toString(2))
        }
    }
    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
