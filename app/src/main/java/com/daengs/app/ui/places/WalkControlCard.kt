package com.daengs.app.ui.places

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.walk.TrackingState
import com.daengs.app.walk.TrailSnapshot
import com.daengs.app.walk.WalkTrackingState
import com.daengs.app.ui.theme.DaengsTheme
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun WalkControlCard(
    state: WalkTrackingState,
    locationGranted: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val realtimeMillis by produceState(
        initialValue = SystemClock.elapsedRealtime(),
        state.trail.state,
        state.activeSinceRealtimeMillis,
    ) {
        value = SystemClock.elapsedRealtime()
        while (state.trail.state == TrackingState.RECORDING) {
            delay(1_000L)
            value = SystemClock.elapsedRealtime()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("산책 기록", fontWeight = FontWeight.Bold)
                Text(
                    walkStateLabel(state),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                "${formatWalkDuration(state.elapsedMillisAt(realtimeMillis))} · " +
                    "${formatWalkDistance(state.trail.distanceMeters)} · " +
                    "${state.trail.sampleCount}개 점",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state.trail.state) {
                    TrackingState.OFF -> Button(
                        onClick = onStart,
                        enabled = locationGranted,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.trail.sampleCount == 0) "산책 시작" else "새 산책 시작") }
                    TrackingState.RECORDING -> {
                        Button(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Text("일시정지")
                        }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Text("종료")
                        }
                    }
                    TrackingState.PAUSED -> {
                        Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                            Text("계속 기록")
                        }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Text("종료")
                        }
                    }
                }
            }
            if (!locationGranted) {
                Text(
                    "위치 권한을 허용하면 산책을 기록할 수 있어요.",
                    color = Color(0xFF8A5A00),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (
                state.trail.state == TrackingState.RECORDING &&
                state.trail.skippedLowAccuracy >= LOW_ACCURACY_STREAK_TO_WARN
            ) {
                Text(
                    "위치 정확도가 낮아 동선 기록을 잠시 건너뛰고 있어요.",
                    color = Color(0xFF8A5A00),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.errorMessage?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

internal fun walkStateLabel(state: WalkTrackingState): String = when (state.trail.state) {
    TrackingState.RECORDING -> "기록 중"
    TrackingState.PAUSED -> "일시정지"
    TrackingState.OFF -> if (state.trail.sampleCount == 0) "준비" else "기록 완료"
}

internal fun formatWalkDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

internal fun formatWalkDistance(meters: Double): String = if (meters >= 1_000.0) {
    String.format(Locale.ROOT, "%.1fkm", meters / 1_000.0)
} else {
    "${meters.coerceAtLeast(0.0).roundToInt()}m"
}

private const val LOW_ACCURACY_STREAK_TO_WARN = 3

@Preview(widthDp = 411, showBackground = true)
@Composable
private fun WalkControlCardPreview() {
    DaengsTheme {
        WalkControlCard(
            state = WalkTrackingState(
                trail = TrailSnapshot(
                    state = TrackingState.PAUSED,
                    distanceMeters = 842.4,
                ),
                activeDurationMillis = 754_000L,
            ),
            locationGranted = true,
            onStart = {},
            onPause = {},
            onResume = {},
            onStop = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}
