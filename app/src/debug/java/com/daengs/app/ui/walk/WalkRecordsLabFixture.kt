package com.daengs.app.ui.walk

import com.daengs.app.location.LocationSample
import com.daengs.app.pet.Pet
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.distanceTo
import com.daengs.app.walk.records.WalkRecord
import com.daengs.app.walk.records.WalkRecordsQuery
import com.daengs.app.walk.records.WalkRecordsSelection
import com.daengs.app.walk.records.WalkRecordsSource
import com.daengs.app.walk.records.selectWalkRecords
import java.time.LocalDate
import java.time.ZoneId

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
        val sheets = WalkTraceBrushLabFixture.sheets()
        val routes = WalkTraceBrushLabFixture.routes()
        dates.mapIndexed { index, date ->
            val id = "sample-record-${index + 1}"
            val at = LocalDate.parse(date).atTime(18 + index % 3, 20).atZone(zone).toInstant().toEpochMilli()
            val duration = (12 + index % 5) * 60_000L
            val paths = routes[index % routes.size]
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
            ), trace = if (index == 3) null else sheets[index % sheets.size].copy(walkId = id))
        }
    }

    override suspend fun select(query: WalkRecordsQuery): WalkRecordsSelection =
        selectWalkRecords(records, query, zone)

    private fun pet(id: String, name: String, breed: String) = Pet(id = id, name = name, breed = breed,
        sex = null, neutered = null, weightKg = null, birthDate = null, birthDateKind = null,
        isPrimary = id == "sample-dog-1")
}
