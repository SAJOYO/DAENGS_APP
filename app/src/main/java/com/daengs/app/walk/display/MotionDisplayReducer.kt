package com.daengs.app.walk.display

/** Pure presentation reducer. All times are injected monotonic times in the active clock. */
internal class MotionDisplayReducer(private val config: MotionDisplayConfig = MotionDisplayConfig()) {
    fun initial(sessionId: String, epoch: DisplayEpoch, nowNanos: Long): MotionDisplayState {
        require(sessionId.isNotBlank() && nowNanos >= epoch.startedNanos)
        return MotionDisplayState(sessionId, epoch, DisplayLifecycle.ACTIVE, MotionDisplay(), nowNanos)
    }

    fun tick(state: MotionDisplayState, nowNanos: Long): MotionDisplayState {
        require(nowNanos >= state.nowNanos) { "Display time must not go backwards" }
        if (state.lifecycle != DisplayLifecycle.ACTIVE) return state.copy(nowNanos = nowNanos)
        val age = nowNanos - (state.freshObservedNanos ?: state.epoch.startedNanos)
        val stale = age >= config.staleAfterNanos
        val freshness = when {
            stale -> DisplayFreshness.STALE
            state.display.measuredNanos == null -> DisplayFreshness.INITIAL
            !state.latestUsable || state.freshObservedNanos == null || age > config.freshForNanos -> DisplayFreshness.HELD
            else -> DisplayFreshness.LIVE
        }
        val recoveryExpired = state.recoveryLastReceivedNanos?.let { nowNanos - it > config.recoveryMaxGapNanos } == true
        return state.copy(
            nowNanos = nowNanos,
            display = state.display.copy(freshness = freshness,
                signal = if (stale) DisplaySignal.DELAYED else state.display.signal),
            recoveryObservedNanos = if (stale || recoveryExpired) null else state.recoveryObservedNanos,
            recoveryReceivedNanos = if (stale || recoveryExpired) null else state.recoveryReceivedNanos,
            recoveryLastNanos = if (stale || recoveryExpired) null else state.recoveryLastNanos,
            recoveryLastReceivedNanos = if (stale || recoveryExpired) null else state.recoveryLastReceivedNanos,
        )
    }

    /** Consume ordered observations, publish only the final display after the batch. */
    fun samples(state: MotionDisplayState, samples: List<DisplaySpeedSample>, nowNanos: Long): MotionDisplayState {
        var next = tick(state, nowNanos)
        if (next.lifecycle != DisplayLifecycle.ACTIVE) return next
        for (sample in samples) {
            if (sample.sessionId != next.sessionId || sample.epoch != next.epoch) continue
            val time = sample.observedNanos
            if (time < next.epoch.startedNanos || time > nowNanos ||
                next.lastObservedNanos?.let { time <= it } == true) continue
            next = next.copy(lastObservedNanos = time)
            val speed = sample.speedMps
            if (speed == null || !speed.isFinite() || speed < 0 || nowNanos - time > config.freshForNanos) {
                next = next.copy(latestUsable = false, recoveryObservedNanos = null,
                    recoveryReceivedNanos = null, recoveryLastNanos = null, recoveryLastReceivedNanos = null)
                continue
            }
            val uninterrupted = next.recoveryLastNanos?.let { time - it <= config.recoveryMaxGapNanos } == true
            val firstObserved = if (uninterrupted) next.recoveryObservedNanos!! else time
            val firstReceived = if (uninterrupted) next.recoveryReceivedNanos!! else nowNanos
            // Both clocks must span recovery: one delayed batch cannot manufacture stability.
            val recovered = time - firstObserved >= config.recoveryForNanos &&
                nowNanos - firstReceived >= config.recoveryForNanos
            val firstReading = next.display.measuredNanos == null && next.display.signal == DisplaySignal.WAITING
            next = next.copy(
                freshObservedNanos = time, latestUsable = true,
                recoveryObservedNanos = firstObserved, recoveryReceivedNanos = firstReceived,
                recoveryLastNanos = time, recoveryLastReceivedNanos = nowNanos,
                display = MotionDisplay(if (speed == 0.0) 0.0 else speed, DisplayFreshness.LIVE,
                    if (firstReading || recovered) DisplaySignal.RECEIVING else next.display.signal,
                    sample.source, time, sample.epoch.clockId),
            )
        }
        return tick(next, nowNanos)
    }

    /** Notification from the runtime; this does not pause/finish recording or drain storage. */
    fun lifecycle(state: MotionDisplayState, lifecycle: DisplayLifecycle, nowNanos: Long): MotionDisplayState {
        require(lifecycle != DisplayLifecycle.ACTIVE) { "A resume needs a new source epoch" }
        val next = tick(state, nowNanos)
        if (state.lifecycle == DisplayLifecycle.FINISHED) return next
        return next.copy(lifecycle = lifecycle, recoveryObservedNanos = null,
            recoveryReceivedNanos = null, recoveryLastNanos = null, recoveryLastReceivedNanos = null,
            display = next.display.copy(
                freshness = if (lifecycle == DisplayLifecycle.PAUSED) DisplayFreshness.PAUSED else DisplayFreshness.FINAL,
                signal = DisplaySignal.WAITING))
    }

    /** New runtime source, including clock-domain changes. Preserve number, reset its freshness. */
    fun sourceChanged(state: MotionDisplayState, epoch: DisplayEpoch, nowNanos: Long): MotionDisplayState {
        require(state.lifecycle != DisplayLifecycle.FINISHED)
        require(epoch.generation > state.epoch.generation && epoch.sourceId != state.epoch.sourceId)
        require(nowNanos >= epoch.startedNanos)
        if (epoch.clockId == state.epoch.clockId) {
            require(nowNanos >= state.nowNanos && epoch.startedNanos >= state.nowNanos)
        }
        return MotionDisplayState(state.sessionId, epoch, DisplayLifecycle.ACTIVE,
            state.display.copy(freshness = if (state.display.measuredNanos == null) DisplayFreshness.INITIAL else DisplayFreshness.HELD,
                signal = DisplaySignal.WAITING), nowNanos)
    }
}
