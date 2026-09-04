package com.daengs.app.ui.walk

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
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
        shape = RoundedCornerShape(16.dp),
        color = CardWhite.copy(alpha = 0.96f),
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
                Text("산책 기록", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    walkStateLabel(state),
                    color = DaengPinkDeep,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "${formatWalkDuration(state.elapsedMillisAt(realtimeMillis))} · " +
                    "${formatWalkDistance(state.trail.distanceMeters)} · " +
                    "${state.trail.sampleCount}개 점",
                color = TextMuted,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state.trail.state) {
                    TrackingState.OFF -> DaengsWideButton(
                        label = if (state.trail.sampleCount == 0) "산책 시작" else "새 산책 시작",
                        onClick = onStart,
                        enabled = locationGranted,
                        accent = true,
                    )
                    TrackingState.RECORDING -> {
                        DaengsWideButton("일시정지", onPause, Modifier.weight(1f), accent = true)
                        DaengsWideButton("종료", onStop, Modifier.weight(1f))
                    }
                    TrackingState.PAUSED -> {
                        DaengsWideButton("계속 기록", onResume, Modifier.weight(1f), accent = true)
                        DaengsWideButton("종료", onStop, Modifier.weight(1f))
                    }
                }
            }
            if (!locationGranted) {
                Text(
                    "위치 권한을 허용하면 산책을 기록할 수 있어요.",
                    color = DaengsColors.Warning,
                    fontSize = 12.sp,
                )
            }
            if (
                state.trail.state == TrackingState.RECORDING &&
                state.trail.skippedLowAccuracy >= LOW_ACCURACY_STREAK_TO_WARN
            ) {
                Text(
                    "위치 정확도가 낮아 동선 기록을 잠시 건너뛰고 있어요.",
                    color = DaengsColors.Warning,
                    fontSize = 12.sp,
                )
            }
            // **버리기만 하고 말을 안 하면 기록이 멈춘 것으로 읽힌다.** 지도에 점이
            // 안 찍히는데 이유를 모르면 사용자는 앱을 못 믿는다. 한 번 튄 것으로는
            // 안 띄운다 — 정확도 경고와 같은 이유로 연속으로 걸릴 때만 말한다.
            if (
                state.trail.state == TrackingState.RECORDING &&
                state.trail.skippedTooFast >= TOO_FAST_STREAK_TO_WARN
            ) {
                Text(
                    "걷는 속도보다 빨라서 이 구간은 산책에 안 담고 있어요.",
                    color = DaengsColors.Warning,
                    fontSize = 12.sp,
                )
            }
            state.errorMessage?.let { message ->
                Surface(color = DaengsColors.ErrorSoft, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        color = DaengsColors.Error,
                        fontSize = 12.sp,
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

fun formatWalkDistance(meters: Double): String = if (meters >= 1_000.0) {
    String.format(Locale.ROOT, "%.1fkm", meters / 1_000.0)
} else {
    "${meters.coerceAtLeast(0.0).roundToInt()}m"
}

private const val LOW_ACCURACY_STREAK_TO_WARN = 3

/**
 * 몇 번 연속으로 빨라야 말해 주나.
 *
 * 정확도 경고와 같은 값이다. GPS 가 한 번 튄 것과 탈것에 탄 것을 가르는 것이 이
 * 숫자다 — 1이면 신호가 한 번 흔들릴 때마다 경고가 깜빡인다.
 */
private const val TOO_FAST_STREAK_TO_WARN = 3

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

/**
 * 탈것에 탔을 때. **버리고 있다는 것을 화면이 말하는지**를 본다.
 *
 * 조용히 버리면 사용자는 앱이 멈춘 줄 안다 — 지도에 점이 안 찍히는데 이유를 모른다.
 */
@Preview(name = "산책 · 걷는 속도보다 빠를 때", widthDp = 411, showBackground = true)
@Composable
private fun WalkControlCardTooFastPreview() {
    DaengsTheme {
        WalkControlCard(
            state = WalkTrackingState(
                trail = TrailSnapshot(
                    state = TrackingState.RECORDING,
                    distanceMeters = 312.0,
                    skippedTooFast = TOO_FAST_STREAK_TO_WARN,
                ),
                activeDurationMillis = 421_000L,
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
