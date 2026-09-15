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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.WalkRouteThumbnail
import com.daengs.app.ui.walk.formatWalkClock
import com.daengs.app.ui.walk.formatWalkDistance
import com.daengs.app.ui.walk.formatWalkDuration
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.ui.walk.walkDiaryTitle
import com.daengs.app.ui.walk.weatherLabel
import com.daengs.app.walk.WalkDepartureWeather
import com.daengs.app.walk.records.WalkRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The query fixes order and pagination first. Group only the current page by local start date. */
internal fun groupWalkRecordDays(records: List<WalkRecord>, zone: ZoneId = ZoneId.systemDefault()) =
    records.groupBy { Instant.ofEpochMilli(it.summary.startedAtMillis).atZone(zone).toLocalDate() }

private val RECORD_DAY = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)

@Composable
internal fun WalkRecordsList(
    records: List<WalkRecord>, pageNumber: Int, pageCount: Int,
    onPrevious: () -> Unit, onNext: () -> Unit, onOpen: (String) -> Unit,
    pets: List<Pet>, modifier: Modifier = Modifier,
) {
    val scroll = rememberLazyListState()
    val zone = ZoneId.systemDefault()
    val groups = remember(records, zone) { groupWalkRecordDays(records, zone) }
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
                items(walks, key = { "walk:${it.summary.sessionId}" }, contentType = { "walk" }) { record ->
                    WalkRecordCard(record, pets, { onOpen(record.summary.sessionId) })
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalkRecordCard(record: WalkRecord, pets: List<Pet>, onOpen: () -> Unit) {
    val walk = record.summary
    val names = walk.dogIds.distinct().map { id -> pets.firstOrNull { it.id == id }?.name ?: "이름 미확인" }
    val companions = if (names.isEmpty()) "동행견 미기록" else names.take(2).joinToString(" · ") +
        if (names.size > 2) " 외 ${names.size - 2}마리" else ""
    val weather = if (WalkDepartureWeather.of(walk.weather?.weatherCode) == WalkDepartureWeather.UNKNOWN)
        "출발 날씨 정보 없음" else "출발 ${weatherLabel(requireNotNull(walk.weather))}"
    Card(onClick = onOpen, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("records-walk-${walk.sessionId}")) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WalkRouteThumbnail(walk, Modifier.size(84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(walkDiaryTitle(walk, record.title?.takeIf { it.isNotBlank() }),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(companions, Modifier.semantics {
                    contentDescription = if (names.isEmpty()) companions else "동행견: ${names.joinToString(", ")}"
                }, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatWalkClock(walk.startedAtMillis)} · $weather",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.background)
        FlowRow(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RecordMetric(DaengsIcon.Pin, "거리", formatWalkDistance(walk.distanceMeters))
            RecordMetric(DaengsIcon.Clock, "시간", formatWalkDuration(walk.activeDurationMillis))
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
