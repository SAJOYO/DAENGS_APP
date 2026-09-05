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
                if (calls == 1) store.save(behavior("a").copy(type = WalkMomentType.BARKING))
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
}
