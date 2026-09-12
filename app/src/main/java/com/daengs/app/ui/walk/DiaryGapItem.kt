package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.trajectory.*
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextMuted

/** Insert display-only gaps without renumbering, editing or synthesizing diary scenes. */
internal fun diaryGapSlots(scenes: List<DiaryScene>, contexts: List<RecordContext>): Map<Int, List<RecordContext>> =
    contexts.filter { it.kind == RecordContextKind.GAP }.distinctBy { it.id }
        .sortedBy { it.fromMillis }.groupBy { gap ->
            if (gap.durationMillis == null) scenes.size
            else scenes.indexOfFirst { it.atMillis > gap.fromMillis }.takeIf { it >= 0 } ?: scenes.size
        }

internal fun diaryGapTitle(gap: RecordContext): String {
    if (gap.durationMillis == null) return "시간 미확인 · 경로 공백"
    val from = formatWalkClock(gap.fromMillis); val to = formatWalkClock(gap.toMillis)
    // Short gaps still need distinct endpoints; only their single title uses seconds.
    return if (from == to) "${formatRouteExplorerClock(gap.fromMillis)}–${formatRouteExplorerClock(gap.toMillis)} · 경로 공백"
        else "$from–$to · 경로 공백"
}

@Composable
internal fun DiaryGapItem(gap: RecordContext, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
        Surface(shape = CircleShape, color = PinkFaint, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("–", color = TextMuted) }
        }
        Text(diaryGapTitle(gap), Modifier.weight(1f).padding(start = 12.dp),
            color = TextMuted, fontSize = 18.sp, lineHeight = 24.sp)
    }
}

@Composable
internal fun DiaryGapDetail(gap: RecordContext, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        TextButton(onClick = onClose) { Text("‹ 장면 목록") }
        Text(diaryGapTitle(gap), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("이 사이의 이동 경로는 확인할 수 없어요.", color = TextMuted)
        if (gap.before != null && gap.after != null && gap.before.point != gap.after.point)
            Text("점선은 앞뒤 위치를 이어 보여줘요.", color = TextMuted)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DiaryGapPreview() { DaengsTheme { Column {
    val gap = RecordContext("preview", RecordContextKind.GAP, 1, 2, 0, 60_000, 60_000,
        null, null, RecordMovement.WALKING, RecordMovement.EXCLUDED)
    DiaryGapItem(gap, {})
    DiaryGapDetail(gap, {})
} } }
