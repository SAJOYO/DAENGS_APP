package com.daengs.app.walk.sync

import androidx.room.withTransaction
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import org.json.JSONArray
import org.json.JSONObject

/** Network responses enter Room together or not at all. Never replace user entries/photos. */
internal class WalkMotionStore(private val database: WalkDatabase, private val owner: () -> String) {
    private val dao get() = database.walkDao()
    fun checkOwner(expected: String) { check(expected.isNotBlank() && owner() == expected) { "계정이 변경됐어요." } }

    suspend fun hasLocal(id: String, account: String): Boolean = database.withTransaction {
        checkOwner(account)
        dao.session(id)?.also { check(it.ownerId == account) } != null
    }

    suspend fun freeze(sessionId: String, walkId: String, account: String): WalkMotionContract.Plan? = database.withTransaction {
        checkOwner(account)
        val session = dao.session(sessionId)?.takeIf { it.ownerId == account } ?: error("산책이 삭제됐어요.")
        check(session.serverWalkId == walkId)
        if (!WalkMotionContract.measured(session.toModel()) || dao.motionBackup(sessionId)?.completedAtMillis != null)
            return@withTransaction null
        val input = dao.motionInput(sessionId, account) ?: error("산책이 삭제됐어요.")
        check(input.session.syncState == WalkSyncState.DERIVED && input.session.serverWalkId == walkId)
        val plan = WalkMotionContract.create(input)
        val old = dao.motionBackup(sessionId)
        if (old == null) dao.insertMotionBackup(plan.row())
        else {
            check(old.manifestFingerprint == plan.manifestHash && old.evidenceFingerprint == plan.evidenceHash) { "고정한 측정 원본이 변경됐어요." }
            val saved = WalkMotionContract.read(input.session, input.fixes, JSONObject(old.manifestJson), plan.points)
            check(saved.evidenceHash == plan.evidenceHash)
        }
        checkOwner(account)
        plan
    }

    suspend fun isComplete(id: String, account: String): Boolean = database.withTransaction {
        checkOwner(account)
        check(dao.session(id)?.ownerId == account)
        dao.motionBackup(id)?.completedAtMillis != null
    }

    suspend fun acknowledge(plan: WalkMotionContract.Plan, account: String, now: Long) = database.withTransaction {
        checkOwner(account)
        val id = plan.input.session.id
        val current = dao.motionInput(id, account) ?: error("산책이 삭제됐어요.")
        check(current.session.serverWalkId == plan.input.session.serverWalkId)
        check(WalkMotionContract.create(current).evidenceHash == plan.evidenceHash)
        val saved = requireNotNull(dao.motionBackup(id))
        check(saved.evidenceFingerprint == plan.evidenceHash)
        dao.updateMotionBackup(saved.copy(completedAtMillis = saved.completedAtMillis ?: now, lastError = null))
        checkOwner(account)
    }

    suspend fun failed(id: String, account: String, reason: String) = database.withTransaction {
        checkOwner(account)
        if (dao.session(id)?.ownerId == account) dao.motionBackup(id)?.takeIf { it.completedAtMillis == null }?.let {
            dao.updateMotionBackup(it.copy(lastError = reason))
        }
    }

    suspend fun restore(session: RecordedSession, fixes: List<RecordedFix>, plan: WalkMotionContract.Plan?, account: String, now: Long, expectedLocal: Boolean, precision: WalkMotionPrecisionSync.Restored? = null) = database.withTransaction {
        checkOwner(account)
        require(session.ownerId == account && session.serverWalkId != null && session.endedAtMillis != null)
        val existing = dao.session(session.id)
        check(!expectedLocal || existing != null) { "복원 도중 산책이 삭제됐어요." }
        if (existing != null) {
            check(existing.ownerId == account && existing.serverWalkId == session.serverWalkId && existing.endedAtMillis == session.endedAtMillis)
            // A locally recorded/previously verified policy is never replaced from the network.
            if (plan == null || (existing.motionPolicyJson != null && (precision == null || existing.coordinateOrigin != null))) return@withTransaction
            if (existing.motionPolicyJson != null) {
                val previous = WalkMotionContract.create(requireNotNull(dao.motionInput(session.id, account)))
                check(previous.evidenceHash == plan.evidenceHash)
                check(dao.motionBackup(session.id)?.evidenceFingerprint == plan.evidenceHash)
            } else check(dao.recordingEpochs(session.id).isEmpty()) { "로컬 측정 원본을 덮어쓸 수 없어요." }
            val local = dao.fixes(session.id).map { it.toModel() }
            check(WalkRecordingContract.rawFingerprint(local) == WalkRecordingContract.rawFingerprint(fixes))
            check(local.map { it.recordingEligible } == fixes.map { it.recordingEligible })
        } else {
            check(dao.insertSession(WalkSessionRow(session.id, startedAtMillis = session.startedAtMillis,
                endedAtMillis = session.endedAtMillis, weatherCode = session.weather?.weatherCode,
                isDay = session.weather?.isDay, temperatureC = session.weather?.temperatureC,
                syncState = session.syncState.storedValue, serverWalkId = session.serverWalkId,
                syncedAtMillis = session.syncedAtMillis, ownerId = account, motionPolicyJson = null)) != -1L)
            session.dogIds.forEach { dao.insertSessionDog(WalkSessionDogRow(session.id, it)) }
        }
        // No epochs have been installed yet; normal ingress append deliberately rejects closed sessions.
        val original = if (existing != null) dao.fixes(session.id).associateBy { it.clientSeq } else emptyMap()
        fixes.forEach { fix ->
            // Preserve local coordinate precision while attaching verified metadata to an old remote restore.
            val local = original[fix.clientSeq]
            val row = (if (local == null || precision != null) fix else fix.copy(lat = local.lat, lng = local.lng,
                atMillis = local.atMillis, accuracyM = local.accuracyM, isMock = local.isMock)).motionRow(session.id, precision != null)
            if (existing == null) dao.insertObservation(row) else dao.updateMotionObservation(row)
        }
        if (plan != null && existing?.motionPolicyJson == null) {
            plan.input.epochs.forEach { e ->
                check(dao.recordingEpochById(e.id) == null) { "다른 산책의 측정 구간과 충돌해요." }
                dao.saveRecordingEpoch(RecordingEpochRow.from(e))
            }
            dao.installMotionPolicy(session.id, account, requireNotNull(plan.input.session.motionPolicyJson))
            dao.insertMotionBackup(plan.row().copy(completedAtMillis = now))
            // A complete motion receipt is issued only after the existing raw finalize is derived.
            dao.markDerived(session.id, now)
        }
        if (precision != null) {
            val row = precision.plan.row().copy(completedAtMillis = now, verifiedAtMillis = now, verificationJson = precision.receipt.toString())
            if (dao.motionPrecision(session.id) == null) dao.insertMotionPrecision(row) else dao.updateMotionPrecision(row)
            dao.installCoordinateOrigin(session.id, account, "verified")
        }
        checkOwner(account)
    }

    private fun WalkMotionContract.Plan.row() = WalkMotionBackupRow(input.session.id, manifest.toString(), manifestHash, evidenceHash)
    companion object { fun objects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) } }
}

internal fun RecordedFix.motionRow(sessionId: String, exactCoordinates: Boolean = false) = WalkFixRow(sessionId, clientSeq, chainIndex, atMillis,
    lat, lng, accuracyM, isMock, ingressSeq, sourceEpoch, clockEpochId, elapsedRealtimeNanos,
    receivedElapsedNanos, receivedAtMillis, speedMps, speedAccuracyMps, bearingDegrees,
    bearingAccuracyDegrees, provider, recordingEligible, speedMps?.toRawBits(), speedAccuracyMps?.toRawBits(),
    bearingDegrees?.toRawBits(), bearingAccuracyDegrees?.toRawBits(),
    if (exactCoordinates) lat.toRawBits() else null, if (exactCoordinates) lng.toRawBits() else null,
    if (exactCoordinates) accuracyM?.toRawBits() else null)
