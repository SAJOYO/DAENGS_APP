package com.daengs.app.ui.places

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.place.*
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

@Composable
internal fun FacilitySearchPanel(state: FacilityUiState, onChoice: (FacilityChoice) -> Unit, onRetry: () -> Unit) {
    if (!state.enabled) return
    val response = state.response
    var expanded by remember(response?.searchId, response?.confirmedLensId) { mutableStateOf(response?.confirmedLensId == null) }
    Surface(color = DaengsColors.SurfaceMuted, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("검색 조건을 확인하고 있어요…", fontSize = 12.sp)
            }
            state.notice?.let { Text(it, fontSize = 12.sp) }
            state.error?.let { Text(it, color = DaengsColors.Error, fontSize = 12.sp) }
            if (state.canRetry) TextButton(onClick = onRetry, enabled = !state.loading) { Text("AI 검색 다시 시도") }
            if (response == null && !state.loading && state.error == null && state.notice == null) {
                Text("원하는 장소와 조건을 문장으로 입력해 주세요.", fontSize = 12.sp)
                Text("AI가 해석한 조건을 확인한 뒤 검색할 수 있어요.", fontSize = 11.sp)
            }
            if (response != null) {
                val confirmed = response.confirmedLens
                if (confirmed != null) {
                    Text("#${confirmed.label} · 적용한 검색 방향", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "조건 설명 접기" else "해석한 조건 보기") }
                } else Text("해석한 조건을 확인해 주세요", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (expanded) {
                    response.signals.forEach { signal ->
                        Text("#${signal.label}${if (signal.required) " · 필수 조건" else ""}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(signal.note, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(signal.options, key = { it.id }) { option ->
                                FilterChip(selected = option.id == signal.selectedOptionId,
                                    enabled = !state.loading && signal.state == "needs_selection" && option.availability == "proxy",
                                    onClick = { onChoice(FacilityChoice.Refine(signal.id, option.id)) },
                                    label = { Text("#${option.label}", fontSize = 12.sp) },
                                    border = BorderStroke(1.dp, DaengsColors.BorderNeutral),
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DaengsColors.BrandPrimarySoft,
                                        selectedLabelColor = DaengsColors.TextPrimary))
                            }
                        }
                        signal.options.forEach { option ->
                            if (option.note.isNotBlank()) Text("${option.label}: ${option.note}", fontSize = 11.sp)
                        }
                    }
                    response.lenses.forEach { lens ->
                        HorizontalDivider(color = DaengsColors.BorderNeutral)
                        Text("#${lens.label}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(lens.note, fontSize = 11.sp)
                        if (response.confirmedLensId == null) TextButton(
                            onClick = { onChoice(FacilityChoice.Confirm(lens.id)) }, enabled = !state.loading,
                        ) { Text("이 방향으로 검색 · ${lens.search.overviewHits(lens.parking).size}곳", fontSize = 12.sp) }
                    }
                    response.notices.forEach { Text(it, fontSize = 11.sp) }
                    if (response.lenses.isEmpty()) Text("조건을 선택하거나 검색 문장을 바꿔 주세요.", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
internal fun FacilityPresentationDetails(presentation: FacilityPresentation) {
    Text(presentation.summary, fontSize = 12.sp)
    presentation.whyMatched.forEach { Text(it, fontSize = 12.sp) }
    presentation.facts.forEach { fact ->
        Text("${fact.label}: ${fact.text}", fontSize = 12.sp,
            color = if (fact.severity in listOf("warning", "critical")) DaengsColors.Error else DaengsColors.TextPrimary)
    }
    presentation.notices.forEach { Text(it, fontSize = 11.sp) }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun FacilityPanelPreview() { DaengsTheme { FacilitySearchPanel(FacilityUiState(enabled = true), {}, {}) } }

@Preview(showBackground = true)
@Composable
private fun FacilityDetailsPreview() {
    DaengsTheme { Column { FacilityPresentationDetails(FacilityPresentation(PlaceKey("preview", "1"),
        "카페 정보를 확인해 주세요.", listOf(FacilityFact("실내 동반", "확인되지 않았어요.", "warning")), emptyList(), emptyList())) } }
}
