package com.daengs.app.ui.walk.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.PetAvatar
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.walk.HistoryFilterSaver
import com.daengs.app.walk.*
import com.daengs.app.walk.records.WalkRecordsQuery
import com.daengs.app.walk.records.carerCandidates
import com.daengs.app.walk.shared.SharedWalkCarer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal enum class RecordsFilter(val title: String) {
    ALL("산책 조건"), DOGS("강아지 선택"), CARERS("보호자 선택"), PERIOD("기간 선택"), BEHAVIOR("행동으로 찾기"),
    CONDITIONS("산책 조건"),
}

// Empty saved list means all dogs. An explicit empty subset is never committed.
internal val RecordsDogIdsSaver = listSaver<Set<String>?, String>(
    save = { it?.sorted().orEmpty() }, restore = { it.toSet().takeIf { ids -> ids.isNotEmpty() } },
)

/**
 * Changes remain a draft until Apply; both views receive the same conditions.
 *
 * 보호자 선택([carers] 가 있을 때만)은 「산책별」에만 적용된다 — 「모아보기」는 내 산책 경로만 그린다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WalkRecordsConditionsSheet(
    kind: RecordsFilter, query: WalkRecordsQuery, pets: List<Pet>, today: LocalDate,
    onApply: (WalkRecordsQuery) -> Unit, onDismiss: () -> Unit,
    behavior: WalkMomentType? = null, onBehaviorApply: (WalkMomentType?) -> Unit = {},
    petsLoaded: Boolean = true, photoOf: (String) -> ImageBitmap? = { null },
    /** 보호자 후보. null 이면 보호자 선택 칸을 두지 않는다(공동 조회를 못 쓰는 화면). */
    carers: List<SharedWalkCarer>? = null,
    myId: String? = null,
    /** 보호자 조건. null 은 모든 보호자. */
    carerIds: Set<String>? = null,
    onCarersApply: (Set<String>?) -> Unit = {},
) {
    var allDogs by rememberSaveable { mutableStateOf(query.dogIds == null) }
    var draftDogs by rememberSaveable { mutableStateOf(query.dogIds.orEmpty().toList()) }
    var allCarers by rememberSaveable { mutableStateOf(carerIds == null) }
    var draftCarers by rememberSaveable { mutableStateOf(carerIds.orEmpty().toList()) }
    var draftFilter by rememberSaveable(stateSaver = HistoryFilterSaver) { mutableStateOf(query.filter) }
    var periodOpen by rememberSaveable { mutableStateOf(false) }
    var draftBehavior by rememberSaveable { mutableStateOf(behavior) }
    var extraOpen by rememberSaveable { mutableStateOf(query.filter.seasons.isNotEmpty() || query.filter.weather.isNotEmpty()) }
    val validDogs = draftDogs.filter { id -> pets.any { it.id == id } }.toSet()
    val carerChoice = carers != null && myId != null
    // 고른 강아지와 함께 돌보는 사람만 후보다. 강아지를 바꿔 후보에서 빠진 선택은 적용할 때 버린다.
    val candidates = if (carerChoice) carerCandidates(carers!!, if (allDogs) null else validDogs, myId!!) else emptyList()
    val validCarers = draftCarers.filter { id -> candidates.any { it.appUserId == id } }.toSet()
    val canApply = (kind != RecordsFilter.DOGS && kind != RecordsFilter.ALL) || allDogs || (petsLoaded && validDogs.isNotEmpty())
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("records-filter-sheet")) {
            Text(kind.title, style = MaterialTheme.typography.headlineSmall)
            Text(when (kind) {
                // 보호자 조건은 「산책별」에만 적용된다 — 보호자 선택이 있을 때는 그 차이를 적는다.
                RecordsFilter.ALL -> if (carerChoice) "보호자 조건만 산책별에 적용되고, 나머지 조건은 산책별과 모아보기에 함께 적용돼요."
                    else "산책별과 모아보기에 함께 적용돼요."
                RecordsFilter.DOGS -> "함께 보고 싶은 강아지를 골라 주세요."
                RecordsFilter.CARERS -> "산책을 다녀온 보호자를 골라 주세요. 산책별에만 적용돼요."
                RecordsFilter.PERIOD -> "산책을 시작한 날짜를 기준으로 찾아요."
                RecordsFilter.BEHAVIOR -> "선택한 강아지의 행동이 기록된 산책을 찾아요."
                RecordsFilter.CONDITIONS -> "계절과 출발 날씨로 산책을 찾아요."
            }, Modifier.padding(top = 8.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                val sections = if (kind == RecordsFilter.ALL) listOfNotNull(RecordsFilter.DOGS,
                    RecordsFilter.CARERS.takeIf { carerChoice }, RecordsFilter.PERIOD, RecordsFilter.BEHAVIOR,
                    RecordsFilter.CONDITIONS) else listOf(kind)
                sections.forEach { section ->
                if (kind == RecordsFilter.ALL && section == RecordsFilter.CONDITIONS) {
                    OutlinedTextField(draftFilter.keyword, { draftFilter = draftFilter.copy(keyword = it.take(200)) },
                        label = { Text("제목·메모 검색") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("records-search"))
                    TextButton(onClick = { extraOpen = !extraOpen }, modifier = Modifier.testTag("records-extra-conditions")) {
                        Text(if (extraOpen) "계절·날씨 접기" else "계절·날씨 더 보기")
                    }
                }
                if (kind == RecordsFilter.ALL && section != RecordsFilter.CONDITIONS) {
                    Text(section.title, Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
                }
                if (section != RecordsFilter.CONDITIONS || kind != RecordsFilter.ALL || extraOpen) when (section) {
                    RecordsFilter.ALL -> Unit
                    RecordsFilter.DOGS -> {
                        RecordsDogRow("모든 강아지", allDogs, { allDogs = !allDogs; draftDogs = emptyList() },
                            Modifier.testTag("records-dog-all"))
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        if (!petsLoaded) Text("강아지 목록을 불러오고 있어요.", Modifier.padding(vertical = 12.dp))
                        else if (pets.isEmpty()) Text("등록된 강아지가 없어요. 전체 기록은 볼 수 있어요.",
                            Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
                        pets.forEach { pet ->
                            RecordsDogRow(pet.name, !allDogs && pet.id in draftDogs, {
                                draftDogs = if (allDogs) listOf(pet.id)
                                    else if (pet.id in draftDogs) draftDogs - pet.id else draftDogs + pet.id
                                allDogs = false
                            }, Modifier.testTag("records-dog-${pet.id}"), pet, photoOf(pet.id))
                        }
                        if (!allDogs && validDogs.isEmpty()) Text("한 마리 이상 선택하거나 모든 강아지를 선택해 주세요.",
                            Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    RecordsFilter.CARERS -> {
                        // 강아지 선택과 같은 체크박스. "모든 보호자" 를 다시 누르면 개별 선택을 모두 푼다.
                        RecordsDogRow("모든 보호자", allCarers, { allCarers = true; draftCarers = emptyList() },
                            Modifier.testTag("records-carer-all"))
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        candidates.forEach { carer ->
                            RecordsDogRow(carer.displayName, !allCarers && carer.appUserId in validCarers, {
                                val current = if (allCarers) emptyList() else draftCarers.filter { it in validCarers }
                                val next = if (carer.appUserId in current) current - carer.appUserId else current + carer.appUserId
                                // 개별 선택이 모두 풀리면 "모든 보호자" 로 돌아간다.
                                allCarers = next.isEmpty()
                                draftCarers = next
                            }, Modifier.testTag("records-carer-${carer.appUserId}"))
                        }
                    }
                    RecordsFilter.PERIOD -> {
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
                                listOf(7, 30).none { draftFilter.from == today.minusDays(it - 1L) && draftFilter.through == today },
                                onClick = { periodOpen = true }, label = { Text("날짜 지정") },
                                modifier = Modifier.testTag("records-period-custom"))
                        }
                        if (draftFilter.from != null || draftFilter.through != null) Text(
                            "${draftFilter.from ?: "처음"} ~ ${draftFilter.through ?: "마지막"}",
                            Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    RecordsFilter.BEHAVIOR -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = draftBehavior == null, onClick = { draftBehavior = null },
                                label = { Text("모든 행동") }, modifier = Modifier.testTag("records-behavior-all"))
                            listOf(WalkMomentType.SNIFFING, WalkMomentType.EXCRETION, WalkMomentType.BARKING).forEach { type ->
                                FilterChip(selected = draftBehavior == type, onClick = { draftBehavior = type },
                                    label = { Text(type.label) }, modifier = Modifier.testTag("records-behavior-${type.behaviorCode}"))
                            }
                        }
                    }
                    RecordsFilter.CONDITIONS -> {
                        Text("계절", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WalkSeason.entries.forEach { season ->
                                FilterChip(selected = season in draftFilter.seasons,
                                    onClick = { draftFilter = draftFilter.copy(seasons = draftFilter.seasons.toggled(season)) },
                                    label = { Text(season.label) }, modifier = Modifier.testTag("records-season-${season.name}"))
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        Text("출발 날씨", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WalkDepartureWeather.entries.forEach { weather ->
                                FilterChip(selected = weather in draftFilter.weather,
                                    onClick = { draftFilter = draftFilter.copy(weather = draftFilter.weather.toggled(weather)) },
                                    label = { Text(weather.label) }, modifier = Modifier.testTag("records-weather-${weather.name}"))
                            }
                        }
                    }
                }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    when (kind) {
                        RecordsFilter.ALL -> {
                            allDogs = true; draftDogs = emptyList(); allCarers = true; draftCarers = emptyList()
                            draftFilter = WalkHistoryFilter(); draftBehavior = null
                        }
                        RecordsFilter.DOGS -> { allDogs = true; draftDogs = emptyList() }
                        RecordsFilter.CARERS -> { allCarers = true; draftCarers = emptyList() }
                        RecordsFilter.PERIOD -> draftFilter = draftFilter.copy(from = null, through = null)
                        RecordsFilter.BEHAVIOR -> draftBehavior = null
                        RecordsFilter.CONDITIONS -> draftFilter = draftFilter.copy(seasons = emptySet(), weather = emptySet())
                    }
                }, modifier = Modifier.testTag("records-conditions-reset")) { Text("초기화") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("records-conditions-cancel")) { Text("취소") }
                Button(enabled = canApply, onClick = {
                    val appliedCarers = if (allCarers || validCarers.isEmpty()) null else validCarers
                    when (kind) {
                        RecordsFilter.ALL -> {
                            onApply(WalkRecordsQuery(if (allDogs) null else validDogs, draftFilter)); onBehaviorApply(draftBehavior)
                            if (carerChoice) onCarersApply(appliedCarers)
                        }
                        RecordsFilter.DOGS -> onApply(query.copy(dogIds = if (allDogs) null else validDogs))
                        RecordsFilter.CARERS -> onCarersApply(appliedCarers)
                        RecordsFilter.PERIOD -> onApply(query.copy(filter = query.filter.copy(from = draftFilter.from, through = draftFilter.through)))
                        RecordsFilter.CONDITIONS -> onApply(query.copy(filter = query.filter.copy(seasons = draftFilter.seasons, weather = draftFilter.weather)))
                        RecordsFilter.BEHAVIOR -> onBehaviorApply(draftBehavior)
                    }
                    onDismiss()
                }, modifier = Modifier.testTag("records-conditions-apply")) { Text("적용") }
            }
        }
    }
    if (periodOpen) WalkRecordsPeriodDialog(draftFilter.from, draftFilter.through,
        onApply = { from, through -> draftFilter = draftFilter.copy(from = from, through = through); periodOpen = false },
        onDismiss = { periodOpen = false })
}

@Composable
private fun RecordsDogRow(
    name: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier,
    pet: Pet? = null, photo: ImageBitmap? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 60.dp)
        .toggleable(selected, role = Role.Checkbox, onValueChange = { onToggle() })
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (pet != null) {
            PetAvatar(photo, pet.breedArt, 36.dp, Modifier.clearAndSetSemantics {})
            Spacer(Modifier.width(12.dp))
        }
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Checkbox(selected, onCheckedChange = null)
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
        confirmButton = { TextButton(enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
            onClick = { onApply(state.selectedStartDateMillis?.pickerDate(), state.selectedEndDateMillis?.pickerDate()) },
            modifier = Modifier.testTag("records-date-apply")) { Text("날짜 선택") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }) {
        DateRangePicker(state, modifier = Modifier.heightIn(max = 460.dp),
            title = { Text("산책 날짜 범위", Modifier.padding(20.dp)) })
    }
}

private fun Long.pickerDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value

@Preview(showBackground = true, widthDp = 320, heightDp = 680)
@Composable private fun RecordsDogsPreview() {
    DaengsTheme { WalkRecordsConditionsSheet(RecordsFilter.DOGS, WalkRecordsQuery(),
        recordsPreviewPets(), LocalDate.of(2026, 9, 11), {}, {}) }
}
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable private fun RecordsBehaviorPreview() {
    DaengsTheme { WalkRecordsConditionsSheet(RecordsFilter.BEHAVIOR, WalkRecordsQuery(),
        emptyList(), LocalDate.of(2026, 9, 11), {}, {}) }
}
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable private fun RecordsConditionsPreview() {
    DaengsTheme { WalkRecordsConditionsSheet(RecordsFilter.ALL, WalkRecordsQuery(),
        emptyList(), LocalDate.of(2026, 9, 11), {}, {}) }
}
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable private fun RecordsCarersPreview() {
    DaengsTheme { WalkRecordsConditionsSheet(RecordsFilter.CARERS, WalkRecordsQuery(),
        recordsPreviewPets(), LocalDate.of(2026, 9, 11), {}, {},
        carers = listOf(SharedWalkCarer("me", "롱롱씨 메인", true, listOf("dog-0")),
            SharedWalkCarer("u2", "키키", false, listOf("dog-0"))), myId = "me") }
}
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable private fun RecordsPeriodPreview() {
    DaengsTheme { WalkRecordsConditionsSheet(RecordsFilter.PERIOD, WalkRecordsQuery(),
        emptyList(), LocalDate.of(2026, 9, 11), {}, {}) }
}
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable private fun RecordsDatePreview() { DaengsTheme { WalkRecordsPeriodDialog(null, null, { _, _ -> }, {}) } }

internal fun recordsPreviewPets(): List<Pet> = listOf("두부", "콩이", "보리", "호두", "이름이 아주 긴 우리집 설기").mapIndexed { i, name ->
    Pet("dog-$i", name, "", null, null, null, null, null, null, false)
}
