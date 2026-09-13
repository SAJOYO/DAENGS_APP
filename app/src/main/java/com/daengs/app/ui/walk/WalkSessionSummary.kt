package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp)) {
        if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "), Modifier.padding(bottom = 6.dp),
            color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        Surface(shape = RoundedCornerShape(14.dp), color = CardWhite,
            border = BorderStroke(1.dp, DaengsColors.BorderNeutral), modifier = Modifier.fillMaxWidth()
            .semantics {
                contentDescription = (metadata + listOf("걸은 시간 $duration", "이동 거리 $distance", "평균 속도 $speed"))
                    .joinToString(", ")
            }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("걸은 시간" to duration, "이동 거리" to distance, "평균 속도" to speed).forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(value, fontSize = 15.sp, lineHeight = 20.sp, color = TextDark, fontWeight = FontWeight.SemiBold)
                        Text(label, fontSize = 11.sp, lineHeight = 15.sp, color = TextMuted)
                    }
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
