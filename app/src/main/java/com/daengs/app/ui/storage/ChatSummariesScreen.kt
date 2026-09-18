package com.daengs.app.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.chat.ChatCitation
import com.daengs.app.chat.ChatLoadState
import com.daengs.app.chat.ChatSummary
import com.daengs.app.chat.ChatSummaryList
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.TextDark

/**
 * 대화 보관함 **전체보기.**
 *
 * ## 왜 화면을 따로 두나
 *
 * 저장소 탭이 케어 기록·진료비·대화 보관함을 **한 `LazyColumn` 에** 이어 붙이고 있었다.
 * 그런데 세 섹션이 다 자라는 것은 아니다 — 케어는 오늘 하루치라 상한이 있고, 진료비는
 * 이미 「이번 달 · 마지막 방문 + 전체보기」로 접혀 있다. **대화 보관함만 그 규칙 밖이었고,
 * 하필 목록 맨 끝이라** 요약이 쌓이는 만큼 탭 전체가 길어졌다.
 *
 * 실기기에서 재 보니 케어 기록만으로 화면의 60%(131 → 약 630dp)를 먹고 진료비가 648dp 로
 * 화면 끝에 걸쳐서, **대화 보관함은 스크롤해야 나왔다.**
 *
 * 그래서 진료비가 이미 고른 답을 따른다 — 탭에는 요약만 두고 자세한 것은 이 화면이 받는다.
 * 알맹이는 새로 만들지 않았다. [ChatSummaryContent] 가 *"기존 Storage/navigation 파일을
 * 건드리지 않고 나중에 꽂을 수 있는 요약 보관함 내용"* 으로 이미 있었다.
 *
 * ## 선택한 요약으로 스크롤하는 일은 여기로 왔다
 *
 * 예전에는 저장소 탭의 목록에서 `animateScrollToItem(index + HEADER_ITEMS)` 로 찾아갔다.
 * **머리 항목 수를 손으로 세어 맞추는 코드**라 섹션을 하나 넣을 때마다 밟을 자리였다.
 * 여기서는 목록이 하나뿐이라 셀 것이 없다.
 */
@Composable
fun ChatSummariesScreen(
    state: ChatLoadState<ChatSummaryList>,
    pendingDeletion: ChatSummary?,
    modifier: Modifier = Modifier,
    selectedSummaryId: String? = null,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenSource: (String) -> Unit = {},
    onOpenCitation: (ChatCitation) -> Unit = {},
    onRequestDelete: (ChatSummary) -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onConfirmDelete: (ChatSummary) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize().background(CreamBg)) {
        // **상태 표시줄 밑으로 기어들지 않게.** 이 저장소의 전체 화면은 전부 이것을 붙인다
        // (`VetVisitsScreen` · `WalkRecordsRoute` · `ScreeningHistoryScreen`).
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ChatSummariesTopBar(onBack)
            ChatSummaryContent(
                state = state,
                pendingDeletion = pendingDeletion,
                onRetry = onRetry,
                onOpenSource = onOpenSource,
                onOpenCitation = onOpenCitation,
                onRequestDelete = onRequestDelete,
                onDismissDelete = onDismissDelete,
                onConfirmDelete = onConfirmDelete,
                selectedSummaryId = selectedSummaryId,
            )
        }
    }
}

@Composable
private fun ChatSummariesTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack)
                .semantics { contentDescription = "뒤로" },
            contentAlignment = Alignment.Center,
            // 뒤로 아이콘은 따로 없다. 오른쪽 꺾쇠를 돌려 쓴다 (`VetVisitsScreen` 과 같다).
        ) { DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(18.dp).rotate(180f), tint = TextDark) }
        Spacer(Modifier.size(4.dp))
        Text("대화 보관함", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
