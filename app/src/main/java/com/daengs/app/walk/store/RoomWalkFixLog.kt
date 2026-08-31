package com.daengs.app.walk.store

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.WalkFixLog

class RoomWalkFixLog(private val dao: WalkDao) : WalkFixLog {
    override suspend fun openSession(session: RecordedSession) = dao.insertSession(
        WalkSessionRow(
            id = session.id,
            dogId = session.dogId,
            startedAtMillis = session.startedAtMillis,
            endedAtMillis = session.endedAtMillis,
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

    override suspend fun deleteSession(sessionId: String) = dao.deleteSession(sessionId)

    override suspend fun unfinishedSessions(): List<RecordedSession> =
        dao.unfinishedSessions().map(WalkSessionRow::toModel)

    override suspend fun session(sessionId: String): RecordedSession? =
        dao.session(sessionId)?.toModel()

    override suspend fun fixes(sessionId: String): List<RecordedFix> =
        dao.fixes(sessionId).map(WalkFixRow::toModel)
}

private fun WalkSessionRow.toModel(): RecordedSession = RecordedSession(
    id = id,
    dogId = dogId,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
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
