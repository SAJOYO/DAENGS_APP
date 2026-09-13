package com.daengs.app.walk.records

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.auth.Session
import com.daengs.app.auth.SessionProvider
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.WalkDepartureWeather
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkHistoryFilter
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSeason
import com.daengs.app.walk.store.*
import com.daengs.app.walk.support.diaryFixture
import com.daengs.app.walk.support.seedSearchWalk
import com.daengs.app.walk.support.titledDiaryFixture
import com.daengs.app.walk.sync.diaryInputStamp
import com.daengs.app.walk.sync.storyboardEntryStamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomWalkRecordsSourceTest {
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var log: RoomWalkFixLog
    private lateinit var source: RoomWalkRecordsSource

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        dao = db.walkDao()
        log = RoomWalkFixLog(dao, owner = { OWNER })
        source = RoomWalkRecordsSource(db, OWNER, { OWNER }, ZoneOffset.UTC)
    }

    @After fun close() = db.close()

    @Test fun `highlighted route keeps original speed timestamps while cards remain simplified`() = runBlocking {
        log.openSession(RecordedSession("speed", dogIds = listOf("dog"), startedAtMillis = 0, endedAtMillis = 600_000))
        (0..300).forEach { i -> log.append("speed", RecordedFix(i, 0, i * 2_000L,
            37.5 + i * 2.0 / 111195, 127.0, 5f, false)) }
        val record = source.select(WalkRecordsQuery()).records.single()
        val full = source.loadRoute(record)
        assertTrue(record.summary.segments.flatten().size < full.segments.flatten().size)
        val style = com.daengs.app.map.style.WalkStylePolicy.parse(java.io.File("src/main/assets/walk-style-v1.json").readText())
        fun colors(summary: com.daengs.app.walk.WalkSummary) = summary.segments.flatMap { path ->
            com.daengs.app.map.style.paintWalkSpeedPath(path.map {
                com.daengs.app.map.style.WalkSpeedPoint(it.point, it.capturedAtMillis)
            }, style, "pink").map { it.color }
        }
        assertTrue(colors(record.summary).all { it == style.unknownColor })
        assertTrue(colors(full).any { it != style.unknownColor })
        assertEquals(record.summary, source.select(WalkRecordsQuery()).records.single().summary)
        assertTrue(runCatching { source.loadRoute(record.copy(summary = record.summary.copy(distanceMeters = -1.0))) }.isFailure)
        val denied = RoomWalkRecordsSource(db, "other", { "other" }, ZoneOffset.UTC)
        assertTrue(runCatching { denied.loadRoute(record) }.isFailure)
    }

    @Test fun `multiple selected dogs include shared walks once and exclude other dogs`() = runBlocking {
        listOf("shared" to listOf("a", "b"), "a-only" to listOf("a"),
            "b-only" to listOf("b"), "other" to listOf("c"), "unknown" to emptyList()).forEach { (id, dogs) ->
            log.openSession(RecordedSession(id, dogIds = dogs, startedAtMillis = 0, endedAtMillis = 600_000))
            note(id, "함께 남긴 메모")
        }
        val selected = source.select(WalkRecordsQuery(setOf("a", "b")))
        assertEquals(setOf("shared", "a-only", "b-only"), selected.sessionIds.toSet())
        assertEquals(3, selected.sessionIds.size)
        assertEquals(setOf("shared", "a-only"), source.select(WalkRecordsQuery("a")).sessionIds.toSet())
        assertEquals(5, source.select(WalkRecordsQuery()).sessionIds.size)
    }

    @Test fun `upload acknowledgement carries the server mapping without claiming a ready sheet`() = runBlocking {
        seedSearchWalk(log, "local", 1)
        val before = source.select(WalkRecordsQuery()).records.single()
        assertNull(before.serverWalkId)
        assertEquals(WalkTraceState.NOT_UPLOADED, before.effectiveTraceState)
        dao.markRawUploaded("local", "server-walk", 1000)
        val after = source.select(WalkRecordsQuery()).records.single()
        assertEquals("server-walk", after.serverWalkId)
        assertEquals(WalkTraceState.NOT_REQUESTED, after.effectiveTraceState)
        assertNull(after.trace)
        assertEquals(before.summary, after.summary)
    }

    @Test fun `full selection crosses thirty records before pagination and retains route without invented traces`() = runBlocking {
        val ids = (1..35).map { "walk-${it.toString().padStart(2, '0')}" }
        ids.forEach { seedSearchWalk(log, it, 10) }

        val selected = source.select(WalkRecordsQuery(dogId = "dog"))

        assertEquals(ids.reversed(), selected.sessionIds)
        assertEquals(ids.reversed().take(5), selected.page(0).map { it.summary.sessionId })
        assertEquals(ids.reversed().takeLast(5), selected.page(6).map { it.summary.sessionId })
        assertTrue(selected.page(7).isEmpty())
        assertTrue(selected.records.all { it.trace == null && it.summary.segments.flatten().isNotEmpty() })
        assertEquals(35, selected.records.size)
    }

    @Test fun `shared conditions combine dog local date season weather and normalized current text`() = runBlocking {
        suspend fun candidate(id: String, at: String, weather: Int?, dog: String = "dog", text: String = "cafe\u0301 산책") {
            session(id, at = Instant.parse(at).toEpochMilli(), weather = weather, dog = dog)
            note(id, text)
        }
        candidate("first", "2026-02-28T15:00:00Z", 61)
        candidate("last", "2026-03-01T14:59:59.999Z", 71)
        candidate("before", "2026-02-28T14:59:59.999Z", 61)
        candidate("after", "2026-03-01T15:00:00Z", 61)
        candidate("unknown", "2026-03-01T03:00:00Z", null)
        candidate("other-dog", "2026-03-01T03:00:00Z", 61, dog = "other")
        candidate("other-text", "2026-03-01T03:00:00Z", 61, text = "강변 산책")
        val localSource = RoomWalkRecordsSource(db, OWNER, { OWNER }, ZoneId.of("Asia/Seoul"))
        val query = WalkRecordsQuery("dog", WalkHistoryFilter(
            keyword = " CAFÉ ", from = LocalDate.of(2026, 3, 1), through = LocalDate.of(2026, 3, 1),
            seasons = setOf(WalkSeason.SPRING, WalkSeason.SUMMER),
            weather = setOf(WalkDepartureWeather.RAIN, WalkDepartureWeather.SNOW),
        ))

        assertEquals(listOf("last", "first"), localSource.select(query).sessionIds)
        assertEquals(listOf("unknown"), localSource.select(query.copy(filter = query.filter.copy(
            weather = setOf(WalkDepartureWeather.UNKNOWN),
        ))).sessionIds)
        assertTrue(localSource.select(query.copy(filter = query.filter.copy(
            weather = setOf(WalkDepartureWeather.CLEAR),
        ))).records.isEmpty())
    }

    @Test fun `legacy search uses current visible title and notes and removes edited deleted or stale text`() = runBlocking {
        // An already completed record predates publication; closing a new walk opts it in.
        log.openSession(RecordedSession("s", dogIds = listOf("dog"), startedAtMillis = 0, endedAtMillis = 600_000))
        (0..5).forEach { i -> log.append("s", RecordedFix(i, 0, i * 120_000L,
            37.5 + i * 80.0 / 111195, 127.0, 5f, false)) }
        assertNull(dao.diaryPublication("s"))
        val stamp = storyboardEntryStamp(emptyList())
        assertTrue(dao.acceptSceneAnalysis(WalkSceneAnalysisRow("s", 1, stamp, "private-reference", "ready",
            titledDiaryFixture().toString(), null), OWNER))
        assertEquals("함께 남긴 산책 기록", source.select(WalkRecordsQuery()).records.single().title)
        assertEquals(listOf("s"), search("함께 남긴").sessionIds)
        assertTrue(search("private-reference").records.isEmpty())

        note("s", "100% 호수_산책")
        assertTrue(search("함께 남긴").records.isEmpty())
        assertEquals(listOf("s"), search("100% 호수_").sessionIds)
        val store = WalkEntryStore(dao)
        store.save(requireNotNull(dao.entry("note-s")?.entry()).copy(note = "변경한 메모"))
        assertTrue(search("호수").records.isEmpty())
        assertEquals(listOf("변경한 메모"), search("변경한").records.single().notes)
        store.delete("note-s")

        val current = source.select(WalkRecordsQuery()).records.single()
        assertTrue(search("변경한").records.isEmpty())
        assertTrue(current.notes.isEmpty())
        assertTrue(current.entries.isEmpty())
        assertNull(current.title)
        assertNull(dao.entry("note-s")?.payload)
    }

    @Test fun `photo input changes invalidate the flow and remove a stale generated title`() = runBlocking {
        withTimeout(10_000) {
            session("s")
            for (id in listOf("note", "action")) {
                insertEntry(WalkEntry(id, "s", WalkMomentType.NOTE, 0, note = "원본 메모"))
            }
            dao.insertPhotoSync(WalkPhotoSyncRow("s", OWNER, "publisher", 1, 1))
            val raw = diaryFixture().put("session_id", "s")
            raw.getJSONObject("bundle").put("client_session_id", "s")
            val stamp = diaryInputStamp(dao.entries("s"), dao.photoSync("s"), emptyList())
            assertTrue(dao.acceptSceneAnalysis(WalkSceneAnalysisRow("s", 1, stamp, "r", "ready", raw.toString(), null), OWNER))
            assertEquals(listOf("s"), search("두부와 함께 남긴 아침").sessionIds)

            val initial = CompletableDeferred<Unit>()
            val invalidated = async {
                source.changes.first {
                    val title = source.select(WalkRecordsQuery()).records.single().title
                    if (!initial.isCompleted) assertEquals("두부와 함께 남긴 아침", title)
                    initial.complete(Unit)
                    title == null
                }
            }
            initial.await()
            dao.touchPhotoSync("s", OWNER)
            invalidated.await()

            assertTrue(search("두부와 함께 남긴 아침").records.isEmpty())
            assertEquals(2, source.select(WalkRecordsQuery()).records.single().entries.size)
        }
    }

    @Test fun `publication alone refreshes titles while pending and late AI stay out of search`() = runBlocking {
        withTimeout(10_000) {
            seedSearchWalk(log, "s", 1)
            val preparation = requireNotNull(dao.diaryPublication("s"))
            val raw = titledDiaryFixture().toString()
            val stamp = storyboardEntryStamp(emptyList())
            val analysis = WalkSceneAnalysisRow("s", 1, stamp, "r", "ready", raw, null)
            assertTrue(dao.acceptSceneAnalysis(analysis, OWNER))
            dao.freezeDiaryBase("s", raw)
            assertNull(source.select(WalkRecordsQuery()).records.single().title)
            assertTrue(search("함께 남긴").records.isEmpty())

            val initial = CompletableDeferred<Unit>()
            val published = async {
                source.changes.first {
                    val title = source.select(WalkRecordsQuery()).records.single().title
                    if (!initial.isCompleted) assertNull(title)
                    initial.complete(Unit)
                    title == "함께 남긴 산책 기록"
                }
            }
            initial.await()
            assertEquals(1, dao.publishDiaryCandidate("s", raw, preparation.startedAtMillis + 1000))
            published.await()
            assertEquals(listOf("s"), search("함께 남긴").sessionIds)

            val late = titledDiaryFixture().put("title", "늦게 도착한 제목").toString()
            assertTrue(dao.acceptSceneAnalysis(analysis.copy(generation = 2, bundle = late), OWNER,
                preparation.deadlineAtMillis + 1))
            assertEquals("함께 남긴 산책 기록", source.select(WalkRecordsQuery()).records.single().title)
            assertTrue(search("늦게 도착한").records.isEmpty())
        }
    }

    @Test fun `entry projection preserves missing estimated corrected locations and exact dog attribution after deletion`() = runBlocking {
        session("s")
        dao.insertSessionDog(WalkSessionDogRow("s", "other"))
        val original = GeoPoint(37.5, 127.0)
        val estimated = pin("final", "estimated", GeoPoint(37.501, 127.001))
        insertEntry(WalkEntry("missing", "s", WalkMomentType.SNIFFING, 1, petId = "dog"))
        insertEntry(WalkEntry("estimated", "s", WalkMomentType.SNIFFING, 2, original, 2, 15f, petId = "dog"), estimated)
        insertEntry(WalkEntry("unlocated", "s", WalkMomentType.SNIFFING, 3, original, 3, 15f, petId = "dog"),
            pin("unlocated", "none", null))
        insertEntry(WalkEntry("unassigned", "s", WalkMomentType.SNIFFING, 4))
        insertEntry(WalkEntry("other", "s", WalkMomentType.SNIFFING, 5, petId = "other"))
        dao.insertEntry(WalkEntryRow("deleted", "s", null, 5, "deleted", false, isV2 = true))
        note("s", "행동에 포함하지 않는 메모")

        val selected = source.select(WalkRecordsQuery("dog"))
        assertEquals(dao.entries("s").mapNotNull(WalkEntryRow::entry), selected.records.single().entries)
        assertNull(selected.records.single().trace)
        val actions = selectWalkRecordBehaviors(selected, WalkMomentType.SNIFFING).records
        assertEquals(listOf("unlocated", "estimated", "missing"), actions.map { it.entry.id })
        assertNull(actions[0].point)
        assertEquals("위치 없이 남긴 행동", actions[0].locationLabel)
        assertEquals(GeoPoint(37.501, 127.001), actions[1].point)
        assertEquals("추정 위치", actions[1].locationLabel)
        assertNull(actions[2].point)
        assertEquals(5, selectWalkRecordBehaviors(source.select(WalkRecordsQuery()), WalkMomentType.SNIFFING).records.size)

        val correctedPoint = GeoPoint(37.502, 127.002)
        dao.finishPinEntry("estimated", estimated, pin("final", "gps", correctedPoint), "corrected", OWNER)
        dao.deletePinAwareEntry("missing", "remove-missing")
        val current = source.select(WalkRecordsQuery("dog"))
        val corrected = selectWalkRecordBehaviors(current, WalkMomentType.SNIFFING).records
        assertEquals(listOf("unlocated", "estimated"), corrected.map { it.entry.id })
        assertEquals(correctedPoint, corrected.last().point)
        assertEquals(original, corrected.last().entry.point)
        assertTrue(corrected.last().entry.syncPending)
        assertEquals(dao.entry("estimated")?.entry(), corrected.last().entry)
        // A prior selection remains a snapshot after later local pin corrections and tombstones.
        assertEquals(3, actions.size)
        assertEquals(GeoPoint(37.501, 127.001), actions[1].point)
    }

    @Test fun `only completed owned records qualify while short entry and photo walks survive without traces`() = runBlocking {
        seedSearchWalk(log, "long", 1)
        for (id in listOf("empty", "entry", "photo", "deleted-only")) session(id)
        note("entry", "짧아도 남긴 기록")
        dao.insertPhoto(WalkPhotoRow("photo-id", "photo", OWNER, 0, 0, 37.5, 127.0, 5f))
        dao.insertEntry(WalkEntryRow("tombstone", "deleted-only", null, 1, "removed", false))
        session("foreign", owner = "other")
        session("blank", owner = "")
        session("unfinished", ended = false)
        for (id in listOf("foreign", "blank", "unfinished")) note(id, "남의 기록 또는 진행 중")

        val selected = source.select(WalkRecordsQuery())

        assertEquals(setOf("long", "entry", "photo"), selected.sessionIds.toSet())
        assertTrue(selected.records.all { it.trace == null })
        assertTrue(selected.records.first { it.summary.sessionId == "photo" }.entries.isEmpty())
        assertNotNull(dao.session("empty"))
        assertNotNull(dao.session("deleted-only"))
    }

    @Test fun `owner mismatch before or during a snapshot rejects the selection`() = runBlocking {
        seedSearchWalk(log, "s", 1)
        for ((expected, current) in listOf(OWNER to "other", "" to "")) {
            val denied = RoomWalkRecordsSource(db, expected, { current }, ZoneOffset.UTC)
            assertTrue(runCatching { denied.select(WalkRecordsQuery()) }.exceptionOrNull() is IllegalStateException)
        }
        val checks = AtomicInteger()
        val changing = RoomWalkRecordsSource(db, OWNER, {
            // Owner is valid on entry and transaction start, then changes before results can publish.
            if (checks.incrementAndGet() <= 2) OWNER else "other"
        }, ZoneOffset.UTC)

        assertTrue(runCatching { changing.select(WalkRecordsQuery()) }.exceptionOrNull() is IllegalStateException)
        assertTrue(checks.get() >= 3)
        assertEquals(listOf("s"), source.select(WalkRecordsQuery()).sessionIds)
    }

    @Test fun `account source invalidates on logout and cannot revive after the same owner logs in again`() = runBlocking {
        withTimeout(10_000) {
            seedSearchWalk(log, "s", 1)
            val login = Session(OWNER, "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
            var stored: Session? = null
            val sessions = SessionProvider(
                loadSession = { stored }, saveSession = { stored = it }, clearSession = { stored = null },
                refreshSession = { error("Local record reads must not refresh tokens") },
                configured = { true }, now = { 0L },
            )
            assertNull(accountWalkRecordsSource(db, sessions, ZoneOffset.UTC))
            sessions.save(login)
            val old = requireNotNull(accountWalkRecordsSource(db, sessions, ZoneOffset.UTC))
            assertEquals(listOf("s"), old.select(WalkRecordsQuery()).sessionIds)
            val initial = CompletableDeferred<Unit>()
            val loggedOut = async {
                old.changes.first {
                    initial.complete(Unit)
                    sessions.accountScope.value.ownerId == null
                }
            }
            initial.await()
            // No Room writes: the account signal alone must discard the displayed selection.
            sessions.clear()
            loggedOut.await()
            assertNull(accountWalkRecordsSource(db, sessions, ZoneOffset.UTC))
            assertTrue(runCatching { old.select(WalkRecordsQuery()) }.exceptionOrNull() is IllegalStateException)

            sessions.save(login)
            assertTrue(runCatching { old.select(WalkRecordsQuery()) }.exceptionOrNull() is IllegalStateException)
            val fresh = requireNotNull(accountWalkRecordsSource(db, sessions, ZoneOffset.UTC))
            assertEquals(listOf("s"), fresh.select(WalkRecordsQuery()).sessionIds)
            assertEquals(OWNER, dao.session("s")?.ownerId)
        }
    }

    private suspend fun session(id: String, at: Long = 0, owner: String = OWNER,
        ended: Boolean = true, weather: Int? = null, dog: String = "dog") {
        dao.insertSession(WalkSessionRow(id, at, owner, if (ended) at + 1_000 else null,
            weatherCode = weather, isDay = weather?.let { true }))
        dao.insertSessionDog(WalkSessionDogRow(id, dog))
    }

    private suspend fun note(sessionId: String, text: String) {
        WalkEntryStore(dao).save(WalkEntry("note-$sessionId", sessionId, WalkMomentType.NOTE, 0, note = text))
    }

    private suspend fun insertEntry(entry: WalkEntry, pin: String? = null) {
        dao.insertEntry(WalkEntryRow(entry.id, entry.sessionId, entry.toJson().toString(), 4,
            "mutation-${entry.id}", false, pinPayload = pin, pinRevision = if (pin == null) 0 else 1, isV2 = true))
    }

    private suspend fun search(keyword: String) = source.select(WalkRecordsQuery(filter = WalkHistoryFilter(keyword)))

    private fun pin(state: String, method: String, point: GeoPoint?) = JSONObject().apply {
        put("state", state)
        put("method", method)
        put("point", point?.let { JSONObject().put("lat", it.latitude).put("lng", it.longitude) } ?: JSONObject.NULL)
    }.toString()

    private companion object { const val OWNER = "owner-a" }
}
