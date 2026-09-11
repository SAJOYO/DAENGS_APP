package com.daengs.app.ui.walk.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.records.WalkRecordsQuery
import java.time.LocalDate

/** One compact header shared by both views. Closing search never clears its committed keyword. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WalkRecordsHeader(
    query: WalkRecordsQuery, pets: List<Pet>, overview: Boolean, behavior: WalkMomentType?,
    onBack: () -> Unit, onOverview: (Boolean) -> Unit, onKeyword: (String) -> Unit,
    onFilter: (RecordsFilter) -> Unit, onReset: () -> Unit, onClearBehavior: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val closeSearch = { focus.clearFocus(); searchOpen = false }
    BackHandler(enabled = searchOpen) { closeSearch() }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().testTag("records-header")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { focus.clearFocus(); onBack() }, modifier = Modifier.semantics { contentDescription = "뒤로" }) {
                    DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(24.dp).rotate(180f), MaterialTheme.colorScheme.onSurface)
                }
                Text("산책 기록", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { if (searchOpen) closeSearch() else searchOpen = true },
                    modifier = Modifier.testTag("records-search-toggle").semantics {
                        contentDescription = if (searchOpen) "검색 접기" else "제목과 메모 검색"
                    }) {
                    if (searchOpen) DaengsIconView(DaengsIcon.Close, Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface)
                    else RecordsSearchIcon()
                }
                val conditionCount = query.filter.seasons.size + query.filter.weather.size
                FilledIconButton(onClick = { onFilter(RecordsFilter.CONDITIONS) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (conditionCount > 0) PinkFaint
                            else MaterialTheme.colorScheme.surface),
                    modifier = Modifier.testTag("records-conditions").semantics {
                        contentDescription = "계절과 출발 날씨 조건"
                        stateDescription = if (conditionCount > 0) "${conditionCount}개 적용 중" else "적용한 조건 없음"
                    }) { RecordsConditionsIcon() }
            }
            if (searchOpen) OutlinedTextField(query.filter.keyword, { onKeyword(it.take(200)) },
                label = { Text("제목·메모 검색") }, singleLine = true,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = if (query.filter.keyword.isNotEmpty()) ({
                    IconButton(onClick = { onKeyword("") }, modifier = Modifier.semantics { contentDescription = "검색어 지우기" }) {
                        DaengsIconView(DaengsIcon.Close, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }) else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).testTag("records-search"))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(false to "산책별", true to "모아보기").forEach { (isOverview, label) ->
                    Tab(selected = overview == isOverview, onClick = { focus.clearFocus(); onOverview(isOverview) },
                        modifier = Modifier.weight(1f).testTag(if (isOverview) "records-view-overview" else "records-view-walks")) {
                        Text(label, Modifier.padding(vertical = 13.dp), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = if (overview == isOverview) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(thickness = 3.dp, color = if (overview == isOverview) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface)
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                val dogLabel = when (val ids = query.dogIds) {
                    null -> "모든 강아지"
                    else -> if (ids.size == 1) pets.firstOrNull { it.id in ids }?.name ?: "선택한 강아지" else "${ids.size}마리"
                }
                RecordsFilterChip(dogLabel, query.dogIds != null, "records-dog-filter", { onFilter(RecordsFilter.DOGS) })
                val periodLabel = when {
                    query.filter.from == null && query.filter.through == null -> "전체 기간"
                    query.filter.through == today && query.filter.from == today.minusDays(6) -> "최근 7일"
                    query.filter.through == today && query.filter.from == today.minusDays(29) -> "최근 30일"
                    else -> "날짜 지정"
                }
                RecordsFilterChip(periodLabel, query.filter.from != null || query.filter.through != null,
                    "records-period-filter", { onFilter(RecordsFilter.PERIOD) })
                RecordsFilterChip(behavior?.label ?: "행동", behavior != null,
                    "records-behavior-filter", { onFilter(RecordsFilter.BEHAVIOR) })
            }
            // Details are only present for active filters; the empty screen keeps a short header.
            val labels = buildList {
                if (!searchOpen && query.filter.keyword.isNotBlank()) add("검색: ${query.filter.keyword}")
                if (periodLabelIsCustom(query, today)) add("${query.filter.from ?: "처음"} ~ ${query.filter.through ?: "마지막"}")
                addAll(query.filter.seasons.sortedBy { it.ordinal }.map { it.label })
                addAll(query.filter.weather.sortedBy { it.ordinal }.map { "출발 ${it.label}" })
                if (behavior != null && !overview) add("${behavior.label} · 모아보기에 적용 중")
            }
            if (query.dogIds != null || query.filter.active || behavior != null) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(labels.joinToString(" · "), Modifier.weight(1f).testTag("records-active-filters"),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (behavior != null) TextButton(onClick = onClearBehavior, modifier = Modifier.testTag("records-behavior-clear")) { Text("행동 해제") }
                    TextButton(onClick = onReset, modifier = Modifier.testTag("records-reset")) { Text("초기화") }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun periodLabelIsCustom(query: WalkRecordsQuery, today: LocalDate): Boolean =
    (query.filter.from != null || query.filter.through != null) &&
        listOf(7, 30).none { query.filter.from == today.minusDays(it - 1L) && query.filter.through == today }

@Composable
private fun RecordsFilterChip(label: String, active: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(selected = active, onClick = onClick, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PinkFaint, selectedLabelColor = DaengPinkDeep), shape = RoundedCornerShape(24.dp),
        label = { Text(label, Modifier.widthIn(max = 120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium) },
        trailingIcon = { DaengsIconView(DaengsIcon.CaretDown, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag))
}

@Composable
private fun RecordsSearchIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(22.dp)) {
        drawCircle(color, size.minDimension * .29f, Offset(size.width * .42f, size.height * .42f), style = Stroke(2.dp.toPx()))
        drawLine(color, Offset(size.width * .64f, size.height * .64f), Offset(size.width * .90f, size.height * .90f), 2.dp.toPx())
    }
}

@Composable
private fun RecordsConditionsIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(22.dp)) {
        listOf(.25f to .35f, .5f to .7f, .75f to .45f).forEach { (y, x) ->
            drawLine(color, Offset(size.width * .1f, size.height * y),
                Offset(size.width * (x - .1f), size.height * y), 1.8.dp.toPx())
            drawLine(color, Offset(size.width * (x + .1f), size.height * y),
                Offset(size.width * .9f, size.height * y), 1.8.dp.toPx())
            drawCircle(color, size.width * .09f, Offset(size.width * x, size.height * y), style = Stroke(1.8.dp.toPx()))
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun RecordsHeaderPreview() {
    DaengsTheme { WalkRecordsHeader(WalkRecordsQuery(), recordsPreviewPets(), false, null, {}, {}, {}, {}, {}, {}) }
}
