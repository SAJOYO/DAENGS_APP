package com.daengs.app.ui.walk.shared

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
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
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.formatWalkDistance
import com.daengs.app.ui.walk.formatWalkDuration
import com.daengs.app.walk.shared.SharedWalk
import com.daengs.app.walk.shared.SharedWalkDetail
import com.daengs.app.walk.shared.SharedWalkDetailStatus
import com.daengs.app.walk.shared.SharedWalkPoint
import com.daengs.app.walk.shared.SharedWalksHolder
import com.daengs.app.walk.shared.SharedWalksStatus
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos

/**
 * 산책 기록 화면에서 여는 「함께 돌보는 보호자의 산책」.
 *
 * **기기 기록과 섞지 않는다** — 목록·상세 모두 서버에서 읽은 남의 산책만 읽기 전용으로 그린다.
 * 고치기·지우기·일기·행동 핀은 없다. 게임·점령 결과도 없다(이번 결정으로 후순위 보류).
 */
@Composable
internal fun SharedWalksRoute(holder: SharedWalksHolder, pets: List<Pet>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val detail = holder.detail
    if (detail !is SharedWalkDetailStatus.Closed) {
        SharedWalkDetailScreen(
            state = detail,
            onBack = holder::closeDetail,
            onRetry = { if (detail is SharedWalkDetailStatus.Unavailable) scope.launch { holder.openDetail(detail.walkId) } },
        )
        return
    }
    LaunchedEffect(holder, pets) {
        val current = holder.petId
        if (current == null || pets.none { it.id == current }) pets.firstOrNull()?.let { holder.open(it.id) }
    }
    SharedWalksScreen(
        pets = pets,
        selectedPetId = holder.petId,
        status = holder.status,
        walks = holder.walks,
        hasMore = holder.nextCursor != null,
        onSelectPet = { id -> scope.launch { holder.open(id) } },
        onLoadMore = { scope.launch { holder.loadMore() } },
        onRetry = { scope.launch { holder.retry() } },
        onOpen = { id -> scope.launch { holder.openDetail(id) } },
        onBack = onBack,
    )
}

private val WHEN = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E) HH:mm", Locale.KOREAN)

internal fun sharedWalkWhen(atMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(atMs).atZone(zone).format(WHEN)

/** 닉네임이 없으면(지금 구성원이 아닌 사람) "이전 보호자" — 케어 기록과 같은 규칙이다. */
internal fun sharedWalkActorLine(actor: CareActor): String =
    actor.nickname?.let { "${it}님이 다녀왔어요" } ?: "이전 보호자가 다녀왔어요"

internal fun sharedWalkDistance(walk: SharedWalk): String =
    walk.distanceM?.let { formatWalkDistance(it.toDouble()) } ?: "측정 전"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedWalksScreen(
    pets: List<Pet>,
    selectedPetId: String?,
    status: SharedWalksStatus,
    walks: List<SharedWalk>,
    hasMore: Boolean,
    onSelectPet: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing).testTag("shared-walks"),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "함께 돌보는 보호자의 산책",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack, modifier = Modifier.testTag("shared-walks-back")) { Text("뒤로") }
        }
        Text(
            "다른 보호자가 다녀온 산책을 볼 수만 있어요. 고치거나 지우는 것은 다녀온 사람만 할 수 있어요.",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        if (pets.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pets.forEach { pet ->
                    FilterChip(
                        selected = pet.id == selectedPetId,
                        onClick = { onSelectPet(pet.id) },
                        label = { Text(pet.name) },
                        modifier = Modifier.testTag("shared-walks-pet-${pet.id}"),
                    )
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).testTag("shared-walks-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(walks, key = { it.id }) { walk -> SharedWalkCard(walk, zone) { onOpen(walk.id) } }
            item(key = "status") {
                SharedWalksStatusLine(status, isEmpty = walks.isEmpty(), hasMore = hasMore, onLoadMore = onLoadMore, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun SharedWalksStatusLine(
    status: SharedWalksStatus,
    isEmpty: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (status) {
            SharedWalksStatus.Idle, SharedWalksStatus.Loading ->
                Text("산책 기록을 불러오고 있어요", color = TextMuted, modifier = Modifier.testTag("shared-walks-loading"))
            SharedWalksStatus.Ready -> when {
                hasMore -> TextButton(onClick = onLoadMore, modifier = Modifier.testTag("shared-walks-more")) { Text("더 보기") }
                isEmpty -> Text(
                    "다른 보호자가 다녀온 산책이 아직 없어요",
                    color = TextMuted,
                    modifier = Modifier.testTag("shared-walks-empty"),
                )
            }
            SharedWalksStatus.Unsupported -> Text(
                "지금 서버에서는 함께 보기를 쓸 수 없어요",
                color = TextMuted,
                modifier = Modifier.testTag("shared-walks-unsupported"),
            )
            is SharedWalksStatus.NotFound ->
                Text(status.message, color = TextMuted, modifier = Modifier.testTag("shared-walks-not-found"))
            is SharedWalksStatus.Failed -> {
                Text(status.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("shared-walks-failed"))
                TextButton(onClick = onRetry, modifier = Modifier.testTag("shared-walks-retry")) { Text("다시 시도") }
            }
        }
    }
}

@Composable
private fun SharedWalkCard(walk: SharedWalk, zone: ZoneId, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("shared-walk-${walk.id}"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WalkBasics(walk, zone)
        }
    }
}

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
private fun SharedWalksPreview() {
    DaengsTheme {
        SharedWalksScreen(
            pets = listOf(Pet("p1", "두부", "maltese", null, null, null, null, null, isPrimary = true)),
            selectedPetId = "p1",
            status = SharedWalksStatus.Ready,
            walks = listOf(previewWalk, previewWalk.copy(id = "w2", actor = CareActor("u3", null), distanceM = null)),
            hasMore = true,
            onSelectPet = {}, onLoadMore = {}, onRetry = {}, onOpen = {}, onBack = {},
            zone = ZoneId.of("Asia/Seoul"),
        )
    }
}

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
