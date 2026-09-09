package com.daengs.app.ui.places

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daengs.app.place.filterCriteria
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.serialization.json.*

@Composable
internal fun PlaceFilterAiPanel(state: PlaceFilterAiState, onConfirm: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
        Text(if (state.loading) "현재 조건에서 변경할 내용을 확인하고 있어요…" else "문장으로 조건을 추가하거나 수정하세요. 말하지 않은 조건은 유지해요.")
        state.message?.let { Text(it) }
        state.response?.takeIf { it.result == null }?.let { response ->
            response.issues.forEach { Text(it) }
            if (response.needsConfirmation) {
                Text("현재: ${response.query.base.criteria.description()} / 이름: ${response.query.base.request.nameQuery.ifEmpty { "조건 없음" }}")
                response.proposed?.let { Text("변경 후: ${it.filterCriteria().description()} / 이름: ${it.getValue("name_query").jsonPrimitive.content.ifEmpty { "조건 없음" }}") }
                TextButton(onClick = onConfirm, enabled = !state.loading) { Text("이 변경 적용") }
            }
        }
        state.response?.proposal?.getValue("edits")?.jsonArray?.forEach {
            val evidence = it.jsonObject.getValue("evidence").jsonObject
            Text("${if (evidence["origin"] == JsonPrimitive("inferred")) "추론한 선호" else "요청 근거"}: ${evidence.getValue("quote").jsonPrimitive.content}")
        }
        if (state.canRetry) TextButton(onClick = onRetry, enabled = !state.loading) { Text("AI 요청 재시도") }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PlaceFilterAiPanelPreview() { DaengsTheme { PlaceFilterAiPanel(PlaceFilterAiState(enabled = true, message = "‘조용한 곳’ 조건을 현재 필터로 확정할 수 없어요."), {}, {}) } }
