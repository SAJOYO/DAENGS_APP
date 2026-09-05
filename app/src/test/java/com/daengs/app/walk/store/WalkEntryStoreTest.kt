package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.sync.WalkEntrySync
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkEntryStoreTest {
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var store: WalkEntryStore
    @Before fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        dao = db.walkDao(); store = WalkEntryStore(dao)
        dao.insertSession(WalkSessionRow("s", startedAtMillis = 0, endedAtMillis = 10000))
    }
    @After fun close() = db.close()
    private fun behavior(id: String) = WalkEntry(id, "s", WalkMomentType.SNIFFING, 1000,
        GeoPoint(37.5, 127.0), 990, 5f)

    @Test fun `같은 위치의 같은 행동도 개별 삭제되고 삭제 본문은 남지 않는다`() = runBlocking {
        store.save(behavior("a")); store.save(behavior("b"))
        assertEquals(2, store.observe("s").first().size)
        store.delete("a")
        assertEquals(listOf("b"), store.observe("s").first().map { it.id })
        assertNull(dao.entry("a")!!.payload)
        assertTrue(dao.entry("a")!!.dirty)
    }

    @Test fun `위치 없는 메모가 짧은 산책을 보관하지만 운동량 횟수는 늘리지 않는다`() = runBlocking {
        store.save(WalkEntry(id = "n", sessionId = "s", type = WalkMomentType.NOTE,
            recordedAtMillis = 100, note = "벤치에서 함께 쉬었다"))
        val history = WalkHistory(RoomWalkFixLog(dao))
        assertTrue(history.keepIfWalk("s"))
        assertEquals(1, history.finished().size)
        assertEquals(0, history.finished().totalsFor(0, 20000).count)
        assertNull(store.observe("s").first().single().point)
    }

    @Test fun `다른 계정의 기록은 조회나 편집할 수 없다`() = runBlocking {
        var owner = "a"
        val log = RoomWalkFixLog(dao) { owner }
        log.openSession(RecordedSession("owned", startedAtMillis = 0, endedAtMillis = 1000))
        val scoped = WalkEntryStore(dao) { owner }
        scoped.save(behavior("a").copy(sessionId = "owned"))
        owner = "b"
        assertTrue(log.finishedSessions().isEmpty())
        assertTrue(scoped.observe("owned").first().isEmpty())
        assertTrue(runCatching { scoped.save(behavior("b").copy(sessionId = "owned")) }.isFailure)
        assertTrue(runCatching { scoped.delete("a") }.isFailure)
    }

    @Test fun `전송 중 수정은 서버 승인으로 사라지지 않고 다음 전송에 반영된다`() = runBlocking {
        store.save(behavior("a"))
        var calls = 0
        val sync = WalkEntrySync(dao) { _, _, method, body ->
            if (method == "GET") JSONObject().put("entries", JSONArray())
            else {
                calls++
                if (calls == 1) store.save(dao.entry("a")!!.entry()!!.copy(type = WalkMomentType.BARKING))
                JSONObject().put("revision", calls).put("mutation_id", body!!.getString("mutation_id"))
            }
        }
        sync.sync("token", "s", "remote")
        assertTrue(dao.entry("a")!!.dirty)
        assertEquals(1, dao.entry("a")!!.revision)
        sync.sync("token", "s", "remote")
        assertFalse(dao.entry("a")!!.dirty)
        assertEquals(WalkMomentType.BARKING, dao.entry("a")!!.entry()!!.type)
    }

    @Test fun `서버 삭제는 아직 전송하지 않은 오래된 변경으로 복원되지 않는다`() = runBlocking {
        store.save(behavior("a"))
        val sync = WalkEntrySync(dao) { _, _, method, _ ->
            if (method == "PUT") throw com.daengs.app.walk.sync.WalkHttpException(409, "deleted")
            JSONObject().put("entries", JSONArray().put(JSONObject().put("id", "a")
                .put("revision", 3).put("mutation_id", "delete").put("content", JSONObject.NULL)))
        }
        sync.sync("token", "s", "remote")
        assertTrue(store.observe("s").first().isEmpty())
        assertFalse(dao.entry("a")!!.dirty)
    }
    @Test fun `열어 둔 편집창은 다른 기기의 대상 정정을 덮어쓰지 않는다`() = runBlocking {
        store.save(behavior("a").copy(petId = "dog-a"))
        dao.acknowledgeEntry("a", 1, dao.entry("a")!!.mutationId)
        val draft = store.observe("s").first().single()
        val remote = draft.copy(petId = "dog-b")
        dao.acceptEntry("a", remote.toJson().toString(), 2, "remote-edit")

        assertTrue(runCatching { store.save(draft.copy(type = WalkMomentType.BARKING)) }.isFailure)
        assertEquals("dog-b", dao.entry("a")!!.entry()!!.petId)
        assertFalse(dao.entry("a")!!.dirty)
        // 사용자 확인 후에만 최신 버전으로 자신의 초안을 다시 제출한다.
        store.save(draft.copy(type = WalkMomentType.BARKING, baseVersion = dao.entry("a")!!.entry()!!.baseVersion))
        assertEquals(WalkMomentType.BARKING, dao.entry("a")!!.entry()!!.type)
        assertTrue(dao.entry("a")!!.dirty)
    }

    @Test fun `같은 revision에서 다른 로컬 편집이 먼저 저장돼도 오래된 초안은 거부한다`() = runBlocking {
        store.save(behavior("a"))
        val draft = dao.entry("a")!!.entry()!!
        store.save(draft.copy(petId = "dog-b"))
        assertTrue(runCatching { store.save(draft.copy(type = WalkMomentType.BARKING)) }.isFailure)
        assertEquals("dog-b", dao.entry("a")!!.entry()!!.petId)
    }

    @Test fun `편집 중 세션이 삭제되면 초안으로 기록을 새로 만들지 않는다`() = runBlocking {
        store.save(behavior("a"))
        val draft = dao.entry("a")!!.entry()!!
        dao.deleteSession("s")
        dao.insertSession(WalkSessionRow("s", startedAtMillis = 0))
        assertTrue(runCatching { store.save(draft) }.isFailure)
        assertNull(dao.entry("a"))
    }

    @Test fun `충돌로 전송에서 제외된 기록도 GET의 삭제 표식으로 지운다`() = runBlocking {
        store.save(behavior("a"))
        dao.conflictEntry("a", 2, dao.entry("a")!!.mutationId, "conflict")
        val sync = WalkEntrySync(dao) { _, _, method, _ ->
            assertEquals("GET", method)
            JSONObject().put("entries", JSONArray().put(JSONObject().put("id", "a")
                .put("revision", 3).put("mutation_id", "delete").put("content", JSONObject.NULL)))
        }
        repeat(3) { sync.sync("token", "s", "remote") }
        assertTrue(store.observe("s").first().isEmpty())
        assertNull(dao.entry("a")!!.syncError)
        assertFalse(dao.entry("a")!!.dirty)
    }

    @Test fun `취소는 삭제가 저장된 세션을 예약하고 진행 중 전송의 ACK도 삭제를 지우지 않는다`() = runBlocking {
        store.save(behavior("a"))
        val sent = dao.entry("a")!!
        val queued = mutableListOf<String>()
        store.deleteAndEnqueue("a") { session ->
            assertNull(dao.entry("a")!!.payload)
            queued.add(session)
        }
        dao.acknowledgeEntry("a", 1, sent.mutationId)
        assertEquals(listOf("s"), queued)
        assertNull(dao.entry("a")!!.payload)
        assertTrue(dao.entry("a")!!.dirty)
        var deletes = 0
        WalkEntrySync(dao) { _, _, method, _ ->
            if (method == "GET") JSONObject().put("entries", JSONArray())
            else { assertEquals("DELETE", method); deletes++; JSONObject().put("revision", 2) }
        }.sync("token", "s", "remote")
        assertEquals(1, deletes)
        assertFalse(dao.entry("a")!!.dirty)
    }

    @Test fun `탈퇴는 토큰 삭제 뒤에도 해당 계정만 지우고 늦은 생성과 복원을 막는다`() = runBlocking {
        var owner = "a"
        val log = RoomWalkFixLog(dao) { owner }
        for (account in listOf("a", "b", "")) {
            log.openSession(RecordedSession("owner-$account", ownerId = account, startedAtMillis = 0))
            store.save(behavior("entry-$account").copy(sessionId = "owner-$account"))
            log.append("owner-$account", RecordedFix(0, 0, 1, 37.5, 127.0, 5f, false))
        }
        owner = ""
        WalkHistory(log).forgetOwner("a")
        assertNull(dao.session("owner-a"))
        assertNull(dao.entry("entry-a"))
        assertTrue(dao.fixes("owner-a").isEmpty())
        for (account in listOf("b", "")) {
            assertNotNull(dao.session("owner-$account"))
            assertNotNull(dao.entry("entry-$account"))
            assertEquals(1, dao.fixes("owner-$account").size)
        }
        assertTrue(runCatching {
            log.openSession(RecordedSession("late-write", ownerId = "a", startedAtMillis = 0))
        }.isFailure)
        assertTrue(runCatching {
            log.restoreSession(RecordedSession("owner-a", ownerId = "a", startedAtMillis = 0,
                serverWalkId = "remote-a"))
        }.isFailure)
        assertNull(dao.session("late-write"))
        assertNull(dao.session("owner-a"))
    }

}
