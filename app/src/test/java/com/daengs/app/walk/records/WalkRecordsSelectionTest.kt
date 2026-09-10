package com.daengs.app.walk.records

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkDepartureWeather
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkHistoryFilter
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSeason
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.pin.ActionPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class WalkRecordsSelectionTest {
    @Test
    fun `dog date season departure weather and visible text combine before selecting ended records`() {
        val base = record("matching", weatherCode = 61, title = "호수 산책")
        val candidates = listOf(base,
            base.copy(summary = base.summary.copy(sessionId = "other-dog", dogIds = listOf("other"))),
            record("clear", title = "호수 산책"),
            record("unknown", weatherCode = null, title = "호수 산책"),
            record("other-text", weatherCode = 61, title = "골목 산책"),
            record("ongoing", weatherCode = 61, title = "호수 산책", ended = false),
            record("other-season", at = "2026-06-10T03:00:00Z", weatherCode = 61, title = "호수 산책"))
        val query = WalkRecordsQuery("dog", WalkHistoryFilter(
            keyword = "호수", from = LocalDate.of(2026, 6, 1), through = LocalDate.of(2026, 9, 30),
            seasons = setOf(WalkSeason.AUTUMN, WalkSeason.WINTER),
            weather = setOf(WalkDepartureWeather.RAIN, WalkDepartureWeather.SNOW),
        ))

        assertEquals(listOf("matching"), selectWalkRecords(candidates, query, KST).sessionIds)
    }

    @Test
    fun `inclusive start dates and seasons share the existing history timezone matcher`() {
        val candidates = listOf(
            record("before", at = "2026-02-28T14:59:59.999Z", weatherCode = null),
            record("first", at = "2026-02-28T15:00:00Z", weatherCode = null),
            record("last", at = "2026-03-01T14:59:59.999Z", weatherCode = null),
            record("after", at = "2026-03-01T15:00:00Z", weatherCode = null),
        )
        val filter = WalkHistoryFilter(from = LocalDate.of(2026, 3, 1), through = LocalDate.of(2026, 3, 1),
            seasons = setOf(WalkSeason.SPRING), weather = setOf(WalkDepartureWeather.UNKNOWN))

        assertEquals(listOf("last", "first"), selectWalkRecords(candidates, WalkRecordsQuery(filter = filter), KST).sessionIds)
        assertEquals(listOf("after", "last"), selectWalkRecords(candidates, WalkRecordsQuery(filter = filter), ZoneOffset.UTC).sessionIds)
        candidates.forEach { record ->
            val summary = record.summary
            val session = RecordedSession(summary.sessionId, startedAtMillis = summary.startedAtMillis,
                endedAtMillis = summary.endedAtMillis, weather = summary.weather)
            assertEquals(filter.matches(session, KST), filter.matches(summary, KST))
        }
        assertFalse(filter.copy(weather = setOf(WalkDepartureWeather.CLEAR)).matches(candidates[1].summary, KST))
    }

    @Test
    fun `title and notes use NFC case insensitive literal search without treating IDs as text`() {
        val candidates = listOf(record("private-reference", title = "Cafe\u0301 산책", notes = listOf("100% 호수_산책")))
        fun ids(keyword: String) = selectWalkRecords(candidates, WalkRecordsQuery(filter = WalkHistoryFilter(keyword)), KST).sessionIds

        assertEquals(listOf("private-reference"), ids(" CAFÉ "))
        assertEquals(listOf("private-reference"), ids("100% 호수_"))
        assertEquals(listOf("private-reference"), ids("   "))
        assertTrue(ids("private-reference").isEmpty())
        assertTrue(ids("100%_산책").isEmpty())
    }

    @Test
    fun `accepted short records remain searchable even when they have no map trace`() {
        val without = record("without", notes = listOf("킁킁 기록"))
        val with = record("with", notes = listOf("킁킁 기록"), trace = trace("with"))
        val selected = selectWalkRecords(listOf(without, with), WalkRecordsQuery(filter = WalkHistoryFilter("킁킁")), KST)

        assertEquals(listOf("without", "with"), selected.sessionIds)
        assertEquals(2, selected.records.size)
        assertEquals(listOf("with"), selected.records.mapNotNull { it.trace?.walkId })
        assertTrue(selected.records.all { it.summary.distanceMeters == 0.0 && it.summary.activeDurationMillis == 0L })
    }

    @Test
    fun `full IDs and traces stay fixed before paging and are detached from mutable input collections`() {
        val dogs = mutableListOf("dog")
        val notes = mutableListOf("메모")
        val cells = mutableSetOf(SpatialDiaryCellId(0, 0))
        val seasons = mutableSetOf(WalkSeason.AUTUMN)
        val candidates = (1..8).map { id ->
            record("walk-$id", dogs = dogs, notes = notes,
                trace = WalkTraceSheet("walk-$id", cells = cells))
        }.toMutableList()
        val query = WalkRecordsQuery("dog", WalkHistoryFilter(seasons = seasons))
        val selected = selectWalkRecords(candidates, query, KST)
        candidates.clear(); dogs.clear(); notes.clear(); cells.clear(); seasons.clear()

        assertEquals((8 downTo 1).map { "walk-$it" }, selected.sessionIds)
        assertEquals(8, selected.records.mapNotNull { it.trace }.size)
        assertEquals((8 downTo 4).map { "walk-$it" }, selected.page(0).map { it.summary.sessionId })
        assertEquals((3 downTo 1).map { "walk-$it" }, selected.page(1).map { it.summary.sessionId })
        assertTrue(selected.page(Int.MAX_VALUE).isEmpty())
        assertEquals(setOf(WalkSeason.AUTUMN), selected.query.filter.seasons)
        assertTrue(selected.records.all { it.summary.dogIds == listOf("dog") && it.notes == listOf("메모") && it.trace!!.cells.size == 1 })
    }

    @Test
    fun `duplicate or mismatched record identities and invalid pages fail explicitly`() {
        val existing = record("walk")
        assertThrows(IllegalArgumentException::class.java) {
            selectWalkRecords(listOf(existing, existing), WalkRecordsQuery(), KST)
        }
        assertThrows(IllegalArgumentException::class.java) { existing.copy(trace = trace("other-walk")) }
        assertThrows(IllegalArgumentException::class.java) { WalkRecordsSelection(WalkRecordsQuery(), listOf(existing, existing)) }
        assertThrows(IllegalArgumentException::class.java) { WalkRecordsSelection(WalkRecordsQuery(), listOf(record("ongoing", ended = false))) }
        val selection = selectWalkRecords(listOf(existing), WalkRecordsQuery(), KST)
        assertThrows(IllegalArgumentException::class.java) { selection.page(-1) }
        assertThrows(IllegalArgumentException::class.java) { selection.page(0, 0) }
        val action = entry("action", "walk")
        assertThrows(IllegalArgumentException::class.java) { existing.copy(entries = listOf(action.copy(sessionId = "other"))) }
        assertThrows(IllegalArgumentException::class.java) { existing.copy(entries = listOf(action, action)) }
        assertThrows(IllegalArgumentException::class.java) { existing.copy(entries = listOf(action.copy(id = ""))) }
    }

    @Test
    fun `behavior query matches the entry dog exactly and never treats notes as actions`() {
        val actions = listOf(
            entry("mine", "shared"),
            entry("other", "shared", petId = "other"),
            entry("unassigned", "shared", petId = null),
            entry("bark", "shared", type = WalkMomentType.BARKING),
            entry("excretion", "shared", type = WalkMomentType.EXCRETION),
            entry("note", "shared", type = WalkMomentType.NOTE),
        )
        val candidate = record("shared", dogs = listOf("dog", "other"), entries = actions)
        val baseline = selectWalkRecords(listOf(candidate), WalkRecordsQuery("dog"), KST)
        val sniffing = selectWalkRecordBehaviors(baseline, WalkMomentType.SNIFFING)

        assertSame(baseline, sniffing.baseline)
        assertEquals(listOf("mine"), sniffing.records.map { it.entry.id })
        assertEquals(listOf("bark"), selectWalkRecordBehaviors(baseline, WalkMomentType.BARKING).records.map { it.entry.id })
        assertEquals(listOf("excretion"), selectWalkRecordBehaviors(baseline, WalkMomentType.EXCRETION).records.map { it.entry.id })
        val allDogs = selectWalkRecords(listOf(candidate), WalkRecordsQuery(), KST)
        assertEquals(setOf("mine", "other", "unassigned"),
            selectWalkRecordBehaviors(allDogs, WalkMomentType.SNIFFING).records.map { it.entry.id }.toSet())
        assertThrows(IllegalArgumentException::class.java) { selectWalkRecordBehaviors(baseline, WalkMomentType.NOTE) }
    }

    @Test
    fun `entry results retain missing locations and traces while related walks keep baseline order`() {
        // This selected walk crosses midnight. Its action is still part of S on the following day.
        val lateAt = Instant.parse("2026-09-10T15:01:00Z").toEpochMilli()
        val earlyAt = Instant.parse("2026-09-10T04:00:00Z").toEpochMilli()
        val source = mutableListOf(entry("late", "older", at = lateAt), entry("early", "older", at = earlyAt))
        val olderStart = record("older", entries = source)
        val older = olderStart.copy(summary = olderStart.summary.copy(endedAtMillis = lateAt + 1_000))
        val newer = record("newer", at = "2026-09-10T06:00:00Z", entries = listOf(entry("middle", "newer", at = earlyAt + 1)))
        val without = record("without")
        val baseline = selectWalkRecords(listOf(older, newer, without), WalkRecordsQuery(filter = WalkHistoryFilter(
            from = LocalDate.of(2026, 9, 10), through = LocalDate.of(2026, 9, 10))), KST)
        source.clear()
        val selected = selectWalkRecordBehaviors(baseline, WalkMomentType.SNIFFING)

        assertEquals(listOf("newer", "without", "older"), baseline.sessionIds)
        assertEquals(listOf("late", "middle", "early"), selected.records.map { it.entry.id })
        assertEquals(listOf("newer", "older"), selected.related.sessionIds)
        assertEquals(baseline.query, selected.related.query)
        assertTrue(selected.records.all { it.point == null && it.walk.trace == null })
        assertEquals(2, baseline.records.single { it.summary.sessionId == "older" }.entries.size)
        assertTrue(selectWalkRecordBehaviors(baseline, WalkMomentType.BARKING).related.records.isEmpty())
    }

    @Test
    fun `behavior identity uses the walk and entry tuple and ties have stable ordering`() {
        val baseline = selectWalkRecords(listOf(
            record("a:b", entries = listOf(entry("c", "a:b"))),
            record("a", entries = listOf(entry("b:c", "a"), entry("c", "a"))),
        ), WalkRecordsQuery(), KST)
        val selected = selectWalkRecordBehaviors(baseline, WalkMomentType.SNIFFING)
        val left = selected.records.single { it.entry.sessionId == "a:b" }
        val right = selected.records.single { it.entry.id == "b:c" }

        assertNotEquals(left.key, right.key)
        assertEquals(3, selected.records.map { it.key }.toSet().size)
        assertEquals(selected.records.map { it.key }.sorted(), selected.records.map { it.key })
        assertEquals(selected.records.map { it.key }, selectWalkRecordBehaviors(
            WalkRecordsSelection(baseline.query, baseline.records.reversed()), WalkMomentType.SNIFFING).records.map { it.key })
    }

    @Test
    fun `behavior display prefers the estimated pin and retains original or missing location labels`() {
        val original = GeoPoint(37.5, 127.0)
        val estimate = GeoPoint(37.501, 127.001)
        val gps = entry("gps", "walk").copy(point = original, locationCapturedAtMillis = 1)
        val estimated = gps.copy(id = "estimated", pin = ActionPin(
            """{"state":"final","method":"estimated","point":{"lat":37.501,"lng":127.001}}"""))
        val unlocated = gps.copy(id = "unlocated", pin = ActionPin(
            """{"state":"unlocated","method":"none","point":null}"""))
        val baseline = selectWalkRecords(listOf(record("walk", entries = listOf(gps, estimated, unlocated,
            entry("missing", "walk")))), WalkRecordsQuery(), KST)
        val records = selectWalkRecordBehaviors(baseline, WalkMomentType.SNIFFING).records.associateBy { it.entry.id }

        assertEquals(estimate, records.getValue("estimated").point)
        assertEquals("추정 위치", records.getValue("estimated").locationLabel)
        assertEquals(original, records.getValue("estimated").entry.point)
        assertEquals(original, records.getValue("gps").point)
        assertEquals("위치와 함께 남긴 기록", records.getValue("gps").locationLabel)
        assertEquals(original, records.getValue("unlocated").entry.point)
        listOf("missing", "unlocated").forEach { id ->
            assertNull(records.getValue(id).point)
            assertEquals("위치 없이 남긴 행동", records.getValue(id).locationLabel)
        }
    }

    private fun record(
        id: String, at: String = "2026-09-10T03:00:00Z", dogs: List<String> = listOf("dog"),
        weatherCode: Int? = 0, ended: Boolean = true, title: String? = null,
        notes: List<String> = emptyList(), trace: WalkTraceSheet? = null,
        entries: List<WalkEntry> = emptyList(),
    ): WalkRecord {
        val start = Instant.parse(at).toEpochMilli()
        return WalkRecord(WalkSummary(id, dogs, start, if (ended) start + 10_000 else null,
            weatherCode?.let { RecordedWeather(it, true, 20f) }, 0.0, 0L, emptyList(), null),
            title, notes, trace, entries)
    }

    private fun entry(id: String, walkId: String, type: WalkMomentType = WalkMomentType.SNIFFING,
        petId: String? = "dog", at: Long = 1L) = WalkEntry(id = id, sessionId = walkId,
        type = type, recordedAtMillis = at, petId = petId, note = if (type == WalkMomentType.NOTE) "메모" else null)

    private fun trace(id: String) = WalkTraceSheet(id, cells = setOf(SpatialDiaryCellId(0, 0)))
    private companion object { val KST: ZoneId = ZoneId.of("Asia/Seoul") }
}
