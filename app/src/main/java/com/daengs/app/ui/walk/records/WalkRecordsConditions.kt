package com.daengs.app.ui.walk.records

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.DogChip
import com.daengs.app.ui.walk.HistoryFilterSaver
import com.daengs.app.walk.WalkDepartureWeather
import com.daengs.app.walk.WalkHistoryFilter
import com.daengs.app.walk.WalkSeason
import com.daengs.app.walk.records.WalkRecordsQuery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Both views use this one committed query. The condition sheet edits a separate draft. */
@Composable
internal fun WalkRecordsConditions(
    query: WalkRecordsQuery,
    pets: List<Pet>,
    onKeyword: (String) -> Unit,
    onOpenConditions: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query.filter.keyword,
                onValueChange = { onKeyword(it.take(200)) },
                label = { Text("제목·메모 검색") },
                singleLine = true,
                trailingIcon = if (query.filter.keyword.isNotEmpty()) ({
                    TextButton(onClick = { onKeyword("") }) { Text("지우기") }
                }) else null,
                modifier = Modifier.weight(1f).testTag("records-search"),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onOpenConditions, modifier = Modifier.testTag("records-conditions")) {
                Text("조건")
            }
        }
        val labels = conditionLabels(query, pets)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (labels.isEmpty()) {
                    Text("전체 산책", Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else labels.forEach { label ->
                    FilterChip(selected = true, onClick = onOpenConditions, label = { Text(label) })
                }
            }
            if (query.dogId != null || query.filter.active) {
                TextButton(onClick = onReset, modifier = Modifier.testTag("records-reset")) { Text("초기화") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WalkRecordsConditionsSheet(
    query: WalkRecordsQuery,
    pets: List<Pet>,
    today: LocalDate,
    onApply: (WalkRecordsQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftDogId by rememberSaveable { mutableStateOf(query.dogId) }
    var draftFilter by rememberSaveable(stateSaver = HistoryFilterSaver) { mutableStateOf(query.filter) }
    var periodOpen by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("산책 고르기", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("records-conditions-cancel")) { Text("취소") }
            }
            Text("두 보기에서 같은 조건을 사용해요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(20.dp))
                Text("산책 범위", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Text("강아지", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = draftDogId == null, onClick = { draftDogId = null },
                        label = { Text("전체") }, modifier = Modifier.testTag("records-dog-all"))
                    pets.forEach { pet ->
                        DogChip(pet.name, pet, draftDogId == pet.id,
                            onClick = { draftDogId = pet.id.takeIf { draftDogId != it } },
                            modifier = Modifier.testTag("records-dog-${pet.id}"))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("기간", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draftFilter.from == null && draftFilter.through == null,
                        onClick = { draftFilter = draftFilter.copy(from = null, through = null) },
                        label = { Text("전체 기간") }, modifier = Modifier.testTag("records-period-all"))
                    listOf(7, 30).forEach { days ->
                        FilterChip(selected = draftFilter.from == today.minusDays(days - 1L) && draftFilter.through == today,
                            onClick = { draftFilter = draftFilter.copy(from = today.minusDays(days - 1L), through = today) },
                            label = { Text("최근 ${days}일") }, modifier = Modifier.testTag("records-period-$days"))
                    }
                    FilterChip(selected = (draftFilter.from != null || draftFilter.through != null) &&
                        listOf(7, 30).none { days ->
                            draftFilter.from == today.minusDays(days - 1L) && draftFilter.through == today
                        },
                        onClick = { periodOpen = true }, label = { Text("날짜 지정") },
                        modifier = Modifier.testTag("records-period-custom"))
                }
                if (draftFilter.from != null || draftFilter.through != null) {
                    Text(periodLabel(draftFilter), style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(Modifier.padding(vertical = 18.dp))
                Text("산책 조건", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Text("계절", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalkSeason.entries.forEach { season ->
                        FilterChip(selected = season in draftFilter.seasons,
                            onClick = { draftFilter = draftFilter.copy(seasons = draftFilter.seasons.toggled(season)) },
                            label = { Text(season.label) }, modifier = Modifier.testTag("records-season-${season.name}"))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("출발 날씨", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalkDepartureWeather.entries.forEach { weather ->
                        FilterChip(selected = weather in draftFilter.weather,
                            onClick = { draftFilter = draftFilter.copy(weather = draftFilter.weather.toggled(weather)) },
                            label = { Text(weather.label) }, modifier = Modifier.testTag("records-weather-${weather.name}"))
                    }
                }
                Text("날씨는 산책을 출발할 때의 기록이에요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    draftDogId = null
                    draftFilter = WalkHistoryFilter(keyword = query.filter.keyword)
                }, modifier = Modifier.testTag("records-conditions-reset")) { Text("조건 초기화") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(WalkRecordsQuery(draftDogId, draftFilter)) },
                    modifier = Modifier.testTag("records-conditions-apply")) { Text("적용") }
            }
        }
    }
    if (periodOpen) {
        WalkRecordsPeriodDialog(draftFilter.from, draftFilter.through,
            onApply = { from, through -> draftFilter = draftFilter.copy(from = from, through = through); periodOpen = false },
            onDismiss = { periodOpen = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalkRecordsPeriodDialog(
    from: LocalDate?, through: LocalDate?,
    onApply: (LocalDate?, LocalDate?) -> Unit, onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = through?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(
            enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
            onClick = { onApply(state.selectedStartDateMillis?.pickerDate(), state.selectedEndDateMillis?.pickerDate()) },
            modifier = Modifier.testTag("records-date-apply"),
        ) { Text("날짜 선택") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }) {
        DateRangePicker(state, modifier = Modifier.heightIn(max = 460.dp),
            title = { Text("산책 날짜 범위", Modifier.padding(20.dp)) })
    }
}

private fun conditionLabels(query: WalkRecordsQuery, pets: List<Pet>): List<String> = buildList {
    query.dogId?.let { id -> add(pets.firstOrNull { it.id == id }?.name ?: "선택한 강아지") }
    if (query.filter.from != null || query.filter.through != null) add(periodLabel(query.filter))
    addAll(query.filter.seasons.sortedBy { it.ordinal }.map { it.label })
    addAll(query.filter.weather.sortedBy { it.ordinal }.map { "출발 ${it.label}" })
}

private fun periodLabel(filter: WalkHistoryFilter) = "${filter.from ?: "처음"} ~ ${filter.through ?: "마지막"}"
private fun Long.pickerDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun WalkRecordsConditionsPreview() {
    DaengsTheme { WalkRecordsConditions(WalkRecordsQuery(filter = WalkHistoryFilter(seasons = setOf(WalkSeason.AUTUMN))),
        emptyList(), {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WalkRecordsConditionsSheetPreview() {
    DaengsTheme { WalkRecordsConditionsSheet(WalkRecordsQuery(), emptyList(), LocalDate.of(2026, 9, 10), {}, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WalkRecordsPeriodDialogPreview() {
    DaengsTheme { WalkRecordsPeriodDialog(null, null, { _, _ -> }, {}) }
}
