package com.daengs.app.ui.walk.shared

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.care.CareActor
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.formatWalkDistance
import com.daengs.app.ui.walk.formatWalkDuration
import com.daengs.app.walk.shared.SharedWalk
import com.daengs.app.walk.shared.SharedWalkDetail
import com.daengs.app.walk.shared.SharedWalkDetailStatus
import com.daengs.app.walk.shared.SharedWalkPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos

/*
 * 공동 보호자가 다녀온 산책의 **읽기 전용 상세**.
 *
 * 목록은 산책 기록 화면 「산책별」에 내 산책과 섞여 있다 — 그 카드를 누르면 여기로 온다. 고치기·지우기·
 * 일기·행동 핀은 없다. 게임·점령 결과도 없다(이번 결정으로 후순위 보류).
 */

private val WHEN = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E) HH:mm", Locale.KOREAN)

internal fun sharedWalkWhen(atMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(atMs).atZone(zone).format(WHEN)

/** 닉네임이 없으면(지금 구성원이 아닌 사람) "이전 보호자" — 케어 기록과 같은 규칙이다. */
internal fun sharedWalkActorLine(actor: CareActor): String =
    actor.nickname?.let { "${it}님이 다녀왔어요" } ?: "이전 보호자가 다녀왔어요"

internal fun sharedWalkDistance(walk: SharedWalk): String =
    walk.distanceM?.let { formatWalkDistance(it.toDouble()) } ?: "측정 전"

@Composable
private fun WalkBasics(walk: SharedWalk, zone: ZoneId) {
    Text(sharedWalkActorLine(walk.actor), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        sharedWalkWhen(walk.startedAtMs, zone),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("시간 ${formatWalkDuration(walk.durationS * 1_000L)}", style = MaterialTheme.typography.labelLarge)
        Text("거리 ${sharedWalkDistance(walk)}", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun SharedWalkDetailScreen(
    state: SharedWalkDetailStatus,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
            .testTag("shared-walk-detail"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "함께 보는 산책",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack, modifier = Modifier.testTag("shared-walk-detail-back")) { Text("뒤로") }
        }
        when (state) {
            SharedWalkDetailStatus.Closed -> Unit
            is SharedWalkDetailStatus.Loading -> Text("산책을 불러오고 있어요", color = TextMuted)
            is SharedWalkDetailStatus.Unavailable -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("shared-walk-detail-error"))
                if (state.retryable) {
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("shared-walk-detail-retry")) { Text("다시 시도") }
                }
            }
            is SharedWalkDetailStatus.Ready -> {
                WalkBasics(state.detail.walk, zone)
                SharedWalkRoute(state.detail.points, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(
                    "볼 수만 있어요. 고치거나 지우는 것은 다녀온 사람만 할 수 있어요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

/** 서버 좌표로만 그리는 경로. 지도 SDK·기기 기록 없이 선 하나다. */
@Composable
private fun SharedWalkRoute(points: List<SharedWalkPoint>, modifier: Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    if (points.size < 2) {
        Box(
            modifier.background(surface, RoundedCornerShape(20.dp)).testTag("shared-walk-no-route"),
            contentAlignment = Alignment.Center,
        ) {
            Text("경로가 기록되지 않은 산책이에요", color = TextMuted)
        }
        return
    }
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier.background(surface, RoundedCornerShape(20.dp)).testTag("shared-walk-route")) {
        val pad = 24.dp.toPx()
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLng = points.minOf { it.lng }
        val maxLng = points.maxOf { it.lng }
        // 경도 1도는 위도에 따라 짧아진다 — cos(위도) 로 줄여야 모양이 옆으로 늘어나지 않는다.
        val lngScale = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = ((maxLng - minLng) * lngScale).coerceAtLeast(1e-9)
        val spanY = (maxLat - minLat).coerceAtLeast(1e-9)
        val scale = minOf((size.width - pad * 2) / spanX, (size.height - pad * 2) / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun at(point: SharedWalkPoint) = Offset(
            (offX + (point.lng - minLng) * lngScale * scale).toFloat(),
            (offY + (maxLat - point.lat) * scale).toFloat(),
        )
        val path = Path().apply {
            points.forEachIndexed { index, point ->
                val o = at(point)
                if (index == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }
        drawPath(path, line, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val previewWalk = SharedWalk(
    id = "w1",
    startedAtMs = 1_756_681_200_000L,
    endedAtMs = 1_756_683_600_000L,
    durationS = 2_400L,
    distanceM = 1_234,
    movingS = 2_100,
    actor = CareActor("u2", "키키"),
    isMine = false,
    petIds = listOf("p1"),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun SharedWalkDetailPreview() {
    DaengsTheme {
        SharedWalkDetailScreen(
            state = SharedWalkDetailStatus.Ready(
                SharedWalkDetail(
                    previewWalk,
                    listOf(
                        SharedWalkPoint(1_756_681_200_000L, 37.5665, 126.9780),
                        SharedWalkPoint(1_756_681_260_000L, 37.5670, 126.9790),
                        SharedWalkPoint(1_756_681_320_000L, 37.5668, 126.9801),
                    ),
                ),
            ),
            onBack = {}, onRetry = {},
            zone = ZoneId.of("Asia/Seoul"),
        )
    }
}
