package com.daengs.app.ui.walk.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.formatWalkDistance

/** One compact summary of the complete filtered selection, independent of the list page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WalkRecordsTotals(count: Int?, distanceMeters: Double, activeDurationMillis: Long, failed: Boolean) {
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(count?.let { "산책 ${it}회" } ?: if (failed) "산책 기록" else "불러오는 중",
            Modifier.testTag("records-count").alignByBaseline(),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (count != null) {
            Text("· 합계 ${formatWalkDistance(distanceMeters)}", Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("· ${formatRecordsTotalDuration(activeDurationMillis)}", Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatRecordsTotalDuration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000
    val hours = minutes / 60
    return when {
        millis > 0 && minutes == 0L -> "1분 미만"
        hours == 0L -> "${minutes}분"
        minutes % 60 == 0L -> "${hours}시간"
        else -> "${hours}시간 ${minutes % 60}분"
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun WalkRecordsTotalsPreview() {
    DaengsTheme { WalkRecordsTotals(10, 9_600.0, 41_376_000, false) }
}
