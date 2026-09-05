package com.daengs.app.walk.store

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWalkAction
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkFixLog
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSyncState

class RoomWalkFixLog(private val dao: WalkDao,
    private val prunePhotos: suspend () -> Unit = {},
    private val owner: () -> String = { "" }) : WalkFixLog {
    override val ownerId: String get() = owner()
    override val historyChanges = kotlinx.coroutines.flow.combine(dao.observeSessions(), dao.observeEntryRevisions(), dao.observePhotoIds()) { _, _, _ -> Unit }

    override suspend fun restoreSession(session: RecordedSession) {
        val verifiedOwner = requireNotNull(session.ownerId)
        val existing = dao.session(session.id)
        require(existing == null || existing.ownerId.isEmpty() || existing.ownerId == verifiedOwner)
        if (existing != null && existing.ownerId.isEmpty()) {
            dao.restoreOwner(session.id, verifiedOwner, requireNotNull(session.serverWalkId))
        }
        openSession(session)
    }

    override suspend fun openSession(session: RecordedSession) {
        val inserted = dao.insertSession(
            WalkSessionRow(
                id = session.id,
                ownerId = session.ownerId ?: owner(),
                startedAtMillis = session.startedAtMillis,
                endedAtMillis = session.endedAtMillis,
                weatherCode = session.weather?.weatherCode,
                isDay = session.weather?.isDay,
                temperatureC = session.weather?.temperatureC,
                syncState = session.syncState.storedValue,
                serverWalkId = session.serverWalkId,
                syncedAtMillis = session.syncedAtMillis,
            ),
        )
        // **처음 열 때만 붙인다.** 이미 있는 세션에 나중 목록을 덧붙이면 그날 데리고
        // 나가지 않은 아이가 그 산책에 섞인다 (시작 시각을 안 덮어쓰는 것과 같은 이유).
        if (inserted == -1L) return
        for (dogId in session.dogIds) {
            dao.insertSessionDog(WalkSessionDogRow(sessionId = session.id, dogId = dogId))
        }
    }

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

    override suspend fun appendAction(action: RecordedWalkAction) {
        if (dao.entry(action.id) != null) return
        val dog = dao.sessionDogs(action.sessionId).singleOrNull()?.dogId
        WalkEntryStore(dao).save(com.daengs.app.walk.WalkEntry(
            id = action.id, sessionId = action.sessionId, type = action.type,
            recordedAtMillis = action.recordedAtMillis, point = action.point,
            locationCapturedAtMillis = action.locationCapturedAtMillis,
            accuracyMeters = action.accuracyMeters, petId = dog,
        ))
    }

    override suspend fun hasEntries(sessionId: String): Boolean =
        dao.entries(sessionId).any { it.payload != null } || dao.hasPhotos(sessionId)

    override suspend fun closeSession(sessionId: String, endedAtMillis: Long) =
        dao.closeSession(sessionId, endedAtMillis)

    override suspend fun stampWeather(sessionId: String, weather: RecordedWeather) =
        dao.stampWeather(
            sessionId = sessionId,
            weatherCode = weather.weatherCode,
            isDay = weather.isDay,
            temperatureC = weather.temperatureC,
        )

    override suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
        prunePhotos()
    }

    override suspend fun forgetDog(dogId: String) {
        // **순서가 중요하다.** 연결을 먼저 떼면 "그 아이와만 나간 산책" 을 찾을 근거가
        // 사라져서, 아무도 안 붙은 산책이 되어 그대로 남는다.
        dao.deleteSessionsOnlyWith(dogId)
        dao.unlinkDog(dogId)
        prunePhotos()
    }

    override suspend fun forgetEverything() {
        dao.deleteOwnerSessions(owner())
        prunePhotos()
    }

    override suspend fun unfinishedSessions(): List<RecordedSession> =
        dao.unfinishedSessions().withDogs()

    override suspend fun finishedSessions(): List<RecordedSession> =
        dao.finishedSessions().withDogs()

    override suspend fun sessionsPendingAnalysis(): List<RecordedSession> =
        (dao.sessionsPendingAnalysis() + dao.dirtyEntrySessions().mapNotNull { dao.session(it) }
            .filter { it.endedAtMillis != null }).distinctBy { it.id }.withDogs()

    /**
     * 아이들을 **한 번에** 붙인다.
     *
     * 산책마다 한 번씩 물으면 스무 건이면 스무 번 왕복한다. 목록이 열릴 때마다
     * 그러면 아깝다.
     */
    private suspend fun List<WalkSessionRow>.withDogs(): List<RecordedSession> {
        if (isEmpty()) return emptyList()
        val dogs = dao.sessionDogs(map { it.id }).groupBy({ it.sessionId }, { it.dogId })
        return filter { it.ownerId == owner() }.map { row -> row.toModel(dogs[row.id].orEmpty()) }
    }

    override suspend fun markRawUploaded(
        sessionId: String,
        serverWalkId: String,
        changedAtMillis: Long,
    ) = dao.markRawUploaded(sessionId, serverWalkId, changedAtMillis)

    override suspend fun markDerived(sessionId: String, changedAtMillis: Long) =
        dao.markDerived(sessionId, changedAtMillis)

    override suspend fun session(sessionId: String): RecordedSession? =
        dao.session(sessionId)?.takeIf { it.ownerId == owner() }?.toModel(dao.sessionDogs(sessionId).map { it.dogId })

    override suspend fun fixes(sessionId: String): List<RecordedFix> =
        dao.fixes(sessionId).map(WalkFixRow::toModel)

    override suspend fun actions(sessionId: String): List<RecordedWalkAction> =
        dao.entries(sessionId).mapNotNull { it.entry() }.sortedBy { it.recordedAtMillis }.filter {
            it.type != WalkMomentType.NOTE && it.point != null
        }.map { entry -> RecordedWalkAction(entry.id, entry.sessionId, entry.type,
            entry.recordedAtMillis, requireNotNull(entry.locationCapturedAtMillis),
            requireNotNull(entry.point), entry.accuracyMeters) }
}

fun WalkSessionRow.toModel(dogIds: List<String> = emptyList()): RecordedSession = RecordedSession(
    id = id,
    dogIds = dogIds,
    ownerId = ownerId,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    // 셋 중 하나라도 없으면 날씨를 못 받은 것으로 본다 — 코드가 곧 있고 없고다.
    weather = weatherCode?.let {
        RecordedWeather(weatherCode = it, isDay = isDay ?: true, temperatureC = temperatureC)
    },
    syncState = WalkSyncState.fromStored(syncState),
    serverWalkId = serverWalkId,
    syncedAtMillis = syncedAtMillis,
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

/** 모르는 미래 코드는 버린다. 앱이 오래됐다고 산책 상세 전체가 열리지 않으면 안 된다. */
private fun WalkActionRow.toModel(): RecordedWalkAction? {
    val type = WalkMomentType.entries.firstOrNull { it.behaviorCode == typeCode } ?: return null
    return RecordedWalkAction(
        id = id,
        sessionId = sessionId,
        type = type,
        recordedAtMillis = recordedAtMillis,
        locationCapturedAtMillis = locationCapturedAtMillis,
        point = GeoPoint(lat, lng),
        accuracyMeters = accuracyM,
    )
}
