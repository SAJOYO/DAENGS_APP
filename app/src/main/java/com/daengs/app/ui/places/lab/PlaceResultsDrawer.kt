package com.daengs.app.ui.places.lab

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

internal enum class PlaceResultsPosition { Collapsed, Preview, Expanded }

/** Expand before scrolling up; let the list consume downward motion until it reaches its top. */
internal fun placeResultsNestedScroll(
    state: AnchoredDraggableState<PlaceResultsPosition>,
    fling: FlingBehavior,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput && available.y < 0f)
            Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput)
            Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (available.y < 0f && state.requireOffset() > state.anchors.minPosition()) {
            settle(available.y)
            return available
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle(available.y)
        return available
    }

    private suspend fun settle(velocity: Float) {
        state.anchoredDrag {
            val scroll = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    val before = state.requireOffset()
                    val after = (before + pixels).coerceIn(state.anchors.minPosition(), state.anchors.maxPosition())
                    dragTo(after)
                    return after - before
                }
            }
            with(fling) { scroll.performFling(velocity) }
        }
    }
}
