package com.daengs.app.ui.places

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.map.features.places.PlaceDiscoveryState
import com.daengs.app.map.features.places.categoryLabel
import com.daengs.app.place.*
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.serialization.json.*
import java.util.UUID

internal fun filterAtomLabel(atom: PlaceFilterAtom): String = when (atom.capability) {
    "purpose.kind" -> atom.value.jsonArray.joinToString("·") { categoryLabel(PlaceKind.fromWire(it.jsonPrimitive.content)) } + if (atom.op == "not_in") " 제외" else " 중 하나"
    "operations.parking" -> if (atom.value.jsonPrimitive.boolean) "주차 가능" else "주차 불가"
    "pet_access.exclusive" -> if (atom.value.jsonPrimitive.boolean) "반려동물 전용" else "반려동물 전용 아님"
    else -> atom.capability
}

internal fun PlaceFilterCriteria.description(): String = buildList {
    add(kinds.joinToString("·", transform = ::categoryLabel))
    if (all.isNotEmpty()) add(all.joinToString(" 그리고 ", transform = ::filterAtomLabel))
    if (any.isNotEmpty()) add(any.joinToString(" 또는 ") { "(${it.all.joinToString(" 그리고 ", transform = ::filterAtomLabel)})" })
    preferences.forEach { add("주차 우선: ${it.scopeKinds.joinToString("·", transform = ::categoryLabel)}") }
    if (showUncertain) add("확인 필요 결과 별도 표시")
}.joinToString(" / ")

@Composable
internal fun PlaceFilterEditor(
    state: PlaceDiscoveryState,
    canApply: Boolean,
    onApply: (PlaceFilterCriteria?) -> Unit,
    onDismiss: () -> Unit,
    onRetryCapabilities: () -> Unit,
) {
    var draft by remember { mutableStateOf(state.filters ?: PlaceFilterCriteria(
        kinds = state.requestedKinds.takeIf { it.size in 1..6 }.orEmpty(),
        preferences = if (state.preferParking && state.requestedKinds.size in 1..6) listOf(PlaceFilterPreference(
            PlaceFilterAtom(UUID.randomUUID().toString(), "operations.parking", "eq", JsonPrimitive(true)),
            state.requestedKinds.filter { it.supportsParkingPreference() },
        )) else emptyList(),
    )) }
    val validation = draft.validationMessage()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("장소 조건") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.filterCapabilities == null) {
                Text(if (state.filterCapabilitiesLoading) "지원 조건 확인 중…" else state.filterError ?: "지원 조건을 확인해 주세요.")
                TextButton(onClick = onRetryCapabilities, enabled = !state.filterCapabilitiesLoading) { Text("다시 확인") }
            } else {
                Text("검색 업종 · 1~6개", style = MaterialTheme.typography.titleSmall)
                FilterKinds(state.filterCapabilities.kinds, draft.kinds) { kinds -> draft = draft.copy(kinds = kinds) }
                Text("모두 만족해야 하는 조건", style = MaterialTheme.typography.titleSmall)
                Text("상관없음은 조건 해제입니다. 정보가 없는 곳은 불가·전용 아님에 포함되지 않아요.", style = MaterialTheme.typography.bodySmall)
                BooleanFilters(draft.all) { draft = draft.copy(all = it) }
                HorizontalDivider()
                Text("대안 묶음 · 아래 묶음 중 하나 이상 만족", style = MaterialTheme.typography.titleSmall)
                Text("위의 공통 조건도 함께 만족해야 해요.", style = MaterialTheme.typography.bodySmall)
                draft.any.forEachIndexed { index, branch ->
                    key(branch.id) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("묶음 ${index + 1}", Modifier.weight(1f))
                            TextButton(onClick = { draft = draft.copy(any = draft.any.filterNot { it.id == branch.id }) }) { Text("묶음 삭제") }
                        }
                        val kindAtom = branch.all.firstOrNull { it.capability == "purpose.kind" }
                        val selected = kindAtom?.value?.jsonArray?.map { PlaceKind.fromWire(it.jsonPrimitive.content) }.orEmpty()
                        Text("묶음 업종 · 선택하지 않으면 모든 검색 업종", style = MaterialTheme.typography.bodySmall)
                        FilterKinds(draft.kinds, selected) { kinds ->
                            val atoms = branch.all.filterNot { it.capability == "purpose.kind" } + if (kinds.isEmpty()) emptyList() else listOf(
                                PlaceFilterAtom(kindAtom?.id ?: UUID.randomUUID().toString(), "purpose.kind", "in", JsonArray(kinds.map { JsonPrimitive(it.wire) })))
                            draft = draft.copy(any = draft.any.map { if (it.id == branch.id) it.copy(all = atoms) else it })
                        }
                        BooleanFilters(branch.all) { atoms -> draft = draft.copy(any = draft.any.map { if (it.id == branch.id) it.copy(all = atoms) else it }) }
                    }
                }
                TextButton(onClick = { draft = draft.copy(any = draft.any + PlaceFilterBranch()) }, enabled = draft.any.size < 4) { Text("대안 묶음 추가") }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.preferences.isNotEmpty(), { checked ->
                        draft = draft.copy(preferences = if (!checked) emptyList() else listOf(PlaceFilterPreference(
                            PlaceFilterAtom(UUID.randomUUID().toString(), "operations.parking", "eq", JsonPrimitive(true)),
                            draft.kinds.filter { it.supportsParkingPreference() })))
                    }, enabled = draft.kinds.any { it.supportsParkingPreference() })
                    Text("주차 가능하면 우선 · 필수 아님")
                }
                draft.preferences.firstOrNull()?.let { preference ->
                    Text("주차 우선을 적용할 업종", style = MaterialTheme.typography.bodySmall)
                    FilterKinds(draft.kinds.filter { it.supportsParkingPreference() }, preference.scopeKinds) { kinds ->
                        draft = draft.copy(preferences = if (kinds.isEmpty()) emptyList() else listOf(preference.copy(scopeKinds = kinds)))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.showUncertain, { draft = draft.copy(showUncertain = it) })
                    Text("조건 정보가 부족한 곳도 별도로 보기")
                }
                if (draft.kinds.any { !it.supportsParkingPreference() }) Text("병원·약국은 주차·전용 여부가 제공되지 않아 확인 필요로 판정됩니다.", style = MaterialTheme.typography.bodySmall)
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.filterError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!canApply) Text("위치를 확인하고 현재 검색이 끝나면 적용할 수 있어요.")
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onApply(draft) }, enabled = canApply && state.filterCapabilities != null && validation == null && !state.filterEditLoading) {
            Text(if (state.filterEditLoading) "적용 중…" else "조건 적용")
        }
    }, dismissButton = {
        Row {
            if (state.filters != null) TextButton(onClick = { onApply(null); onDismiss() }) { Text("전체 해제") }
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    })
}

@Composable
private fun FilterKinds(available: List<PlaceKind>, selected: List<PlaceKind>, onChange: (List<PlaceKind>) -> Unit) {
    Column {
        available.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { kind -> FilterChip(selected = kind in selected, onClick = {
                    onChange(if (kind in selected) selected - kind else selected + kind)
                }, label = { Text(categoryLabel(kind)) }, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BooleanFilters(atoms: List<PlaceFilterAtom>, onChange: (List<PlaceFilterAtom>) -> Unit) {
    listOf("operations.parking" to "주차", "pet_access.exclusive" to "반려동물 전용").forEach { (capability, label) ->
        Text(label, style = MaterialTheme.typography.bodyMedium)
        val current = atoms.firstOrNull { it.capability == capability }?.value?.jsonPrimitive?.boolean
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null to "상관없음", true to if (capability == "operations.parking") "가능" else "전용", false to if (capability == "operations.parking") "불가" else "전용 아님").forEach { (value, text) ->
                FilterChip(current == value, onClick = { onChange(atoms.setBoolean(capability, value)) }, label = { Text(text) })
            }
        }
    }
}

@Composable
internal fun PlaceFilterEvidence(evidence: JsonObject) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (evidence["state"] == JsonPrimitive("unknown")) "확인 필요 · 조건 충족 여부를 알 수 없어요" else "검색 조건을 만족한 곳이에요")
        evidence.getValue("atoms").jsonArray.forEach { value ->
            val atom = value.jsonObject
            val capability = atom.getValue("capability").jsonPrimitive.content
            val label = when (capability) { "operations.parking" -> "주차"; "pet_access.exclusive" -> "반려동물 전용"; else -> "업종" }
            val verdict = when (atom.getValue("state").jsonPrimitive.content) { "true" -> "충족"; "false" -> "불충족"; else -> "정보 부족" }
            val reason = when (atom["unknown_reason"]?.jsonPrimitive?.contentOrNull) {
                "parse_failed" -> "원문 해석 불가"; "insufficient_evidence" -> "근거 부족"; "not_provided" -> "미제공"; else -> null
            }
            Text("$label: $verdict${reason?.let { " ($it)" }.orEmpty()} · 출처 ${atom.getValue("source").jsonPrimitive.content}", style = MaterialTheme.typography.bodySmall)
        }
        Text("대안 묶음이 있으면 일부 조건이 불충족이어도 다른 묶음으로 충족할 수 있어요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FilterEditorPreview() {
    val capabilities = PlaceFilterCapabilities(Json.parseToJsonElement("""
        {"contract_version":"place-filter-v1","candidate_kinds":["cafe","restaurant"],"max_candidate_kinds":6,
        "unknown_policies":["exclude","separate"],"capabilities":[
        {"id":"purpose.kind","operators":["in","not_in"]},
        {"id":"operations.parking","operators":["eq"],"prefer_values":[true]},
        {"id":"pet_access.exclusive","operators":["eq"]}]}
    """).jsonObject)
    DaengsTheme { PlaceFilterEditor(PlaceDiscoveryState(requestedKinds = listOf(PlaceKind.CAFE), filterCapabilities = capabilities), true, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun FilterControlsPreview() {
    DaengsTheme { Column { FilterKinds(listOf(PlaceKind.CAFE, PlaceKind.RESTAURANT), listOf(PlaceKind.CAFE), {}); BooleanFilters(emptyList(), {}) } }
}

@Preview(showBackground = true)
@Composable
private fun FilterEvidencePreview() {
    DaengsTheme { PlaceFilterEvidence(buildJsonObject { put("state", "unknown"); put("atoms", JsonArray(emptyList())) }) }
}
