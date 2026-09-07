package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.titledDiaryFixture
import com.daengs.app.walk.sync.storyboardEntryStamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

internal suspend fun seedSearchWalk(log: RoomWalkFixLog, id: String, day: Int) {
    val at = LocalDate.of(2026, 9, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    log.openSession(RecordedSession(id, dogIds = listOf("dog"), startedAtMillis = at,
        weather = RecordedWeather(61, true, 20f)))
    (0..5).forEach { i -> log.append(id, RecordedFix(i, 0, at + i * 120_000L,
        37.5 + i * 80.0 / 111195, 127.0, 5f, false)) }
    log.closeSession(id, at + 600_000)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkHistorySearchTest {
    private lateinit var db: WalkDatabase
    private lateinit var log: RoomWalkFixLog
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        log = RoomWalkFixLog(db.walkDao())
    }
    @After fun close() = db.close()
    private suspend fun note(id: String, text: String) {
        WalkEntryStore(db.walkDao()).save(WalkEntry(id = "note-$id", sessionId = id, type = WalkMomentType.NOTE,
            recordedAtMillis = 1L, note = text))
    }

    @Test fun `search crosses candidate pages before limiting and only matching walks load GPS`() = runBlocking {
        for (i in 1..20) {
            seedSearchWalk(log, "s-$i", i)
            note("s-$i", if (i % 2 == 0) "호수 산책" else "골목 산책")
        }
        val foreign = RoomWalkFixLog(db.walkDao(), owner = { "other" })
        seedSearchWalk(foreign, "foreign", 25)
        note("foreign", "호수 산책")
        val reads = mutableListOf<String>()
        val history = WalkHistory(object : WalkFixLog by log {
            override suspend fun fixes(sessionId: String): List<RecordedFix> {
                reads += sessionId; return log.fixes(sessionId)
            }
        })
        val filter = WalkHistoryFilter("호수", LocalDate.of(2026,9,2), LocalDate.of(2026,9,18),
            setOf(WalkSeason.AUTUMN, WalkSeason.WINTER), setOf(WalkDepartureWeather.RAIN))
        val first = history.finishedPage(filter = filter, dogId = "dog", zone = ZoneOffset.UTC)
        val second = history.finishedPage(first.next, dogId = "dog", filter = filter, zone = ZoneOffset.UTC)
        assertEquals(listOf(18,16,14,12,10), first.walks.map { it.sessionId.removePrefix("s-").toInt() })
        assertEquals(listOf(8,6,4,2), second.walks.map { it.sessionId.removePrefix("s-").toInt() })
        assertNull(second.next)
        assertTrue(reads.all { it.startsWith("s-") && it.removePrefix("s-").toInt() in 2..18 && it.removePrefix("s-").toInt() % 2 == 0 })
    }

    @Test fun `search uses current visible title and notes not JSON metadata and drops stale title`() = runBlocking {
        seedSearchWalk(log, "s", 1)
        val dao = db.walkDao()
        val stamp = storyboardEntryStamp(emptyList())
        dao.acceptSceneAnalysis(WalkSceneAnalysisRow("s",1,stamp,"private-reference","ready",titledDiaryFixture().toString(),null), "")
        assertTrue(log.historySearchText(listOf("s"))["s"]!!.contains("함께 남긴 산책 기록"))
        val history = WalkHistory(log)
        assertEquals(1, history.finishedPage(filter = WalkHistoryFilter("함께 남긴")).walks.size)
        assertTrue(history.finishedPage(filter = WalkHistoryFilter("private-reference")).walks.isEmpty())
        note("s", "100% 호수_산책")
        assertTrue(history.finishedPage(filter = WalkHistoryFilter("함께 남긴")).walks.isEmpty())
        assertEquals(1, history.finishedPage(filter = WalkHistoryFilter("100% 호수_")).walks.size)
        val store = WalkEntryStore(dao)
        val entry = dao.entry("note-s")!!.entry()!!
        store.save(entry.copy(note = "변경한 메모"))
        assertTrue(history.finishedPage(filter = WalkHistoryFilter("호수")).walks.isEmpty())
        assertEquals(1, history.finishedPage(filter = WalkHistoryFilter("변경한")).walks.size)
        store.delete("note-s")
        assertTrue(history.finishedPage(filter = WalkHistoryFilter("변경한")).walks.isEmpty())
        log.deleteSession("s")
        assertTrue(log.historySearchText(listOf("s")).isEmpty())
    }

    @Test fun `title writes invalidate history search without changing GPS or session rows`() = runBlocking {
        seedSearchWalk(log, "s", 1)
        val stamp = storyboardEntryStamp(emptyList())
        val ready = CompletableDeferred<Unit>()
        val waiter = async { withTimeout(5000) {
            log.historyChanges.first {
                val found = log.historySearchText(listOf("s"))["s"]?.any { it.contains("함께 남긴") } == true
                ready.complete(Unit)
                found
            }
        } }
        ready.await()
        db.walkDao().acceptSceneAnalysis(WalkSceneAnalysisRow("s",1,stamp,"revision","ready",titledDiaryFixture().toString(),null), "")
        waiter.await()
    }

    @Test fun `date and season use local start day and unknown weather is not clear`() {
        val at = Instant.parse("2026-02-28T15:00:00Z").toEpochMilli()
        val session = RecordedSession("s", startedAtMillis = at, endedAtMillis = at + 600_000)
        val query = WalkHistoryFilter(from = LocalDate.of(2026,3,1), through = LocalDate.of(2026,3,1),
            seasons = setOf(WalkSeason.SPRING), weather = setOf(WalkDepartureWeather.UNKNOWN))
        assertTrue(query.matches(session, ZoneId.of("Asia/Seoul")))
        assertFalse(query.matches(session, ZoneOffset.UTC))
        assertFalse(query.copy(weather = setOf(WalkDepartureWeather.CLEAR)).matches(session, ZoneId.of("Asia/Seoul")))
        assertEquals(WalkDepartureWeather.UNKNOWN, WalkDepartureWeather.of(1234))
        assertEquals(WalkDepartureWeather.SNOW, WalkDepartureWeather.of(66))
        assertEquals(WalkDepartureWeather.CLOUDY, WalkDepartureWeather.of(45))
        assertTrue(WalkHistoryFilter("CAFÉ").matchesText(listOf("cafe\u0301 산책")))
    }
}
