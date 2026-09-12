package com.daengs.app.ui.walk.review

import com.daengs.app.walk.sync.RemoteWalk
import com.daengs.app.walk.sync.RemoteWalkDetail
import com.daengs.app.walk.sync.WalkMotionContract
import com.daengs.app.walk.sync.WalkMotionContract.long
import com.daengs.app.walk.sync.WalkPrecisionContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID

/** Diagnostic export only: no Room restore, upload, finalize, selection or active-view mutation. */
internal class MotionRecordReviewCapture(
    private val owner: String,
    private val appVersion: String,
    private val folder: File,
    private val checkScope: () -> Unit,
    private val request: suspend (String) -> MotionReviewResponse,
) {
    private val sources = JSONArray()

    private fun write(name: String, json: JSONObject) {
        checkScope()
        folder.mkdirs()
        File(folder, name).writeText(json.toString(2))
    }

    private suspend fun fetch(name: String, path: String): MotionReviewResponse {
        checkScope()
        val response = request(path)
        checkScope()
        // No request headers, tokens or exception messages are exported.
        val source = JSONObject().put("file", "$name.json").put("path", path)
            .put("http_status", response.status).put("body_sha256", motionReviewHash(response.body))
            .put("etag", response.etag ?: JSONObject.NULL)
        folder.mkdirs()
        // The envelope preserves exact response bytes (as a UTF-8 string), including HTTP failures.
        write("$name.json", JSONObject().put("body", response.body))
        sources.put(source)
        write("sources.json", JSONObject().put("sources", sources))
        if (response.status in setOf(401, 403) || response.status in 300..399) {
            throw MotionReviewHttpFailure(response.status)
        }
        return response
    }

    private fun MotionReviewResponse.json(): JSONObject {
        if (status != 200) throw MotionReviewHttpFailure(status)
        return JSONObject(body)
    }

    suspend fun run(walkId: String? = null) = withContext(Dispatchers.IO) {
        require(owner.isNotBlank())
        require(!folder.exists()) { "REVIEW_CAPTURE_EXISTS" }
        walkId?.let { require(UUID.fromString(it).toString() == it) }
        write("capture.json", JSONObject().put("format", "walk-motion-review-v1")
            .put("owner_id", owner).put("app_version", appVersion)
            .put("input_origin", "server-backup").put("independent_device_input_verified", false)
            .put("server_walk_id", walkId ?: JSONObject.NULL))
        write("status.json", JSONObject().put("state", "running"))
        try {
            if (walkId == null) inventory() else capture(walkId)
            write("status.json", JSONObject().put("state", "complete"))
        } catch (error: Exception) {
            // Keep partial sources but never mark an interrupted run as a completed comparison.
            if (runCatching { checkScope() }.isSuccess) write("status.json", JSONObject()
                .put("state", "failed").put("error_type", error.javaClass.simpleName)
                .put("http_status", (error as? MotionReviewHttpFailure)?.status ?: JSONObject.NULL))
            throw error
        }
    }

    private suspend fun inventory() {
        val caps = fetch("motion-capabilities", "/app/walks/motion-capabilities")
        fetch("trajectory-capabilities", "/app/walks/trajectory-capabilities")
        if (caps.status != 200 || !caps.json().getBoolean("backup_supported")) {
            write("inventory.json", JSONObject().put("state", "backup_unavailable")
                .put("http_status", caps.status).put("records", JSONArray()))
            return
        }
        val records = JSONArray()
        val ids = mutableSetOf<String>()
        val cursors = mutableSetOf<String>()
        var cursor: String? = null
        var pageIndex = 0
        var listContract = "legacy_unpaginated_response"
        do {
            check(pageIndex < 100) { "REVIEW_PAGE_LIMIT" }
            val path = "/app/walks" + (cursor?.let { "?cursor=${URLEncoder.encode(it, "UTF-8")}" } ?: "")
            val page = fetch("list-${pageIndex++}", path).json()
            val walks = page.getJSONArray("walks")
            for (i in 0 until walks.length()) {
                check(ids.size < 1000) { "REVIEW_RECORD_LIMIT" }
                val walk = RemoteWalk.parse(walks.getJSONObject(i))
                require(UUID.fromString(walk.id).toString() == walk.id && ids.add(walk.id))
                val status = fetch("backup-${ids.size - 1}", "/app/walks/${walk.id}/motion-backup")
                val state = when (status.status) {
                    200 -> status.json().getString("state").also { require(it in setOf("complete", "collecting")) }
                    404 -> "not_found"
                    409 -> "conflict"
                    503 -> "unavailable"
                    else -> throw MotionReviewHttpFailure(status.status)
                }
                records.put(JSONObject().put("walk_id", walk.id).put("client_session_id", walk.clientSessionId)
                    .put("started_at_ms", walk.startedAtMillis).put("backup_state", state)
                    .put("http_status", status.status))
                write("inventory.json", JSONObject().put("state", "scanning").put("records", records))
            }
            if (page.has("next_cursor")) listContract = "cursor_response"
            cursor = if (page.isNull("next_cursor")) null else page.getString("next_cursor")
            if (cursor != null) require(cursor.isNotBlank() && cursor.length <= 1024 && cursors.add(cursor))
        } while (cursor != null)
        write("inventory.json", JSONObject().put("state", "complete").put("list_contract", listContract)
            .put("pages", pageIndex).put("records", records)
            .put("independent_device_input_verified", false))
    }

    private suspend fun capture(walkId: String) {
        val detailJson = fetch("detail", "/app/walks/$walkId").json()
        val detail = RemoteWalkDetail.parse(detailJson)
        require(detail.walk.id == walkId)
        val status = fetch("motion-backup", "/app/walks/$walkId/motion-backup").json()
        require(status.getString("state") == "complete") { "REVIEW_BACKUP_NOT_COMPLETE" }
        val manifest = status.getJSONObject("manifest")
        val count = manifest.long("point_count")
        require(count == detail.fixes.size.toLong() && count <= 10_000) { "REVIEW_POINT_LIMIT_OR_COUNT" }
        val points = chunks(walkId, "motion-backup", WalkMotionContract.manifestDigest(manifest), count.toInt())
        val base = WalkMotionContract.read(detail.walk.toSession(0).copy(ownerId = owner), detail.fixes, manifest, points)
        WalkMotionContract.validateStatus(status, base, true)
        val precisionStatus = fetch("motion-precision", "/app/walks/$walkId/motion-precision")
        val precision = when (precisionStatus.status) {
            404 -> null
            200 -> {
                val value = precisionStatus.json()
                require(value.getString("state") == "complete") { "REVIEW_PRECISION_NOT_COMPLETE" }
                val m = value.getJSONObject("manifest")
                val p = WalkPrecisionContract.read(base, m,
                    chunks(walkId, "motion-precision", WalkPrecisionContract.manifestDigest(m), count.toInt()))
                WalkPrecisionContract.validateStatus(value, p, true)
                p
            }
            else -> throw MotionReviewHttpFailure(precisionStatus.status)
        }
        val input = precision?.base ?: base
        val basis = if (precision != null) "device-fix-bits-v1" else "stored-raw-v1-six-decimals"
        write("kotlin-replay.json", motionReviewReplay(input).put("coordinate_basis", basis)
            .put("manifest_fingerprint", base.manifestHash).put("evidence_fingerprint", base.evidenceHash)
            .put("precision_fingerprint", precision?.evidenceHash ?: JSONObject.NULL))
        // Keep failures as evidence. A completed capture does not mean its comparisons passed.
        fetch("motion-calculation", "/app/walks/$walkId/motion-calculation")
        fetch("trajectory-capabilities", "/app/walks/trajectory-capabilities")
        val candidate = fetch("trajectory-calculation", "/app/walks/$walkId/trajectory-calculation?version=walk-trajectory-calculation-v1")
        if (candidate.status == 200) require(candidate.etag == "\"${motionReviewHash(candidate.body)}\"") {
            "REVIEW_ETAG_MISMATCH"
        }
    }

    private suspend fun chunks(walkId: String, kind: String, manifestHash: String, count: Int): List<JSONObject> {
        val points = mutableListOf<JSONObject>()
        for (i in 0 until (count + 255) / 256) {
            val page = fetch("$kind-chunk-$i", "/app/walks/$walkId/$kind/chunks/$i").json()
            require(page.getString("manifest_fingerprint") == manifestHash && page.long("chunk_index") == i.toLong())
            val chunk = page.getJSONArray("points").let { a -> (0 until a.length()).map(a::getJSONObject) }
            require(chunk.size == minOf(256, count - i * 256))
            val hash = if (kind == "motion-backup") WalkMotionContract.chunkDigest(chunk) else WalkPrecisionContract.chunkDigest(chunk)
            require(page.getString("chunk_fingerprint") == hash)
            points += chunk
        }
        return points
    }
}
