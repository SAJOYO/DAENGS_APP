package com.daengs.app.walk.sync

import android.app.Application
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.store.WalkEntryRow
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WalkRecordingSyncTest {
    private fun fixture() = JSONObject(javaClass.getResource("/walk/gps-recording-v1.json")!!.readText())
    private fun fixes(): List<RecordedFix> = RemoteWalkDetail.parse(fixture().getJSONObject("upload")
        .put("id", "walk")).fixes
    private fun receipt(points: List<RecordedFix>) = JSONObject()
        .put("contract_version", WalkRecordingContract.VERSION).put("policy_version", WalkRecordingContract.POLICY)
        .put("point_count", points.size).put("known_point_count", points.count { it.recordingEligible != null })
        .put("raw_input_fingerprint", WalkRecordingContract.rawFingerprint(points))
        .put("evidence_fingerprint", WalkRecordingContract.evidenceFingerprint(points))
    private fun detail(points: List<RecordedFix>) = fixture().getJSONObject("upload").put("id", "walk")
        .put("points", points.toUploadPoints()).put("recording_receipt", receipt(points))

    @Test fun `legacy compatibility only accepts explicit lack of recording support`() = runBlocking {
        assertFalse(WalkRecordingSync { _, _, _, _ -> JSONObject() }.supports("t"))
        assertFalse(WalkRecordingSync { _, _, _, _ -> throw WalkHttpException(404, "old") }.supports("t"))
        assertTrue(WalkRecordingSync { _, _, _, _ -> JSONObject().put("gps_recording_versions",
            JSONArray(listOf(WalkRecordingContract.VERSION))) }.supports("t"))
        for (failure in listOf(IOException("offline"), WalkHttpException(401, "login"), WalkHttpException(500, "server"))) {
            assertSame(failure, runCatching { WalkRecordingSync { _, _, _, _ -> throw failure }.supports("t") }.exceptionOrNull())
        }
        var account = "first"
        val changed = WalkRecordingSync { _, _, _, _ -> account = "second"; throw WalkHttpException(404, "old") }
        assertTrue(runCatching { changed.supports("t") { check(account == "first") } }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun `legacy release still verifies and repairs recording metadata on supporting servers`() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            com.daengs.app.walk.store.WalkDatabase::class.java).build()
        try {
            val log = com.daengs.app.walk.store.RoomWalkFixLog(db.walkDao(), owner = { "owner" })
            val local = fixes()
            log.openSession(RecordedSession("s", startedAtMillis = local.first().atMillis, endedAtMillis = local.first().atMillis + 60_000))
            local.forEach { log.append("s", it) }
            var uploads = 0
            var finalized = 0
            val api = object : WalkApiClient {
                override val configured = true
                override suspend fun upload(token: String, session: RecordedSession, fixes: List<RecordedFix>): Result<String> {
                    uploads++; assertEquals(local, fixes); return Result.success("walk")
                }
                override suspend fun appendPoints(token: String, walkId: String, clientSessionId: String, fixes: List<RecordedFix>): Result<Unit> = error("single batch")
                override suspend fun finalize(token: String, walkId: String, manifest: WalkFinalizeManifest): Result<Unit> {
                    finalized++; return Result.success(Unit)
                }
                override suspend fun list(token: String): Result<List<RemoteWalk>> = error("push only")
                override suspend fun detail(token: String, walkId: String): Result<RemoteWalkDetail> = error("recording transport owns receipt")
            }
            var stored = local.map { it.copy(recordingEligible = null) }
            var retainRepair = false
            var repairs = 0
            val recording = WalkRecordingSync { _, path, method, _ ->
                when {
                    path == "/entry-capabilities" -> JSONObject().put("gps_recording_versions", JSONArray(listOf(WalkRecordingContract.VERSION)))
                    method == "GET" -> detail(stored)
                    else -> { repairs++; if (retainRepair) stored = local; receipt(stored) }
                }
            }
            val sync = WalkSync(log, api, recording = recording, requireRecordingSupport = false, warn = { _, _ -> })
            assertTrue(runCatching { sync.syncPendingSession("t", "s") }.isFailure)
            assertEquals(0, finalized)
            assertEquals(com.daengs.app.walk.WalkSyncState.RAW_UPLOADED, log.session("s")!!.syncState)
            retainRepair = true
            sync.syncPendingSession("t", "s")
            assertEquals(1, uploads)
            assertEquals(1, finalized)
            assertEquals(2, repairs)
            assertEquals(local, stored)
            assertEquals(false, log.fixes("s").single().recordingEligible)
        } finally { db.close() }
    }

    @Test fun `production serializers agree with the shared server contract and preserve unknown`() {
        val shared = fixture()
        val points = fixes()
        val expected = shared.getJSONObject("expected_receipt")
        assertEquals(expected.getString("raw_input_fingerprint"), WalkRecordingContract.rawFingerprint(points))
        assertEquals(expected.getString("evidence_fingerprint"), WalkRecordingContract.evidenceFingerprint(points))
        val upload = shared.getJSONObject("upload")
        val session = RecordedSession(upload.getString("client_session_id"),
            startedAtMillis = upload.getString("started_at").isoToMillis(),
            endedAtMillis = upload.getString("ended_at").isoToMillis())
        val actual = WalkApi.uploadBody(session, points)
        assertFalse(actual.getJSONArray("points").getJSONObject(0).getBoolean("recording_eligible"))
        assertEquals(false, RemoteWalkDetail.parse(detail(points)).fixes.single().recordingEligible)
        val legacy = points.map { it.copy(recordingEligible = null) }
        assertNull(RemoteWalkDetail.parse(detail(legacy)).fixes.single().recordingEligible)
        assertEquals(WalkRecordingContract.rawFingerprint(points), WalkRecordingContract.rawFingerprint(legacy))
        val original = shared.getJSONObject("pin_request")
        val row = WalkEntryRow("entry", session.id, original.getJSONObject("content").toString(),
            0, "local", true, isV2 = true, pinPayload = original.getJSONObject("pin").toString())
        val pending = PinPending.from(row, recordingEvidence = expected.getString("evidence_fingerprint"))
        // The paired Python contract test consumes these actual Kotlin-produced requests.
        val out = File("build/outputs/contracts/gps-recording-v1.json")
        requireNotNull(out.parentFile).mkdirs()
        out.writeText(shared.put("upload", actual).put("pin_request", pending.body).toString(2))
    }

    @Test fun `lost repair acknowledgement is retried without rewriting known metadata`() = runBlocking {
        val local = fixes()
        var stored = local.map { it.copy(recordingEligible = null) }
        var puts = 0
        val sync = WalkRecordingSync { _, _, method, body ->
            if (method == "GET") detail(stored) else {
                puts++
                assertEquals(false, body!!.getJSONArray("points").getJSONObject(0).getBoolean("recording_eligible"))
                stored = local
                throw IOException("lost ack")
            }
        }
        assertTrue(runCatching { sync.ensure("token", "walk", local) }.exceptionOrNull() is IOException)
        assertEquals(WalkRecordingContract.evidenceFingerprint(local), sync.ensure("token", "walk", local))
        assertEquals(1, puts)
    }

    @Test fun `success response without stored metadata cannot release synchronization`() = runBlocking {
        val local = fixes()
        val old = local.map { it.copy(recordingEligible = null) }
        val sync = WalkRecordingSync { _, _, method, _ -> if (method == "GET") detail(old) else receipt(old) }
        assertTrue(runCatching { sync.ensure("token", "walk", local) }.isFailure)
    }

    @Test fun `raw or known metadata mismatch cannot issue a repair`() = runBlocking {
        for (stored in listOf(fixes().map { it.copy(lat = 38.0) }, fixes().map { it.copy(recordingEligible = true) })) {
            var puts = 0
            val sync = WalkRecordingSync { _, _, method, _ ->
                if (method != "GET") puts++
                detail(stored)
            }
            assertTrue(runCatching { sync.ensure("token", "walk", fixes()) }.isFailure)
            assertEquals(0, puts)
        }
    }

    @Test fun `unsupported server and account transition preserve the pending evidence`() = runBlocking {
        assertTrue(runCatching { WalkRecordingSync { _, _, _, _ -> JSONObject() }.requireSupport("t") }.exceptionOrNull() is IOException)
        assertTrue(runCatching { WalkRecordingSync { _, _, _, _ -> throw WalkHttpException(404, "old server") }.requireSupport("t") }.exceptionOrNull() is IOException)
        var owner = "first"
        var calls = 0
        val sync = WalkRecordingSync { _, _, _, _ -> calls++; owner = "second"; detail(fixes()) }
        assertTrue(runCatching { sync.ensure("t", "walk", fixes()) { check(owner == "first") } }.isFailure)
        assertEquals(1, calls)
    }

    @Test fun `repairs more than two thousand points in bounded batches and verifies the final receipt`() = runBlocking {
        val sample = fixes().single()
        val local = (0..2000).map { sample.copy(clientSeq = it, recordingEligible = it % 2 == 0) }
        var stored = local.map { it.copy(recordingEligible = null) }
        val sizes = mutableListOf<Int>()
        val sync = WalkRecordingSync { _, _, method, body ->
            if (method == "GET") detail(stored) else {
                val changes = body!!.getJSONArray("points")
                sizes += changes.length()
                val seqs = (0 until changes.length()).map { changes.getJSONObject(it).getInt("client_seq") }.toSet()
                stored = stored.map { if (it.clientSeq in seqs) local[it.clientSeq] else it }
                receipt(stored)
            }
        }
        assertEquals(WalkRecordingContract.evidenceFingerprint(local), sync.ensure("t", "walk", local))
        assertEquals(listOf(2000, 1), sizes)
    }
}
