package com.daengs.app.walk.pin

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.store.WalkDao
import com.daengs.app.walk.store.WalkEntryRow
import java.util.UUID

/** Raw fixes have already passed the ordered writer before this store is called. */
class ActionPinStore(private val dao: WalkDao, private val owner: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val activeSessionId: () -> String? = { null }) {
    private val estimator = ActionPinEstimator()

    suspend fun create(id: String, request: ActionPinRequest, type: WalkMomentType,
        direct: ActionPinSourceRef?): ActionPin? {
        require(type != WalkMomentType.NOTE)
        if (owner() != request.ownerId || dao.session(request.sessionId)?.ownerId != request.ownerId) return null
        val observations = observations(request.sessionId, request.ownerId.orEmpty())
        val result = estimator.begin(request, observations, direct, maxOf(now(), request.targetAtMillis))
        val original = observations.singleOrNull { result.method == ActionPinMethod.OBSERVED && it.ref == direct }
        val entry = WalkEntry(id = id, sessionId = request.sessionId, type = type,
            recordedAtMillis = request.targetAtMillis, point = original?.point,
            locationCapturedAtMillis = original?.atMillis, accuracyMeters = original?.accuracyMeters,
            petId = dao.sessionDogs(request.sessionId).singleOrNull()?.dogId)
        val pin = result.toPin()
        return if (dao.createPinEntry(WalkEntryRow(id, request.sessionId, entry.validate().toJson().toString(),
                0, UUID.randomUUID().toString(), true, pinPayload = pin.payload, isV2 = true,
                pinDirty = true, pinChainIndex = request.chainIndex), request.ownerId.orEmpty())) pin else null
    }

    suspend fun finish(id: String, reason: ActionPinReason = ActionPinReason.DEADLINE, cutoff: Long? = null) {
        val row = dao.entry(id) ?: return
        if (row.payload == null) return
        val pin = row.pinPayload?.let(::ActionPin) ?: return
        if (pin.state != "provisional" || !pin.isLocalPolicy || row.pinChainIndex < 0) return
        val account = owner()
        val session = dao.session(row.sessionId)?.takeIf { it.ownerId == account } ?: return
        val initial = pin.resolution(account, row.sessionId, row.pinChainIndex)
        val computed = maxOf(now(), initial.computedAtMillis)
        if (reason == ActionPinReason.DEADLINE && computed < initial.resolveByMillis) return
        val terminalReason = if (session.endedAtMillis != null) ActionPinReason.SESSION_ENDED else reason
        val end = maxOf(initial.request.targetAtMillis, minOf(cutoff ?: computed, session.endedAtMillis ?: computed))
        val result = try {
            estimator.finish(initial, observations(row.sessionId, account), computed, terminalReason, end)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { estimator.finish(initial, emptyList(), computed, ActionPinReason.ESTIMATOR_FAILED, end) }
        dao.finishPinEntry(id, pin.payload, result.toPin(minOf(end, computed, initial.resolveByMillis)).payload,
            UUID.randomUUID().toString(), account)
    }

    suspend fun finishSession(session: String, cutoff: Long) {
        dao.entries(session).forEach { finish(it.id, ActionPinReason.SESSION_ENDED, cutoff) }
    }

    suspend fun recover() {
        dao.pendingPinEntries().forEach {
            // Activity/auth refresh can recover while the service still collects observations.
            // Only orphaned sessions close early; live ones retain their original deadline.
            finish(it.id, if (it.sessionId == activeSessionId()) ActionPinReason.DEADLINE else ActionPinReason.RECOVERED)
        }
    }

    private suspend fun observations(session: String, account: String) = dao.fixes(session).map {
        ActionPinObservation(account, session, it.clientSeq, it.chainIndex, it.atMillis,
            GeoPoint(it.lat, it.lng), it.accuracyM, it.isMock)
    }
}
