package com.daengs.app.ui.walk.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !overlapOnly, onClick = { onOverlapOnly(false) },
                label = { Text("전체 흔적") }, colors = colors,
                modifier = Modifier.testTag("records-traces-all"))
            FilterChip(selected = overlapOnly, onClick = { onOverlapOnly(true) },
                label = { Text("겹친 구간") }, colors = colors,
                modifier = Modifier.testTag("records-traces-overlap"))
        }
        if (overlapOnly) {
            Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("최소", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(2, 3, 5).forEach { minimum ->
                    FilterChip(selected = minimumWalks == minimum, onClick = { onMinimumWalks(minimum) },
                        label = { Text("${minimum}회") }, colors = colors,
                        modifier = Modifier.testTag("records-overlap-min-$minimum"))
                }
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
