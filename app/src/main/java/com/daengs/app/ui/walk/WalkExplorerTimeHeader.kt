package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*

@Composable
internal fun WalkExplorerTimeHeader(state: WalkRouteExplorerState, onOverview: () -> Unit) {
    val measured = state.review?.timeline?.durationMillis != null && state.duration > 0
    val replay = state.mode == RouteExplorerMode.REPLAY
    val slice = state.selectedSlice
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("explorer-time-header")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (replay) {
                TextButton(onClick = { if (!state.returnToRange()) state.overview() }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (slice != null) "구간 수정" else "재생 닫기", fontSize = 13.sp)
                }
                RoutePlaybackSpeedMenu(state.playbackSpeed, state::choosePlaybackSpeed)
            } else if (measured) {
                TextButton(onClick = { state.overview(); onOverview() },
                    modifier = Modifier.weight(1f).semantics { selected = state.mode == RouteExplorerMode.OVERVIEW },
                    colors = if (state.mode == RouteExplorerMode.OVERVIEW) ButtonDefaults.textButtonColors(containerColor = PinkFaint) else ButtonDefaults.textButtonColors(),
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("전체 산책", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (slice == null) TextDark else TextMuted)
                }
                TextButton(onClick = { if (slice == null) state.selectTimeRange(0, minOf(60_000, state.duration)) },
                    modifier = Modifier.weight(1f).semantics { selected = slice != null },
                    colors = if (slice != null) ButtonDefaults.textButtonColors(containerColor = PinkFaint) else ButtonDefaults.textButtonColors(),
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("구간 고르기", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (slice != null) TextDark else TextMuted)
                }
            } else {
                TextButton(onClick = { state.overview(); onOverview() }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("전체 동선", fontSize = 13.sp) }
                RoutePlaybackSpeedMenu(state.playbackSpeed, state::choosePlaybackSpeed)
            }
            Button(onClick = state::togglePlayback, enabled = state.canPlayback,
                modifier = if (measured && !replay) Modifier.weight(1f) else Modifier,
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (state.playing) "일시정지" else "동선 재생", fontSize = 13.sp, maxLines = 1)
            }
        }
        if (replay) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.widthIn(min = 72.dp).padding(end = 8.dp)) {
                    Text(formatWalkDuration(state.elapsed), fontSize = 13.sp, color = TextDark)
                    Text("/ " + formatWalkDuration(slice?.until ?: state.duration), fontSize = 11.sp, color = TextMuted)
                }
                Slider(value = state.elapsed.toFloat(), onValueChange = { state.seek(it.toLong()) },
                    valueRange = (slice?.from ?: 0).toFloat()..(slice?.until ?: state.duration.coerceAtLeast(1)).toFloat(),
                    modifier = Modifier.weight(1f).testTag("explorer-replay-slider").semantics { contentDescription = "재생 위치" })
            }
        } else if (measured && slice != null) MeasurementTimeControls(state)
        else if (measured) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("기록 중 " + formatWalkDuration(state.duration), Modifier.weight(1f), fontSize = 12.sp, color = TextMuted)
            for (minutes in listOf(1, 3, 5)) TextButton(onClick = { state.selectTimeRange(0, minOf(minutes * 60_000L, state.duration)) },
                contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.width(48.dp)) { Text("${minutes}분", fontSize = 12.sp) }
        }
    }
}
