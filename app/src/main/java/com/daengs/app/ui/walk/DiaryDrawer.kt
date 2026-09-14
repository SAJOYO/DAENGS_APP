package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.reading.DiaryReadingChrome

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.*
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import kotlin.math.roundToInt

internal enum class DiaryDrawerValue { Compact, Browsing, Expanded }

@Stable
internal class DiaryDrawerState(initialValue: DiaryDrawerValue, val compactEnabled: Boolean) {
    val drag = AnchoredDraggableState(initialValue)
    val currentValue get() = drag.settledValue
    val targetValue get() = drag.targetValue
    fun requireOffset() = drag.requireOffset()
    suspend fun expand() = drag.animateTo(DiaryDrawerValue.Expanded)
    suspend fun partialExpand() = drag.animateTo(DiaryDrawerValue.Browsing)
    suspend fun showDetails() {
        if (targetValue == DiaryDrawerValue.Compact) partialExpand()
    }
    suspend fun lower() = drag.animateTo(when (targetValue) {
        DiaryDrawerValue.Expanded -> DiaryDrawerValue.Browsing
        else -> if (compactEnabled) DiaryDrawerValue.Compact else DiaryDrawerValue.Browsing
    })
    suspend fun raise() = drag.animateTo(when (targetValue) {
        DiaryDrawerValue.Compact -> DiaryDrawerValue.Browsing
        else -> DiaryDrawerValue.Expanded
    })
}

@Composable
internal fun rememberDiaryDrawerState(initialValue: DiaryDrawerValue, compactEnabled: Boolean): DiaryDrawerState =
    rememberSaveable(compactEnabled, saver = Saver(
        save = { it.currentValue.name },
        restore = { DiaryDrawerState(DiaryDrawerValue.valueOf(it), compactEnabled) },
    )) { DiaryDrawerState(initialValue, compactEnabled) }

/** Three real drag anchors; the map is a fixed sibling, never a resized sheet child. */
@Composable
internal fun DiaryDrawerLayout(
    state: DiaryDrawerState, height: Dp, expandedHeight: Dp, browsingHeight: Dp, compactHeight: Dp,
    sheetContent: @Composable (Dp) -> Unit, content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val anchors = remember(density, height, expandedHeight, browsingHeight, compactHeight, state.compactEnabled) {
        with(density) { DraggableAnchors {
            DiaryDrawerValue.Expanded at (height - expandedHeight).toPx()
            DiaryDrawerValue.Browsing at (height - browsingHeight).toPx()
            if (state.compactEnabled) DiaryDrawerValue.Compact at (height - compactHeight).toPx()
        } }
    }
    SideEffect { state.drag.updateAnchors(anchors, state.targetValue) }
    val fling = AnchoredDraggableDefaults.flingBehavior(state.drag)
    val nestedScroll = remember(state, fling) { DiaryDrawerNestedScroll(state.drag, fling) }
    // Scroll against the visible height. Keep the browsing contents composed below
    // the tabs when compact so folding alone does not discard the reading position.
    // The tabless reader also needs a visible viewport; measuring it expanded makes
    // lower cards appear reachable to the list while they are clipped off screen.
    val contentHeight = with(density) {
        (height.toPx() - (state.drag.offset.takeIf { it.isFinite() } ?: anchors.positionOf(state.targetValue)))
            .toDp().coerceIn(browsingHeight, expandedHeight)
    }
    Box(Modifier.fillMaxSize()) {
        content()
        Surface(Modifier.fillMaxWidth().height(contentHeight)
            .offset { IntOffset(0, (state.drag.offset.takeIf { it.isFinite() }
                ?: anchors.positionOf(state.targetValue)).roundToInt()) }
            .then(if (state.compactEnabled) Modifier else Modifier.nestedScroll(nestedScroll))
            .anchoredDraggable(state.drag, Orientation.Vertical, flingBehavior = fling),
            shape = RoundedCornerShape(topStart = DiaryReadingChrome.Corner, topEnd = DiaryReadingChrome.Corner),
            color = CardWhite, contentColor = TextDark, shadowElevation = 4.dp,
        ) { sheetContent(contentHeight) }
    }
}

/** Raise before scrolling the text; lower only after its own scroll has reached the top. */
private class DiaryDrawerNestedScroll(
    private val state: AnchoredDraggableState<DiaryDrawerValue>, private val fling: FlingBehavior,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0 && source == NestedScrollSource.UserInput)
            Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (available.y < 0 && state.requireOffset() > state.anchors.minPosition()) {
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
        state.anchoredDrag { anchors ->
            val scroll = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    val previous = state.requireOffset()
                    val next = (previous + pixels).coerceIn(anchors.minPosition(), anchors.maxPosition())
                    dragTo(next)
                    return next - previous
                }
            }
            with(fling) { scroll.performFling(velocity) }
        }
    }
}

internal class DiaryDrawerPreviewValues : PreviewParameterProvider<DiaryDrawerValue> {
    override val values = DiaryDrawerValue.entries.asSequence()
}

@Preview(showBackground = true, widthDp = 390, heightDp = 600)
@Composable
private fun DiaryDrawerPreview(@PreviewParameter(DiaryDrawerPreviewValues::class) value: DiaryDrawerValue) {
    val state = rememberDiaryDrawerState(value, compactEnabled = true)
    DaengsTheme { DiaryDrawerLayout(state, 600.dp, 450.dp, 258.dp, 72.dp,
        sheetContent = {
            Column {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.height(48.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("장면 3"); Text("동선 탐색")
                }
                Text("벤치 옆에서 함께 쉬었던 기억", Modifier.padding(20.dp))
            }
        }, content = { Box(Modifier.fillMaxSize().background(PinkFaint)) }) }
}
