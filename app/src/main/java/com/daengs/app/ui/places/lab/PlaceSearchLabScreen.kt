package com.daengs.app.ui.places.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.place.*
import com.daengs.app.map.features.places.categoryLabel
import com.daengs.app.map.features.places.placeMarkerId
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.places.PlaceSearchStyle
import kotlinx.serialization.json.*

/** 상태와 이벤트만 받아 Preview·fixture·향후 실제 API에서 같은 화면을 사용한다. */
@Composable
fun PlaceSearchLabScreen(
    state: PlaceSearchLabState,
    onEdit: (String) -> Unit = {}, onAi: () -> Unit = {}, onSubmit: () -> Unit = {},
    onCategory: (PlaceKind?) -> Unit = {}, onParking: (Boolean) -> Unit = {},
    onDog: (String) -> Unit = {}, onToggle: (PlaceKey) -> Unit = {}, onRetry: () -> Unit = {},
    onAction: (String) -> Unit = {},
    live: Boolean = false,
    onBack: (() -> Unit)? = null,
    onRadius: (Int) -> Unit = {},
    onRefreshProfiles: () -> Unit = {},
    cardActions: (@Composable (PlaceSearchHit) -> Unit)? = null,
    categoryContent: (@Composable () -> Unit)? = null,
    conditionContent: (@Composable () -> Unit)? = null,
    answerContent: (@Composable () -> Unit)? = null,
    onSearchFilters: (() -> Unit)? = null,
    searchFilterCount: Int = 0,
    aiConnected: Boolean = false,
    showAiToggle: Boolean = true,
    emptyMessage: String = "검색 결과가 없어요.",
    showRetry: Boolean = true,
    resultLabel: String = state.applied.kind?.let(::categoryLabel) ?: "전체",
    map: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(DaengsColors.SurfaceMuted)) },
) {
    var profiles by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    val kinds = remember { listOf<PlaceKind?>(null, PlaceKind.CAFE, PlaceKind.RESTAURANT) + PlaceKind.entries.filter { it != PlaceKind.CAFE && it != PlaceKind.RESTAURANT } }
    PlaceResultsScaffold(
        detailOpen = state.expanded != null,
        header = {
            PlaceSearchHeader(state.draft,
                if (state.aiMode) "AI에게 원하는 장소를 말해보세요" else if (live) "장소명 검색" else "장소명·주소 검색",
                state.aiMode, onEdit, onSubmit, onAi, onBack, onSearchFilters, searchFilterCount, showAiToggle)
            if (state.aiMode && !aiConnected) Text("AI 조건 검색 · 아직 미연결", fontSize = 11.sp)
            answerContent?.invoke()
        },
        categories = {
            if (categoryContent != null) categoryContent() else LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(kinds.chunked(2)) { pair ->
                    Column {
                        pair.forEach { kind ->
                            Surface(
                                color = if (state.applied.kind == kind) DaengsColors.BrandPrimarySoft else DaengsColors.Surface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.width(62.dp).height(55.dp).clickable { onCategory(kind) },
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(categorySymbol(kind), fontSize = 19.sp)
                                    Text(kind?.let(::categoryLabel) ?: "전체보기", fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            if (conditionContent != null) {
                Spacer(Modifier.height(8.dp))
                conditionContent()
            }
        },
        controls = {
            val count = when (state.phase) { LabPhase.RESULTS, LabPhase.EMPTY -> "${state.hits.size}곳${if (state.truncated) "+" else ""}"; LabPhase.UNSAMPLED -> "미수집"; else -> "—" }
            PlaceResultControls("$resultLabel $count",
                state.selectedDogIds.joinToString("·") { if (live) state.profileNames[it] ?: "반려견" else if (it == "demo-bori") "보리" else "초코" }.ifEmpty { "반려견 선택" } + " ▾",
                state.selectedDogIds.isNotEmpty(), state.applied.radiusMeters, state.applied.parkingFirst,
                { profiles = true; if (live) onRefreshProfiles() }, { filters = true }, { onParking(!state.applied.parkingFirst) })
        },
        map = {
            Box(Modifier.fillMaxSize()) {
                map()
                PlaceFloatingNotices(listOfNotNull(state.notice, state.profileMessage.takeIf { live }).distinct(),
                    Modifier.align(Alignment.TopCenter).padding(start = 16.dp, end = 16.dp, top = 52.dp))
            }
        },
        results = {
            if (state.truncated) item { Text("일부 업종은 결과가 더 있어요. 반경을 줄여 확인하세요.", Modifier.padding(16.dp), fontSize = 11.sp) }
            if (state.applied.kind == null && !live) item { Text("전체보기 · 카페·음식점 표본만 포함", Modifier.padding(16.dp), fontSize = 11.sp) }
            if (state.phase == LabPhase.RESULTS) {
                items(state.hits, key = { placeMarkerId(it.place.key) }) { hit ->
                    PlaceResultRow(hit, state.selected == hit.place.key, { onToggle(hit.place.key) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = PlaceSearchStyle.Border)
                }
            } else item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(when (state.phase) {
                        LabPhase.LOADING -> "찾는 중…"
                        LabPhase.EMPTY -> emptyMessage
                        LabPhase.ERROR -> state.errorText ?: "검토 데이터를 읽지 못했어요."
                        LabPhase.PERMISSION -> "위치 권한이 필요해요."
                        LabPhase.UNSAMPLED -> "이 종류는 검토판에 수집하지 않았어요."
                        else -> ""
                    }, fontSize = 13.sp)
                    if (showRetry && (state.phase == LabPhase.ERROR || state.phase == LabPhase.PERMISSION)) TextButton(onClick = onRetry) { Text("다시 확인") }
                }
            }
        },
    )
    state.hits.firstOrNull { it.place.key == state.expanded }?.let { hit ->
        PlaceDetailSheet(hit, { onToggle(hit.place.key) }, onAction, cardActions, state.profileNames)
    }
    if (profiles && live) AlertDialog(onDismissRequest = { profiles = false }, title = { Text("함께 갈 반려견") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("선택하지 않으면 반려견 조건을 적용하지 않아요.", fontSize = 12.sp)
            state.profileMessage?.let { Text(it, fontSize = 12.sp) }
            state.profileNames.forEach { (id, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(id in state.selectedDogIds, { onDog(id) }, enabled = state.profilesReady,
                        modifier = Modifier.semantics { contentDescription = "$name 선택" })
                    Text(if (state.profileNames.values.count { it == name } > 1) "$name · ${id.takeLast(4)}" else name)
                }
            }
            TextButton(onClick = onRefreshProfiles) { Text("목록 새로고침") }
        }
    }, confirmButton = { TextButton(onClick = { profiles = false }) { Text("완료") } })
    if (profiles && !live) AlertDialog(onDismissRequest = { profiles = false }, title = { Text("함께 갈 반려견") }, text = {
        Column {
            Text("가상 프로필 · 검색 미연동", fontSize = 12.sp)
            listOf("demo-bori" to "보리", "demo-choco" to "초코").forEach { (id, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(id in state.selectedDogIds, { onDog(id) }); Text(name) }
            }
        }
    }, confirmButton = { TextButton(onClick = { profiles = false }) { Text("완료") } })
    if (filters) AlertDialog(onDismissRequest = { filters = false }, title = { Text(if (live) "검색 반경" else "검색 조건") }, text = {
        Column {
        if (live) listOf(1000, 3000, 5000, 10000, 20000).forEach { meters ->
            TextButton(onClick = { onRadius(meters); filters = false }) { Text("${meters / 1000}km${if (state.applied.radiusMeters == meters) " ✓" else ""}") }
        }
        Text(if (live && onSearchFilters != null) "‘주차 우선’은 정렬 설정이에요. 필수 조건은 검색창의 필터에서 확인하세요."
            else if (live) "주차는 필수 조건이 아닌 우선 정렬입니다. 실내 동반 조건은 카드에서 확인하세요."
            else "반경 3km · 대형견 30kg·3세의 저장 응답입니다.\n실내 동반 등 추가 제한은 장소 상세에서 확인하세요.")
        }
    }, confirmButton = { TextButton(onClick = { filters = false }) { Text("완료") } })
}

private fun categorySymbol(kind: PlaceKind?): String = when (kind) {
    null -> "▦"; PlaceKind.CAFE -> "☕"; PlaceKind.RESTAURANT -> "🍽"; PlaceKind.HOSPITAL -> "✚"
    PlaceKind.PHARMACY -> "💊"; PlaceKind.PET_SHOP -> "🐾"; PlaceKind.GROOMING -> "✂"
    PlaceKind.SHOPPING -> "🛍"; PlaceKind.ETC -> "⋯"; else -> "⌂"
}

@Composable
private fun PlaceResultControls(count: String, dogLabel: String, dogSelected: Boolean, radiusMeters: Int,
    parking: Boolean, onDog: () -> Unit, onRadius: () -> Unit, onParking: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val availableWidth = maxWidth
        val singleRow = availableWidth >= 340.dp
        val controls: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier.semantics { contentDescription = "검색 조건" },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                PlaceConditionChip(dogLabel, dogSelected, onDog, Modifier.weight(1f, fill = false))
                PlaceConditionChip("반경 ${radiusMeters / 1000}km ▾", false, onRadius)
                PlaceConditionChip("주차 우선", parking, onParking, Modifier.semantics {
                    stateDescription = if (parking) "켜짐" else "꺼짐"
                })
            }
        }
        if (singleRow) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(count, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                controls(Modifier.widthIn(max = availableWidth - 84.dp))
            }
        } else {
            Column {
                Text(count, Modifier.fillMaxWidth().padding(top = 6.dp), fontSize = 13.sp)
                controls(Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun ResultControlsPreview() {
    DaengsTheme { PlaceResultControls("카페 12곳", "반려견 선택 ▾", false, 3000, false, {}, {}, {}) }
}

@Composable
private fun PlaceConditionChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(selected = selected, onClick = onClick, modifier = modifier,
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) DaengsColors.BrandPrimary else PlaceSearchStyle.Border),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = DaengsColors.Surface,
            labelColor = DaengsColors.TextPrimary,
            selectedContainerColor = DaengsColors.BrandPrimarySoft,
            selectedLabelColor = DaengsColors.TextPrimary,
        ),
    )
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun PlaceConditionsPreview() {
    DaengsTheme {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceConditionChip("반려견 선택 ▾", false, {}, Modifier.weight(1f, fill = false))
            PlaceConditionChip("반경 3km ▾", false, {})
            PlaceConditionChip("주차 우선", true, {})
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SearchLabPreview() { DaengsTheme { PlaceSearchLabScreen(PlaceSearchLabState(phase = LabPhase.EMPTY)) } }

internal fun PreviewPlaceHit(): PlaceSearchHit {
    val key = PlaceKey("preview", "cafe")
    val hit = PlaceSearchHit(
        PlaceResult(key, emptyList(), "검토 카페", com.daengs.app.location.GeoPoint(37.54, 127.05), 1300,
            PlaceMatch(key, PlaceKind.CAFE), emptyList(),
            PlaceFacts(null, null, null, null, null, null, null, null,
                PetAccessFacts(buildJsonObject { put("restrictions", "야외만 동반 가능"); put("size", "모두 가능") }, true, false, null, "any", null), null),
            emptyMap(), com.daengs.app.map.layers.places.FacilityIconGroup.ETC),
        PlaceEvaluations(null),
    )
    return hit
}
