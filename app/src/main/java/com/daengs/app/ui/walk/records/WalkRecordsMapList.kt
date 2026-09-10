package com.daengs.app.ui.walk.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.daengs.app.map.layers.traces.WalkTraceSheet
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.WalkRouteThumbnail
import com.daengs.app.ui.walk.dogNames
import com.daengs.app.ui.walk.formatWalkDay
import com.daengs.app.ui.walk.formatWalkDistance
import com.daengs.app.ui.walk.formatWalkDuration
import com.daengs.app.ui.walk.previewDiarySummary
import com.daengs.app.ui.walk.walkDiaryTitle
import com.daengs.app.walk.diary.SpatialDiaryCellId
import com.daengs.app.walk.records.WalkRecord
import com.daengs.app.walk.records.WalkTraceState

/** Selecting and hiding change presentation only. Every selected record stays in this list. */
@Composable
internal fun WalkRecordsMapList(
    records: List<WalkRecord>,
    pets: List<Pet>,
    selectedId: String?,
    hiddenIds: Set<String>,
    availableTraceIds: Set<String>?,
    onSelect: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier = modifier.testTag("records-map-list"),
        state = listState,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(records, key = { it.summary.sessionId }) { record ->
            val id = record.summary.sessionId
            val traceState = when {
                record.effectiveTraceState != WalkTraceState.READY -> MapRecordTraceState.MISSING
                availableTraceIds == null -> MapRecordTraceState.PREPARING
                id !in availableTraceIds -> MapRecordTraceState.MISSING
                id in hiddenIds -> MapRecordTraceState.HIDDEN
                else -> MapRecordTraceState.VISIBLE
            }
            WalkRecordMapCard(record, pets, id == selectedId, traceState,
                onSelect = { onSelect(id) }, onToggleHidden = { onToggleHidden(id) }, onOpen = { onOpen(id) })
        }
    }
}

@Composable
private fun WalkRecordMapCard(
    record: WalkRecord,
    pets: List<Pet>,
    isSelected: Boolean,
    traceState: MapRecordTraceState,
    onSelect: () -> Unit,
    onToggleHidden: () -> Unit,
    onOpen: () -> Unit,
) {
    val walk = record.summary
    val status = if (record.effectiveTraceState != WalkTraceState.READY) traceStateLabel(record.effectiveTraceState)
    else when (traceState) {
        MapRecordTraceState.PREPARING -> "지도 표시 확인 전"
        MapRecordTraceState.MISSING -> "지도 흔적 없음"
        MapRecordTraceState.HIDDEN -> "지도에서 숨김"
        MapRecordTraceState.VISIBLE -> if (isSelected) "선택됨" else null
    }
    OutlinedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().testTag("records-map-record-${walk.sessionId}")
            .semantics {
                selected = isSelected
                status?.let { stateDescription = it }
            },
        border = BorderStroke(if (isSelected) 2.dp else 1.dp,
            if (isSelected) DaengPink else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) PinkFaint else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            WalkRouteThumbnail(walk, Modifier.size(72.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(walkDiaryTitle(walk, record.title), fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (record.title != null) {
                    Text(formatWalkDay(walk.startedAtMillis), style = MaterialTheme.typography.labelSmall,
                        color = TextMuted)
                }
                val names = dogNames(walk.dogIds, pets)
                if (names.isNotEmpty()) {
                    Text(names.joinToString(" · "), color = DaengPinkDeep,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${formatWalkDuration(walk.activeDurationMillis)} · ${formatWalkDistance(walk.distanceMeters)}",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                status?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) DaengPinkDeep else TextMuted)
                }
            }
        }
        if (isSelected) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = traceState == MapRecordTraceState.VISIBLE || traceState == MapRecordTraceState.HIDDEN,
                    onClick = onToggleHidden,
                    modifier = Modifier.testTag("records-map-hide-${walk.sessionId}"),
                ) {
                    Text(if (traceState == MapRecordTraceState.HIDDEN) "지도에 다시 표시" else "지도에서 숨기기")
                }
                TextButton(onClick = onOpen, modifier = Modifier.testTag("records-map-open-${walk.sessionId}")) {
                    Text("이 산책 보기")
                }
            }
        }
    }
}

private enum class MapRecordTraceState { PREPARING, MISSING, HIDDEN, VISIBLE }

@Preview(name = "산책 목록", showBackground = true, widthDp = 390, heightDp = 280)
@Composable
private fun WalkRecordsMapListPreview() {
    DaengsTheme {
        WalkRecordsMapList(mapListPreviewRecords(), emptyList(), null, emptySet(), setOf("walk-1", "walk-2"),
            {}, {}, {}, Modifier.fillMaxSize())
    }
}

@Preview(name = "선택한 산책", showBackground = true, widthDp = 390, heightDp = 280)
@Composable
private fun SelectedWalkRecordMapPreview() {
    DaengsTheme {
        WalkRecordsMapList(mapListPreviewRecords(), emptyList(), "walk-1", emptySet(), setOf("walk-1", "walk-2"),
            {}, {}, {}, Modifier.fillMaxSize())
    }
}

@Preview(name = "지도에서 숨긴 산책", showBackground = true, widthDp = 390, heightDp = 280)
@Composable
private fun HiddenWalkRecordMapPreview() {
    DaengsTheme {
        WalkRecordsMapList(mapListPreviewRecords(), emptyList(), "walk-1", setOf("walk-1"), setOf("walk-1", "walk-2"),
            {}, {}, {}, Modifier.fillMaxSize())
    }
}

@Preview(name = "지도 흔적 없는 산책", showBackground = true, widthDp = 390, heightDp = 280)
@Composable
private fun MissingTraceWalkRecordMapPreview() {
    DaengsTheme {
        WalkRecordsMapList(mapListPreviewRecords().takeLast(1), emptyList(), "walk-3", emptySet(), emptySet(),
            {}, {}, {}, Modifier.fillMaxSize())
    }
}

@Preview(name = "지도 준비 중", showBackground = true, widthDp = 390, heightDp = 280)
@Composable
private fun PreparingWalkRecordMapPreview() {
    DaengsTheme {
        WalkRecordsMapList(mapListPreviewRecords(), emptyList(), "walk-1", emptySet(), null,
            {}, {}, {}, Modifier.fillMaxSize())
    }
}

private fun mapListPreviewRecords(): List<WalkRecord> = (1..3).map { index ->
    val id = "walk-$index"
    WalkRecord(
        summary = previewDiarySummary().copy(sessionId = id),
        title = when (index) {
            1 -> "숲길에서 천천히 걸으며 오래 머물렀던 저녁 산책"
            2 -> "비가 오기 전에 한 바퀴"
            else -> "경로는 있지만 지도 흔적이 없는 산책"
        },
        trace = if (index < 3) WalkTraceSheet(id, cells = setOf(SpatialDiaryCellId(0, 0))) else null,
    )
}
