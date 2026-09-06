package com.daengs.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatCapability
import com.daengs.app.chat.ChatLoadState
import com.daengs.app.chat.ChatSession
import com.daengs.app.chat.ChatSessionList
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/** 미래 Chat 화면이 그대로 끼울 수 있는 최근 대화 한 덩어리. ChatScreen 에 의존하지 않는다. */
@Composable
fun RecentChatsContent(
    state: ChatLoadState<ChatSessionList>,
    selectedSessionId: String?,
    pendingDeletion: ChatSession?,
    onRetry: () -> Unit,
    onSelect: (ChatSession) -> Unit,
    onRequestDelete: (ChatSession) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (ChatSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        when (state) {
            ChatLoadState.Idle, ChatLoadState.Loading -> RecentChatsLoading()
            is ChatLoadState.Failed -> RecentChatsError(state.error, onRetry)
            is ChatLoadState.Ready -> if (state.value.recent.isEmpty()) {
                RecentChatsEmpty()
            } else {
                RecentSessionList(
                    sessions = state.value.recent,
                    selectedSessionId = selectedSessionId,
                    onSelect = onSelect,
                    onDelete = onRequestDelete,
                )
            }
        }
    }
    pendingDeletion?.let { session ->
        SessionDeleteConfirmation(
            session = session,
            onDismiss = onDismissDelete,
            onConfirm = { onConfirmDelete(session) },
        )
    }
}

@Composable
fun RecentChatsLoading(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = DaengPink, strokeWidth = 2.dp)
        Text("최근 대화를 불러오고 있어요", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
fun RecentChatsEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("아직 남겨 둔 대화가 없어요", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("첫 답변이 완성되면 최근 대화에 보여요.", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
fun RecentChatsError(error: ChatApiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(error.message ?: "최근 대화를 불러오지 못했어요.", color = DaengsColors.Error, fontSize = 13.sp)
        DaengsTextAction("다시 시도", onRetry)
    }
}

/** 서버가 준 newest-first 순서를 그대로 그린다. 능력별 탭이나 그룹을 만들지 않는다. */
@Composable
fun RecentSessionList(
    sessions: List<ChatSession>,
    selectedSessionId: String?,
    onSelect: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(sessions, key = { it.id }) { session ->
            val selected = session.id == selectedSessionId
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) PinkSoft else CardWhite)
                    .border(
                        1.dp,
                        if (selected) DaengPink.copy(alpha = 0.5f) else DaengsColors.BorderNeutral,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(session) }
                    .padding(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.title.ifBlank { "제목 없는 대화" },
                        color = TextDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    DaengsTextAction("삭제", { onDelete(session) }, tint = DaengsColors.Error)
                }
                val labels = session.agentCategories.mapNotNull(ChatCapability::label).distinct()
                if (labels.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ChatCapabilityBadges(session.agentCategories)
                }
            }
        }
    }
}

/** raw capability 를 정확한 정본 라벨로만 바꾼다. 모르는 값에는 배지를 만들지 않는다. */
@Composable
fun ChatCapabilityBadges(capabilities: List<String>, modifier: Modifier = Modifier) {
    val labels = capabilities.mapNotNull(ChatCapability::label).distinct()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { label ->
            Text(
                label,
                color = DaengPinkDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PinkSoft)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun SessionDeleteConfirmation(
    session: ChatSession,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대화를 삭제할까요?", color = TextDark) },
        text = { Text("‘${session.title.ifBlank { "제목 없는 대화" }}’의 문답은 다시 볼 수 없어요. 저장한 요약은 남아 있어요.") },
        confirmButton = { DaengsTextAction("삭제", onConfirm, tint = DaengsColors.Error) },
        dismissButton = { DaengsTextAction("취소", onDismiss) },
        containerColor = CardWhite,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun RecentChatsLoadingPreview() = DaengsTheme { RecentChatsLoading() }

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun RecentChatsEmptyPreview() = DaengsTheme { RecentChatsEmpty() }

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun RecentChatsErrorPreview() = DaengsTheme {
    RecentChatsError(ChatApiError(503, null, "최근 대화를 불러오지 못했어요."), {})
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun RecentSessionListPreview() = DaengsTheme {
    RecentSessionList(PREVIEW_SESSIONS, PREVIEW_SESSIONS.first().id, {}, {})
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatCapabilityBadgesPreview() = DaengsTheme {
    ChatCapabilityBadges(listOf("training", "life", "walk", "place"), Modifier.padding(12.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun SessionDeleteConfirmationPreview() = DaengsTheme {
    SessionDeleteConfirmation(PREVIEW_SESSIONS.first(), {}, {})
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun RecentChatsContentPreview() = DaengsTheme {
    RecentChatsContent(
        state = ChatLoadState.Ready(ChatSessionList(PREVIEW_SESSIONS, 5)),
        selectedSessionId = null,
        pendingDeletion = null,
        onRetry = {},
        onSelect = {},
        onRequestDelete = {},
        onDismissDelete = {},
        onConfirmDelete = {},
        modifier = Modifier.padding(16.dp),
    )
}

private val PREVIEW_SESSIONS = listOf(
    ChatSession("00000000-0000-4000-8000-000000000001", "pet", "배변 훈련 이야기", listOf("training"), 1, 2),
    ChatSession("00000000-0000-4000-8000-000000000002", "pet", "산책해도 될까?", listOf("walk", "unknown"), 1, 2),
)
