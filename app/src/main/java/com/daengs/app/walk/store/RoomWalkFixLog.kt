package com.daengs.app.walk.store

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkFixLog

class RoomWalkFixLog(private val dao: WalkDao) : WalkFixLog {
    override suspend fun openSession(session: RecordedSession) = dao.insertSession(
        WalkSessionRow(
            id = session.id,
            dogId = session.dogId,
            startedAtMillis = session.startedAtMillis,
            endedAtMillis = session.endedAtMillis,
            weatherCode = session.weather?.weatherCode,
            isDay = session.weather?.isDay,
            temperatureC = session.weather?.temperatureC,
        ),
    )

    override suspend fun append(sessionId: String, fix: RecordedFix) = dao.insertFix(
        WalkFixRow(
            sessionId = sessionId,
            clientSeq = fix.clientSeq,
            chainIndex = fix.chainIndex,
            atMillis = fix.atMillis,
            lat = fix.lat,
            lng = fix.lng,
            accuracyM = fix.accuracyM,
            isMock = fix.isMock,
        ),
    )

    override suspend fun closeSession(sessionId: String, endedAtMillis: Long) =
        dao.closeSession(sessionId, endedAtMillis)

    override suspend fun stampWeather(sessionId: String, weather: RecordedWeather) =
        dao.stampWeather(
            sessionId = sessionId,
            weatherCode = weather.weatherCode,
            isDay = weather.isDay,
            temperatureC = weather.temperatureC,
        )

    override suspend fun deleteSession(sessionId: String) = dao.deleteSession(sessionId)

    override suspend fun unfinishedSessions(): List<RecordedSession> =
        dao.unfinishedSessions().map(WalkSessionRow::toModel)

    override suspend fun finishedSessions(): List<RecordedSession> =
        dao.finishedSessions().map(WalkSessionRow::toModel)

    override suspend fun session(sessionId: String): RecordedSession? =
        dao.session(sessionId)?.toModel()

    override suspend fun fixes(sessionId: String): List<RecordedFix> =
        dao.fixes(sessionId).map(WalkFixRow::toModel)
}

fun WalkSessionRow.toModel(): RecordedSession = RecordedSession(
    id = id,
    dogId = dogId,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    // 셋 중 하나라도 없으면 날씨를 못 받은 것으로 본다 — 코드가 곧 있고 없고다.
    weather = weatherCode?.let {
        RecordedWeather(weatherCode = it, isDay = isDay ?: true, temperatureC = temperatureC)
    },
)

private fun WalkFixRow.toModel(): RecordedFix = RecordedFix(
    clientSeq = clientSeq,
    chainIndex = chainIndex,
    atMillis = atMillis,
    lat = lat,
    lng = lng,
    accuracyM = accuracyM,
    isMock = isMock,
)
