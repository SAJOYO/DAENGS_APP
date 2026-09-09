package com.daengs.app.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.place.ConversationFilterEdit
import com.daengs.app.place.ConversationUiState
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

@Composable
internal fun ConversationFiltersDialog(
    state: ConversationUiState,
    onApply: (ConversationFilterEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    val result = state.result
    val filters = result?.appliedPlaceFilters() ?: AppliedPlaceFilters()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("검색 필터") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (result == null) Text("장소를 검색하면 적용된 조건을 여기서 확인할 수 있어요.")
                else {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(it, color = DaengsColors.Error) }
                    state.notice?.let { Text(it) }
                    if (filters.count == 0) Text("추가로 적용된 필수 조건이 없어요.")
                    else Text("아래 조건을 충족하는 장소만 표시해요.")
                    filters.all.forEach { condition ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(condition.label, Modifier.weight(1f))
                            TextButton(enabled = !state.busy, onClick = {
                                onApply(ConversationFilterEdit(result.sessionId, result.revision, removeAll = listOf(condition.id)))
                            }, modifier = Modifier.semantics { contentDescription = condition.label + " 해제" }) {
                                Text("해제")
                            }
                        }
                    }
                    if (filters.any.isNotEmpty()) {
                        Surface(color = DaengsColors.SurfaceMuted, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (filters.all.isEmpty()) "다음 조합 중 하나" else "위 조건을 충족하면서, 다음 조합 중 하나")
                                filters.any.forEachIndexed { index, branch ->
                                    if (index > 0) Text("또는", color = DaengsColors.TextSecondary)
                                    Text(branch.conditions.joinToString("\n그리고 ") { it.label })
                                }
                                TextButton(enabled = !state.busy, onClick = {
                                    onApply(ConversationFilterEdit(result.sessionId, result.revision, removeAny = filters.any.map { it.id }))
                                }) { Text("조합 조건 해제") }
                            }
                        }
                    }
                    Text("카테고리와 반경은 화면의 기존 선택에서 바꿀 수 있어요.")
                    Text(if (result.parkingFirst) "주차 우선: 켜짐 · 필수 조건을 통과한 장소의 순서만 바꿔요."
                        else "주차 우선: 꺼짐 · 주차 필수 조건의 적용 여부와는 별개예요.",
                        color = DaengsColors.TextSecondary)
                    if (!result.matches) {
                        Text("현재 목록은 변경 전 조건의 결과예요.", color = DaengsColors.Error)
                        TextButton(enabled = !state.busy, onClick = {
                            onApply(ConversationFilterEdit(result.sessionId, result.revision))
                        }) { Text("현재 조건으로 검색") }
                    }
                    state.filterRetry?.let { retry ->
                        if (!state.busy) TextButton(onClick = { onApply(retry) }) { Text("다시 시도") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun ConversationFiltersDialogPreview() {
    DaengsTheme { ConversationFiltersDialog(ConversationUiState(), {}, {}) }
}
