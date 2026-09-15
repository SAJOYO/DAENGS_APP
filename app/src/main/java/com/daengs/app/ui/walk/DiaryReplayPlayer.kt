package com.daengs.app.ui.walk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*
import com.daengs.app.ui.walk.reading.DiaryReadingChrome
import java.util.Locale

/** Compact transport shared by the ready and playing states; records get the remaining drawer. */
@Composable
internal fun DiaryReplayPlayer(state: WalkRouteExplorerState, timeline: DiaryReplayTimeline?) {
    val replay = state.mode == RouteExplorerMode.REPLAY
    val frame = state.replayFrame
    val position = if (replay) state.elapsed else state.selectedSlice?.from ?: 0L
    val wall = if (replay) frame?.recordedAtMillis else state.review?.summary?.startedAtMillis
    val speed = frame?.derivedSpeedMetersPerSecond?.takeIf { it.isFinite() && it >= 0 && !frame.inGap }
    Column(Modifier.fillMaxWidth().padding(horizontal = DiaryReadingChrome.Gutter)
        .padding(top = 8.dp, bottom = 8.dp).testTag("explorer-time-header")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledIconButton(onClick = state::togglePlayback, enabled = state.canPlayback,
                modifier = Modifier.size(48.dp).testTag("replay-play")
                    .semantics { contentDescription = if (state.playing) "일시정지" else "산책 재생" },
                shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = DaengPink)) {
                val color = LocalContentColor.current
                Canvas(Modifier.size(22.dp)) {
                    if (state.playing) {
                        drawLine(color, Offset(size.width * .3f, 2f), Offset(size.width * .3f, size.height - 2f), 4.dp.toPx(), StrokeCap.Round)
                        drawLine(color, Offset(size.width * .7f, 2f), Offset(size.width * .7f, size.height - 2f), 4.dp.toPx(), StrokeCap.Round)
                    } else drawPath(Path().apply {
                        moveTo(size.width * .22f, 0f); lineTo(size.width, size.height / 2)
                        lineTo(size.width * .22f, size.height); close()
                    }, color)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(wall?.let(::formatRouteExplorerClock) ?: formatWalkDuration(position),
                    fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = TextDark,
                    maxLines = 1, modifier = Modifier.testTag("replay-clock"))
                Text(when {
                    replay && frame?.inGap != false -> "위치 기록 없음"
                    position >= state.duration && state.duration > 0 -> "도착"
                    position == 0L -> "출발"
                    state.playing -> "재생 중"
                    replay -> "일시정지"
                    else -> "산책 다시 보기"
                }, fontSize = 10.sp, lineHeight = 14.sp, color = TextMuted)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.testTag("replay-current-speed")) {
                Text(speed?.let { String.format(Locale.US, "%.1f", it * 3.6) } ?: "–",
                    fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Text("km/h", fontSize = 9.sp, lineHeight = 12.sp, color = TextMuted)
            }
            Box(Modifier.width(48.dp).background(PinkFaint, RoundedCornerShape(12.dp))) {
                RoutePlaybackSpeedMenu(state.playbackSpeed, state::choosePlaybackSpeed, compact = true)
            }
        }
        if (state.selectedSlice != null && !replay) MeasurementTimeControls(state)
        else DiaryReplayTrack(state, timeline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryReplayTrack(state: WalkRouteExplorerState, timeline: DiaryReplayTimeline?) {
    val from = state.selectedSlice?.from ?: 0L
    val until = state.selectedSlice?.until ?: state.duration.coerceAtLeast(1)
    val position = state.elapsed.coerceIn(from, until)
    val interaction = remember { MutableInteractionSource() }
    val trackColor = TextDark
    val markerColor = TextMuted
    Box(Modifier.fillMaxWidth()) {
        Slider(value = position.toFloat(), onValueChange = { state.seek(it.toLong()) }, enabled = state.canPlayback,
            valueRange = from.toFloat()..until.toFloat(), interactionSource = interaction,
            colors = SliderDefaults.colors(activeTrackColor = trackColor, inactiveTrackColor = PinkFaint, thumbColor = DaengPink),
            thumb = { SliderDefaults.Thumb(interaction, thumbSize = DpSize(14.dp, 14.dp)) },
            track = { SliderDefaults.Track(it, Modifier.height(4.dp), thumbTrackGapSize = 0.dp, drawStopIndicator = null,
                colors = SliderDefaults.colors(activeTrackColor = trackColor, inactiveTrackColor = PinkFaint)) },
            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("explorer-replay-slider")
                .semantics { contentDescription = "재생 위치" })
        // Dense checkpoints share pixels; navigation remains in the accessible previous/next controls.
        Canvas(Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter).padding(horizontal = 7.dp)) {
            val occupied = hashSetOf<Int>()
            val stops = timeline?.navigationStops(state.duration, from, until) ?: listOf(from, until)
            stops.forEach { elapsed ->
                val x = size.width * ((elapsed - from).toDouble() / (until - from)).toFloat()
                if (occupied.add((x / 4.dp.toPx()).toInt())) drawCircle(markerColor, 1.5.dp.toPx(), Offset(x, size.height / 2))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatWalkDuration(position), fontSize = 10.sp, lineHeight = 14.sp, color = TextMuted)
        Text(formatWalkDuration(until), fontSize = 10.sp, lineHeight = 14.sp, color = TextMuted)
    }
}

@Composable
internal fun ReplayRecordNavigation(previous: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)
        .testTag(if (previous) "replay-previous-event" else "replay-next-event")
        .semantics { contentDescription = if (previous) "이전 기록" else "다음 기록" }) {
        val color = LocalContentColor.current
        Canvas(Modifier.size(16.dp)) {
            val start = if (previous) .65f else .35f
            val end = 1 - start
            drawLine(color, Offset(size.width * start, size.height * .2f), Offset(size.width * end, size.height * .5f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(size.width * end, size.height * .5f), Offset(size.width * start, size.height * .8f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun DiaryReplayPlayerPreview() {
    val scope = rememberCoroutineScope()
    val read = remember { explorerPanelPreviewRead() }
    val state = remember { WalkRouteExplorerState(scope, 0).apply { adopt(read); choosePanel(true); seek(15_000) } }
    DaengsTheme { Column {
        DiaryReplayPlayer(state, diaryReplayTimeline(read))
        Row { ReplayRecordNavigation(true, false, {}); ReplayRecordNavigation(false, true, {}) }
    } }
}
