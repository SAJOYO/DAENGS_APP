package com.daengs.app.walk.display

import com.daengs.app.walk.motion.*

/** Position/path acceptance is independent of speed evidence. High speed is still a reading. */
internal fun MotionEstimate.displaySample(sessionId: String, epoch: DisplayEpoch): DisplaySpeedSample? {
    val identity = ref ?: return null
    val time = observedElapsedNanos ?: return null
    if (identity.sessionId != sessionId || identity.sourceEpoch != epoch.sourceId ||
        identity.clockEpochId != epoch.clockId || identity.chainIndex.toLong() != epoch.generation) return null
    val source = when (speedSource) {
        SpeedSource.DEVICE -> DisplaySpeedSource.DEVICE
        SpeedSource.COORDINATE_WINDOW -> DisplaySpeedSource.COORDINATE_WINDOW
        SpeedSource.UNKNOWN -> null
    }
    val qualified = source != null && speedQuality in setOf(SpeedQuality.TRUSTED, SpeedQuality.ESTIMATED) &&
        positionQuality != PositionQuality.MOCK && reasons.none { it in rejectedSpeedReasons }
    return DisplaySpeedSample(sessionId, epoch, time,
        speedMps?.takeIf { qualified && it.isFinite() && it >= 0 }, source ?: DisplaySpeedSource.DEVICE)
}

// SPEED_MISSING/INVALID/UNCERTAIN may describe device speed while coordinates supply a valid fallback.
private val rejectedSpeedReasons = setOf(MotionReason.MOCK, MotionReason.SPEED_CONFLICT,
    MotionReason.OUT_OF_ORDER, MotionReason.DUPLICATE, MotionReason.SAME_TIME_CONFLICT,
    MotionReason.INVALID_TIME, MotionReason.MISSING_METADATA, MotionReason.OUTSIDE_ACTIVE_INTERVAL,
    MotionReason.SOURCE_CHANGED, MotionReason.CLOCK_DOMAIN_CHANGED, MotionReason.CHAIN_CHANGED)
