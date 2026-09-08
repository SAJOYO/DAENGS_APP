package com.daengs.app.ui.walk

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.location.LocationSample
import com.daengs.app.map.style.rememberWalkStyle
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.TrackingState
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Display-only freshness check; does not alter stored points or tracking. */
internal fun walkGaugeSpeed(sample: LocationSample?, state: TrackingState, nowNanos: Long): Float? {
    if (state != TrackingState.RECORDING || sample == null) return null
    val timestamp = sample.elapsedRealtimeNanos ?: return null
    if (nowNanos < timestamp || nowNanos - timestamp > 8_000_000_000L) return null
    val accuracy = sample.accuracyMeters ?: return null
    if (!accuracy.isFinite() || accuracy !in 0f..50f) return null
    return sample.speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0f }
}

@Composable
internal fun WalkSpeedometer(speed: Float?, modifier: Modifier = Modifier) {
    val selection by rememberWalkStyle()
    val policy = selection.policy
    val theme = policy.theme(selection.themeId)
    val validSpeed = speed?.takeIf { it.isFinite() && it >= 0f }
    var lastSpeed by rememberSaveable { mutableFloatStateOf(0f) }
    var hasReceivedSpeed by rememberSaveable { mutableStateOf(false) }
    SideEffect {
        if (validSpeed != null) {
            lastSpeed = validSpeed
            hasReceivedSpeed = true
        }
    }
    val displayedSpeed = validSpeed ?: lastSpeed
    val signalDescription = when {
        validSpeed != null -> "현재 수신 속도"
        hasReceivedSpeed -> "속도 신호 대기. 마지막 수신 값 유지"
        else -> "속도 신호 대기. 측정 전 초기값"
    }
    val fraction by animateFloatAsState(
        targetValue = displayedSpeed.div(policy.speedMax.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(650), label = "speedNeedle")
    val value = String.format(Locale.ROOT, "%.1f", displayedSpeed)
    Surface(modifier.testTag("speedometer"), shape = RoundedCornerShape(12.dp), color = CardWhite) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                    Text("속도", fontSize = 10.sp, lineHeight = 12.sp)
                    Canvas(Modifier.align(Alignment.CenterEnd).size(14.dp).testTag("speedSignalLamp").semantics {
                        contentDescription = "속도 수신 상태"
                        stateDescription = signalDescription
                    }) {
                        val lamp = if (validSpeed == null) Color(0xffc99522) else TextMuted.copy(alpha = .22f)
                        val triangle = Path().apply {
                            moveTo(size.width*.5f, size.height*.12f)
                            lineTo(size.width*.92f, size.height*.88f)
                            lineTo(size.width*.08f, size.height*.88f)
                            close()
                        }
                        drawPath(triangle, lamp, style = Stroke(1.4.dp.toPx()))
                        drawLine(lamp, Offset(size.width*.5f, size.height*.38f),
                            Offset(size.width*.5f, size.height*.59f), 1.4.dp.toPx(), StrokeCap.Round)
                        drawCircle(lamp, 0.8.dp.toPx(), Offset(size.width*.5f, size.height*.73f))
                    }
                }
                Box(Modifier.fillMaxWidth().height(68.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(58.dp).testTag("speedDial").semantics {
                        contentDescription = "${theme.label} 속도계. $signalDescription. $value m/s"
                    }) {
                        val radius = size.width / 2 - 6.dp.toPx()
                        val center = Offset(size.width / 2, size.height - 3.dp.toPx())
                        val origin = Offset(center.x - radius, center.y - radius)
                        val arcSize = Size(radius * 2, radius * 2)
                        // Sample the same speed-to-color function used by route segments.
                        for (i in 0 until 180) {
                            drawArc(Color(policy.color(policy.speedMax * (i + .5) / 180, theme.id)),
                                180f + i, 1.4f, false, origin, arcSize, style = Stroke(5.dp.toPx()))
                        }
                        for (tick in 0..4) {
                            val angle = Math.PI + Math.PI * tick / 4
                            fun point(r: Float) = Offset(center.x + cos(angle).toFloat()*r, center.y + sin(angle).toFloat()*r)
                            drawLine(TextMuted.copy(alpha = .55f), point(radius - 7.dp.toPx()),
                                point(radius - 10.dp.toPx()), 1.dp.toPx())
                        }
                        run {
                            val angle = Math.PI + Math.PI * fraction
                            val tip = Offset(center.x + cos(angle).toFloat()*(radius - 13.dp.toPx()),
                                center.y + sin(angle).toFloat()*(radius - 13.dp.toPx()))
                            drawLine(TextDark, center, tip, 2.dp.toPx(), StrokeCap.Round)
                            drawCircle(TextDark, 3.dp.toPx(), center)
                        }
                    }
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0", fontSize = 9.sp, lineHeight = 10.sp)
                        Text(String.format(Locale.ROOT, "%s+", policy.speedMax.toString().removeSuffix(".0")), fontSize = 9.sp, lineHeight = 10.sp)
                    }
                }
                Row(Modifier.testTag("speedReading").semantics { stateDescription = signalDescription },
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(value, Modifier.alignByBaseline(), fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                    Text("m/s", Modifier.alignByBaseline(), fontSize = 10.sp, lineHeight = 12.sp, color = TextMuted)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WalkSpeedometerPreview() { DaengsTheme { WalkSpeedometer(1.2f) } }

@Preview(showBackground = true)
@Composable
private fun WalkSpeedometerWaitingPreview() { DaengsTheme { WalkSpeedometer(null) } }
