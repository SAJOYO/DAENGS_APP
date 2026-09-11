package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkSummary

@Composable
internal fun WalkSessionSummary(summary: WalkSummary, dogNames: List<String> = emptyList()) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        if (dogNames.isNotEmpty()) Text(dogNames.joinToString(" · "), color = TextMuted,
            style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("걸은 시간" to formatWalkDuration(summary.activeDurationMillis),
                "이동 거리" to formatWalkDistance(summary.distanceMeters),
                "평균 속도" to formatAverageSpeed(summary)).forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(value, fontWeight = FontWeight.Bold, color = TextDark,
                        style = MaterialTheme.typography.titleMedium)
                    Text(label, color = TextMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SessionSummaryPreview() {
    DaengsTheme { WalkSessionSummary(WalkSummary("preview", emptyList(), 0, 1_800_000,
        null, 1_200.0, 1_800_000, emptyList(), null), listOf("두부")) }
}
