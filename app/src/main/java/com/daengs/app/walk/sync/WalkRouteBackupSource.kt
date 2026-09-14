package com.daengs.app.walk.sync

import androidx.room.withTransaction
import com.daengs.app.walk.WalkSyncState
import com.daengs.app.walk.motion.MotionPolicies
import com.daengs.app.walk.motion.MotionPolicySelection
import com.daengs.app.walk.store.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** GPS route receipts only; says nothing about entry, photo or diary delivery. */
enum class WalkRouteBackupState { PENDING, CHECKING, NEEDS_RETRY, COMPLETE }

class WalkRouteBackupSource(
    private val database: WalkDatabase,
    private val owner: String,
    private val isCurrentAccount: () -> Boolean,
    private val enqueue: suspend (String) -> Unit,
) {
    fun observe(id: String) = database.invalidationTracker.createFlow(
        "walk_session", "walk_motion_backup", "walk_motion_precision",
    ).map { read(id) }.distinctUntilChanged()

    suspend fun read(id: String): WalkRouteBackupState? = database.withTransaction {
        if (owner.isBlank() || !isCurrentAccount()) return@withTransaction null
        val dao = database.walkDao()
        val session = dao.session(id)?.takeIf { it.ownerId == owner && it.endedAtMillis != null }
            ?: return@withTransaction null
        val result = routeBackupState(session, dao.motionBackup(id), dao.motionPrecision(id))
        result.takeIf { isCurrentAccount() }
    }

    /** Queue acknowledgement is not an upload receipt. The UI must keep observing Room. */
    suspend fun request(id: String): Boolean {
        val status = read(id) ?: return false
        if (status == WalkRouteBackupState.COMPLETE || !isCurrentAccount()) return false
        enqueue(id)
        return isCurrentAccount()
    }
}

internal fun routeBackupState(s: WalkSessionRow, base: WalkMotionBackupRow?, precision: WalkMotionPrecisionRow?): WalkRouteBackupState {
    if (s.serverWalkId == null || s.syncState != WalkSyncState.DERIVED.storedValue) return WalkRouteBackupState.PENDING
    val policy = MotionPolicies.resolveJson(s.id, s.motionPolicyJson)
    if (policy is MotionPolicySelection.Unsupported) return WalkRouteBackupState.NEEDS_RETRY
    if (policy == MotionPolicySelection.Legacy || (policy as MotionPolicySelection.Supported).policy.stored.measurementVersion == null)
        return WalkRouteBackupState.COMPLETE
    if (base?.completedAtMillis == null)
        return if (base?.lastError != null) WalkRouteBackupState.NEEDS_RETRY else WalkRouteBackupState.PENDING
    if (s.coordinateOrigin == null) return WalkRouteBackupState.COMPLETE
    if (s.coordinateOrigin !in setOf("captured", "verified")) return WalkRouteBackupState.NEEDS_RETRY
    if (precision?.completedAtMillis != null && precision.verifiedAtMillis != null && precision.verificationJson != null)
        return WalkRouteBackupState.COMPLETE
    if (precision?.lastError != null) return WalkRouteBackupState.NEEDS_RETRY
    return if (precision?.completedAtMillis != null) WalkRouteBackupState.CHECKING else WalkRouteBackupState.PENDING
}
