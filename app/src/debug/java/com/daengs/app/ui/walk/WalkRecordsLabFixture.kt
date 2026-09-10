package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.pet.Pet
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.distanceTo
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.diary.SpatialDiaryHexGrid
import com.daengs.app.walk.records.WalkRecord
import com.daengs.app.walk.records.WalkRecordsQuery
import com.daengs.app.walk.records.WalkRecordsSelection
import com.daengs.app.walk.records.WalkRecordsSource
import com.daengs.app.walk.records.selectWalkRecords
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/** Fictional sessions only. No Room, server calls, or writes to a user's history. */
internal object WalkRecordsLabFixture : WalkRecordsSource {
    val today: LocalDate = LocalDate.of(2026, 9, 10)
    val pets = listOf(pet("sample-dog-1", "두부", "maltese"), pet("sample-dog-2", "보리", "shiba"))
    private val zone = ZoneId.systemDefault()
    private val dates = listOf("2026-09-09", "2026-09-08", "2026-09-07", "2026-09-05",
        "2026-09-03", "2026-09-01", "2026-08-29", "2026-08-20", "2026-08-05",
        "2026-06-12", "2026-04-18", "2026-01-22")
    private val weatherCodes = listOf(0, 61, 0, 3, 61, 0, 0, 61, 3, 0, 0, 71)
    private val titles = listOf("저녁 바람 따라 한 바퀴", "비 온 뒤 서울숲", "둘이 함께 걷던 날",
        "구름 아래 느긋하게", "우산과 함께", "9월 첫 산책", "여름 끝의 산책", "촉촉한 숲길",
        "구름이 머문 오후", "긴 여름 저녁", "봄바람을 따라서", "눈 위에 남긴 발자국")

    val records: List<WalkRecord> by lazy {
        val routes = WalkTraceBrushLabFixture.routes()
        val firstLoop = routes[0].first()
        val secondLoop = routes[1].first()
        // Full A appears three times and full B twice. Other recorded walks return along
        // prefixes, leaving a heavily shared opening and visibly different 2/3/5-walk areas.
        // The first two keep the original experiment, including A's disconnected single point.
        val chosenRoutes = listOf(
            routes[0],
            routes[1],
            outAndBack(firstLoop.take(4)),
            routes[1], // The existing fourth record still has its real route but no trace.
            routes[0],
            outAndBack(firstLoop.take(2)),
            outAndBack(firstLoop.take(3)),
            routes[1],
            routes[0],
            outAndBack(firstLoop.take(5)),
            outAndBack(secondLoop.take(6)),
            outAndBack(firstLoop.take(4)),
        )
        dates.mapIndexed { index, date ->
            val id = "sample-record-${index + 1}"
            val at = LocalDate.parse(date).atTime(18 + index % 3, 20).atZone(zone).toInstant().toEpochMilli()
            val duration = (12 + index % 5) * 60_000L
            val paths = chosenRoutes[index]
            val pointCount = paths.sumOf { it.size }
            var ordinal = 0
            val segments = paths.map { path -> path.map { point ->
                LocationSample(point, at + duration * ordinal++ / pointCount, accuracyMeters = 5f)
            } }
            val summary = WalkSummary(
                sessionId = id,
                dogIds = if (index == 2) pets.map { it.id } else listOf(pets[index % pets.size].id),
                startedAtMillis = at, endedAtMillis = at + duration,
                weather = if (index == 8) null else RecordedWeather(weatherCodes[index], false, 22f),
                distanceMeters = paths.sumOf { path -> path.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) } },
                activeDurationMillis = duration, segments = segments, anchor = paths.first().first(),
            )
            WalkRecord(summary, titles[index], notes = listOf(
                if (weatherCodes[index] == 61) "비가 그쳐 나무 그늘 아래를 천천히 걸었어요." else "나무 그늘에서 잠깐 쉬었다 돌아왔어요.",
            ), trace = if (index == 3) null else WalkTraceSheet(id, TRACE_RADIUS_U, traceCells(paths)))
        }
    }

    override suspend fun select(query: WalkRecordsQuery): WalkRecordsSelection =
        selectWalkRecords(records, query, zone)

    private fun outAndBack(path: List<GeoPoint>): List<List<GeoPoint>> =
        listOf(path + path.dropLast(1).asReversed())

    /** Sample the same paths shown by the record. Never join separate segments or single points. */
    private fun traceCells(paths: List<List<GeoPoint>>): Set<SpatialDiaryCellId> = buildSet {
        paths.forEach { path ->
            if (path.size == 1) add(SpatialDiaryHexGrid.cellFor(path.single(), TRACE_RADIUS_U))
            path.zipWithNext().forEach { (start, end) ->
                val steps = ceil(start.distanceTo(end) / TRACE_SAMPLE_METRES).toInt().coerceAtLeast(1)
                for (step in 0..steps) {
                    val fraction = step.toDouble() / steps
                    val point = GeoPoint(
                        latitude = start.latitude + (end.latitude - start.latitude) * fraction,
                        longitude = start.longitude + (end.longitude - start.longitude) * fraction,
                    )
                    add(SpatialDiaryHexGrid.cellFor(point, TRACE_RADIUS_U))
                }
            }
        }
    }

    private fun pet(id: String, name: String, breed: String) = Pet(id = id, name = name, breed = breed,
        sex = null, neutered = null, weightKg = null, birthDate = null, birthDateKind = null,
        isPrimary = id == "sample-dog-1")

    private const val TRACE_RADIUS_U = 8.0
    private const val TRACE_SAMPLE_METRES = 2.0
}
