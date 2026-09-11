package com.daengs.app.walk.display

/** Presentation timings only; never used by distance, recording or authentication policies. */
internal data class MotionDisplayConfig(
    val freshForNanos: Long = 3_000_000_000L,
    val staleAfterNanos: Long = 10_000_000_000L,
    val recoveryForNanos: Long = 5_000_000_000L,
    val recoveryMaxGapNanos: Long = 2_000_000_000L,
) {
    init {
        require(freshForNanos > 0 && staleAfterNanos > freshForNanos)
        require(recoveryForNanos > 0 && recoveryMaxGapNanos in 1..freshForNanos)
    }
}

/** Runtime-issued generation; a resumed/restarted source must use a greater generation. */
internal data class DisplayEpoch(
    val generation: Long,
    val sourceId: String,
    val clockId: String,
    val startedNanos: Long,
) {
    init {
        require(generation >= 0 && startedNanos >= 0)
        require(sourceId.isNotBlank() && clockId.isNotBlank())
    }
}

internal enum class DisplaySpeedSource { DEVICE, COORDINATE_WINDOW }
internal enum class DisplayFreshness { INITIAL, LIVE, HELD, STALE, PAUSED, FINAL }
internal enum class DisplaySignal { WAITING, RECEIVING, DELAYED }
internal enum class DisplayLifecycle { ACTIVE, PAUSED, FINISHED }

/** Already-qualified engine speed. Null means unavailable, never a measured stop. */
internal data class DisplaySpeedSample(
    val sessionId: String,
    val epoch: DisplayEpoch,
    val observedNanos: Long,
    val speedMps: Double?,
    val source: DisplaySpeedSource = DisplaySpeedSource.DEVICE,
)

/** Read-only rendering value. It contains no elapsed-age ticker or recording commands. */
internal data class MotionDisplay(
    val speedMps: Double = 0.0,
    val freshness: DisplayFreshness = DisplayFreshness.INITIAL,
    val signal: DisplaySignal = DisplaySignal.WAITING,
    val source: DisplaySpeedSource? = null,
    val measuredNanos: Long? = null,
    val measuredClockId: String? = null,
) {
    init { require(speedMps.isFinite() && speedMps >= 0.0) }
}

/** Constant-size reducer memory; old display readings can outlive the active source/clock. */
internal data class MotionDisplayState(
    val sessionId: String,
    val epoch: DisplayEpoch,
    val lifecycle: DisplayLifecycle,
    val display: MotionDisplay,
    val nowNanos: Long,
    val lastObservedNanos: Long? = null,
    val freshObservedNanos: Long? = null,
    val latestUsable: Boolean = false,
    val recoveryObservedNanos: Long? = null,
    val recoveryReceivedNanos: Long? = null,
    val recoveryLastNanos: Long? = null,
    val recoveryLastReceivedNanos: Long? = null,
)
