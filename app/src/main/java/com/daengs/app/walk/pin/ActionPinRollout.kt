package com.daengs.app.walk.pin

import com.daengs.app.location.LocationSample
import com.daengs.app.walk.RecordedWalkAction
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.isAccurateEnoughForMoment
import com.daengs.app.walk.isFreshEnoughForMoment

/** Release decision only. GPS preservation and existing v2 outboxes remain available. */
object ActionPinRollout {
    val legacyCreation: Boolean = true
}

/** A v1 action requires a fresh observed position; it cannot encode an estimated/missing pin. */
internal fun legacyWalkAction(
    sample: LocationSample?, id: String, sessionId: String, type: WalkMomentType,
    recordedAtMillis: Long, nowElapsedNanos: Long,
): RecordedWalkAction? {
    if (type == WalkMomentType.NOTE || sample == null || sample.isMock ||
        sample.accuracyMeters?.let { it.isFinite() && it > 0 } != true ||
        !sample.isAccurateEnoughForMoment() || !sample.isFreshEnoughForMoment(nowElapsedNanos) ||
        sample.capturedAtMillis > recordedAtMillis) return null
    return RecordedWalkAction(id, sessionId, type, recordedAtMillis,
        sample.capturedAtMillis, sample.point, sample.accuracyMeters)
}
