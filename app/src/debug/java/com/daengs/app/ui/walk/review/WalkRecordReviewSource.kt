package com.daengs.app.ui.walk.review

import com.daengs.app.BuildConfig
import com.daengs.app.auth.SessionProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import com.daengs.app.walk.sync.RemoteWalk
import com.daengs.app.walk.sync.RemoteWalkDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal data class ReviewRecord(val detail: WalkSessionDetail, val scenes: List<DiaryScene>, val folder: String)
private class ReviewHttpFailure(val code: Int) : Exception()

/** GET-only record transport. Auth stays inside SessionProvider; credentials never enter exports. */
internal class WalkRecordReviewSource(private val sessions: SessionProvider, private val root: File) {
    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        require(path == "/app/walks" || path == "/app/walks/storyboard/capabilities" ||
            path.matches(Regex("/app/walks/[0-9a-f-]{36}(/storyboard\\?bundle_format=(walk-diary-(board|bundle)-v1|walk-storyboard-candidates-v[1-5]))?")))
        val scope = sessions.accountScope.value
        val auth = sessions.freshSession() ?: error("같은 카카오 계정으로 로그인해 주세요.")
        check(sessions.accountScope.value == scope && auth.appUserId == scope.ownerId)
        val connection = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer ${auth.accessToken}")
            root.mkdirs()
            File(root, "last-request.json").writeText(JSONObject().put("path", path)
                .put("http_status", connection.responseCode).toString())
            if (connection.responseCode != 200) {
                if (connection.responseCode in setOf(404, 409, 422)) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    File(root, "error-${sha256(path).take(16)}.json").writeText(JSONObject()
                        .put("path", path).put("http_status", connection.responseCode).put("detail", error).toString())
                }
                throw ReviewHttpFailure(connection.responseCode)
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 32 * 1024 * 1024) { "한 번에 읽을 수 있는 기록 크기를 넘었어요." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            check(sessions.accountScope.value == scope)
            bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }

    suspend fun list(): List<RemoteWalk> {
        val json = JSONObject(get("/app/walks")).getJSONArray("walks")
        return (0 until json.length()).map { RemoteWalk.parse(json.getJSONObject(it)) }
            .sortedByDescending { it.startedAtMillis }
    }

    suspend fun read(walk: RemoteWalk): ReviewRecord {
        UUID.fromString(walk.id); UUID.fromString(walk.clientSessionId)
        val scope = sessions.accountScope.value
        val raw = get("/app/walks/${walk.id}")
        val remote = RemoteWalkDetail.parse(JSONObject(raw))
        require(remote.walk.id == walk.id && remote.walk.clientSessionId == walk.clientSessionId)
        val directory = File(root, "${scope.ownerId}/${walk.clientSessionId}/${System.currentTimeMillis()}")
        withContext(Dispatchers.IO) {
            check(sessions.accountScope.value == scope)
            directory.mkdirs()
            File(directory, "detail.json").writeText(raw)
        }
        val capabilities = get("/app/walks/storyboard/capabilities")
        withContext(Dispatchers.IO) { File(directory, "capabilities.json").writeText(capabilities) }
        val caps = JSONObject(capabilities)
        val formats = caps.optJSONArray("diary_formats") ?: JSONArray()
        val available = (0 until formats.length()).map(formats::getString)
        val candidates = when {
            ServerDiaryBoard.FORMAT in available -> listOf(ServerDiaryBoard.FORMAT)
            ServerDiaryBundle.FORMAT in available -> listOf(ServerDiaryBundle.FORMAT)
            else -> (5 downTo 1).map { "walk-storyboard-candidates-v$it" }
        }
        var chosen: Pair<String, String>? = null
        for (candidate in candidates) {
            try { chosen = candidate to get("/app/walks/${walk.id}/storyboard?bundle_format=$candidate"); break }
            catch (e: ReviewHttpFailure) { if (e.code !in setOf(404, 409, 422)) throw e }
        }
        val (format, storyboard) = chosen ?: error("원본은 확보했지만 저장된 장면을 읽을 수 없어요.")
        check(sessions.accountScope.value == scope)
        withContext(Dispatchers.IO) {
            File(directory, "storyboard.json").writeText(storyboard)
        }
        return withContext(Dispatchers.Default) {
            // Explicit historical comparison, not an inferred restoration of a missing motion policy.
            val detail = readCompletedRoute(remote.walk.toSession(0), remote.fixes, explicitLegacyComparison = true)
            val summary = detail.summary
            val response = JSONObject(storyboard)
            require(response.getString("session_id") == walk.clientSessionId && response.getString("status") == "ready")
            val bundle = GeoStoryboardBundle.parse(if (format.startsWith("walk-storyboard-candidates-"))
                response.getJSONObject("bundle").toString() else storyboard)
            require(bundle.sessionId == walk.clientSessionId)
            val scenes = diaryWalk(summary, emptyList(), emptyList(), StoryboardDraft(),
                StoryboardAnalysisView(bundle, false, "저장된 서버 장면의 비교 사본"), remote.fixes).scenes
            val review = CompletedRouteReview(detail)
            val trace = legacyReviewTrace(remote.fixes)
            check(kotlin.math.abs(trace.getDouble("distance_m") - summary.distanceMeters) < 1e-8)
            val decisions = trace.getJSONArray("decisions")
            val dispositionBySeq = (0 until decisions.length()).map(decisions::getJSONObject)
                .associate { it.getInt("seq") to it.getString("disposition") }
            val report = JSONObject().apply {
                put("format", "walk-real-record-review-v1")
                put("origin", BuildConfig.API_BASE_URL)
                put("app_version", BuildConfig.VERSION_NAME)
                put("owner_id", scope.ownerId)
                put("server_walk_id", walk.id); put("client_session_id", walk.clientSessionId)
                put("fetched_at", Instant.now().toString())
                put("policy", "explicit-app-legacy-baseline")
                put("storyboard_format", format)
                put("legacy_trace", trace)
                put("observation_connectivity", observationConnectivityReport(detail, scenes))
                put("record_presentation", observedRouteMapReport(review, scenes))
                // Change one setting at a time on the same source, without adopting either result.
                put("without_speed_limit", legacyReviewTrace(remote.fixes, speedLimit = Double.POSITIVE_INFINITY))
                put("without_speed_or_min_distance", legacyReviewTrace(remote.fixes,
                    speedLimit = Double.POSITIVE_INFINITY, minDistance = 0.0))
                put("detail_sha256", sha256(raw)); put("storyboard_sha256", sha256(storyboard))
                put("point_count", remote.fixes.size)
                put("distance_m", summary.distanceMeters)
                put("active_duration_ms", summary.activeDurationMillis)
                put("started_at_ms", summary.startedAtMillis); put("ended_at_ms", summary.endedAtMillis)
                put("route_first_at_ms", detail.route.points.firstOrNull()?.capturedAtMillis ?: JSONObject.NULL)
                put("route_last_at_ms", detail.route.points.lastOrNull()?.capturedAtMillis ?: JSONObject.NULL)
                put("section_count", review.sections.size)
                put("scenes", JSONArray().apply { scenes.forEach { scene ->
                    val focus = review.sceneFocus(scene)
                    put(JSONObject().put("id", scene.id).put("at_ms", scene.atMillis)
                        .put("observation_seq", scene.source?.observation?.clientSeq ?: JSONObject.NULL)
                        .put("observation_at_ms", scene.source?.observation?.atMillis ?: JSONObject.NULL)
                        .put("observation_disposition", scene.source?.observation?.clientSeq
                            ?.let(dispositionBySeq::get) ?: JSONObject.NULL)
                        .put("relation", focus.relation.name).put("highlight_path_count", focus.paths.size)
                        .put("binding_reader", focus.binding?.readerVersion ?: JSONObject.NULL)
                        .put("binding_from_seq", focus.binding?.fromSeq ?: JSONObject.NULL)
                        .put("binding_to_seq", focus.binding?.toSeq ?: JSONObject.NULL)
                        .put("binding_event_at_ms", focus.binding?.eventAtMillis ?: JSONObject.NULL)
                        .put("binding_location_at_ms", focus.binding?.locationAtMillis ?: JSONObject.NULL))
                } })
            }
            withContext(Dispatchers.IO) {
                check(sessions.accountScope.value == scope)
                File(directory, "report.json").writeText(report.toString(2))
            }
            ReviewRecord(detail, scenes, directory.absolutePath)
        }
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
