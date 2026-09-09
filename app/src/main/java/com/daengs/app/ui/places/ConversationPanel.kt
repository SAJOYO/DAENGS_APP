package com.daengs.app.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.place.ConversationUiState
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

/** A single answer channel; internal notices are never appended here. */
@Composable
internal fun ConversationPanel(state: ConversationUiState, validationError: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val error = state.error ?: validationError
        if (error != null) Text(error, color = DaengsColors.Error)
        else state.result?.answer?.let { Text(it) }
        if (state.result?.matches == false) Text("현재 목록은 변경 전 조건의 결과예요.")
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationPanelPreview() {
    DaengsTheme { ConversationPanel(ConversationUiState(busy = true)) }
}
