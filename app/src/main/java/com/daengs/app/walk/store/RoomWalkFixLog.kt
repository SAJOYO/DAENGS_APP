package com.daengs.app.walk.store

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.RecordedSession
import com.daengs.app.walk.RecordedWalkAction
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkFixLog
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSyncState
import com.daengs.app.walk.toEntryMoments
import com.daengs.app.walk.toMomentGroups
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomWalkFixLog(private val dao: WalkDao,
    private val prunePhotos: suspend () -> Unit = {},
    private val owner: () -> String = { "" }) : WalkFixLog {
    // 세션 생성/복원과 탈퇴 삭제를 직렬화한다. 이미 실행 중인 작업의 늦은 응답도 막는다.
    private val sessionMutex = Mutex()
    private val forgottenOwners = mutableSetOf<String>()

    /** Share the withdrawal barrier with atomic network restoration, without holding it during HTTP. */
    internal suspend fun restoringForOwner(expected: String, block: suspend () -> Unit) = sessionMutex.withLock {
        check(expected.isNotBlank() && expected == owner() && expected !in forgottenOwners)
        block()
    }

    override val ownerId: String get() = owner()
    /** On-demand diagnostics only; history, pin upload and keep/discard still use their release policies. */
    suspend fun compareMotion(sessionId: String): com.daengs.app.walk.motion.RecordedMotionComparison? =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val expectedOwner = owner()
            val input = dao.motionInput(sessionId, expectedOwner) ?: return@withContext null
            val result = com.daengs.app.walk.motion.compareRecordedMotion(input)
            check(owner() == expectedOwner) { "산책을 읽는 동안 계정이 변경됐어요." }
            result
        }

    override val historyChanges = kotlinx.coroutines.flow.combine(dao.observeSessions(), dao.observeEntryRevisions(),
        dao.observePhotoIds(), dao.observeAnalysisChanges(), dao.observeDiaryPublicationCount()) { _, _, _, _, _ -> Unit }

    override suspend fun historySearchText(sessionIds: List<String>): Map<String, List<String>> {
        val expectedOwner = owner()
        val result = dao.historySearchText(sessionIds, expectedOwner)
        return if (owner() == expectedOwner) result else emptyMap()
    }

    override suspend fun restoreSession(session: RecordedSession) = sessionMutex.withLock {
        val verifiedOwner = requireNotNull(session.ownerId)
        check(verifiedOwner !in forgottenOwners) { "탈퇴한 계정의 산책입니다." }
        val existing = dao.session(session.id)
        require(existing == null || existing.ownerId.isEmpty() || existing.ownerId == verifiedOwner)
        if (existing != null && existing.ownerId.isEmpty()) {
            dao.restoreOwner(session.id, verifiedOwner, requireNotNull(session.serverWalkId))
        }
        openSessionLocked(session, originatedHere = false)
    }

    override suspend fun openSession(session: RecordedSession) = sessionMutex.withLock {
        openSessionLocked(session, originatedHere = true)
    }

    private suspend fun openSessionLocked(session: RecordedSession, originatedHere: Boolean) {
        val capturedOwner = session.ownerId ?: owner()
        check(capturedOwner !in forgottenOwners) { "탈퇴한 계정의 산책입니다." }
        val inserted = dao.insertSession(
            WalkSessionRow(
                id = session.id,
                ownerId = capturedOwner,
                startedAtMillis = session.startedAtMillis,
                endedAtMillis = session.endedAtMillis,
                weatherCode = session.weather?.weatherCode,
                isDay = session.weather?.isDay,
                temperatureC = session.weather?.temperatureC,
                syncState = session.syncState.storedValue,
                serverWalkId = session.serverWalkId,
                syncedAtMillis = session.syncedAtMillis,
                motionPolicyJson = session.motionPolicyJson,
                coordinateOrigin = if (originatedHere) "captured" else null,
            ),
        )
        // **처음 열 때만 붙인다.** 이미 있는 세션에 나중 목록을 덧붙이면 그날 데리고
        // 나가지 않은 아이가 그 산책에 섞인다 (시작 시각을 안 덮어쓰는 것과 같은 이유).
        if (inserted == -1L) return
        if (originatedHere) dao.insertPhotoSync(WalkPhotoSyncRow(session.id, capturedOwner,
            java.util.UUID.randomUUID().toString()))
        for (dogId in session.dogIds) {
            dao.insertSessionDog(WalkSessionDogRow(sessionId = session.id, dogId = dogId))
        }
    }

    override suspend fun append(sessionId: String, fix: RecordedFix) = appendFix(
        WalkFixRow(
            sessionId = sessionId,
            clientSeq = fix.clientSeq,
            chainIndex = fix.chainIndex,
            atMillis = fix.atMillis,
            lat = fix.lat,
            lng = fix.lng,
            accuracyM = fix.accuracyM,
            isMock = fix.isMock,
            ingressSeq = fix.ingressSeq,
            sourceEpoch = fix.sourceEpoch,
            clockEpochId = fix.clockEpochId,
            elapsedRealtimeNanos = fix.elapsedRealtimeNanos,
            receivedElapsedNanos = fix.receivedElapsedNanos,
            receivedAtMillis = fix.receivedAtMillis,
            speedMps = fix.speedMps,
            speedAccuracyMps = fix.speedAccuracyMps,
            bearingDegrees = fix.bearingDegrees,
            bearingAccuracyDegrees = fix.bearingAccuracyDegrees,
            provider = fix.provider,
            recordingEligible = fix.recordingEligible,
            speedMpsBits = fix.speedMps?.toRawBits(),
            speedAccuracyMpsBits = fix.speedAccuracyMps?.toRawBits(),
            bearingDegreesBits = fix.bearingDegrees?.toRawBits(),
            bearingAccuracyDegreesBits = fix.bearingAccuracyDegrees?.toRawBits(),
            latBits = fix.lat.toRawBits(), lngBits = fix.lng.toRawBits(), accuracyBits = fix.accuracyM?.toRawBits(),
        ),
    )

    private suspend fun appendFix(row: WalkFixRow) {
        if (row.ingressSeq == null) dao.insertFix(row) else dao.appendObservation(row)
    }

    override suspend fun saveRecordingEpoch(epoch: com.daengs.app.walk.RecordingEpoch) =
        dao.saveRecordingEpoch(RecordingEpochRow.from(epoch))

    override suspend fun recordingEpochs(sessionId: String) = dao.recordingEpochs(sessionId).map { it.toModel() }

    override suspend fun observationsAfter(sessionId: String, afterSeq: Long, limit: Int) =
        dao.observationsAfter(sessionId, afterSeq, limit).map(WalkFixRow::toModel)

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
        dao.closeAndPrepareDiary(sessionId, endedAtMillis)

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

    override suspend fun forgetOwner(ownerId: String) = withContext(NonCancellable) {
        require(ownerId.isNotBlank())
        sessionMutex.withLock {
            forgottenOwners.add(ownerId)
            dao.deleteOwnerSessions(ownerId)
        }
        // 삭제와 경합하던 사진 저장까지 끝낸 뒤 고아 파일을 회수한다.
        prunePhotos()
    }

    override suspend fun unfinishedSessions(): List<RecordedSession> =
        dao.unfinishedSessions().withDogs()

    override suspend fun finishedSessions(): List<RecordedSession> =
        dao.finishedSessions().withDogs()

    override suspend fun finishedSessionsPage(before: com.daengs.app.walk.WalkHistoryCursor?, dogId: String?, limit: Int): List<RecordedSession> =
        dao.finishedSessionsPage(owner(), dogId, before?.startedAtMillis, before?.sessionId, limit).withDogs()

    override suspend fun sessionsPendingAnalysis(): List<RecordedSession> =
        (dao.sessionsPendingAnalysis() + dao.pendingMotionSessions().filter { row ->
            when (val p = com.daengs.app.walk.motion.MotionPolicies.resolveJson(row.id, row.motionPolicyJson)) {
                is com.daengs.app.walk.motion.MotionPolicySelection.Supported -> p.policy.stored.measurementVersion != null
                is com.daengs.app.walk.motion.MotionPolicySelection.Unsupported -> true
                else -> false
            }
        } + (dao.dirtyEntrySessions() + dao.dirtyPhotoSessions()).mapNotNull { dao.session(it) }
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

    override suspend fun moments(sessionId: String): List<com.daengs.app.walk.WalkMoment> {
        val rows = dao.entries(sessionId)
        val legacyIds = rows.filter { !it.isV2 }.map { it.id }.toSet()
        // Keep the pre-existing history grouping for v1; v2 pins retain their action identity.
        val legacy = actions(sessionId).filter { it.id in legacyIds }.toMomentGroups()
        return legacy + rows.filter { it.isV2 }.mapNotNull { it.entry() }.toEntryMoments()
    }
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
    motionPolicyJson = motionPolicyJson,
)

internal fun WalkFixRow.toModel(): RecordedFix = RecordedFix(
    clientSeq = clientSeq,
    chainIndex = chainIndex,
    atMillis = atMillis,
    lat = latBits?.let(Double::fromBits) ?: lat,
    lng = lngBits?.let(Double::fromBits) ?: lng,
    accuracyM = accuracyBits?.let(Float::fromBits) ?: accuracyM,
    isMock = isMock,
    ingressSeq = ingressSeq,
    sourceEpoch = sourceEpoch,
    clockEpochId = clockEpochId,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    receivedElapsedNanos = receivedElapsedNanos,
    receivedAtMillis = receivedAtMillis,
    speedMps = speedMpsBits?.let(Float::fromBits) ?: speedMps,
    speedAccuracyMps = speedAccuracyMpsBits?.let(Float::fromBits) ?: speedAccuracyMps,
    bearingDegrees = bearingDegreesBits?.let(Float::fromBits) ?: bearingDegrees,
    bearingAccuracyDegrees = bearingAccuracyDegreesBits?.let(Float::fromBits) ?: bearingAccuracyDegrees,
    provider = provider,
    recordingEligible = recordingEligible,
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
