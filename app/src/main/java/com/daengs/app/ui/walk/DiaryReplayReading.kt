package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.R
import com.daengs.app.ui.theme.*
import com.daengs.app.ui.walk.reading.DiarySceneText
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.diary.DiaryScene

/** One time's records, including simultaneous events. Content grows vertically inside the drawer. */
@Composable
internal fun DiaryReplayReading(timeline: DiaryReplayTimeline, state: WalkRouteExplorerState) {
    val from = state.selectedSlice?.from ?: 0L
    val until = state.selectedSlice?.until ?: state.duration
    val checkpoint = timeline.current(state.elapsed, state.duration, from, until)
    val ended = state.elapsed >= state.duration
    val previous = timeline.previous(state.elapsed, from)
    val next = timeline.next(state.elapsed, until)
    Column(Modifier.fillMaxWidth().testTag("diary-replay-reading")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text(when {
                ended -> "산책 끝"
                state.elapsed == 0L -> "산책 시작"
                checkpoint == null -> "이동 중"
                else -> "이때의 기록"
            }, Modifier.weight(1f), style=MaterialTheme.typography.titleSmall, color=TextDark)
            TextButton(onClick={ state.seek(requireNotNull(previous)) }, enabled=previous!=null,
                modifier=Modifier.testTag("replay-previous-event"), contentPadding=PaddingValues(horizontal=8.dp)) { Text("이전") }
            TextButton(onClick={ state.seek(requireNotNull(next)) }, enabled=next!=null,
                modifier=Modifier.testTag("replay-next-event"), contentPadding=PaddingValues(horizontal=8.dp)) { Text("다음") }
        }
        checkpoint?.readingEvents()?.forEach { event -> DiaryReplayEventReading(event) }
        if (timeline.unresolvedCount > 0) Text("시간을 연결하지 못한 기록 ${timeline.unresolvedCount}건",
            style=MaterialTheme.typography.bodySmall, color=TextMuted)
    }
}

@Composable
private fun DiaryReplayEventReading(event: DiaryReplayEvent) {
    val action = event.action
    val scene = event.scene
    Column(Modifier.fillMaxWidth().testTag("replay-event:${event.id}").padding(bottom=12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(shape=RoundedCornerShape(10.dp), color=PinkFaint) {
                Box(Modifier.size(36.dp), contentAlignment=Alignment.Center) {
                    if (scene != null) Text("${event.ordinal}", color=TextDark, style=MaterialTheme.typography.titleSmall)
                    else if (action != null) Icon(painterResource(when(action.type) {
                        WalkMomentType.SNIFFING -> R.drawable.ic_walk_sniffing
                        WalkMomentType.EXCRETION -> R.drawable.ic_walk_excretion
                        WalkMomentType.BARKING -> R.drawable.ic_walk_barking
                        WalkMomentType.NOTE -> R.drawable.ic_walk_note
                    }), null, Modifier.size(28.dp), tint=Color.Unspecified)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(if (scene != null && action != null) "장면 ${event.ordinal} · ${scene.title}"
                    else scene?.title ?: action?.type?.label.orEmpty(), color=TextDark, style=MaterialTheme.typography.titleSmall)
                Text(formatWalkClock(scene?.atMillis ?: requireNotNull(action).recordedAtMillis), color=TextMuted,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        if (scene != null) Box(Modifier.padding(top=8.dp)) { DiarySceneText(scene.body) }
    }
}

@Composable
internal fun DiaryReplayInspectionReading(inspection: DiaryReplayInspection) {
    Column(Modifier.fillMaxWidth().testTag("diary-replay-inspection")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text("선택한 기록", Modifier.weight(1f), style=MaterialTheme.typography.titleSmall, color=TextDark)
            TextButton(onClick=inspection::clear, modifier=Modifier.testTag("return-to-replay")) { Text("재생으로 돌아가기") }
        }
        if (inspection.markerIds.isNotEmpty()) {
            DiaryReplayCheckpoint(0,inspection.events()).readingEvents().forEach { DiaryReplayEventReading(it) }
        } else {
            if (inspection.explorer.analyzing) Text("통과 기록을 확인하고 있어요.", color=TextMuted)
            inspection.explorer.error?.let { Text(it,color=TextMuted) }
            WalkExplorerSelectionDetails(inspection.explorer)
        }
    }
}

@Preview(showBackground=true, widthDp=320, fontScale=1.3f)
@Composable
private fun DiaryReplayReadingPreview() { DaengsTheme {
    Column(Modifier.padding(16.dp)) {
        DiaryReplayEventReading(DiaryReplayEvent("action", 1_000, action=WalkEntry("a","s",WalkMomentType.SNIFFING,1_000)))
        DiaryReplayEventReading(DiaryReplayEvent("scene", 2_000,
            scene=DiaryScene("scene","s",2_000,"나무 그늘에서","두부와 잠깐 쉬어 갔다.",null,""), ordinal=2))
    }
} }

@Preview(showBackground=true,widthDp=320)
@Composable
private fun DiaryReplayInspectionPreview() {
    val scope=rememberCoroutineScope()
    val read=remember { explorerPanelPreviewRead() }
    val inspection=remember { DiaryReplayInspection(scope).apply { adopt(read); selectMarkers(setOf(read.diary!!.scenes[1].id)) } }
    DaengsTheme { DiaryReplayInspectionReading(inspection) }
}
