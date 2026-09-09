package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.WalkPhotoCapture
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.store.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkPhotoSyncTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var directory: File
    private lateinit var store: WalkPhotoStore
    private var owner = "a"
    private val session = UUID.randomUUID().toString()
    private val walk = UUID.randomUUID().toString()
    private val captured = WalkPhotoCapture(session, "a", 2000,
        LocationSample(GeoPoint(37.5, 127.0), 1900, 1_000_000_000, 5f))
    private val sent = mutableListOf<String>()

    @Before fun open(): Unit = runBlocking {
        context.deleteDatabase("photo-sync-test.db")
        directory = temporary.newFolder("photos")
        reopen()
        dao.insertSession(WalkSessionRow(session, startedAtMillis = 1000, endedAtMillis = 6000,
            ownerId = owner, serverWalkId = walk, syncState = "derived"))
    }
    private fun reopen() {
        db = Room.databaseBuilder(context, WalkDatabase::class.java, "photo-sync-test.db").build()
        dao = db.walkDao()
        store = WalkPhotoStore(dao, directory) { owner }
    }
    @After fun close() { db.close(); context.deleteDatabase("photo-sync-test.db") }
    private suspend fun photo() = store.save(captured, temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) })
    private fun ack(body: JSONObject) = JSONObject().apply {
        put("format", "walk-photo-metadata-v1"); put("status", "complete")
        put("client_session_id", session); put("publisher_id", body.getString("publisher_id"))
        put("revision", body.getLong("revision")); put("records", JSONArray())
    }
    private fun syncer(put: suspend (JSONObject) -> JSONObject = { ack(it) }) =
        WalkPhotoSync(dao, { owner }) { _, path, method, body ->
            if (method == "GET") {
                assertEquals("/photo-metadata/capabilities", path)
                JSONObject().put("write_versions", JSONArray().put("walk-photo-metadata-v1")).put("max_records", 200)
            } else {
                assertEquals("/$walk/photo-metadata", path)
                sent += body!!.toString()
                put(body)
            }
        }

    @Test fun `사진 메타데이터만 보내고 위치 샘플 시각을 보존한다`() = runBlocking {
        val photo = photo()
        syncer().sync("token", session, walk)
        val wire = JSONObject(sent.single()).getJSONArray("photos").getJSONObject(0)
        assertEquals("1970-01-01T00:00:02Z", wire.getString("captured_at"))
        assertEquals("1970-01-01T00:00:01.900Z", wire.getString("location_captured_at"))
        assertEquals(setOf("id", "captured_at", "location_captured_at", "point", "accuracy_m"),
            wire.keys().asSequence().toSet())
        assertArrayEquals(byteArrayOf(1, 2, 3), photo.file.readBytes())
        assertTrue(dao.dirtyPhotoSessions().isEmpty())
        assertEquals(dao.photoSync(session)!!.revision, dao.photoSync(session)!!.acknowledgedRevision)
    }

    @Test fun `응답 유실과 재실행 뒤에도 같은 요청을 재전송한다`() = runBlocking {
        photo()
        assertTrue(runCatching { syncer { throw IOException("lost ACK") }.sync("token", session, walk) }.isFailure)
        val original = dao.photoSync(session)!!.pendingPayload!!
        db.close(); reopen()
        syncer().sync("token", session, walk)
        assertEquals(listOf(original, original), sent)
        assertNull(dao.photoSync(session)!!.pendingPayload)
        assertTrue(dao.dirtyPhotoSessions().isEmpty())
    }

    @Test fun `전송 중 추가한 사진은 이전 응답으로 완료 처리되지 않는다`() = runBlocking {
        photo()
        var first = true
        syncer { body -> if (first) { first = false; photo() }; ack(body) }.sync("token", session, walk)
        assertEquals(listOf(1, 2), sent.map { JSONObject(it).getJSONArray("photos").length() })
        val requests = sent.map(::JSONObject)
        assertEquals(requests.first().getLong("revision"), requests.last().getLong("expected_revision"))
        assertTrue(dao.dirtyPhotoSessions().isEmpty())
    }

    @Test fun `응답을 잃고 삭제한 사진은 원래 요청 확인 뒤 삭제 목록으로 전송한다`() = runBlocking {
        val photo = photo()
        runCatching { syncer { throw IOException("lost") }.sync("token", session, walk) }
        store.delete(photo.id)
        syncer().sync("token", session, walk)
        assertEquals(listOf(1, 1, 0), sent.map { JSONObject(it).getJSONArray("photos").length() })
        assertFalse(photo.file.exists())
        assertTrue(dao.dirtyPhotoSessions().isEmpty())
    }

    @Test fun `서버에서 복원한 빈 산책은 사진 없음 목록을 전송하지 않는다`() = runBlocking {
        syncer().sync("token", session, walk)
        assertTrue(sent.isEmpty())
        assertNull(dao.photoSync(session))
    }

    @Test fun `원 촬영 기기의 사진 없는 세션은 빈 목록을 명시적으로 보낼 수 있다`() = runBlocking {
        dao.insertPhotoSync(WalkPhotoSyncRow(session, owner, UUID.randomUUID().toString()))
        syncer().sync("token", session, walk)
        assertEquals(0, JSONObject(sent.single()).getJSONArray("photos").length())
    }

    @Test fun `로컬 시작만 사진 게시자를 만들고 서버 복원은 만들지 않는다`() = runBlocking {
        val log = RoomWalkFixLog(dao) { owner }
        val local = UUID.randomUUID().toString()
        val restored = UUID.randomUUID().toString()
        log.openSession(RecordedSession(id = local, startedAtMillis = 1000, ownerId = owner))
        log.restoreSession(RecordedSession(id = restored, startedAtMillis = 1000,
            endedAtMillis = 6000, ownerId = owner, serverWalkId = walk))
        assertNotNull(dao.photoSync(local))
        assertNull(dao.photoSync(restored))
    }

    @Test fun `계정이 응답 도중 바뀌면 전송 완료로 표시하지 않는다`() = runBlocking {
        photo()
        syncer { body -> owner = "b"; ack(body) }.sync("token", session, walk)
        assertEquals(0L, dao.photoSync(session)!!.acknowledgedRevision)
        assertNotNull(dao.photoSync(session)!!.pendingPayload)
        syncer().sync("new-token", session, walk)
        assertEquals(1, sent.size)
    }

    @Test fun `다른 서버 산책 ID로 사진을 전송하지 않는다`() = runBlocking {
        photo()
        syncer().sync("token", session, UUID.randomUUID().toString())
        assertTrue(sent.isEmpty())
    }

    @Test fun `서버 기능이 비활성이면 대기 원본을 보존한다`() = runBlocking {
        photo()
        WalkPhotoSync(dao, { owner }) { _, _, method, _ ->
            assertEquals("GET", method)
            JSONObject().put("write_versions", JSONArray())
        }.sync("token", session, walk)
        assertEquals(0L, dao.photoSync(session)!!.acknowledgedRevision)
        assertNotNull(dao.photos(session).single())
    }

    @Test fun `다른 게시자 응답이나 충돌이 와도 완료 버전은 전진하지 않는다`() = runBlocking {
        photo()
        assertTrue(runCatching {
            syncer { ack(it).put("publisher_id", UUID.randomUUID().toString()) }.sync("token", session, walk)
        }.isFailure)
        assertEquals(0L, dao.photoSync(session)!!.acknowledgedRevision)
        assertNotNull(dao.photoSync(session)!!.pendingPayload)
    }
}
