package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.ui.theme.DaengsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasurementTimeControls(state: WalkRouteExplorerState) {
    val slice = state.selectedSlice
    val from = slice?.from ?: state.elapsed
    val until = slice?.until ?: (from + 60_000).coerceAtMost(state.duration)
    var presets by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.widthIn(min = 62.dp).padding(end = 8.dp)) {
            Text(formatWalkDuration(from), fontSize = 13.sp)
            Text("– " + formatWalkDuration(until), fontSize = 11.sp)
        }
        RangeSlider(value = from.toFloat()..until.toFloat(), onValueChange = {
            if (it.endInclusive.toLong() > it.start.toLong()) state.selectTimeRange(it.start.toLong(), it.endInclusive.toLong())
        }, valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(),
            modifier = Modifier.weight(1f).testTag("explorer-range-slider").semantics { contentDescription = "탐색 시간 범위" })
        Box {
            TextButton(onClick = { presets = true }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("길이 ▾", fontSize = 12.sp) }
            DropdownMenu(expanded = presets, onDismissRequest = { presets = false }) {
                for (minutes in listOf(1, 3, 5)) DropdownMenuItem(text = { Text("${minutes}분") }, onClick = {
                    presets = false
                    val start = from.coerceAtMost((state.duration - 1).coerceAtLeast(0))
                    state.selectTimeRange(start, (start + minutes * 60_000L).coerceAtMost(state.duration))
                })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MeasurementTimeControlsPreview() {
    val scope = rememberCoroutineScope()
    DaengsTheme { MeasurementTimeControls(remember { WalkRouteExplorerState(scope, 300_000) }) }
}
