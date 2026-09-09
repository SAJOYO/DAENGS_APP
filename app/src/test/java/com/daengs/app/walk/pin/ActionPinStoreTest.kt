package com.daengs.app.walk.pin

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ActionPinStoreTest {
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var pins: ActionPinStore
    private var owner = "owner"
    private var time = 10_000L
    private val request get() = ActionPinRequest(UUID.randomUUID(), owner, "s", 0, 10_000)
    @Before fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        dao = db.walkDao()
        dao.insertSession(WalkSessionRow("s", 0, "owner", null))
        pins = ActionPinStore(dao, { owner }, { time })
    }
    @After fun close() = db.close()
    private suspend fun fix(seq: Int, at: Long, accuracy: Float = 5f, chain: Int = 0, mock: Boolean = false) =
        dao.insertFix(WalkFixRow("s", seq, chain, at, 37.5, 127.0, accuracy, mock))

    @Test fun `GPS가 한 점도 없어도 즉시 기록하고 기한에 위치 없음으로 종료한다`() = runBlocking {
        pins.create("e", request, WalkMomentType.SNIFFING, null)
        val initial = dao.entry("e")!!
        assertTrue(initial.isV2)
        assertNull(initial.entry()!!.point)
        assertEquals("provisional", initial.entry()!!.pin!!.state)
        time = 18_000; pins.finish("e")
        val final = dao.entry("e")!!
        assertEquals("unlocated", final.entry()!!.pin!!.state)
        assertEquals(initial.payload, final.payload)
        assertEquals(1, dao.entries("s").size)
    }

    @Test fun `정상 GPS만 원본 위치와 observed에 쓰며 소수 accuracy를 서버 원본과 맞춘다`() = runBlocking {
        fix(0, 9_000, 5.2f)
        pins.create("e", request, WalkMomentType.BARKING, ActionPinSourceRef(0, 0, 9_000))
        val row = dao.entry("e")!!
        assertEquals("observed", row.entry()!!.pin!!.method)
        assertEquals(9_000L, row.entry()!!.locationCapturedAtMillis)
        assertEquals(5.2, JSONObject(row.pinPayload!!).getDouble("uncertainty_m"), 0.0)
        time = 30_000; pins.recover()
        assertEquals(row, dao.entry("e"))
    }

    @Test fun `불량 GPS는 기록을 막지 않고 마지막 위치로 남기며 원본 location은 만들지 않는다`() = runBlocking {
        fix(0, 1_000, 200f)
        pins.create("e", request, WalkMomentType.SNIFFING, null)
        val entry = dao.entry("e")!!.entry()!!
        assertNull(entry.point)
        assertEquals("last_known", entry.pin!!.method)
        assertNotNull(entry.pin.point)
        assertEquals("unknown", JSONObject(entry.pin.payload).getString("uncertainty_basis"))
        val log = RoomWalkFixLog(dao, owner = { owner })
        assertTrue(log.actions("s").isEmpty()) // No manufactured original GPS action.
        assertEquals("위치 추정 중", log.moments("s").single().locationLabel)
        time = 18_000; pins.finish("e")
        assertEquals("마지막 확인 위치", log.moments("s").single().locationLabel)
    }

    @Test fun `후속 관측으로 동일 ID를 한 번 확정하고 탭 시각과 content를 보존한다`() = runBlocking {
        pins.create("e", request, WalkMomentType.EXCRETION, null)
        val content = dao.entry("e")!!.payload
        fix(0, 11_000); fix(1, 13_000); fix(2, 15_000)
        time = 18_000; pins.finish("e")
        val final = dao.entry("e")!!
        assertEquals("estimated", final.entry()!!.pin!!.method)
        assertEquals(10_000L, final.entry()!!.recordedAtMillis)
        assertEquals(content, final.payload)
        fix(3, 19_000); pins.finish("e")
        assertEquals(final, dao.entry("e"))
    }

    @Test fun `기한을 지난 관측은 복구 계산에 들어가지 않는다`() = runBlocking {
        pins.create("e", request, WalkMomentType.SNIFFING, null)
        fix(0, 19_000)
        time = 30_000; pins.recover()
        assertEquals("unlocated", dao.entry("e")!!.entry()!!.pin!!.state)
    }

    @Test fun `일시정지 종료 시점 뒤 관측과 다음 chain은 추정에 섞이지 않는다`() = runBlocking {
        pins.create("e", request, WalkMomentType.SNIFFING, null)
        fix(0, 12_000); fix(1, 13_000, chain = 1)
        time = 18_000; pins.finishSession("s", 11_000)
        assertEquals("unlocated", dao.entry("e")!!.entry()!!.pin!!.state)
    }

    @Test fun `계정이 바뀐 작업과 삭제 뒤 작업은 핀을 다시 쓰지 못한다`() = runBlocking {
        fix(0, 9_000)
        pins.create("e", request, WalkMomentType.SNIFFING, null)
        val before = dao.entry("e")!!
        owner = "other"; time = 18_000; pins.recover()
        assertEquals(before, dao.entry("e"))
        owner = "owner"
        dao.preparePinRequest("e", owner)
        WalkEntryStore(dao).delete("e")
        pins.recover()
        assertNull(dao.entry("e")!!.payload)
        assertNull(dao.entry("e")!!.pinPayload)
        assertNull(dao.entry("e")!!.pendingRequest)
    }

    @Test fun `위치 없는 행동도 짧은 산책 삭제 판단에서 보존한다`() = runBlocking {
        pins.create("e", request, WalkMomentType.SNIFFING, null)
        dao.closeSession("s", 11_000)
        assertTrue(WalkHistory(RoomWalkFixLog(dao, owner = { owner })).keepIfWalk("s"))
        assertNotNull(dao.session("s"))
    }

    @Test fun `같은 위치 반복 탭은 각각의 행동을 보존한다`() = runBlocking {
        fix(0, 9_000)
        pins.create("e1", request, WalkMomentType.SNIFFING, null)
        pins.create("e2", request, WalkMomentType.SNIFFING, null)
        assertEquals(2, dao.entries("s").size)
    }
}
