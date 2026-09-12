package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.WalkSyncState
import com.daengs.app.walk.store.RoomWalkFixLog
import com.daengs.app.walk.store.WalkDatabase
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
class WalkUploadSyncTest {
    @Test fun `잘못된 청크 확인은 Room 완료 상태를 바꾸지 않고 재전송 후 evidence와 봉인을 잇는다`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val log = RoomWalkFixLog(db.walkDao(), owner = { "owner" })
            log.openSession(RecordedSession(UPLOAD_SESSION, startedAtMillis = 1_000, endedAtMillis = 10_000))
            for (seq in 0 until 2_001) log.append(UPLOAD_SESSION, uploadFix(seq).copy(recordingEligible = false))
            val local = log.fixes(UPLOAD_SESSION)
            val events = mutableListOf<String>()
            var badAcknowledgement = true
            var lostFinalize = true
            val api = object : WalkApiClient {
                override val configured = true
                override suspend fun upload(token: String, session: RecordedSession, fixes: List<RecordedFix>): Result<String> = runCatching {
                    events += "create"
                    WalkUploadReceipt.verify(uploadReceipt(fixes, "replayed"), session.id, fixes)
                }
                override suspend fun appendPoints(token: String, walkId: String, clientSessionId: String, fixes: List<RecordedFix>): Result<Unit> = runCatching {
                    events += "append"
                    val response = uploadReceipt(fixes, "replayed")
                    if (badAcknowledgement) response.getJSONObject("chunk").put("point_count", local.size)
                    WalkUploadReceipt.verify(response, clientSessionId, fixes, walkId)
                    Unit
                }
                override suspend fun finalize(token: String, walkId: String, manifest: WalkFinalizeManifest): Result<Unit> = runCatching {
                    events += "finalize"
                    assertEquals(WalkSyncState.RAW_UPLOADED, log.session(UPLOAD_SESSION)!!.syncState)
                    assertEquals(WalkFinalizeManifest(2_001, 2_000), manifest)
                    assertEquals(UPLOAD_WALK, walkId)
                    if (lostFinalize) throw IOException("봉인 응답 유실")
                }
                override suspend fun list(token: String): Result<List<RemoteWalk>> = error("push only")
                override suspend fun detail(token: String, walkId: String): Result<RemoteWalkDetail> = error("recording owns GET")
            }
            val recording = WalkRecordingSync { _, path, method, _ ->
                if (path == "/entry-capabilities") {
                    JSONObject().put("gps_recording_versions", JSONArray(listOf(WalkRecordingContract.VERSION)))
                } else {
                    assertEquals("/$UPLOAD_WALK", path)
                    assertEquals("GET", method)
                    assertEquals(WalkSyncState.RAW_UPLOADED, log.session(UPLOAD_SESSION)!!.syncState)
                    events += "evidence"
                    legacyUpload(local).put("recording_receipt", JSONObject()
                        .put("contract_version", WalkRecordingContract.VERSION).put("policy_version", WalkRecordingContract.POLICY)
                        .put("point_count", local.size).put("known_point_count", local.size)
                        .put("raw_input_fingerprint", WalkRecordingContract.rawFingerprint(local))
                        .put("evidence_fingerprint", WalkRecordingContract.evidenceFingerprint(local)))
                }
            }
            fun newSync() = WalkSync(log, api, recording = recording, warn = { _, _ -> })

            assertTrue(runCatching { newSync().syncPendingSession("t", UPLOAD_SESSION) }.isFailure)
            assertEquals(WalkSyncState.LOCAL_ONLY, log.session(UPLOAD_SESSION)!!.syncState)
            assertNull(log.session(UPLOAD_SESSION)!!.serverWalkId)
            assertEquals(listOf("create", "append"), events)

            badAcknowledgement = false
            events.clear()
            assertTrue(runCatching { newSync().syncPendingSession("t", UPLOAD_SESSION) }.isFailure)
            assertEquals(listOf("create", "append", "evidence", "finalize"), events)
            assertEquals(WalkSyncState.RAW_UPLOADED, log.session(UPLOAD_SESSION)!!.syncState)
            assertEquals(UPLOAD_WALK, log.session(UPLOAD_SESSION)!!.serverWalkId)

            lostFinalize = false
            events.clear()
            newSync().syncPendingSession("t", UPLOAD_SESSION)
            assertEquals(listOf("evidence", "finalize"), events)
            assertEquals(WalkSyncState.DERIVED, log.session(UPLOAD_SESSION)!!.syncState)
            assertEquals(local, log.fixes(UPLOAD_SESSION))
        } finally { db.close() }
    }
}
