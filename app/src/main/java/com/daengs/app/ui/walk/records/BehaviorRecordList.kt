package com.daengs.app.ui.walk.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.WalkRouteThumbnail
import com.daengs.app.ui.walk.walkDiaryTitle
import com.daengs.app.walk.records.WalkBehaviorRecord
import com.daengs.app.walk.records.WalkTraceState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Every matching entry remains a row, including unlocated entries and hidden walks. */
@Composable
internal fun BehaviorRecordList(
    records: List<WalkBehaviorRecord>,
    pets: List<Pet>,
    selectedKey: String?,
    hiddenIds: Set<String>,
    hideableIds: Set<String>,
    onSelect: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    availabilityChecked: Boolean = true,
) {
    LazyColumn(modifier.testTag("records-behavior-list"), state = listState,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(records, key = { it.key }) { record ->
            val id = record.walk.summary.sessionId
            BehaviorRecordCard(record, pets, record.key == selectedKey, id in hiddenIds,
                id in hideableIds, availabilityChecked, { onSelect(record.key) }, { onToggleHidden(id) }, { onOpen(id) })
        }
    }
}

@Composable
private fun BehaviorRecordCard(
    record: WalkBehaviorRecord,
    pets: List<Pet>,
    isSelected: Boolean,
    hidden: Boolean,
    canHide: Boolean,
    availabilityChecked: Boolean,
    onSelect: () -> Unit,
    onToggleHidden: () -> Unit,
    onOpen: () -> Unit,
) {
    val walk = record.walk
    val time = Instant.ofEpochMilli(record.entry.recordedAtMillis).atZone(ZoneId.systemDefault())
        .format(BehaviorRecordTimeFormat)
    val dog = record.entry.petId?.let { id -> pets.firstOrNull { it.id == id }?.name ?: "강아지 정보 없음" }
        ?: "강아지 미지정"
    val status = if (hidden) "이 산책은 지도에서 숨김" else record.locationLabel
    OutlinedCard(onClick = onSelect,
        modifier = Modifier.fillMaxWidth().testTag("records-behavior-entry-${record.key}").semantics {
            selected = isSelected
            stateDescription = status
        },
        border = BorderStroke(if (isSelected) 2.dp else 1.dp,
            if (isSelected) DaengPink else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) PinkFaint else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WalkRouteThumbnail(walk.summary, Modifier.size(58.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${record.entry.type.label} · $dog", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(walkDiaryTitle(walk.summary, walk.title), style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextMuted)
                Text(status, style = MaterialTheme.typography.labelSmall,
                    color = if (hidden || isSelected) DaengPinkDeep else TextMuted)
                if (walk.traceState != null && walk.effectiveTraceState != WalkTraceState.READY) {
                    Text(traceStateLabel(walk.effectiveTraceState), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                if (!canHide && isSelected && walk.traceState == null) Text(if (availabilityChecked) "지도에 표시할 위치·흔적 없음" else "지도 표시 확인 전",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
        if (isSelected) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleHidden, enabled = canHide,
                    modifier = Modifier.testTag("records-behavior-hide-${record.key}")) {
                    Text(if (hidden) "지도에 다시 표시" else "이 산책 숨기기")
                }
                TextButton(onClick = onOpen, modifier = Modifier.testTag("records-behavior-open-${record.key}")) {
                    Text("이 산책 보기")
                }
            }
        }
    }
}

private val BehaviorRecordTimeFormat = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

@Preview(name = "행동 기록", showBackground = true, widthDp = 390, heightDp = 280)
@Composable
private fun BehaviorRecordListPreview() {
    val result = previewRecordBehaviors()
    DaengsTheme { BehaviorRecordList(result.records, emptyList(), result.records.firstOrNull()?.key,
        emptySet(), result.related.sessionIds.toSet(), {}, {}, {}, Modifier.fillMaxSize()) }
}

@Preview(name = "숨김·위치 없음", showBackground = true, widthDp = 390, heightDp = 280)
@Composable
private fun HiddenBehaviorRecordListPreview() {
    val result = previewRecordBehaviors()
    DaengsTheme { BehaviorRecordList(result.records, emptyList(), result.records.lastOrNull()?.key,
        result.related.sessionIds.toSet(), result.related.sessionIds.toSet(), {}, {}, {}, Modifier.fillMaxSize()) }
}
