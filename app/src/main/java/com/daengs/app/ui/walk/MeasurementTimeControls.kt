package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.ui.theme.DaengsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasurementTimeControls(state: WalkRouteExplorerState, scenes: List<DiaryScene>, onScene: (DiaryScene) -> Unit) {
    val slice = state.selectedSlice
    val from = slice?.from ?: state.elapsed
    val until = slice?.until ?: (from + 60_000).coerceAtMost(state.duration)
    Column {
        Text("시간 범위 · 기록 중 경과 시간", style = MaterialTheme.typography.titleSmall)
        Text("일시정지 시간은 제외해요. 시계가 바뀐 경계와 경로 공백은 이어 그리지 않아요.", style = MaterialTheme.typography.bodySmall)
        Row {
            for (minutes in listOf(1, 3, 5)) TextButton(onClick = {
                val start = from.coerceAtMost((state.duration - 1).coerceAtLeast(0))
                state.selectTimeRange(start, (start + minutes * 60_000L).coerceAtMost(state.duration))
            }) { Text("${minutes}분") }
        }
        RangeSlider(value = from.toFloat()..until.toFloat(), onValueChange = {
            if (it.endInclusive.toLong() > it.start.toLong()) state.selectTimeRange(it.start.toLong(), it.endInclusive.toLong())
        }, valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(), modifier = Modifier.fillMaxWidth())
        Text(formatWalkDuration(from) + "–" + formatWalkDuration(until))
        if (slice != null) {
            TextButton(onClick = { state.seek(slice.from) }) { Text("범위 시작으로 이동") }
            Text("이 범위의 장면 ${scenes.size}개", style = MaterialTheme.typography.titleSmall)
            scenes.forEach { scene -> TextButton(onClick = { onScene(scene) }) { Text(scene.title) } }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MeasurementTimeControlsPreview() {
    val scope = rememberCoroutineScope()
    DaengsTheme { MeasurementTimeControls(remember { WalkRouteExplorerState(scope, 300_000) }, emptyList(), {}) }
}
