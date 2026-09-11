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

    @Test fun `과거 v1 산책은 capabilities 미지원 확인 후 저장 조회 수정 삭제한다`() = runBlocking {
        val store = WalkEntryStore(dao)
        dao.insertSession(WalkSessionRow("legacy", 0, owner, null))
        store.save(com.daengs.app.walk.WalkEntry("old", "legacy", WalkMomentType.SNIFFING, now,
            com.daengs.app.location.GeoPoint(37.5, 127.0), now - 1, 5f))
        val calls = mutableListOf<String>()
        var remote: JSONObject? = null
        var revision = 0
        val v2 = WalkEntryV2Sync(dao, { owner }) { _, path, method, _, v2 ->
            assertFalse(v2)
            assertEquals("/entry-capabilities", path)
            assertEquals("GET", method)
            calls += "capabilities"
            throw WalkHttpException(404, "missing")
        }
        val sync = WalkEntrySync(dao, v2, owner = { owner }) { _, path, method, body ->
            calls += method
            assertTrue(path.startsWith("/walk/entries"))
            if (method == "GET") JSONObject().put("entries", JSONArray().apply { remote?.let { put(it) } })
            else {
                revision++
                if (method == "PUT") { assertFalse(body!!.has("pin")); assertTrue(body.getJSONObject("content").has("location")) }
                JSONObject().put("id", "old").put("revision", revision)
                    .put("mutation_id", body?.getString("mutation_id") ?: "deleted")
                    .put("content", body?.getJSONObject("content") ?: JSONObject.NULL).also { remote = it }
            }
        }
        sync.sync("token", "legacy", "walk")
        assertFalse(dao.entry("old")!!.dirty)
        store.save(dao.entry("old")!!.entry()!!.copy(type = WalkMomentType.BARKING))
        sync.sync("token", "legacy", "walk")
        assertEquals(WalkMomentType.BARKING, dao.entry("old")!!.entry()!!.type)
        store.delete("old")
        sync.sync("token", "legacy", "walk")
        assertEquals(listOf("capabilities", "PUT", "GET", "capabilities", "PUT", "GET",
            "capabilities", "DELETE", "GET"), calls)
        assertNull(dao.entry("old")!!.payload)
        assertFalse(dao.entry("old")!!.dirty)
    }

    @Test fun `혼합 산책은 v2 지원이 없으면 보류하고 기존 v2 영수증과 핀을 보존한다`() = runBlocking {
        dao.preparePinRequest(id, owner)
        val original = dao.entry(id)!!
        WalkEntryStore(dao).save(com.daengs.app.walk.WalkEntry("legacy", "s", WalkMomentType.BARKING, now,
            com.daengs.app.location.GeoPoint(37.5, 127.0), now - 1, 5f))
        var probes = 0
        val v2 = WalkEntryV2Sync(dao, { owner }) { _, path, method, _, v2 ->
            probes++
            assertFalse(v2)
            assertEquals("GET", method)
            assertEquals("/entry-capabilities", path)
            throw WalkHttpException(404, "missing")
        }
        val sync = WalkEntrySync(dao, v2, owner = { owner }) { _, _, _, _ ->
            error("혼합 산책을 v1으로 우회하면 안 된다")
        }
        try { sync.sync("token", "s", "walk"); fail() } catch (_: IOException) { }
        assertEquals(1, probes)
        assertTrue(dao.entry("legacy")!!.dirty)
        assertEquals(original, dao.entry(id))
    }

    @Test fun `혼합 산책은 기존 메모를 v1으로 새 행동을 v2로 보내고 v2 목록을 읽는다`() = runBlocking {
        WalkEntryStore(dao).save(com.daengs.app.walk.WalkEntry("legacy", "s", WalkMomentType.NOTE,
            now, note = "과거 메모"))
        val calls = mutableListOf<Triple<String, String, Boolean>>()
        var behavior: JSONObject? = null
        var note: JSONObject? = null
        val v2 = WalkEntryV2Sync(dao, { owner }) { _, path, method, body, useV2 ->
            calls += Triple(method, path, useV2)
            when {
                path == "/entry-capabilities" -> caps()
                method == "GET" -> JSONObject().put("entries", JSONArray().put(behavior!!).put(note!!))
                path.endsWith("/legacy") -> {
                    assertFalse(useV2)
                    assertFalse(body!!.has("pin"))
                    JSONObject().put("id", "legacy").put("revision", 1)
                        .put("mutation_id", body.getString("mutation_id"))
                        .put("content", body.getJSONObject("content")).also {
                            note = JSONObject(it.toString()).put("contract_version", "walk-entry-v2")
                                .put("deleted", false).put("pin_revision", 0).put("pin", JSONObject.NULL)
                        }
                }
                else -> {
                    assertTrue(useV2)
                    assertEquals("/walk/entries/$id", path)
                    assertEquals("provisional", body!!.getJSONObject("pin").getString("state"))
                    assertTrue(body.getJSONObject("content").isNull("location"))
                    ack(body).also { behavior = it }
                }
            }
        }
        val sync = WalkEntrySync(dao, v2, owner = { owner }) { _, _, _, _ ->
            error("v2 지원 서버를 구형 목록 경로로 우회하면 안 된다")
        }
        sync.sync("token", "s", "walk")
        assertEquals(Triple("GET", "/entry-capabilities", false), calls.first())
        assertEquals(Triple("GET", "/walk/entries", true), calls.last())
        assertEquals(setOf(Triple("PUT", "/walk/entries/$id", true), Triple("PUT", "/walk/entries/legacy", false)),
            calls.filter { it.first == "PUT" }.toSet())
        assertEquals(4, calls.size)
        assertTrue(dao.entry(id)!!.isV2)
        assertFalse(dao.entry(id)!!.dirty)
        assertFalse(dao.entry(id)!!.pinDirty)
        assertFalse(dao.entry("legacy")!!.isV2)
        assertFalse(dao.entry("legacy")!!.dirty)
        assertEquals("과거 메모", dao.entry("legacy")!!.entry()!!.note)
    }

    @Test fun `과거 v1도 다른 계정 산책을 보내거나 계정 전환 뒤 ACK를 적용하지 않는다`() = runBlocking {
        dao.insertSession(WalkSessionRow("legacy", 0, owner, null))
        WalkEntryStore(dao).save(com.daengs.app.walk.WalkEntry("old", "legacy", WalkMomentType.SNIFFING, now,
            com.daengs.app.location.GeoPoint(37.5, 127.0), now - 1, 5f))
        var calls = 0
        val sync = WalkEntrySync(dao, owner = { owner }) { _, _, _, body ->
            calls++
            owner = "other"
            JSONObject().put("revision", 1).put("mutation_id", body!!.getString("mutation_id"))
        }
        owner = "other"
        sync.sync("token", "legacy", "walk")
        assertEquals(0, calls)
        owner = "owner"
        try { sync.sync("token", "legacy", "walk"); fail() } catch (_: IllegalStateException) { }
        assertEquals(1, calls)
        assertTrue(dao.entry("old")!!.dirty)
        assertEquals(0, dao.entry("old")!!.revision)
    }
    private fun caps(write: Boolean = true) = JSONObject().put("read_versions", JSONArray(listOf("walk-entry-v1", "walk-entry-v2")))
        .put("write_versions", JSONArray(listOf("walk-entry-v1") + if (write) listOf("walk-entry-v2") else emptyList()))
        .put("active_policy_versions", JSONArray(if (write) listOf("action-pin-policy-v1") else emptyList<String>()))
        .put("pin_observation_cutoff_supported", true)
    private fun ack(body: JSONObject, revision: Int = 1, pinRevision: Int = 1, content: JSONObject? = null) = JSONObject()
        .put("contract_version", "walk-entry-v2").put("id", id).put("revision", revision).put("pin_revision", pinRevision)
        .put("mutation_id", body.getString("mutation_id")).put("deleted", false)
        .put("content", content ?: body.getJSONObject("content")).put("pin", body.optJSONObject("pin") ?: JSONObject.NULL)

    @Test fun `응답 유실 뒤 로컬 확정되어도 최초 요청을 그대로 재전송하고 pin 종료를 이어 보낸다`() = runBlocking {
        var firstBody: String? = null
        var remote: JSONObject? = null
        var lose = true
        var puts = 0
        val v2 = WalkEntryV2Sync(dao, { owner }) { _, path, method, body, v2 ->
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
        val sync = WalkEntrySync(dao, v2, owner = { owner }) { _, _, _, _ ->
            error("v2 재전송을 v1으로 우회하면 안 된다")
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

    @Test fun `pin 없는 서버 v2 메모는 426 이후 v1을 반복하지 않고 정정을 보낸다`() = runBlocking {
        val note = com.daengs.app.walk.WalkEntry(id = id, sessionId = "s", type = WalkMomentType.NOTE,
            recordedAtMillis = now, note = "local note")
        val row = WalkEntryRow(id, "s", note.toJson().toString(), 1, UUID.randomUUID().toString(), true)
        dao.updatePinRow(row)
        var remote = JSONObject().put("id", id).put("revision", 1).put("pin_revision", 0)
            .put("mutation_id", UUID.randomUUID().toString()).put("content", note.toJson()).put("pin", JSONObject.NULL)
        var legacyWrites = 0
        var v2Writes = 0
        val sync = WalkEntryV2Sync(dao, { owner }) { _, path, method, body, v2 ->
            if (path == "/entry-capabilities") caps()
            else if (method == "GET") JSONObject().put("entries", JSONArray().put(remote))
            else if (!v2) { legacyWrites++; throw WalkHttpException(426, "walk_entry_upgrade_required") }
            else {
                v2Writes++
                assertFalse(body!!.has("pin"))
                ack(body, 2, 0).also { remote = it }
            }
        }
        sync.sync("token", "s", "walk")
        assertTrue(dao.entry(id)!!.isV2)
        WalkEntryStore(dao).save(dao.entry(id)!!.entry()!!.copy(note = "confirmed note"))
        sync.sync("token", "s", "walk")
        assertEquals(1, legacyWrites)
        assertEquals(1, v2Writes)
        assertFalse(dao.entry(id)!!.dirty)
        assertEquals("confirmed note", remote.getJSONObject("content").getString("note"))
    }

    @Test fun `쓰기 비활성화는 로컬 기록을 유지하고 v1로 우회하지 않는다`() = runBlocking {
        val v2 = WalkEntryV2Sync(dao, { owner }) { _, path, method, _, _ ->
            assertEquals("GET", method)
            if (path == "/entry-capabilities") caps(false) else JSONObject().put("entries", JSONArray())
        }
        val sync = WalkEntrySync(dao, v2, owner = { owner }) { _, _, _, _ ->
            error("쓰기 보류를 v1 전송으로 우회하면 안 된다")
        }
        try { sync.sync("token", "s", "walk"); fail() } catch (_: IOException) { }
        assertNotNull(dao.entry(id)!!.payload)
        assertTrue(dao.entry(id)!!.dirty)
        assertNull(dao.entry(id)!!.pendingRequest)
    }

    @Test fun `옛 서버도 위치 없는 행동을 v1에 전송하지 않는다`() = runBlocking {
        var calls = 0
        val v2 = WalkEntryV2Sync(dao, { owner }) { _, path, _, _, _ ->
            calls++; assertEquals("/entry-capabilities", path)
            throw WalkHttpException(404, "missing")
        }
        val sync = WalkEntrySync(dao, v2, owner = { owner }) { _, _, _, _ ->
            error("위치 없는 행동을 구형 전송으로 바꾸면 안 된다")
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
