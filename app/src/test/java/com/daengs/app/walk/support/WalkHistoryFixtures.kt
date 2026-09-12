package com.daengs.app.walk.support

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.store.RoomWalkFixLog
import java.time.LocalDate
import java.time.ZoneOffset

internal suspend fun seedSearchWalk(log: RoomWalkFixLog, id: String, day: Int) {
    val at = LocalDate.of(2026, 9, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    log.openSession(RecordedSession(id, dogIds = listOf("dog"), startedAtMillis = at,
        weather = RecordedWeather(61, true, 20f)))
    (0..5).forEach { i -> log.append(id, RecordedFix(i, 0, at + i * 120_000L,
        37.5 + i * 80.0 / 111195, 127.0, 5f, false)) }
    log.closeSession(id, at + 600_000)
}
