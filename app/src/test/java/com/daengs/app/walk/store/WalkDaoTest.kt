package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
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
        log.openSession(RecordedSession("s1", dogId = "first", startedAtMillis = 100L))
        log.openSession(RecordedSession("s1", dogId = "second", startedAtMillis = 999L))

        val stored = log.session("s1")

        assertEquals("first", stored?.dogId)
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
        RecordedSession(id, dogId = null, startedAtMillis = startedAtMillis)

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
