package com.daengs.app.ui.walk.review

import android.app.Application
import com.daengs.app.walk.sync.WalkMotionContract
import com.daengs.app.walk.sync.WalkPrecisionContract
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MotionRecordReviewCaptureTest {
    @get:Rule val temporary = TemporaryFolder()
    private val remote = "22222222-2222-2222-2222-222222222222"
    private fun cases() = JSONObject(javaClass.getResource("/walk/gps-motion-precision-v1.json")!!.readText())
        .getJSONArray("cases").let { a -> (0 until a.length()).map(a::getJSONObject) }
    private fun walking() = cases().first { it.getString("name") == "walking" }
    private fun detail(c: JSONObject): JSONObject {
        val manifest = c.getJSONObject("manifest")
        val epochs = manifest.getJSONArray("epochs")
        return JSONObject().put("id", remote).put("client_session_id", manifest.getString("client_session_id"))
            .put("started_at", Instant.ofEpochMilli(epochs.getJSONObject(0).getLong("started_at_millis")))
            .put("ended_at", Instant.ofEpochMilli(epochs.getJSONObject(epochs.length() - 1).getLong("ended_at_millis")))
            .put("points", c.getJSONArray("raw_points"))
    }

    private fun response(c: JSONObject, path: String): MotionReviewResponse {
        val value = when {
            path == "/app/walks" -> JSONObject().put("walks", JSONArray().put(detail(c)))
            path.endsWith("motion-capabilities") -> JSONObject().put("backup_supported", true)
            path == "/app/walks/$remote" -> detail(c)
            path.contains("motion-backup") || path.contains("motion-precision") -> {
                val precision = path.contains("motion-precision")
                val m = c.getJSONObject(if (precision) "precision_manifest" else "manifest")
                val points = c.getJSONArray(if (precision) "precision_points" else "points")
                val objects = (0 until points.length()).map(points::getJSONObject)
                val hash = if (precision) WalkPrecisionContract.manifestDigest(m) else WalkMotionContract.manifestDigest(m)
                if (path.contains("/chunks/")) {
                    val index = path.substringAfterLast('/').toInt()
                    val chunk = objects.chunked(256)[index]
                    JSONObject().put("manifest_fingerprint", hash).put("chunk_index", index)
                        .put("points", JSONArray(chunk)).put("chunk_fingerprint",
                            if (precision) WalkPrecisionContract.chunkDigest(chunk) else WalkMotionContract.chunkDigest(chunk))
                } else JSONObject().put("version", if (precision) WalkPrecisionContract.VERSION else WalkMotionContract.VERSION)
                    .put("state", "complete").put("calculation_verified", false).put("manifest", m)
                    .put("manifest_fingerprint", hash).put("received_chunks", JSONArray(objects.chunked(256).indices.toList()))
                    .put("evidence_fingerprint", c.getString(if (precision) "precision_fingerprint" else "evidence_fingerprint"))
            }
            else -> return MotionReviewResponse(404, "{}")
        }
        return MotionReviewResponse(200, value.toString())
    }

    private fun capture(folder: File, check: () -> Unit = {}, get: suspend (String) -> MotionReviewResponse) =
        MotionRecordReviewCapture("fixture-owner", "synthetic-test", folder, check, get)

    @Test fun `32 complete backups run through actual Kotlin without claiming device original verification`() = runBlocking {
        for (c in cases()) {
            val name = c.getString("name")
            val folder = System.getenv("DAENGS_MOTION_REVIEW_FIXTURE_OUTPUT")?.let { File(it, name) }
                ?: File(temporary.root, name)
            capture(folder) { path -> response(c, path) }.run(remote)
            val replay = JSONObject(File(folder, "kotlin-replay.json").readText())
            val expected = c.getJSONObject("expected")
            val summary = replay.getJSONObject("summary")
            assertFalse(replay.getBoolean("independent_device_input_verified"))
            assertEquals("device-fix-bits-v1", replay.getString("coordinate_basis"))
            assertEquals(expected.getDouble("distance_m"), summary.getDouble("distance_m"), 1e-7)
            assertEquals(expected.getJSONArray("segments").toString(), summary.getJSONArray("segments").toString())
            assertEquals(c.getJSONArray("points").length(), replay.getJSONArray("steps").length())
        }
    }

    @Test fun `inventory distinguishes missing collecting conflict unavailable and complete backups`() = runBlocking {
        val folder = File(temporary.root, "inventory")
        val states = listOf(404 to "not_found", 200 to "collecting", 409 to "conflict", 503 to "unavailable", 200 to "complete")
        var current = 0
        capture(folder) { path ->
            when {
                path == "/app/walks" -> MotionReviewResponse(200, JSONObject().put("walks", JSONArray(states.indices.map { i ->
                    detail(walking()).put("id", "22222222-2222-2222-2222-${i.toString().padStart(12, '0')}")
                })).toString())
                path.endsWith("motion-backup") -> states[current++].let { (code, state) ->
                    MotionReviewResponse(code, JSONObject().put("state", state).toString())
                }
                else -> response(walking(), path)
            }
        }.run()
        val rows = JSONObject(File(folder, "inventory.json").readText()).getJSONArray("records")
        assertEquals(states.map { it.second }, (0 until rows.length()).map { rows.getJSONObject(it).getString("backup_state") })
    }

    @Test fun `cursor pages are followed and repeated cursors reject an incomplete inventory`() = runBlocking {
        for (repeat in listOf(false, true)) {
            val folder = File(temporary.root, "pages-$repeat")
            val paths = mutableListOf<String>()
            val result = runCatching { capture(folder) { path ->
                paths += path
                if (path == "/app/walks" || path.contains("?cursor=")) MotionReviewResponse(200,
                    JSONObject().put("walks", JSONArray()).put("next_cursor",
                        if (path == "/app/walks" || repeat) "next+token=" else JSONObject.NULL).toString())
                else response(walking(), path)
            }.run() }
            assertTrue(paths.contains("/app/walks?cursor=next%2Btoken%3D"))
            assertEquals(repeat, result.isFailure)
        }
    }

    @Test fun `unavailable capability is not an empty successful motion inventory`() = runBlocking {
        val folder = File(temporary.root, "unavailable")
        capture(folder) { MotionReviewResponse(503, "{}") }.run()
        assertEquals("backup_unavailable", JSONObject(File(folder, "inventory.json").readText()).getString("state"))
    }

    @Test fun `missing precision replays coarse input with a distinct basis`() = runBlocking {
        val folder = File(temporary.root, "coarse")
        capture(folder) { path -> if (path.endsWith("motion-precision")) MotionReviewResponse(404, "{}") else response(walking(), path) }.run(remote)
        assertEquals("stored-raw-v1-six-decimals", JSONObject(File(folder, "kotlin-replay.json").readText()).getString("coordinate_basis"))
    }

    @Test fun `corrupt hash order incomplete receipt and unknown policy never produce a replay`() = runBlocking {
        val edits = listOf<Pair<String, (JSONObject) -> Unit>>(
            "motion-backup" to { it.put("state", "collecting") },
            "motion-backup" to { it.put("evidence_fingerprint", "sha256:wrong") },
            "motion-backup" to { it.getJSONObject("manifest").getJSONObject("policy").put("version", "future") },
            "motion-backup/chunks/0" to { it.put("chunk_fingerprint", "wrong") },
            "motion-backup/chunks/0" to { it.getJSONArray("points").getJSONObject(0).put("client_seq", 3) },
            "motion-precision" to { it.put("state", "collecting") },
        )
        for ((index, edit) in edits.withIndex()) {
            val folder = File(temporary.root, "invalid-$index")
            assertTrue(runCatching { capture(folder) { path ->
                val result = response(walking(), path)
                if (path.endsWith(edit.first)) result.copy(body = JSONObject(result.body).also(edit.second).toString()) else result
            }.run(remote) }.isFailure)
            assertFalse(File(folder, "kotlin-replay.json").exists())
        }
    }

    @Test fun `account change rejects the response before writing its body`() = runBlocking {
        val folder = File(temporary.root, "account")
        var valid = true
        assertTrue(runCatching { capture(folder, { check(valid) }) { path ->
            response(walking(), path).also { valid = false }
        }.run(remote) }.isFailure)
        assertFalse(File(folder, "detail.json").exists())
        assertEquals("running", JSONObject(File(folder, "status.json").readText()).getString("state"))
    }

    @Test fun `path allowlist permits only encoded owner walk reads`() {
        for (path in listOf("/app/walks", "/app/walks?cursor=a%2Bb%3D", "/app/walks/$remote/motion-backup/chunks/39",
            "/app/walks/$remote/trajectory-calculation?version=walk-trajectory-calculation-v1")) assertTrue(allowedMotionReviewPath(path))
        for (path in listOf("https://evil.example/app/walks", "/app/walks/$remote/finalize", "/app/walks/$remote/motion-backup/complete",
            "/app/walks/../pets", "/app/walks?cursor=x&owner=other")) assertFalse(allowedMotionReviewPath(path))
    }
}
