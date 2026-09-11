package com.daengs.app.walk.store

import com.daengs.app.walk.support.seedSearchWalk
import com.daengs.app.walk.support.titledDiaryFixture
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.WalkDiaryReader
import com.daengs.app.walk.sync.storyboardEntryStamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkHistorySearchTest {
    private lateinit var db: WalkDatabase
    private lateinit var log: RoomWalkFixLog
    private lateinit var reader: WalkDiaryReader
    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, WalkDatabase::class.java).build()
        log = RoomWalkFixLog(db.walkDao())
        reader = WalkDiaryReader(db.walkDao(), WalkPhotoStore(db.walkDao(), File(context.cacheDir, "history-search")) { "" }) { "" }
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

    @Test fun `search keeps the published title and live notes without exposing metadata or a late title`() = runBlocking {
        seedSearchWalk(log, "s", 1)
        val dao = db.walkDao()
        val preparation = requireNotNull(dao.prepareLocalDiary("s", ""))
        val stamp = storyboardEntryStamp(emptyList())
        val source = WalkSceneAnalysisRow("s",1,stamp,"private-reference","ready",titledDiaryFixture().toString(),null)
        assertTrue(dao.acceptSceneAnalysis(source, "", nowMillis = preparation.deadlineAtMillis - 1))
        assertEquals(source.bundle, dao.diaryPublication("s")!!.publishedBundle)
        assertTrue(log.historySearchText(listOf("s"))["s"]!!.contains("함께 남긴 산책 기록"))
        val history = WalkHistory(log)
        assertEquals(1, history.finishedPage(filter = WalkHistoryFilter("함께 남긴")).walks.size)
        assertTrue(history.finishedPage(filter = WalkHistoryFilter("private-reference")).walks.isEmpty())
        note("s", "100% 호수_산책")
        assertEquals(1, history.finishedPage(filter = WalkHistoryFilter("함께 남긴")).walks.size)
        assertEquals(1, history.finishedPage(filter = WalkHistoryFilter("100% 호수_")).walks.size)
        val late = source.copy(generation = 2, entryStamp = storyboardEntryStamp(dao.entries("s")),
            bundle = titledDiaryFixture().put("title", "늦게 도착한 제목").toString())
        assertTrue(dao.acceptSceneAnalysis(late, "", nowMillis = preparation.deadlineAtMillis + 1))
        assertEquals(late.bundle, dao.sceneAnalysis("s")!!.bundle)
        assertTrue(history.finishedPage(filter = WalkHistoryFilter("늦게 도착한")).walks.isEmpty())
        assertEquals("함께 남긴 산책 기록", reader.observeTitles(listOf("s")).first()["s"])
        val store = WalkEntryStore(dao)
        val entry = dao.entry("note-s")!!.entry()!!
        store.save(entry.copy(note = "변경한 메모"))
        assertTrue(history.finishedPage(filter = WalkHistoryFilter("호수")).walks.isEmpty())
        assertEquals(1, history.finishedPage(filter = WalkHistoryFilter("변경한")).walks.size)
        store.delete("note-s")
        assertTrue(history.finishedPage(filter = WalkHistoryFilter("변경한")).walks.isEmpty())
        assertEquals(1, history.finishedPage(filter = WalkHistoryFilter("함께 남긴")).walks.size)
        log.deleteSession("s")
        assertTrue(log.historySearchText(listOf("s")).isEmpty())
    }

    @Test fun `legacy analysis title is removed from search when its inputs change`() = runBlocking {
        val dao = db.walkDao()
        // Already-completed records predate publication and retain their original read contract.
        dao.insertSession(WalkSessionRow("s", 0, "", 1000))
        assertNull(dao.diaryPublication("s"))
        val stamp = storyboardEntryStamp(emptyList())
        assertTrue(dao.acceptSceneAnalysis(WalkSceneAnalysisRow("s", 1, stamp, "legacy", "ready",
            titledDiaryFixture().toString(), null), ""))
        assertTrue(log.historySearchText(listOf("s"))["s"]!!.contains("함께 남긴 산책 기록"))
        note("s", "기존 기록에 추가한 메모")
        assertEquals(listOf("기존 기록에 추가한 메모"), log.historySearchText(listOf("s"))["s"])
        assertTrue(reader.observeTitles(listOf("s")).first().isEmpty())
    }

    @Test fun `publication invalidates title search without changing GPS or session rows`() = runBlocking {
        seedSearchWalk(log, "s", 1)
        val dao = db.walkDao()
        val preparation = requireNotNull(dao.prepareLocalDiary("s", ""))
        val sessionBefore = dao.session("s")
        val fixesBefore = dao.fixes("s")
        assertTrue(reader.observeTitles(listOf("s")).first().isEmpty())
        val ready = CompletableDeferred<Unit>()
        val waiter = async { withTimeout(5000) {
            log.historyChanges.first {
                val found = log.historySearchText(listOf("s"))["s"]?.any { it.contains("함께 남긴") } == true
                ready.complete(Unit)
                found
            }
        } }
        ready.await()
        // Only publication changes: its notification must refresh the title search on its own.
        assertEquals(1, dao.publishDiaryCandidate("s", titledDiaryFixture().toString(), preparation.deadlineAtMillis - 1))
        waiter.await()
        assertEquals("함께 남긴 산책 기록", reader.observeTitles(listOf("s")).first()["s"])
        assertEquals(sessionBefore, dao.session("s"))
        assertEquals(fixesBefore, dao.fixes("s"))
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
