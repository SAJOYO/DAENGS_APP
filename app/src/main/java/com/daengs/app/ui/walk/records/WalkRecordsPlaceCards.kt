package com.daengs.app.ui.walk.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.formatWalkClock
import com.daengs.app.walk.diary.DiaryActionTarget
import com.daengs.app.walk.records.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class PlaceWalk(val actions: List<WalkBehaviorRecord>) {
    val walk get() = actions.first().walk
    val id get() = walk.summary.sessionId
}

/** A visit is a session, never a coordinate or a day. Keep every original action within it. */
internal fun placeWalks(records: List<WalkBehaviorRecord>): List<PlaceWalk> = records.distinctBy { it.key }
    .groupBy { it.walk.summary.sessionId }.values.map { actions ->
        PlaceWalk(actions.sortedWith(compareBy<WalkBehaviorRecord> { it.entry.recordedAtMillis }.thenBy { it.key }))
    }.sortedWith(compareByDescending<PlaceWalk> { it.walk.summary.startedAtMillis }.thenBy { it.id })

/** Peek and expanded list resolve the same key; a refreshed/deleted record cannot become a stale target. */
internal fun List<PlaceWalk>.currentPlaceAction(key: String?): WalkBehaviorRecord? =
    flatMap { it.actions }.firstOrNull { it.key == key } ?: firstOrNull()?.actions?.firstOrNull()

@Composable
internal fun WalkRecordsPlacePeek(visits: List<PlaceWalk>, current: WalkBehaviorRecord, pets: List<Pet>,
    onSelect: (WalkBehaviorRecord) -> Unit, onExpand: () -> Unit, onOpen: (DiaryActionTarget) -> Unit) {
    val index = visits.indexOfFirst { it.id == current.walk.summary.sessionId }
    Column(Modifier.testTag("records-place-peek")) {
        Surface(onClick = onExpand, color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("records-place-peek-card")) {
            WalkRecordCardHeader(current.walk, pets, compact = true,
                compactMeta = "${placeActionDay(current)} ${formatWalkClock(current.entry.recordedAtMillis)} · ${current.entry.type.label}" +
                    if (current.point == null) " · 위치 없음" else "")
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.background)
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                onSelect(current)
                onOpen(DiaryActionTarget(current.entry.sessionId, current.entry.id))
            }, modifier = Modifier.testTag("records-place-peek-open")) { Text("이 산책 일기") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSelect(visits[index - 1].actions.first()) }, enabled = index > 0,
                modifier = Modifier.width(48.dp).testTag("records-place-previous")
                    .semantics { contentDescription = "이전 산책" }) { Text("‹") }
            Text("${index + 1} / ${visits.size}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("records-place-index").semantics {
                    contentDescription = "${visits.size}개 산책 중 ${index + 1}번째"
                })
            TextButton(onClick = { onSelect(visits[index + 1].actions.first()) }, enabled = index < visits.lastIndex,
                modifier = Modifier.width(48.dp).testTag("records-place-next")
                    .semantics { contentDescription = "다음 산책" }) { Text("›") }
        }
    }
}

@Composable
internal fun WalkRecordsPlaceList(visits: List<PlaceWalk>, pets: List<Pet>, selectedKey: String?,
    onSelect: (WalkBehaviorRecord) -> Unit, onLocate: (WalkBehaviorRecord) -> Unit,
    onOpen: (DiaryActionTarget) -> Unit, source: WalkRecordsSource?,
    modifier: Modifier, listState: LazyListState) {
    val current = visits.currentPlaceAction(selectedKey)
    LazyColumn(modifier.testTag("records-behavior-list"), state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(visits, key = { it.id }) { visit ->
            val active = current?.walk?.summary?.sessionId == visit.id
            Card(onClick = { onSelect(visit.actions.first()) }, shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth().testTag("records-place-walk-${visit.id}")
                    .semantics { selected = active }) {
                Text("${placeActionDay(visit.actions.first())} · 행동 ${visit.actions.size}건",
                    Modifier.padding(start = 14.dp, top = 12.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                WalkRecordCardHeader(visit.walk, pets)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    visit.actions.forEach { action ->
                        val dog = action.entry.petId?.let { id -> pets.firstOrNull { it.id == id }?.name ?: "이름 미확인" }
                            ?: "강아지 미지정"
                        FilterChip(selected = current?.key == action.key,
                            onClick = { onSelect(action) },
                            label = { Text("${formatWalkClock(action.entry.recordedAtMillis)} ${action.entry.type.label} · $dog") },
                            modifier = Modifier.testTag("records-behavior-entry-${action.key}"))
                    }
                }
                if (active && current != null) {
                    BehaviorRecordReading(current, source, Modifier.padding(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onLocate(current) }, enabled = current.point != null,
                            modifier = Modifier.testTag("records-place-locate")) { Text("지도에서 보기") }
                        TextButton(onClick = { onOpen(DiaryActionTarget(current.entry.sessionId, current.entry.id)) },
                            modifier = Modifier.testTag("records-behavior-open-${current.key}")) { Text("일기에서 이어 보기") }
                    }
                }
            }
        }
    }
}

private fun placeActionDay(record: WalkBehaviorRecord) = Instant.ofEpochMilli(record.entry.recordedAtMillis)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일"))

@Composable
internal fun WalkRecordsEmptyActions(message: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("records-actions-empty"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAction, modifier = Modifier.testTag("records-actions-empty-action")) { Text(action) }
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun EmptyActionsPreview() { DaengsTheme { WalkRecordsEmptyActions("킁킁 기록이 없어요.", "모든 행동 보기", {}) } }

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun PlacePeekPreview() { DaengsTheme {
    val visits = placeWalks(previewRecordBehaviors().records)
    visits.currentPlaceAction(null)?.let { WalkRecordsPlacePeek(visits, it, emptyList(), {}, {}, {}) }
} }

@Preview(showBackground = true, widthDp = 390, heightDp = 600)
@Composable
private fun PlaceListPreview() { DaengsTheme {
    WalkRecordsPlaceList(placeWalks(previewRecordBehaviors().records), emptyList(), null, {}, {}, {}, null,
        Modifier.fillMaxSize(), androidx.compose.foundation.lazy.rememberLazyListState())
} }
