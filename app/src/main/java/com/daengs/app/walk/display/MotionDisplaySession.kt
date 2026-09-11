package com.daengs.app.walk.display

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owned by a session runtime, never by a Composable. Calls must be serialized by that owner.
 * No GPS subscription, timer, recording command or persistence is started here.
 */
internal class MotionDisplaySession(
    sessionId: String,
    epoch: DisplayEpoch,
    nowNanos: Long,
    private val reducer: MotionDisplayReducer = MotionDisplayReducer(),
) {
    private var state = reducer.initial(sessionId, epoch, nowNanos)
    private val mutableDisplay = MutableStateFlow(state.display)
    val display = mutableDisplay.asStateFlow()

    fun onSamples(samples: List<DisplaySpeedSample>, nowNanos: Long) = publish(reducer.samples(state, samples, nowNanos))
    fun onTick(nowNanos: Long) = publish(reducer.tick(state, nowNanos))
    fun onLifecycle(lifecycle: DisplayLifecycle, nowNanos: Long) = publish(reducer.lifecycle(state, lifecycle, nowNanos))
    fun onSourceChanged(epoch: DisplayEpoch, nowNanos: Long) = publish(reducer.sourceChanged(state, epoch, nowNanos))

    private fun publish(next: MotionDisplayState) {
        state = next
        mutableDisplay.value = next.display
    }
}
