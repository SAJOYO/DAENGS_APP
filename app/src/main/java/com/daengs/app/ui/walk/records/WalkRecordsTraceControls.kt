package com.daengs.app.ui.walk.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.map.layers.traces.TraceOverlapPalette
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint

/** Display choices only: the host owns the selected walks, counts and map status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WalkRecordsTraceControls(
    overlapOnly: Boolean,
    minimumWalks: Int,
    onOverlapOnly: (Boolean) -> Unit,
    onMinimumWalks: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = PinkFaint,
        selectedLabelColor = DaengPinkDeep,
    )
    Column(modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !overlapOnly, onClick = { onOverlapOnly(false) },
                    label = { Text("전체 흔적") }, colors = colors,
                    modifier = Modifier.testTag("records-traces-all"))
                FilterChip(selected = overlapOnly, onClick = { onOverlapOnly(true) },
                    label = { Text("겹친 구간") }, colors = colors,
                    modifier = Modifier.testTag("records-traces-overlap"))
            }
        }
        if (overlapOnly) WalkRecordsOverlapOptions(minimumWalks, onMinimumWalks)
    }
}

@Composable
internal fun WalkRecordsOverlapOptions(minimumWalks: Int, onMinimumWalks: (Int) -> Unit) {
    Column {
        Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("최소", style = MaterialTheme.typography.labelMedium)
            listOf(2, 3, 5).forEach { minimum ->
                FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PinkFaint, selectedLabelColor = DaengPinkDeep), selected = minimumWalks == minimum, onClick = { onMinimumWalks(minimum) },
                    label = { Text("${minimum}회") },
                    modifier = Modifier.testTag("records-overlap-min-$minimum"))
            }
        }
        OverlapColorLegend(Modifier.padding(bottom = 4.dp))
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun WalkRecordsOverlapOptionsPreview() {
    DaengsTheme { WalkRecordsOverlapOptions(2, {}) }
}

/** Fixed original walk-count buckets, independent of the currently selected minimum. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlapColorLegend(modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth().testTag("records-overlap-legend"),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("겹친 산책", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(
            Triple("2", "2회", TraceOverlapPalette.TEAL_RGB),
            Triple("3-4", "3–4회", TraceOverlapPalette.YELLOW_RGB),
            Triple("5", "5회 이상", TraceOverlapPalette.ORANGE_RGB),
        ).forEach { (bucket, label, rgb) ->
            val colorName = when (bucket) { "2" -> "청록"; "3-4" -> "노랑"; else -> "주황" }
            Row(Modifier.testTag("records-overlap-legend-$bucket").semantics(mergeDescendants = true) {
                contentDescription = "$label $colorName"
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val shape = RoundedCornerShape(2.dp)
                Box(Modifier.size(12.dp).background(Color(0xFF000000.toInt() or rgb), shape)
                    .border(.5.dp, MaterialTheme.colorScheme.outlineVariant, shape))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Preview(name = "전체 흔적", showBackground = true, widthDp = 390)
@Composable
private fun AllWalkRecordsTraceControlsPreview() {
    DaengsTheme { WalkRecordsTraceControls(false, 2, {}, {}, Modifier.padding(horizontal = 18.dp)) }
}

@Preview(name = "겹친 구간", showBackground = true, widthDp = 390)
@Composable
private fun OverlapWalkRecordsTraceControlsPreview() {
    DaengsTheme { WalkRecordsTraceControls(true, 3, {}, {}, Modifier.padding(horizontal = 18.dp)) }
}

@Preview(name = "넓은 화면", showBackground = true, widthDp = 700)
@Composable
private fun WideWalkRecordsTraceControlsPreview() {
    DaengsTheme { WalkRecordsTraceControls(true, 5, {}, {}, Modifier.padding(horizontal = 18.dp)) }
}

@Preview(name = "겹친 산책 색 범례", showBackground = true, widthDp = 390)
@Composable
private fun OverlapColorLegendPreview() {
    DaengsTheme { OverlapColorLegend(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) }
}
