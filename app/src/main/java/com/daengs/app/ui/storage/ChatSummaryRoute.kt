package com.daengs.app.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.care.CareLogCoordinator
import com.daengs.app.chat.ChatCitation
import com.daengs.app.chat.ChatHistoryState
import com.daengs.app.chat.ChatLoadState
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

/**
 * Home 의 저장소 탭. **한 번에 스크롤되는 한 목록**이다 — 위에 오늘의 케어 기록(#201),
 * 그 아래 대화 보관함, 맨 밑에 사진·영상 안내. 두 코디네이터의 서버 상태를 화면에 잇는
 * 얇은 경계이고, 판단은 코디네이터에 있다.
 */
@Composable
fun ChatSummaryRoute(
    petId: String?,
    historyState: ChatHistoryState,
    coordinator: ChatSummaryCoordinator,
    careCoordinator: CareLogCoordinator,
    accessTokenProvider: suspend () -> String?,
    onOpenSource: (String) -> Unit,
    onOpenCitation: (ChatCitation) -> Unit,
    modifier: Modifier = Modifier,
    currentUserId: String? = null,
    selectedPetIsOwner: Boolean = true,
) {
    val state by coordinator.state.collectAsState()
    val careState by careCoordinator.state.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDeletion by remember { mutableStateOf<ChatSummary?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(petId, coordinator, careCoordinator) {
        coordinator.selectPet(petId)
        careCoordinator.selectPet(petId)
        val token = accessTokenProvider() ?: return@LaunchedEffect
        careCoordinator.load(token)
        coordinator.load(token)
    }
    DisposableEffect(coordinator, careCoordinator) {
        onDispose {
            coordinator.cancelPending()
            careCoordinator.cancelPending()
        }
    }

    val summaries = (state.summaries as? ChatLoadState.Ready)?.value?.summaries.orEmpty()
    LaunchedEffect(state.selectedSummaryId, summaries) {
        val index = summaries.indexOfFirst { it.id == state.selectedSummaryId }
        if (index >= 0) listState.animateScrollToItem(index + HEADER_ITEMS)
    }

    val source = historyState.detail?.takeIf { detail ->
        detail.session.petId == petId &&
            detail.turns.any { it.processingStatus == ChatTurn.ProcessingStatus.COMPLETED }
    }
    val withToken: (suspend (String) -> Unit) -> Unit = { action ->
        scope.launch { accessTokenProvider()?.let { action(it) } }
    }

    LazyColumn(
        modifier.fillMaxSize().background(CreamBg).testTag("storage-list"),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ⚠️ 보관함 앞의 항목 수는 [HEADER_ITEMS] 와 같아야 한다 — 선택된 요약으로 스크롤할 때 더한다.
        item(key = "care") {
            if (petId == null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("오늘의 케어 기록", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("로그인하고 대표 강아지를 골라 주세요.", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                CareLogSection(
                    state = careState,
                    onRecord = { kind -> withToken { careCoordinator.record(it, kind) } },
                    onRetryLoad = { withToken { careCoordinator.load(it) } },
                    onConfirmDelete = { event -> withToken { careCoordinator.delete(it, event.id) } },
                    onDismissError = { careCoordinator.clearErrors() },
                    canDelete = { event -> canDeleteCareEvent(event, currentUserId, selectedPetIsOwner) },
                )
            }
        }
        item(key = "summaries-head") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("대화 보관함", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("완료된 AI 대화를 요약해 오래 보관할 수 있어요.", color = TextMuted, fontSize = 13.sp)
                if (petId != null) {
                    DaengsWideButton(
                        label = if (source == null) "요약할 대화를 먼저 완료해 주세요" else "현재 대화 요약 저장",
                        onClick = { source?.let { detail -> withToken { coordinator.create(it, detail) } } },
                        enabled = source != null,
                        busy = state.creatingSessionId != null,
                        accent = true,
                    )
                    state.createError?.let { error ->
                        SummaryActionError(error.message ?: "요약을 만들지 못했어요.") {
                            source?.let { detail -> withToken { coordinator.create(it, detail) } }
                        }
                    }
                    state.deleteError?.let { error ->
                        SummaryActionError(error.message ?: "요약을 삭제하지 못했어요.") { coordinator.clearErrors() }
                    }
                }
            }
        }
        if (petId != null) {
            chatSummaryItems(
                state = state.summaries,
                onRetry = { withToken { coordinator.load(it) } },
                onOpenSource = onOpenSource,
                onOpenCitation = onOpenCitation,
                onRequestDelete = { pendingDeletion = it },
                selectedSummaryId = state.selectedSummaryId,
            )
        }
        item(key = "photos") { StoragePhotosNotice(Modifier.fillMaxWidth()) }
    }

    pendingDeletion?.let { summary ->
        SummaryDeleteConfirmation(summary, { pendingDeletion = null }) {
            pendingDeletion = null
            withToken { coordinator.delete(it, summary.id) }
        }
    }
}

/** 보관함 요약 목록 앞에 놓인 항목 수 (케어 기록 · 보관함 머리). */
private const val HEADER_ITEMS = 2

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
    val scope = rememberCoroutineScope()
    DaengsTheme {
        ChatSummaryRoute(
            petId = null,
            historyState = ChatHistoryState(),
            coordinator = ChatSummaryCoordinator(scope),
            careCoordinator = CareLogCoordinator(scope),
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
