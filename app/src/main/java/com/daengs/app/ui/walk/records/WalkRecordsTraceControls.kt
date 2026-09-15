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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.tracePigmentColor
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
    menuExtras: @Composable () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("records-map-display")) {
            Text(if (overlapOnly) "겹친 구간 · ${minimumWalks}회 이상 ▾" else "전체 흔적 ▾", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)) {
            DropdownMenuItem(text = { Text("전체 흔적") }, onClick = { onOverlapOnly(false); open = false },
                modifier = Modifier.testTag("records-traces-all"))
            DropdownMenuItem(text = { Text("겹친 구간") }, onClick = { onOverlapOnly(true) },
                modifier = Modifier.testTag("records-traces-overlap"))
            if (overlapOnly) Box(Modifier.padding(horizontal = 16.dp)) {
                WalkRecordsOverlapOptions(minimumWalks, { onMinimumWalks(it); open = false })
            }
            menuExtras()
        }
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
        ShadowLegend(Modifier.padding(bottom = 4.dp))
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun WalkRecordsOverlapOptionsPreview() {
    DaengsTheme { WalkRecordsOverlapOptions(2, {}) }
}

/** Fixed visible-session strength scale; changing the minimum never rescales it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShadowLegend(modifier: Modifier = Modifier) {
    val policy = LocalRecordsTracePolicy.current
    FlowRow(modifier.fillMaxWidth().testTag("records-overlap-legend"),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("산책이 쌓일수록 짙게", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        policy.density.legend().forEach { item ->
            val bucket = item.key
            val label = item.label
            val opacity = item.opacity
            Row(Modifier.testTag("records-overlap-legend-$bucket").semantics(mergeDescendants = true) {
                contentDescription = "$label 그림자 농도 ${(opacity * 100).toInt()}퍼센트"
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val shape = RoundedCornerShape(2.dp)
                Box(Modifier.size(12.dp).background(tracePigmentColor(policy.rgb).copy(alpha = opacity), shape)
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

@Preview(name = "산책 그림자 농도 범례", showBackground = true, widthDp = 390)
@Composable
private fun ShadowLegendPreview() {
    DaengsTheme { ShadowLegend(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) }
}
