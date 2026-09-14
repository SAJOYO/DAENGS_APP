package com.daengs.app.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.daengs.app.place.ConversationUiState
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

/** A single answer channel; internal notices are never appended here. */
@Composable
internal fun ConversationPanel(state: ConversationUiState, validationError: String? = null,
    showAnswer: Boolean = true, onRetryAnswer: () -> Unit = {}, onRetrySearch: () -> Unit = {},
    onApplyCurrentFilters: () -> Unit = {}, filterSummary: String = "", onOpenFilters: () -> Unit = {},
    showAnswerRecovery: Boolean = showAnswer) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (filterSummary.isNotEmpty()) TextButton(onClick = onOpenFilters) {
            Text(filterSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (state.busy || (showAnswer && state.answerBusy)) LinearProgressIndicator(Modifier.fillMaxWidth())
        val error = state.error ?: validationError
        if (error != null) Text(error, color = DaengsColors.Error)
        else if (showAnswer) (state.commandAnswer ?: state.result?.answer)?.let { Text(it) }
        state.notice?.let { Text(it) }
        if (state.error != null || state.result?.failed == true) {
            TextButton(onClick = onRetrySearch) { Text("검색 다시 시도") }
        }
        if (showAnswerRecovery && !state.busy && !state.answerBusy && state.result?.answerStatus == "pending") {
            state.answerError?.let { Text(it) }
            TextButton(onClick = onRetryAnswer) { Text("설명 다시 받기") }
        }
        if (state.result?.matches == false) {
            Text("현재 목록은 변경 전 조건의 결과예요.")
            TextButton(enabled = !state.busy, onClick = onApplyCurrentFilters) { Text("현재 조건으로 검색") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationPanelPreview() {
    DaengsTheme { ConversationPanel(ConversationUiState(busy = true)) }
}
