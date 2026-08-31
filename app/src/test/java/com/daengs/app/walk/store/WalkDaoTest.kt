package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWeather
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 실제 Room/SQLite를 JVM에서 실행해 행 매핑이 아닌 저장 계약을 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkDaoTest {
    private lateinit var db: WalkDatabase
    private lateinit var log: RoomWalkFixLog

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WalkDatabase::class.java,
        ).build()
        log = RoomWalkFixLog(db.walkDao())
    }

    @After
    fun tearDown() = db.close()

    /**
     * 목록에 **끝난 산책만** 온다.
     *
     * 강제 종료로 열린 채 남은 세션이 섞이면 "0m 짜리 산책"이 쌓인다 — 그건 기록이
     * 아니라 사고의 흔적이다. 이어 기록할지 버릴지는 아직 정하지 않은 별개 문제다.
     */
    @Test
    fun `끝난 산책만 최근 순으로 온다`() = runBlocking {
        log.openSession(RecordedSession("done-old", dogIds = emptyList(), startedAtMillis = 1_000L))
        log.openSession(RecordedSession("done-new", dogIds = emptyList(), startedAtMillis = 5_000L))
        log.openSession(RecordedSession("open", dogIds = emptyList(), startedAtMillis = 9_000L))
        log.closeSession("done-old", 2_000L)
        log.closeSession("done-new", 6_000L)

        assertEquals(listOf("done-new", "done-old"), log.finishedSessions().map { it.id })
    }

    /** 날씨는 세션을 연 뒤 따로 온다. 못 받으면 null 로 남고 "맑음"으로 채우지 않는다. */
    @Test
    fun `날씨는 나중에 찍히고 한 번만 쓴다`() = runBlocking {
        log.openSession(RecordedSession("s1", dogIds = emptyList(), startedAtMillis = 1_000L))
        assertNull(log.session("s1")?.weather)

        log.stampWeather("s1", RecordedWeather(weatherCode = 61, isDay = true, temperatureC = 18.5f))
        val stamped = log.session("s1")?.weather
        assertEquals(61, stamped?.weatherCode)
        assertEquals(18.5f, stamped?.temperatureC)

        // 두 번째 호출은 무시된다 — 나갈 때의 날씨가 산책 도중에 덮이면 안 된다.
        log.stampWeather("s1", RecordedWeather(weatherCode = 0, isDay = false, temperatureC = 3f))
        assertEquals(61, log.session("s1")?.weather?.weatherCode)
    }

    /**
     * 데리고 나간 아이들이 **다** 남는다.
     *
     * 한 아이만 남으면 나중에 챗봇이 나머지 아이의 운동량을 통째로 못 본다.
     * 아무도 안 골랐으면 빈 목록 그대로다 — 아무나 갖다 붙이지 않는다.
     */
    @Test
    fun `데리고 나간 아이들이 다 남는다`() = runBlocking {
        log.openSession(
            RecordedSession("s1", dogIds = listOf("dog-1", "dog-2"), startedAtMillis = 1_000L),
        )
        log.openSession(RecordedSession("s2", dogIds = emptyList(), startedAtMillis = 2_000L))

        assertEquals(listOf("dog-1", "dog-2"), log.session("s1")?.dogIds)
        assertEquals(emptyList<String>(), log.session("s2")?.dogIds)
    }

    /** 같은 아이를 두 번 적어도 한 줄이다. 서버의 walk_pets 와 같은 성질이다. */
    @Test
    fun `같은 아이를 두 번 적어도 한 마리다`() = runBlocking {
        log.openSession(
            RecordedSession("s1", dogIds = listOf("dog-1", "dog-1"), startedAtMillis = 1_000L),
        )

        assertEquals(listOf("dog-1"), log.session("s1")?.dogIds)
    }

    /** 세션을 지우면 연결도 같이 간다. 남으면 없는 산책을 가리키는 줄이 된다. */
    @Test
    fun `세션을 지우면 아이 연결도 지워진다`() = runBlocking {
        log.openSession(RecordedSession("s1", dogIds = listOf("dog-1"), startedAtMillis = 1_000L))

        log.deleteSession("s1")

        assertEquals(emptyList<String>(), db.walkDao().sessionDogs("s1").map { it.dogId })
    }

    @Test
    fun `fixes survive as written and come back in client order`() = runBlocking {
        log.openSession(session("s1"))
        log.append("s1", fix(1, lat = 37.1, chainIndex = 1, isMock = true))
        log.append("s1", fix(0, lat = 37.0))

        val stored = log.fixes("s1")

        assertEquals(listOf(0, 1), stored.map { it.clientSeq })
        assertEquals(37.0, stored.first().lat, 1e-9)
        assertEquals(5f, stored.first().accuracyM)
        assertEquals(1, stored.last().chainIndex)
        assertTrue(stored.last().isMock)
    }

    @Test
    fun `re-sending the same client sequence keeps the first raw fix`() = runBlocking {
        log.openSession(session("s1"))
        log.append("s1", fix(0, lat = 37.0))
        log.append("s1", fix(0, lat = 38.0))

        val stored = log.fixes("s1")

        assertEquals(1, stored.size)
        assertEquals(37.0, stored.single().lat, 1e-9)
    }

    @Test
    fun `re-opening a known session keeps its original identity and start`() = runBlocking {
        log.openSession(RecordedSession("s1", dogIds = listOf("first"), startedAtMillis = 100L))
        log.openSession(RecordedSession("s1", dogIds = listOf("second"), startedAtMillis = 999L))

        val stored = log.session("s1")

        // 아이도 처음 것이다 — 나중 목록을 덧붙이면 그날 안 나간 아이가 섞인다.
        assertEquals(listOf("first"), stored?.dogIds)
        assertEquals(100L, stored?.startedAtMillis)
    }

    @Test
    fun `only sessions without an explicit close are unfinished`() = runBlocking {
        log.openSession(session("open", startedAtMillis = 200L))
        log.append("open", fix(0))
        log.openSession(session("older", startedAtMillis = 100L))
        log.openSession(session("done", startedAtMillis = 50L))
        log.closeSession("done", endedAtMillis = 500L)

        val unfinished = log.unfinishedSessions()

        assertEquals(listOf("older", "open"), unfinished.map { it.id })
        assertEquals(1, log.fixes("open").size)
    }

    @Test
    fun `closing twice keeps the first end time`() = runBlocking {
        log.openSession(session("s1"))
        log.closeSession("s1", endedAtMillis = 500L)
        log.closeSession("s1", endedAtMillis = 900L)

        assertTrue(log.unfinishedSessions().isEmpty())
        assertEquals(500L, log.session("s1")?.endedAtMillis)
    }

    @Test
    fun `deleting a session cascades to its raw fixes`() = runBlocking {
        log.openSession(session("s1"))
        log.append("s1", fix(0))

        log.deleteSession("s1")

        assertNull(log.session("s1"))
        assertTrue(log.fixes("s1").isEmpty())
    }

    private fun session(id: String, startedAtMillis: Long = 0L) =
        RecordedSession(id, dogIds = emptyList(), startedAtMillis = startedAtMillis)

    private fun fix(
        seq: Int,
        lat: Double = 37.0,
        chainIndex: Int = 0,
        isMock: Boolean = false,
    ) = RecordedFix(
        clientSeq = seq,
        chainIndex = chainIndex,
        atMillis = seq.toLong(),
        lat = lat,
        lng = 127.0,
        accuracyM = 5f,
        isMock = isMock,
    )
}
