package com.daengs.app.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatCitation
import com.daengs.app.chat.ChatLoadState
import com.daengs.app.chat.ChatSummary
import com.daengs.app.chat.ChatSummaryList
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/** 기존 Storage/navigation 파일을 건드리지 않고 나중에 꽂을 수 있는 요약 보관함 내용. */
@Composable
fun ChatSummaryContent(
    state: ChatLoadState<ChatSummaryList>,
    pendingDeletion: ChatSummary?,
    onRetry: () -> Unit,
    onOpenSource: (String) -> Unit,
    onOpenCitation: (ChatCitation) -> Unit,
    onRequestDelete: (ChatSummary) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (ChatSummary) -> Unit,
    modifier: Modifier = Modifier,
    selectedSummaryId: String? = null,
) {
    when (state) {
        ChatLoadState.Idle, ChatLoadState.Loading -> ChatSummariesLoading(modifier)
        is ChatLoadState.Failed -> ChatSummariesError(state.error, onRetry, modifier)
        is ChatLoadState.Ready -> if (state.value.summaries.isEmpty()) {
            ChatSummariesEmpty(modifier)
        } else {
            ChatSummaryListContent(
                summaries = state.value.summaries,
                selectedSummaryId = selectedSummaryId,
                onOpenSource = onOpenSource,
                onOpenCitation = onOpenCitation,
                onDelete = onRequestDelete,
                modifier = modifier,
            )
        }
    }
    pendingDeletion?.let { summary ->
        SummaryDeleteConfirmation(summary, onDismissDelete) { onConfirmDelete(summary) }
    }
}

@Composable
fun ChatSummariesLoading(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = DaengPink, strokeWidth = 2.dp)
        Text("저장한 요약을 불러오고 있어요", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
fun ChatSummariesEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("저장한 대화 요약이 없어요", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("완료된 대화에서 요약을 만들면 여기에 모여요.", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
fun ChatSummariesError(error: ChatApiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(error.message ?: "요약을 불러오지 못했어요.", color = DaengsColors.Error, fontSize = 13.sp)
        DaengsTextAction("다시 시도", onRetry)
    }
}

@Composable
fun ChatSummaryListContent(
    summaries: List<ChatSummary>,
    onOpenSource: (String) -> Unit,
    onOpenCitation: (ChatCitation) -> Unit,
    onDelete: (ChatSummary) -> Unit,
    modifier: Modifier = Modifier,
    selectedSummaryId: String? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedSummaryId, summaries) {
        val selectedIndex = summaries.indexOfFirst { it.id == selectedSummaryId }
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }
    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(summaries, key = { it.id }) { summary ->
            ChatSummaryCard(
                summary = summary,
                onOpenSource = onOpenSource,
                onOpenCitation = onOpenCitation,
                onDelete = onDelete,
                selected = summary.id == selectedSummaryId,
            )
        }
    }
}

/** 완성 요약의 사용자용 필드만 보인다. model·promptVersion 같은 내부 값은 그리지 않는다. */
@Composable
fun ChatSummaryCard(
    summary: ChatSummary,
    onOpenSource: (String) -> Unit,
    onOpenCitation: (ChatCitation) -> Unit,
    onDelete: (ChatSummary) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) PinkSoft else CardWhite)
            .border(
                1.dp,
                if (selected) DaengPink.copy(alpha = 0.55f) else DaengsColors.BorderNeutral,
                RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(summary.title, color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            DaengsTextAction("삭제", { onDelete(summary) }, tint = DaengsColors.Error)
        }
        SummarySection("물어본 내용", summary.questionSummary)
        SummarySection("답변 요약", summary.answerSummary)
        if (summary.keyPoints.isNotEmpty()) SummaryBulletSection("핵심", summary.keyPoints)
        if (summary.cautions.isNotEmpty()) SummaryBulletSection("주의", summary.cautions, caution = true)
        if (summary.sourceCitations.isNotEmpty()) {
            Text("출처", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            summary.sourceCitations.forEach { citation ->
                if (citation.url == null) {
                    Text("• ${citation.label}", color = TextMuted, fontSize = 13.sp)
                } else {
                    DaengsTextAction("• ${citation.label}", { onOpenCitation(citation) })
                }
            }
        }
        // 원본이 최대 5개 유지에 밀린 요약은 링크만 감춘다. 요약 내용은 그대로 남는다.
        summary.sourceSessionId?.let { sourceId ->
            Spacer(Modifier.height(2.dp))
            DaengsTextAction("원본 대화 보기", { onOpenSource(sourceId) })
        }
    }
}

@Composable
private fun SummarySection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(content, color = TextMuted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun SummaryBulletSection(title: String, items: List<String>, caution: Boolean = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (caution) DaengsColors.ErrorSoft else PinkSoft)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            color = if (caution) DaengsColors.Error else TextDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        items.forEach { Text("• $it", color = TextDark, fontSize = 13.sp, lineHeight = 19.sp) }
    }
}

@Composable
fun SummaryDeleteConfirmation(summary: ChatSummary, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("요약을 삭제할까요?", color = TextDark) },
        text = { Text("‘${summary.title}’ 요약은 삭제하면 되돌릴 수 없어요. 원본 대화는 남아 있어요.") },
        confirmButton = { DaengsTextAction("삭제", onConfirm, tint = DaengsColors.Error) },
        dismissButton = { DaengsTextAction("취소", onDismiss) },
        containerColor = CardWhite,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatSummariesLoadingPreview() = DaengsTheme { ChatSummariesLoading() }

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatSummariesEmptyPreview() = DaengsTheme { ChatSummariesEmpty() }

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatSummariesErrorPreview() = DaengsTheme {
    ChatSummariesError(ChatApiError(401, null, "다시 로그인해 주세요."), {})
}

@Preview(widthDp = 411, heightDp = 700, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatSummaryCardPreview() = DaengsTheme {
    ChatSummaryCard(PREVIEW_SUMMARY, {}, {}, {}, Modifier.padding(16.dp))
}

@Preview(widthDp = 411, heightDp = 700, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatSummaryListPreview() = DaengsTheme {
    ChatSummaryListContent(listOf(PREVIEW_SUMMARY), {}, {}, {}, Modifier.padding(16.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun SummaryDeleteConfirmationPreview() = DaengsTheme {
    SummaryDeleteConfirmation(PREVIEW_SUMMARY, {}, {})
}

@Preview(widthDp = 411, heightDp = 700, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatSummaryContentPreview() = DaengsTheme {
    ChatSummaryContent(
        state = ChatLoadState.Ready(ChatSummaryList(listOf(PREVIEW_SUMMARY))),
        pendingDeletion = null,
        onRetry = {},
        onOpenSource = {},
        onOpenCitation = {},
        onRequestDelete = {},
        onDismissDelete = {},
        onConfirmDelete = {},
        modifier = Modifier.padding(16.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun SummarySectionPreview() = DaengsTheme {
    SummarySection("답변 요약", "같은 장소와 시간을 반복해 주세요.")
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun SummaryBulletSectionPreview() = DaengsTheme {
    SummaryBulletSection("핵심", listOf("장소를 고정해요", "바로 보상해요"))
}

private val PREVIEW_SUMMARY = ChatSummary(
    id = "00000000-0000-4000-8000-000000000010",
    petId = "pet",
    sourceSessionId = "00000000-0000-4000-8000-000000000001",
    sourceTurnCount = 2,
    title = "배변 훈련 요약",
    questionSummary = "실내 배변 실수를 줄이는 방법을 물었어요.",
    answerSummary = "같은 장소와 시간을 반복하고 성공 직후 보상해 주세요.",
    keyPoints = listOf("배변 장소를 고정해요", "성공 직후 바로 보상해요"),
    cautions = listOf("갑작스러운 변화가 계속되면 진료가 필요할 수 있어요"),
    sourceCitations = listOf(ChatCitation("반려동물 행동 안내", null), ChatCitation("동물보호 안내", "https://example.org")),
    agentCategories = listOf("training"),
    model = "model",
    promptVersion = "v1",
    completedAtMs = 2,
    createdAtMs = 1,
)
