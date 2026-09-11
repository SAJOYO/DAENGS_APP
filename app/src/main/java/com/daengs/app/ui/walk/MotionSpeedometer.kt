package com.daengs.app.ui.walk

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.style.rememberWalkStyle
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.display.*
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Display-only stage-4 component. The production WalkSpeedometer is switched in stage 5. */
@Composable
internal fun MotionSpeedometer(display: MotionDisplay, modifier: Modifier = Modifier) {
    val selection by rememberWalkStyle()
    val policy = selection.policy
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val fraction by animateFloatAsState(
        (display.speedMps / policy.speedMax).coerceIn(0.0, 1.0).toFloat(),
        animationSpec = tween(if (display.speedMps == 0.0) 0 else 450), label = "motionSpeedNeedle")
    val value = if (display.speedMps >= 1000) "999+" else String.format(Locale.ROOT, "%.1f", display.speedMps)
    val caption = when (display.freshness) {
        DisplayFreshness.PAUSED -> "일시정지"
        DisplayFreshness.FINAL -> "산책 종료"
        else -> when (display.signal) {
            DisplaySignal.RECEIVING -> "속도 측정 중"
            DisplaySignal.DELAYED -> "수신 불안정"
            DisplaySignal.WAITING -> "측정 대기"
        }
    }
    val description = when (display.freshness) {
        DisplayFreshness.INITIAL -> "측정 전 초기값"
        DisplayFreshness.LIVE -> "최근 측정 속도"
        DisplayFreshness.HELD, DisplayFreshness.STALE -> "마지막 측정값 유지"
        DisplayFreshness.PAUSED -> "일시정지 전 측정값 유지"
        DisplayFreshness.FINAL -> "종료 시 마지막 측정값"
    }
    Surface(modifier.testTag("motionSpeedometer"), shape = RoundedCornerShape(12.dp), color = CardWhite) {
        Column(Modifier.width(144.dp * scale).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("속도", Modifier.fillMaxWidth().height(18.dp * scale), fontSize = 11.sp,
                lineHeight = 14.sp, maxLines = 1, textAlign = TextAlign.Center, color = TextDark)
            Canvas(Modifier.fillMaxWidth().height(66.dp * scale).testTag("motionSpeedDial")) {
                val radius = size.width / 2 - 7.dp.toPx()
                val center = Offset(size.width / 2, size.height - 5.dp.toPx())
                val origin = Offset(center.x - radius, center.y - radius)
                val arc = Size(radius * 2, radius * 2)
                for (i in 0 until 180) {
                    drawArc(Color(policy.color(policy.speedMax * (i + .5) / 180, selection.themeId)),
                        180f + i, 1.4f, false, origin, arc, style = Stroke(5.dp.toPx()))
                }
                val angle = Math.PI + Math.PI * fraction
                val tip = Offset(center.x + cos(angle).toFloat() * (radius - 12.dp.toPx()),
                    center.y + sin(angle).toFloat() * (radius - 12.dp.toPx()))
                drawLine(TextDark, center, tip, 2.dp.toPx(), StrokeCap.Round)
                drawCircle(TextDark, 3.dp.toPx(), center)
            }
            Row(Modifier.fillMaxWidth().height(14.dp * scale), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", fontSize = 10.sp, lineHeight = 12.sp, color = TextDark)
                Text("${policy.speedMax.toString().removeSuffix(".0")}+", fontSize = 10.sp, lineHeight = 12.sp, color = TextDark)
            }
            Row(Modifier.fillMaxWidth().height(28.dp * scale).testTag("motionSpeedReading")
                .semantics(mergeDescendants = true) { stateDescription = description },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.width(66.dp * scale), fontSize = 20.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 1)
                Spacer(Modifier.width(5.dp))
                Text("m/s", fontSize = 11.sp, lineHeight = 14.sp, color = TextDark, maxLines = 1)
            }
            Row(Modifier.fillMaxWidth().height(24.dp * scale).testTag("motionSpeedSignal")
                .semantics(mergeDescendants = true) { contentDescription = "속도 수신 상태"; stateDescription = caption },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(7.dp)) {
                    drawCircle(when (display.signal) {
                        DisplaySignal.RECEIVING -> DaengsColors.Success
                        DisplaySignal.DELAYED -> DaengsColors.Warning
                        DisplaySignal.WAITING -> TextMuted
                    })
                }
                Spacer(Modifier.width(6.dp))
                Text("GPS", fontSize = 11.sp, lineHeight = 14.sp, color = TextDark, maxLines = 1)
            }
            Text(caption, Modifier.fillMaxWidth().height(20.dp * scale).testTag("motionSpeedCaption"),
                fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Center, color = TextDark, maxLines = 1)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MotionSpeedometerInitialPreview() { DaengsTheme { MotionSpeedometer(MotionDisplay()) } }

@Preview(showBackground = true)
@Composable
private fun MotionSpeedometerLivePreview() {
    DaengsTheme { MotionSpeedometer(MotionDisplay(1.2, DisplayFreshness.LIVE, DisplaySignal.RECEIVING)) }
}

@Preview(showBackground = true)
@Composable
private fun MotionSpeedometerStalePreview() {
    DaengsTheme { MotionSpeedometer(MotionDisplay(1.2, DisplayFreshness.STALE, DisplaySignal.DELAYED)) }
}

@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun MotionSpeedometerStoppedLargeTextPreview() {
    DaengsTheme { MotionSpeedometer(MotionDisplay(0.0, DisplayFreshness.LIVE, DisplaySignal.RECEIVING)) }
}
