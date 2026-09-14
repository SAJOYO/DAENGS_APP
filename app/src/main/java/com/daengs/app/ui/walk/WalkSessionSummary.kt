package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.reading.DiaryReadingChrome
import com.daengs.app.ui.walk.reading.DiaryReviewTheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.RecordedWeather
import kotlin.math.roundToInt

@Composable
internal fun WalkSessionSummary(summary: WalkSummary, dogNames: List<String> = emptyList(), compact: Boolean = false) {
    val duration = formatWalkDuration(summary.activeDurationMillis)
    val distance = formatWalkDistance(summary.distanceMeters)
    val speed = formatAverageSpeed(summary)
    val temperature = summary.weather?.temperatureC?.takeIf { it.isFinite() }?.let { "${it.roundToInt()}°C" } ?: "—"
    if (compact) {
        DiaryInlineSummary(listOf("걸은 시간" to duration, "이동 거리" to distance,
            "평균 속도" to speed, "온도" to temperature))
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp)) {
        if (dogNames.isNotEmpty()) Text(dogNames.joinToString(" · "), Modifier.padding(bottom = 6.dp),
            color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        Surface(shape = RoundedCornerShape(14.dp), color = CardWhite,
            border = BorderStroke(1.dp, DaengsColors.BorderNeutral), modifier = Modifier.fillMaxWidth()
            .semantics {
                contentDescription = (dogNames + listOf("걸은 시간 $duration", "이동 거리 $distance", "평균 속도 $speed", "온도 $temperature"))
                    .joinToString(", ")
            }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("시간" to duration, "거리" to distance, "속도" to speed, "온도" to temperature).forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(value, fontSize = 15.sp, lineHeight = 20.sp, color = TextDark, fontWeight = FontWeight.SemiBold)
                        Text(label, fontSize = 11.sp, lineHeight = 15.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryInlineSummary(metrics: List<Pair<String, String>>) {
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = DiaryReadingChrome.Gutter).padding(bottom = 12.dp)
        .clearAndSetSemantics { contentDescription = metrics.joinToString(", ") { "${it.first} ${it.second}" } },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        metrics.forEachIndexed { index, (_, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    VerticalDivider(Modifier.height(12.dp), color = DaengsColors.BorderNeutral)
                    Spacer(Modifier.width(10.dp))
                }
                Text(value, fontSize = 13.sp, lineHeight = 18.sp,
                    fontWeight = if (index == 3) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (index == 3) TextMuted else TextDark)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun InlineSummaryPreview() { DiaryReviewTheme {
    WalkSessionSummary(WalkSummary("preview", emptyList(), 0, 1_800_000,
        RecordedWeather(0, false, 22f), 1_200.0, 1_800_000, emptyList(), null), compact = true)
} }

@Preview(showBackground = true, widthDp = 320)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun SessionSummaryPreview() {
    DiaryReviewTheme { WalkSessionSummary(WalkSummary("preview", emptyList(), 0, 1_800_000,
        RecordedWeather(0, false, 22f), 1_200.0, 1_800_000, emptyList(), null)) }
}
