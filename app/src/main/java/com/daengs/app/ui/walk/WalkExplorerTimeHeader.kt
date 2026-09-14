package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.reading.DiaryReadingChrome
import com.daengs.app.ui.walk.reading.DiaryReviewTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*

/** A quiet mode switch, one primary action, then elapsed time above the full-width timeline. */
@Composable
internal fun WalkExplorerTimeHeader(state: WalkRouteExplorerState, onOverview: () -> Unit) {
    val measured = state.review?.timeline?.durationMillis != null && state.duration > 0
    val replay = state.mode == RouteExplorerMode.REPLAY
    val slice = state.selectedSlice
    Column(Modifier.fillMaxWidth().padding(horizontal = DiaryReadingChrome.Gutter).testTag("explorer-time-header")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (replay) {
                TextButton(onClick = { if (!state.returnToRange()) state.overview() }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Box(Modifier.fillMaxWidth()) {
                        Text(if (slice != null) "구간 수정" else "재생 닫기", fontSize = 12.sp, color = TextMuted)
                    }
                }
                RoutePlaybackSpeedMenu(state.playbackSpeed, state::choosePlaybackSpeed, compact = true)
            } else if (measured) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(36.dp).background(PinkFaint, RoundedCornerShape(10.dp)))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp).selectableGroup()) {
                        ExplorerMode("전체 산책", state.mode == RouteExplorerMode.OVERVIEW, Modifier.weight(1f)) {
                            state.overview(); onOverview()
                        }
                        ExplorerMode("구간 고르기", slice != null, Modifier.weight(1f)) {
                            if (slice == null) state.selectTimeRange(0, minOf(60_000, state.duration))
                        }
                    }
                }
            } else {
                TextButton(onClick = { state.overview(); onOverview() }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 0.dp)) { Text("전체 동선", fontSize = 12.sp, color = TextDark) }
                RoutePlaybackSpeedMenu(state.playbackSpeed, state::choosePlaybackSpeed, compact = true)
            }
            Button(onClick = state::togglePlayback, enabled = state.canPlayback,
                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Text(if (state.playing) "일시정지" else "동선 재생", fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
            }
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

@Composable
private fun ExplorerMode(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (selected) Box(Modifier.fillMaxWidth().height(30.dp).background(CardWhite, RoundedCornerShape(7.dp)))
        Text(label, Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1,
            color = if (selected) TextDark else TextMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun ExplorerTimeHeaderPreview() {
    val scope = rememberCoroutineScope()
    val read = remember { explorerPanelPreviewRead() }
    val state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read); selectTimeRange(0, 30_000) } }
    DiaryReviewTheme { WalkExplorerTimeHeader(state, {}) }
}
