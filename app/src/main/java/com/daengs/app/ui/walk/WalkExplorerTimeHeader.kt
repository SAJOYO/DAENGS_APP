package com.daengs.app.ui.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*
import com.daengs.app.ui.walk.reading.DiaryReadingChrome
import com.daengs.app.ui.walk.reading.DiaryReviewTheme

/** Playback and the full-width timeline remain reachable while details scroll below. */
@Composable
internal fun WalkExplorerTimeHeader(state: WalkRouteExplorerState) {
    val measured = state.review?.timeline?.durationMillis != null && state.duration > 0
    val replay = state.mode == RouteExplorerMode.REPLAY
    val slice = state.selectedSlice
    Column(Modifier.fillMaxWidth().padding(horizontal = DiaryReadingChrome.Gutter).testTag("explorer-time-header")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = state::togglePlayback, enabled = state.canPlayback,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Text(if (state.playing) "일시정지" else "동선 재생", fontSize = 14.sp, lineHeight = 20.sp)
            }
            RoutePlaybackSpeedMenu(state.playbackSpeed, state::choosePlaybackSpeed, compact = true)
        }
        if (replay) {
            ExplorerTimeLabel(state.elapsed, slice?.until ?: state.duration, replay = true)
            ExplorerReplaySlider(state)
        } else if (measured && slice != null) MeasurementTimeControls(state)
        else if (measured) {
            ExplorerTimeLabel(0, state.duration)
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(DaengPink, RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** Secondary range actions use the same selection commands as before. */
@Composable
internal fun WalkExplorerRangeActions(state: WalkRouteExplorerState, onOverview: () -> Unit) {
    val measured = state.review?.timeline?.durationMillis != null && state.duration > 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { state.overview(); onOverview() }, contentPadding = PaddingValues(0.dp)) {
            Text(if (measured) "전체 산책" else "전체 동선", fontSize = 12.sp, color = TextMuted)
        }
        if (state.mode == RouteExplorerMode.REPLAY) {
            TextButton(onClick = { if (!state.returnToRange()) state.overview() }, contentPadding = PaddingValues(0.dp)) {
                Text(if (state.selectedSlice != null) "구간 수정" else "재생 닫기", fontSize = 12.sp, color = TextMuted)
            }
        } else if (measured) {
            TextButton(onClick = {
                if (state.selectedSlice == null) state.selectTimeRange(0, minOf(60_000, state.duration))
            }, contentPadding = PaddingValues(0.dp)) {
                Text("구간 고르기", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun ExplorerTimeHeaderPreview() {
    val scope = rememberCoroutineScope()
    val read = remember { explorerPanelPreviewRead() }
    val state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read); selectTimeRange(0, 30_000) } }
    DiaryReviewTheme { Column { WalkExplorerTimeHeader(state); WalkExplorerRangeActions(state, {}) } }
}
