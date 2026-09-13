package com.daengs.app.ui.walk

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasurementTimeControls(state: WalkRouteExplorerState) {
    val slice = state.selectedSlice
    val from = slice?.from ?: state.elapsed
    val until = slice?.until ?: (from + 60_000).coerceAtMost(state.duration)
    val startInteraction = remember { MutableInteractionSource() }
    val endInteraction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth()) {
        ExplorerTimeLabel(from, until, selection = true)
        RangeSlider(value = from.toFloat()..until.toFloat(), onValueChange = {
            if (it.endInclusive.toLong() > it.start.toLong()) state.selectTimeRange(it.start.toLong(), it.endInclusive.toLong())
        }, valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(),
            startInteractionSource = startInteraction, endInteractionSource = endInteraction,
            startThumb = { SliderDefaults.Thumb(startInteraction, thumbSize = DpSize(18.dp, 18.dp)) },
            endThumb = { SliderDefaults.Thumb(endInteraction, thumbSize = DpSize(18.dp, 18.dp)) },
            track = { SliderDefaults.Track(it, Modifier.height(4.dp), thumbTrackGapSize = 0.dp, drawStopIndicator = null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("explorer-range-slider").semantics { contentDescription = "탐색 시간 범위" })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExplorerTimeLabel(from: Long, until: Long, replay: Boolean = false, selection: Boolean = false) {
    FlowRow(Modifier.fillMaxWidth().testTag("explorer-time-label"), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("경과", fontSize = 10.sp, lineHeight = 14.sp, color = TextMuted)
            Text(formatWalkDuration(from), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
            Text((if (replay) "/ " else "– ") + formatWalkDuration(until), fontSize = 17.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold, color = TextDark)
        }
        if (selection) Text(formatWalkDuration(until - from) + " 선택", Modifier.align(Alignment.CenterVertically),
            fontSize = 10.sp, lineHeight = 14.sp, color = TextMuted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExplorerReplaySlider(state: WalkRouteExplorerState) {
    val slice = state.selectedSlice
    val interaction = remember { MutableInteractionSource() }
    Slider(value = state.elapsed.toFloat(), onValueChange = { state.seek(it.toLong()) },
        valueRange = (slice?.from ?: 0).toFloat()..(slice?.until ?: state.duration.coerceAtLeast(1)).toFloat(),
        interactionSource = interaction,
        thumb = { SliderDefaults.Thumb(interaction, thumbSize = DpSize(18.dp, 18.dp)) },
        track = { SliderDefaults.Track(it, Modifier.height(4.dp), thumbTrackGapSize = 0.dp, drawStopIndicator = null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("explorer-replay-slider").semantics { contentDescription = "재생 위치" })
}

/** Presets live below the timeline so both range handles keep the full reading width. */
@Composable
internal fun ExplorerRangePresets(state: WalkRouteExplorerState) {
    val slice = state.selectedSlice
    var presets by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (slice == null) {
            Text("빠른 구간", Modifier.weight(1f), fontSize = 12.sp, color = TextMuted)
            for (minutes in listOf(1, 3, 5)) TextButton(onClick = { state.selectTimeRange(0, minOf(minutes * 60_000L, state.duration)) },
                contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.width(48.dp)) { Text("${minutes}분", fontSize = 12.sp) }
        } else {
            TextButton(onClick = { state.seek(slice.from) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("범위 시작으로 이동", fontSize = 12.sp, color = TextMuted)
            }
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { presets = true }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("길이 ▾", fontSize = 12.sp) }
                DropdownMenu(expanded = presets, onDismissRequest = { presets = false }) {
                    for (minutes in listOf(1, 3, 5)) DropdownMenuItem(text = { Text("${minutes}분") }, onClick = {
                        presets = false
                        val start = slice.from.coerceAtMost((state.duration - 1).coerceAtLeast(0))
                        state.selectTimeRange(start, (start + minutes * 60_000L).coerceAtMost(state.duration))
                    })
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun MeasurementTimeControlsPreview() {
    val scope = rememberCoroutineScope()
    val read = remember { explorerPanelPreviewRead() }
    val state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read); selectTimeRange(0, 30_000) } }
    DaengsTheme { Column { MeasurementTimeControls(state); ExplorerRangePresets(state) } }
}
