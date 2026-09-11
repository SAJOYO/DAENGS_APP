package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkSummary

@Composable
internal fun WalkSessionSummary(summary: WalkSummary, dogNames: List<String> = emptyList()) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        val metadata = dogNames + listOfNotNull(summary.weather?.let(::weatherLabel))
        if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "),
            modifier = Modifier.padding(bottom = 10.dp), color = TextMuted,
            style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("걸은 시간" to formatWalkDuration(summary.activeDurationMillis),
                "이동 거리" to formatWalkDistance(summary.distanceMeters),
                "평균 속도" to formatAverageSpeed(summary)).forEach { (label, value) ->
                BoxWithConstraints(Modifier.weight(1f)) {
                    val compact = maxWidth / LocalDensity.current.fontScale < 90.dp
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium)
                        Text(buildAnnotatedString {
                            val unitStart = value.indexOfFirst(Char::isLetter).let { if (it < 0) value.length else it }
                            append(value.take(unitStart).trimEnd())
                            if (unitStart < value.length) withStyle(SpanStyle(
                                fontSize = .55.em, fontWeight = FontWeight.Normal, color = TextMuted)) {
                                append(" " + value.substring(unitStart))
                            }
                        }, fontWeight = FontWeight.Medium, color = TextDark,
                            fontSize = if (compact) 20.sp else 22.sp,
                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
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
