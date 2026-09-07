package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal val HistoryFilterSaver = listSaver<WalkHistoryFilter, String>(
    save = { listOf(it.keyword, it.from?.toString().orEmpty(), it.through?.toString().orEmpty(),
        it.seasons.sortedBy { s -> s.ordinal }.joinToString(",") { s -> s.name },
        it.weather.sortedBy { w -> w.ordinal }.joinToString(",") { w -> w.name }) },
    restore = { WalkHistoryFilter(it[0], it[1].takeIf(String::isNotEmpty)?.let(LocalDate::parse),
        it[2].takeIf(String::isNotEmpty)?.let(LocalDate::parse),
        it[3].split(',').filter(String::isNotEmpty).map(WalkSeason::valueOf).toSet(),
        it[4].split(',').filter(String::isNotEmpty).map(WalkDepartureWeather::valueOf).toSet()) },
)

@Composable
internal fun WalkHistorySearchLayout(
    filter: WalkHistoryFilter, onFilter: (WalkHistoryFilter) -> Unit,
    loading: Boolean, error: String?, empty: Boolean, hasAny: Boolean,
    onRetry: () -> Unit, onReset: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier) {
        WalkHistorySearchControls(filter, onFilter, onReset)
        HorizontalDivider(color = TextMuted.copy(alpha = .15f))
        when {
            error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("다시 시도") }
            }
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("산책 기록을 찾고 있어요.", color = TextMuted)
            }
            empty -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (hasAny) "조건에 맞는 산책이 없어요." else "아직 산책 기록이 없어요.",
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(if (hasAny) "다른 검색어나 조건으로 찾아보세요." else "산책을 마치면 동선과 일기가 이곳에 쌓여요.",
                    color = TextMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                if (hasAny) TextButton(onClick = onReset) { Text("전체 기록 보기") }
            }
            else -> content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WalkHistorySearchControls(filter: WalkHistoryFilter, onFilter: (WalkHistoryFilter) -> Unit,
    onReset: () -> Unit, modifier: Modifier = Modifier) {
    var menu by rememberSaveable { mutableStateOf<String?>(null) }
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = PinkFaint, selectedLabelColor = DaengPinkDeep)
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        OutlinedTextField(value = filter.keyword, onValueChange = { onFilter(filter.copy(keyword = it.take(200))) },
            label = { Text("제목·메모 검색") }, singleLine = true,
            trailingIcon = if (filter.keyword.isNotEmpty()) ({ TextButton(onClick = { onFilter(filter.copy(keyword = "")) }) { Text("지우기") } }) else null,
            modifier = Modifier.fillMaxWidth().testTag("history-search"))
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter.from != null || filter.through != null, onClick = { menu = "period" },
                label = { Text("기간 ▾") }, colors = chipColors, modifier = Modifier.testTag("history-period"))
            Box {
                FilterChip(selected = filter.seasons.isNotEmpty(), onClick = { menu = "season" },
                    label = { Text("계절${if (filter.seasons.isEmpty()) "" else " ${filter.seasons.size}"} ▾") }, colors = chipColors)
                DropdownMenu(expanded = menu == "season", onDismissRequest = { menu = null }) {
                    WalkSeason.entries.forEach { season ->
                        DropdownMenuItem(text = { Text(season.label) },
                            leadingIcon = { Checkbox(checked = season in filter.seasons, onCheckedChange = null) },
                            onClick = { onFilter(filter.copy(seasons = filter.seasons.toggled(season))) })
                    }
                    DropdownMenuItem(text = { Text("닫기") }, onClick = { menu = null })
                }
            }
            Box {
                FilterChip(selected = filter.weather.isNotEmpty(), onClick = { menu = "weather" },
                    label = { Text("출발 날씨${if (filter.weather.isEmpty()) "" else " ${filter.weather.size}"} ▾") }, colors = chipColors)
                DropdownMenu(expanded = menu == "weather", onDismissRequest = { menu = null }) {
                    WalkDepartureWeather.entries.forEach { weather ->
                        DropdownMenuItem(text = { Text(weather.label) },
                            leadingIcon = { Checkbox(checked = weather in filter.weather, onCheckedChange = null) },
                            onClick = { onFilter(filter.copy(weather = filter.weather.toggled(weather))) })
                    }
                    DropdownMenuItem(text = { Text("닫기") }, onClick = { menu = null })
                }
            }
        }
        if (filter.active) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val labels = buildList {
                    if (filter.from != null || filter.through != null) add("${filter.from ?: "처음"} ~ ${filter.through ?: "마지막"}")
                    addAll(filter.seasons.sortedBy { it.ordinal }.map { it.label })
                    addAll(filter.weather.sortedBy { it.ordinal }.map { it.label })
                }
                Text(labels.joinToString(" · ").ifEmpty { "제목·메모에서 검색" }, Modifier.weight(1f),
                    color = TextMuted, style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onReset) { Text("초기화") }
            }
        }
    }
    if (menu == "period") {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = filter.from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = filter.through?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { menu = null },
            confirmButton = { TextButton(enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    onFilter(filter.copy(from = state.selectedStartDateMillis?.pickerDate(), through = state.selectedEndDateMillis?.pickerDate()))
                    menu = null
                }) { Text("적용") } },
            dismissButton = { Row {
                TextButton(onClick = { onFilter(filter.copy(from = null, through = null)); menu = null }) { Text("전체 기간") }
                TextButton(onClick = { menu = null }) { Text("취소") }
            } }) {
            DateRangePicker(state = state, modifier = Modifier.heightIn(max = 460.dp),
                title = { Text("산책 날짜 범위", Modifier.padding(20.dp)) })
        }
    }
}

private fun Long.pickerDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun EmptyHistorySearchPreview() {
    DaengsTheme { var filter by remember { mutableStateOf(WalkHistoryFilter()) }
        WalkHistorySearchLayout(filter, { filter = it }, false, null, true, false, {},
            { filter = WalkHistoryFilter() }, Modifier.fillMaxSize()) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun FilteredHistorySearchPreview() {
    DaengsTheme { var filter by remember { mutableStateOf(WalkHistoryFilter(seasons = setOf(WalkSeason.AUTUMN))) }
        WalkHistorySearchLayout(filter, { filter = it }, false, null, false, true, {},
            { filter = WalkHistoryFilter() }, Modifier.fillMaxSize()) {
            WalkHistoryPageContent(listOf(previewDiarySummary()), 1, false, false, {}, {}, {}, modifier = Modifier.weight(1f))
        } }
}
