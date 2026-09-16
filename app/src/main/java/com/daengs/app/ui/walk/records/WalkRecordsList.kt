package com.daengs.app.ui.walk.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.care.CareActor
import com.daengs.app.pet.Pet
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.walk.WalkRouteThumbnail
import com.daengs.app.ui.walk.formatWalkClock
import com.daengs.app.ui.walk.formatWalkDistance
import com.daengs.app.ui.walk.formatWalkDuration
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.ui.walk.walkDiaryTitle
import com.daengs.app.ui.walk.weatherLabel
import com.daengs.app.walk.WalkDepartureWeather
import com.daengs.app.walk.records.WalkRecord
import com.daengs.app.walk.records.WalkRecordRow
import com.daengs.app.walk.records.sharedWalkRecord
import com.daengs.app.walk.shared.SharedWalk
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The query fixes order and pagination first. Group only the current page by local start date. */
internal fun groupWalkRecordDays(records: List<WalkRecord>, zone: ZoneId = ZoneId.systemDefault()) =
    records.groupBy { Instant.ofEpochMilli(it.summary.startedAtMillis).atZone(zone).toLocalDate() }

internal fun groupWalkRecordRowDays(rows: List<WalkRecordRow>, zone: ZoneId = ZoneId.systemDefault()) =
    rows.groupBy { Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() }

private val RECORD_DAY = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)

@Composable
internal fun WalkRecordsList(
    records: List<WalkRecord>, pageNumber: Int, pageCount: Int,
    onPrevious: () -> Unit, onNext: () -> Unit, onOpen: (String) -> Unit,
    pets: List<Pet>, modifier: Modifier = Modifier,
) = WalkRecordRowsList(records.map { WalkRecordRow.Mine(it) }, pageNumber, pageCount, onPrevious, onNext,
    onOpen, {}, pets, modifier)

/**
 * 내 산책과 공동 보호자 산책을 섞은 한 쪽. [showActor] 면 모든 카드의 같은 자리에 수행자 배지를 둔다
 * — 내 산책은 "나", 공동 보호자 산책은 닉네임.
 */
@Composable
internal fun WalkRecordRowsList(
    rows: List<WalkRecordRow>, pageNumber: Int, pageCount: Int,
    onPrevious: () -> Unit, onNext: () -> Unit, onOpen: (String) -> Unit, onOpenShared: (SharedWalk) -> Unit,
    pets: List<Pet>, modifier: Modifier = Modifier, showActor: Boolean = false,
) {
    val scroll = rememberLazyListState()
    val zone = ZoneId.systemDefault()
    val groups = remember(rows, zone) { groupWalkRecordRowDays(rows, zone) }
    Column(modifier) {
        LazyColumn(state = scroll, modifier = Modifier.weight(1f).testTag("records-walk-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.entries.forEachIndexed { groupIndex, (day, walks) ->
                item(key = "day:$day", contentType = "date") {
                    Text(day.format(RECORD_DAY), Modifier.fillMaxWidth()
                        .padding(top = if (groupIndex == 0) 2.dp else 12.dp, bottom = 2.dp)
                        .testTag("records-day-$day").semantics { heading() },
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                items(walks, key = { row -> if (row is WalkRecordRow.Shared) "shared:${row.id}" else "walk:${row.id}" },
                    contentType = { "walk" }) { row ->
                    when (row) {
                        is WalkRecordRow.Mine -> WalkRecordCard(row.record, pets, { onOpen(row.id) },
                            actor = if (showActor) MY_WALK_ACTOR else null)
                        is WalkRecordRow.Shared -> {
                            val record = remember(row.walk) { sharedWalkRecord(row.walk) }
                            WalkRecordCard(record, pets, { onOpenShared(row.walk) },
                                actor = sharedWalkActor(row.walk.actor),
                                distance = row.walk.distanceM?.let { formatWalkDistance(it.toDouble()) } ?: "측정 전")
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("records-pagination"),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrevious, enabled = pageNumber > 1,
                        modifier = Modifier.weight(1f)) { Text("‹ 이전") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$pageNumber 페이지", style = MaterialTheme.typography.labelLarge)
                        Text("전체 ${pageCount}페이지", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onNext, enabled = pageNumber < pageCount,
                        modifier = Modifier.weight(1f)) { Text("다음 ›") }
                }
            }
        }
    }
}

/** 카드의 수행자 배지 — 보이는 이름과 읽어 주는 뜻. */
internal data class WalkActorLabel(val name: String, val description: String)

internal val MY_WALK_ACTOR = WalkActorLabel("나", "내 산책")

/** 닉네임이 없으면(지금 구성원이 아닌 사람) "이전 보호자" — 케어 기록과 같은 규칙이다. */
internal fun sharedWalkActor(actor: CareActor) = WalkActorLabel(actor.displayName, "${actor.displayName}의 산책")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalkRecordCard(
    record: WalkRecord, pets: List<Pet>, onOpen: () -> Unit,
    actor: WalkActorLabel? = null,
    distance: String = formatWalkDistance(record.summary.distanceMeters),
) {
    val walk = record.summary
    Card(onClick = onOpen, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("records-walk-${walk.sessionId}")) {
        WalkRecordCardHeader(record, pets, actor = actor)
        HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.background)
        FlowRow(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RecordMetric(DaengsIcon.Pin, "거리", distance)
            RecordMetric(DaengsIcon.Clock, "시간", formatWalkDuration(walk.activeDurationMillis))
        }
    }
}

/**
 * Shared with map inspection; the default layout is the existing walk-list card.
 *
 * [actor] 가 있으면 강아지 이름 아래에 수행자 배지를 둔다 — 「산책별」 목록 카드만 쓰고, 지도 점검의
 * 좁은 카드(`compact`)에는 넣지 않는다.
 */
@Composable
internal fun WalkRecordCardHeader(record: WalkRecord, pets: List<Pet>, compact: Boolean = false,
    compactMeta: String? = null, actor: WalkActorLabel? = null) {
    val walk = record.summary
    val names = walk.dogIds.distinct().map { id -> pets.firstOrNull { it.id == id }?.name ?: "이름 미확인" }
    val companions = if (names.isEmpty()) "동행견 미기록" else names.take(2).joinToString(" · ") +
        if (names.size > 2) " 외 ${names.size - 2}마리" else ""
    val weather = if (WalkDepartureWeather.of(walk.weather?.weatherCode) == WalkDepartureWeather.UNKNOWN)
        "출발 날씨 정보 없음" else "출발 ${weatherLabel(requireNotNull(walk.weather))}"
    Row(Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 6.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
        // The shared endpoint label (출발·도착) needs the existing 48dp minimum.
        WalkRouteThumbnail(walk, Modifier.size(if (compact) 48.dp else 84.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
            Text(walkDiaryTitle(walk),
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            Text(if (compact) "$companions · $weather" else companions, Modifier.semantics {
                contentDescription = (if (names.isEmpty()) companions else "동행견: ${names.joinToString(", ")}") +
                    if (compact) " · $weather" else ""
            }, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (!compact) actor?.let { WalkActorBadge(it, Modifier.testTag("records-walk-actor-${walk.sessionId}")) }
            if (!compact) Text("${formatWalkClock(walk.startedAtMillis)} · $weather",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else compactMeta?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/**
 * 산책 수행자. 마이 화면 「공동 돌봄」 배지와 같은 바탕·글자색·크기에 앱 공용 사람 아이콘을 얹고
 * 문장 없이 이름만 둔다. 읽어 주기는 "키키의 산책"·"내 산책" 이다.
 */
@Composable
internal fun WalkActorBadge(actor: WalkActorLabel, modifier: Modifier = Modifier) {
    Surface(color = PinkFaint, shape = RoundedCornerShape(8.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = actor.description }) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            DaengsIconView(DaengsIcon.Person, Modifier.size(11.dp), DaengPinkDeep)
            Text(actor.name, color = DaengPinkDeep, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecordMetric(icon: DaengsIcon, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DaengsIconView(icon, Modifier.size(16.dp).semantics { contentDescription = label },
            MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 720)
@Preview(showBackground = true, widthDp = 320, heightDp = 680, fontScale = 1.5f)
@Composable
private fun RecordsListPreview() {
    val walk = previewDiarySummary()
    DaengsTheme { WalkRecordsList(listOf(
        WalkRecord(walk.copy(dogIds = listOf("dog-0", "dog-1", "dog-2")), "나무 그늘 따라 걸은 오후"),
        WalkRecord(walk.copy(sessionId = "without-route", segments = emptyList()), ""),
    ), 1, 2, {}, {}, {}, recordsPreviewPets(), Modifier.fillMaxSize()) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun UnifiedRecordsListPreview() {
    val walk = previewDiarySummary()
    val shared = SharedWalk("shared-1", walk.startedAtMillis - 3_600_000, walk.startedAtMillis - 1_800_000, 1_800,
        1_240, 1_600, CareActor("u2", "키키"), false, listOf("dog-0"), weatherCode = 3, isDay = true)
    DaengsTheme { WalkRecordRowsList(listOf(
        WalkRecordRow.Mine(WalkRecord(walk.copy(dogIds = listOf("dog-0")), "나무 그늘 따라 걸은 오후")),
        WalkRecordRow.Shared(shared),
    ), 1, 1, {}, {}, {}, {}, recordsPreviewPets(), Modifier.fillMaxSize(), showActor = true) }
}
