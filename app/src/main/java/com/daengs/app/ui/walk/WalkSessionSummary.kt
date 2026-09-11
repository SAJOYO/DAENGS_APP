package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkSummary

@Composable
internal fun WalkSessionSummary(summary: WalkSummary, dogNames: List<String> = emptyList()) {
    val duration = formatWalkDuration(summary.activeDurationMillis)
    val distance = formatWalkDistance(summary.distanceMeters)
    val speed = formatAverageSpeed(summary)
    val metadata = dogNames + listOfNotNull(summary.weather?.let(::weatherLabel))
    Text((metadata + listOf(duration, distance, "평균 $speed")).joinToString(" · "),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp)
            .semantics {
                contentDescription = (metadata + listOf("걸은 시간 $duration", "이동 거리 $distance", "평균 속도 $speed"))
                    .joinToString(", ")
            },
        color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal,
        style = MaterialTheme.typography.bodySmall)
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SessionSummaryPreview() {
    DaengsTheme { WalkSessionSummary(WalkSummary("preview", emptyList(), 0, 1_800_000,
        null, 1_200.0, 1_800_000, emptyList(), null), listOf("두부")) }
}
