package com.daengs.app.ui.places.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.place.*
import com.daengs.app.map.features.places.categoryLabel
import com.daengs.app.map.features.places.toCardPresentation
import com.daengs.app.map.features.places.placeMarkerId
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
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
    onRadius: (Int) -> Unit = {},
    onRefreshProfiles: () -> Unit = {},
    cardActions: (@Composable (PlaceSearchHit) -> Unit)? = null,
    categoryContent: (@Composable () -> Unit)? = null,
    conditionContent: (@Composable () -> Unit)? = null,
    aiConnected: Boolean = false,
    emptyMessage: String = "검색 결과가 없어요.",
    showRetry: Boolean = true,
    resultLabel: String = state.applied.kind?.let(::categoryLabel) ?: "전체",
    map: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(DaengsColors.SurfaceMuted)) },
) {
    var profiles by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    val kinds = remember { listOf<PlaceKind?>(null, PlaceKind.CAFE, PlaceKind.RESTAURANT) + PlaceKind.entries.filter { it != PlaceKind.CAFE && it != PlaceKind.RESTAURANT } }
    Column(Modifier.fillMaxSize().background(DaengsColors.AppBackground).safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().background(DaengsColors.Surface).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.draft, onValueChange = onEdit, modifier = Modifier.weight(1f),
                    placeholder = { Text(if (state.aiMode) "원하는 동반 조건" else if (live) "장소명 검색" else "장소명·주소 검색", fontSize = 12.sp) },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                )
                IconButton(onClick = onAi, modifier = Modifier.semantics {
                    contentDescription = "AI 조건 검색 전환"
                    stateDescription = if (state.aiMode) "켜짐" else "꺼짐"
                }) { Text("🤖", color = if (state.aiMode) DaengsColors.BrandPrimary else DaengsColors.TextPrimary) }
                IconButton(onClick = onSubmit, modifier = Modifier.semantics { contentDescription = "검색 실행" }) { Text("↑") }
            }
            if (state.aiMode && !aiConnected) Text("AI 조건 검색 · 아직 미연결", fontSize = 11.sp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { profiles = true; if (live) onRefreshProfiles() }, modifier = Modifier.weight(1f)) {
                    Text("🐾 " + state.selectedDogIds.joinToString("·") { if (live) state.profileNames[it] ?: "반려견" else if (it == "demo-bori") "보리" else "초코" }.ifEmpty { "반려견" } + " ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { filters = true }) { Text("${state.applied.radiusMeters / 1000}km ▾", fontSize = 11.sp) }
                TextButton(onClick = { onParking(!state.applied.parkingFirst) }) { Text(if (state.applied.parkingFirst) "주차 우선 ✓" else "주차 우선", fontSize = 11.sp) }
                IconButton(onClick = { filters = true }, modifier = Modifier.semantics { contentDescription = "검색 조건" }) { Text("⚙") }
            }
            conditionContent?.invoke()
            state.notice?.let { Text(it, fontSize = 11.sp, maxLines = 3) }
            if (live) state.profileMessage?.let { Text(it, fontSize = 11.sp) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { map() }
        Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = DaengsColors.Surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(4.dp).background(DaengsColors.BorderNeutral, RoundedCornerShape(4.dp)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val count = when (state.phase) { LabPhase.RESULTS, LabPhase.EMPTY -> "${state.hits.size}곳${if (state.truncated) "+" else ""}"; LabPhase.UNSAMPLED -> "미수집"; else -> "—" }
                    Text("$resultLabel $count", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = { onParking(!state.applied.parkingFirst) }) { Text(if (state.applied.parkingFirst) "주차 우선 ▾" else "가까운 순 ▾", fontSize = 11.sp) }
                }
                if (state.truncated) Text("일부 업종은 결과가 더 있어요. 반경을 줄여 확인하세요.", Modifier.padding(horizontal = 16.dp), fontSize = 10.sp)
                if (state.applied.kind == null && !live) Text("전체보기 · 카페·음식점 표본만 포함", Modifier.padding(horizontal = 16.dp), fontSize = 10.sp)
                if (state.phase == LabPhase.RESULTS) {
                    val list = rememberLazyListState()
                    LaunchedEffect(state.selected, state.hits) {
                        val index = state.hits.indexOfFirst { it.place.key == state.selected }
                        if (index >= 0) list.animateScrollToItem(index)
                    }
                    LazyRow(state = list, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.hits, key = { placeMarkerId(it.place.key) }) { hit ->
                            PlaceDrawerCard(hit, state.expanded == hit.place.key, state.selected == hit.place.key, { onToggle(hit.place.key) }, onAction, cardActions, state.profileNames)
                        }
                    }
                } else {
                    Text(when (state.phase) {
                        LabPhase.LOADING -> "찾는 중…"
                        LabPhase.EMPTY -> emptyMessage
                        LabPhase.ERROR -> state.errorText ?: "검토 데이터를 읽지 못했어요."
                        LabPhase.PERMISSION -> "위치 권한이 필요해요."
                        LabPhase.UNSAMPLED -> "이 종류는 검토판에 수집하지 않았어요."
                        else -> ""
                    }, Modifier.padding(20.dp), fontSize = 13.sp)
                    if (showRetry && (state.phase == LabPhase.ERROR || state.phase == LabPhase.PERMISSION)) TextButton(onClick = onRetry) { Text("다시 확인") }
                }
            }
        }
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
    if (filters) AlertDialog(onDismissRequest = { filters = false }, title = { Text("검색 조건") }, text = {
        Column {
        if (live) listOf(1000, 3000, 5000, 10000, 20000).forEach { meters ->
            TextButton(onClick = { onRadius(meters); filters = false }) { Text("${meters / 1000}km${if (state.applied.radiusMeters == meters) " ✓" else ""}") }
        }
        Text(if (live) "주차는 필수 조건이 아닌 우선 정렬입니다. 실내 동반 조건은 카드에서 확인하세요." else "반경 3km · 대형견 30kg·3세의 저장 응답입니다.\n실내 동반 등 추가 제한은 카드를 펼쳐 확인하세요.")
        }
    }, confirmButton = { TextButton(onClick = { filters = false }) { Text("완료") } })
}

private fun categorySymbol(kind: PlaceKind?): String = when (kind) {
    null -> "▦"; PlaceKind.CAFE -> "☕"; PlaceKind.RESTAURANT -> "🍽"; PlaceKind.HOSPITAL -> "✚"
    PlaceKind.PHARMACY -> "💊"; PlaceKind.PET_SHOP -> "🐾"; PlaceKind.GROOMING -> "✂"
    PlaceKind.SHOPPING -> "🛍"; PlaceKind.ETC -> "⋯"; else -> "⌂"
}

@Composable
fun PlaceDrawerCard(hit: PlaceSearchHit, expanded: Boolean, selected: Boolean, onToggle: () -> Unit, onAction: (String) -> Unit = {}, actions: (@Composable (PlaceSearchHit) -> Unit)? = null, dogNames: Map<String, String> = emptyMap()) {
    val p = hit.place
    val presentation = hit.toCardPresentation()
    val access = p.facts.petAccess
    val allowed = if (access?.dogOk == false) false else access?.allowed
    val registration = when (allowed) { true -> "동반 가능 등록"; false -> "동반 불가 등록"; null -> "동반 여부 확인 필요" }
    val mark = when (allowed) { true -> "✓"; false -> "×"; null -> "?" }
    Surface(modifier = Modifier.width(292.dp), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) DaengsColors.BrandPrimary else DaengsColors.BorderNeutral)) {
        Column(Modifier.padding(14.dp)) {
            Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).semantics { stateDescription = if (expanded) "펼침" else "접힘" }) {
                Row { Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp); Text(if (expanded) "⌃" else "⌄") }
                Text(presentation.meta, color = DaengsColors.TextSecondary, fontSize = 12.sp)
                Text(if (expanded) "🐾$mark $registration" else "🐾$mark", Modifier.padding(top = 8.dp).semantics { contentDescription = registration }, fontSize = 14.sp)
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                if (hit.evaluations.dogs.isNotEmpty()) {
                    Text("선택한 반려견 기준", fontSize = 13.sp)
                    hit.evaluations.dogs.forEach { evaluation ->
                        Text("${dogNames[evaluation.ref] ?: "반려견"} · ${com.daengs.app.ui.places.dogEvaluationLabel(evaluation)}", fontSize = 12.sp)
                    }
                }
                Text("추가 동반 조건", Modifier.padding(top = 8.dp), fontSize = 13.sp)
                val raw = p.facts.restrictions?.get("raw")?.jsonPrimitive?.contentOrNull
                    ?: p.facts.petAccess?.raw?.get("restrictions")?.jsonPrimitive?.contentOrNull
                val restrictionEvaluation = hit.evaluations.restrictions
                val chips = (restrictionEvaluation?.get("chips") as? JsonArray)
                    ?: (p.facts.restrictions?.get("chips") as? JsonArray)
                chips?.forEach { chip ->
                    (chip as? JsonObject)?.get("label")?.jsonPrimitive?.contentOrNull?.let { Text(it, fontSize = 12.sp) }
                }
                restrictionEvaluation?.get("state")?.jsonPrimitive?.contentOrNull?.let { status ->
                    if (status != "compatible") Text(if (status == "incompatible") "추가 동반 조건 불일치" else "추가 동반 조건 확인 필요", fontSize = 12.sp)
                }
                Text(raw?.takeIf { it.isNotBlank() } ?: if (p.facts.restrictions?.get("state")?.jsonPrimitive?.contentOrNull == "none_confirmed") "추가 제한 없음으로 등록" else "제한 정보 없음 · 확인 필요", fontSize = 12.sp)
                Text("허용 크기: ${p.facts.petAccess?.raw?.get("size")?.jsonPrimitive?.contentOrNull ?: "정보 없음"}", fontSize = 12.sp)
                hit.evaluations.dogAccess?.let { Text(when (it.state) { DogAccessState.COMPATIBLE -> "크기·체중 조건상 가능"; DogAccessState.INCOMPATIBLE -> "크기·체중 조건 불일치"; DogAccessState.UNKNOWN -> "크기·체중 조건 확인 필요" }, fontSize = 12.sp) }
                Text("${p.key.source} · ${p.classifications.firstOrNull()?.asOf ?: "날짜 미상"}\n현재 동반 정책은 방문 전 확인해 주세요.", Modifier.padding(vertical = 10.dp), fontSize = 10.sp, color = DaengsColors.TextSecondary)
                p.fieldSources["facts.restrictions"]?.let { source ->
                    Text("추가 조건 출처: ${source.source.source} · ${source.asOf ?: "날짜 미상"}", fontSize = 10.sp)
                }
                presentation.parking?.let { Text(it.text, fontSize = 12.sp) }
                p.facts.hoursText?.let { Text(it, fontSize = 12.sp) }
                p.facts.address?.let { Text(it, fontSize = 12.sp) }
                if (actions != null) actions(hit) else {
                p.facts.phone?.let { TextButton(onClick = { onAction("전화는 실제 앱 연결 단계에서 확인합니다.") }) { Text("전화로 확인") } }
                TextButton(onClick = { onAction("길찾기는 실제 앱 연결 단계에서 확인합니다.") }) { Text("길찾기") }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SearchLabPreview() { DaengsTheme { PlaceSearchLabScreen(PlaceSearchLabState(phase = LabPhase.EMPTY)) } }

@Preview(showBackground = true)
@Composable
private fun DrawerCardPreview() {
    val key = PlaceKey("preview", "cafe")
    val hit = PlaceSearchHit(
        PlaceResult(key, emptyList(), "검토 카페", com.daengs.app.location.GeoPoint(37.54, 127.05), 1300,
            PlaceMatch(key, PlaceKind.CAFE), emptyList(),
            PlaceFacts(null, null, null, null, null, null, null, null,
                PetAccessFacts(buildJsonObject { put("restrictions", "야외만 동반 가능"); put("size", "모두 가능") }, true, false, null, "any", null), null),
            emptyMap(), com.daengs.app.map.layers.places.FacilityIconGroup.ETC),
        PlaceEvaluations(null),
    )
    DaengsTheme { PlaceDrawerCard(hit, expanded = true, selected = true, onToggle = {}) }
}
