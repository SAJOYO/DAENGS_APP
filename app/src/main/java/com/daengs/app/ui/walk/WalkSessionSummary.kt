package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkSummary

@Composable
internal fun WalkSessionSummary(summary: WalkSummary, dogNames: List<String> = emptyList()) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        val metadata = dogNames + listOfNotNull(summary.weather?.let(::weatherLabel))
        if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "),
            modifier = Modifier.padding(bottom = 10.dp), color = TextMuted,
            style = MaterialTheme.typography.bodyMedium)
        Surface(shape = RoundedCornerShape(20.dp), color = CardWhite) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                listOf("걸은 시간" to formatWalkDuration(summary.activeDurationMillis),
                    "이동 거리" to formatWalkDistance(summary.distanceMeters),
                    "평균 속도" to formatAverageSpeed(summary)).forEachIndexed { index, (label, value) ->
                    if (index > 0) VerticalDivider(Modifier.height(32.dp), color = PinkFaint)
                    BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        val compact = maxWidth / LocalDensity.current.fontScale < 90.dp
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, color = TextMuted, style = MaterialTheme.typography.labelMedium)
                            Text(buildAnnotatedString {
                                val unitStart = value.indexOfFirst(Char::isLetter).let { if (it < 0) value.length else it }
                                append(value.take(unitStart).trimEnd())
                                if (unitStart < value.length) withStyle(SpanStyle(
                                    fontSize = .55.em, fontWeight = FontWeight.Medium, color = TextMuted)) {
                                    append(" " + value.substring(unitStart))
                                }
                            }, fontWeight = FontWeight.Bold, color = TextDark,
                                fontSize = if (compact) 20.sp else 24.sp,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                        }
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
