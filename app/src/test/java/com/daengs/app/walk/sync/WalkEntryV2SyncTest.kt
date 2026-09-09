package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.pin.*
import com.daengs.app.walk.store.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkEntryV2SyncTest {
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var pins: ActionPinStore
    private var now = 10_000L
    private var owner = "owner"
    private val id = UUID.randomUUID().toString()
    @Before fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        dao = db.walkDao()
        dao.insertSession(WalkSessionRow("s", 0, owner, null))
        pins = ActionPinStore(dao, { owner }, { now })
        pins.create(id, ActionPinRequest(UUID.randomUUID(), owner, "s", 0, now), WalkMomentType.SNIFFING, null)
        Unit
    }
    @After fun close() = db.close()
    private fun caps(write: Boolean = true) = JSONObject().put("read_versions", JSONArray(listOf("walk-entry-v2")))
        .put("write_versions", JSONArray(if (write) listOf("walk-entry-v2") else emptyList<String>()))
        .put("active_policy_versions", JSONArray(if (write) listOf("action-pin-policy-v1") else emptyList<String>()))
    private fun ack(body: JSONObject, revision: Int = 1, pinRevision: Int = 1, content: JSONObject? = null) = JSONObject()
        .put("id", id).put("revision", revision).put("pin_revision", pinRevision)
        .put("mutation_id", body.getString("mutation_id")).put("deleted", false)
        .put("content", content ?: body.getJSONObject("content")).put("pin", body.optJSONObject("pin") ?: JSONObject.NULL)

    @Test fun `응답 유실 뒤 로컬 확정되어도 최초 요청을 그대로 재전송하고 pin 종료를 이어 보낸다`() = runBlocking {
        var firstBody: String? = null
        var remote: JSONObject? = null
        var lose = true
        var puts = 0
        val sync = WalkEntryV2Sync(dao, { owner }) { _, path, method, body, v2 ->
            if (path == "/entry-capabilities") caps()
            else if (method == "GET") JSONObject().put("entries", JSONArray().put(remote!!))
            else {
                assertTrue(v2); puts++
                if (path.endsWith("/pin")) {
                    assertEquals(1, body!!.getInt("expected_revision"))
                    assertEquals(1, body.getInt("expected_pin_revision"))
                    remote = ack(body, 2, 2, remote!!.getJSONObject("content"))
                } else if (firstBody == null) {
                    firstBody = body.toString(); remote = ack(body!!)
                } else assertEquals(firstBody, body.toString())
                if (lose) { lose = false; throw IOException("lost ACK") }
                remote!!
            }
        }
        try { sync.sync("token", "s", "walk"); fail() } catch (_: IOException) { }
        assertNotNull(dao.entry(id)!!.pendingRequest)
        now = 18_000; pins.finish(id)
        assertEquals("unlocated", dao.entry(id)!!.entry()!!.pin!!.state)
        sync.sync("token", "s", "walk")
        val saved = dao.entry(id)!!
        assertEquals(3, puts)
        assertEquals(2, saved.revision)
        assertEquals(2, saved.pinRevision)
        assertFalse(saved.dirty); assertFalse(saved.pinDirty)
        assertNull(saved.pendingRequest)
        assertEquals("unlocated", saved.entry()!!.pin!!.state)
    }

    @Test fun `삭제 중 도착한 생성 ACK는 좌표와 본문을 복원하지 않는다`() = runBlocking {
        val sent = dao.preparePinRequest(id, owner)!!
        val response = ack(PinPending(JSONObject(sent)).body)
        WalkEntryStore(dao).delete(id)
        dao.ackPinRequest(id, sent, response.toString(), owner)
        dao.acceptPinRemote("s", response.toString(), owner)
        val row = dao.entry(id)!!
        assertNull(row.payload); assertNull(row.pinPayload); assertNull(row.pendingRequest)
        assertTrue(row.dirty)
        assertEquals("delete", PinPending(JSONObject(dao.preparePinRequest(id, owner)!!)).kind)
    }

    @Test fun `서버 삭제 표식은 충돌 상태와 미전송 정정 및 핀을 모두 파기한다`() = runBlocking {
        dao.preparePinRequest(id, owner)
        dao.acceptPinRemote("s", JSONObject().put("id", id).put("deleted", true)
            .put("revision", 5).put("mutation_id", UUID.randomUUID()).toString(), owner)
        val row = dao.entry(id)!!
        assertNull(row.payload); assertNull(row.pinPayload); assertNull(row.pendingRequest)
        assertFalse(row.dirty); assertFalse(row.pinDirty)
        now = 18_000; pins.recover()
        assertEquals(row, dao.entry(id))
    }

    @Test fun `쓰기 비활성화는 로컬 기록을 유지하고 v1로 우회하지 않는다`() = runBlocking {
        val sync = WalkEntryV2Sync(dao, { owner }) { _, path, method, _, _ ->
            assertEquals("GET", method)
            if (path == "/entry-capabilities") caps(false) else JSONObject().put("entries", JSONArray())
        }
        try { sync.sync("token", "s", "walk"); fail() } catch (_: IOException) { }
        assertNotNull(dao.entry(id)!!.payload)
        assertTrue(dao.entry(id)!!.dirty)
        assertNull(dao.entry(id)!!.pendingRequest)
    }

    @Test fun `옛 서버도 위치 없는 행동을 v1에 전송하지 않는다`() = runBlocking {
        var calls = 0
        val sync = WalkEntryV2Sync(dao, { owner }) { _, path, _, _, _ ->
            calls++; assertEquals("/entry-capabilities", path)
            throw WalkHttpException(404, "missing")
        }
        try { sync.sync("token", "s", "walk"); fail() } catch (_: IOException) { }
        assertEquals(1, calls)
        assertNotNull(dao.entry(id)!!.payload)
    }

    @Test fun `일시정지 cutoff를 지원하지 않는 서버에는 안전하게 보류하고 지원 후 원본 시각을 전송한다`() = runBlocking {
        now = 18_000; pins.finishSession("s", 11_000)
        var supports = false
        var remote: JSONObject? = null
        var writes = 0
        val sync = WalkEntryV2Sync(dao, { owner }) { _, path, method, body, _ ->
            if (path == "/entry-capabilities") caps().put("pin_observation_cutoff_supported", supports)
            else if (method == "GET") JSONObject().put("entries", JSONArray().apply { remote?.let { put(it) } })
            else {
                writes++
                assertEquals("1970-01-01T00:00:11Z", body!!.getJSONObject("pin").getString("observation_cutoff_at"))
                ack(body).also { remote = it }
            }
        }
        try { sync.sync("token", "s", "walk"); fail() } catch (_: IOException) { }
        assertEquals(0, writes)
        assertNotNull(dao.entry(id)!!.payload)
        supports = true; sync.sync("token", "s", "walk")
        assertEquals(1, writes)
        assertFalse(dao.entry(id)!!.dirty)
    }

    @Test fun `서버가 먼저 핀을 확정한 CAS 충돌은 원격 확정을 채택한다`() = runBlocking {
        val sent = dao.preparePinRequest(id, owner)!!
        val created = ack(PinPending(JSONObject(sent)).body)
        dao.ackPinRequest(id, sent, created.toString(), owner)
        now = 18_000; pins.finish(id)
        val pending = dao.preparePinRequest(id, owner)!!
        val final = ack(PinPending(JSONObject(pending)).body, 3, 2, created.getJSONObject("content"))
        val sync = WalkEntryV2Sync(dao, { owner }) { _, path, method, _, _ ->
            if (path == "/entry-capabilities") caps()
            else if (method == "PUT") throw WalkHttpException(409, "conflict")
            else JSONObject().put("entries", JSONArray().put(final))
        }
        sync.sync("token", "s", "walk")
        assertEquals(3, dao.entry(id)!!.revision)
        assertFalse(dao.entry(id)!!.pinDirty)
        assertNull(dao.entry(id)!!.pendingRequest)
    }

    @Test fun `위치 검증 거부는 기기 원본을 보관하고 수정 가능한 오류로 표시한다`() = runBlocking {
        val sync = WalkEntryV2Sync(dao, { owner }) { _, path, method, _, _ ->
            if (path == "/entry-capabilities") caps()
            else if (method == "PUT") throw WalkHttpException(422, "invalid")
            else JSONObject().put("entries", JSONArray())
        }
        sync.sync("token", "s", "walk")
        assertNotNull(dao.entry(id)!!.payload)
        assertNotNull(dao.entry(id)!!.syncError)
        assertTrue(dao.entry(id)!!.dirty)
    }

    @Test fun `통신 중 계정 전환은 다른 계정으로 ACK를 적용하지 않는다`() = runBlocking {
        val sync = WalkEntryV2Sync(dao, { owner }) { _, path, _, body, _ ->
            if (path == "/entry-capabilities") caps() else {
                owner = "other"; ack(body!!)
            }
        }
        try { sync.sync("token", "s", "walk"); fail() } catch (_: IllegalStateException) { }
        assertEquals(0, dao.entry(id)!!.revision)
        assertTrue(dao.entry(id)!!.dirty)
    }

    @Test fun `내용 정정 중 핀 확정되어도 원래 content 요청과 새 pin 요청을 분리한다`() = runBlocking {
        val first = dao.preparePinRequest(id, owner)!!
        dao.ackPinRequest(id, first, ack(PinPending(JSONObject(first)).body).toString(), owner)
        val entry = dao.entry(id)!!.entry()!!
        WalkEntryStore(dao).save(entry.copy(type = WalkMomentType.BARKING))
        val contentSent = dao.preparePinRequest(id, owner)!!
        val pending = PinPending(JSONObject(contentSent))
        assertEquals("content", pending.kind)
        assertFalse(pending.body.has("pin"))
        now = 18_000; pins.finish(id)
        val response = ack(pending.body, 2).put("pin", JSONObject(first).getJSONObject("body").getJSONObject("pin"))
        dao.ackPinRequest(id, contentSent, response.toString(), owner)
        assertFalse(dao.entry(id)!!.dirty)
        assertTrue(dao.entry(id)!!.pinDirty)
        assertEquals("pin", PinPending(JSONObject(dao.preparePinRequest(id, owner)!!)).kind)
    }
}
