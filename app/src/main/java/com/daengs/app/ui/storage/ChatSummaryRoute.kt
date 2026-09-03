package com.daengs.app.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.chat.ChatCitation
import com.daengs.app.chat.ChatHistoryState
import com.daengs.app.chat.ChatSummary
import com.daengs.app.chat.ChatSummaryCoordinator
import com.daengs.app.chat.ChatTurn
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

/** Home 의 저장소 탭과 서버 요약 상태를 잇는 얇은 화면 경계. */
@Composable
fun ChatSummaryRoute(
    petId: String?,
    historyState: ChatHistoryState,
    coordinator: ChatSummaryCoordinator,
    accessTokenProvider: suspend () -> String?,
    onOpenSource: (String) -> Unit,
    onOpenCitation: (ChatCitation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by coordinator.state.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDeletion by remember { mutableStateOf<ChatSummary?>(null) }

    LaunchedEffect(petId, coordinator) {
        coordinator.selectPet(petId)
        val token = accessTokenProvider() ?: return@LaunchedEffect
        coordinator.load(token)
    }
    DisposableEffect(coordinator) {
        onDispose { coordinator.cancelPending() }
    }

    val source = historyState.detail?.takeIf { detail ->
        detail.session.petId == petId &&
            detail.turns.any { it.processingStatus == ChatTurn.ProcessingStatus.COMPLETED }
    }
    Column(
        modifier.fillMaxSize().background(CreamBg).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("대화 보관함", color = TextDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("완료된 AI 대화를 요약해 오래 보관할 수 있어요.", color = TextMuted, fontSize = 13.sp)

        if (petId == null) {
            Text("로그인하고 대표 강아지를 골라 주세요.", color = TextMuted, fontSize = 14.sp)
        } else {
            DaengsWideButton(
                label = if (source == null) "요약할 대화를 먼저 완료해 주세요" else "현재 대화 요약 저장",
                onClick = {
                    val detail = source ?: return@DaengsWideButton
                    scope.launch {
                        val token = accessTokenProvider() ?: return@launch
                        coordinator.create(token, detail)
                    }
                },
                enabled = source != null,
                busy = state.creatingSessionId != null,
                accent = true,
            )
            state.createError?.let { error ->
                SummaryActionError(error.message ?: "요약을 만들지 못했어요.") {
                    val detail = source ?: return@SummaryActionError
                    scope.launch {
                        val token = accessTokenProvider() ?: return@launch
                        coordinator.create(token, detail)
                    }
                }
            }
            state.deleteError?.let { error ->
                SummaryActionError(error.message ?: "요약을 삭제하지 못했어요.") {
                    coordinator.clearErrors()
                }
            }
            ChatSummaryContent(
                state = state.summaries,
                pendingDeletion = pendingDeletion,
                onRetry = {
                    scope.launch {
                        val token = accessTokenProvider() ?: return@launch
                        coordinator.load(token)
                    }
                },
                onOpenSource = onOpenSource,
                onOpenCitation = onOpenCitation,
                onRequestDelete = { pendingDeletion = it },
                onDismissDelete = { pendingDeletion = null },
                onConfirmDelete = { summary ->
                    pendingDeletion = null
                    scope.launch {
                        val token = accessTokenProvider() ?: return@launch
                        coordinator.delete(token, summary.id)
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                selectedSummaryId = state.selectedSummaryId,
            )
        }
    }
}

@Composable
private fun SummaryActionError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(message, color = DaengsColors.Error, fontSize = 13.sp)
        DaengsTextAction("다시 시도", onRetry)
    }
}

@Preview(widthDp = 411, heightDp = 760, showBackground = true)
@Composable
private fun ChatSummaryRoutePreview() {
    val coordinator = ChatSummaryCoordinator(rememberCoroutineScope())
    DaengsTheme {
        ChatSummaryRoute(
            petId = null,
            historyState = ChatHistoryState(),
            coordinator = coordinator,
            accessTokenProvider = { null },
            onOpenSource = {},
            onOpenCitation = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SummaryActionErrorPreview() = DaengsTheme {
    SummaryActionError("요약 저장에 실패했어요.") {}
}
