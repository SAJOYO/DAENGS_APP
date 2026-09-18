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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.daengs.app.care.CareEvent
import com.daengs.app.care.CareKind
import com.daengs.app.care.CareLogState
import com.daengs.app.chat.ChatLoadState
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.ZoneId

/**
 * 오늘의 케어 기록 **전체보기.**
 *
 * ## 왜 화면을 따로 두나
 *
 * 저장소 탭에서 이 섹션 하나가 화면의 60% 를 먹고 있었다 — 실기기에서 재 보니 항목
 * 일곱 건에 131 → 약 620dp 였고, 그 아래 진료비는 화면 끝에 걸치고 대화 보관함은 아예
 * 화면 밖이었다. 항목 하나가 52dp 쯤이라 스무 건이면 케어만으로 한 화면을 넘는다.
 *
 * 진료비가 이미 고른 답을 따른다 — 탭에는 **합계 한 줄과 최근 둘**만 두고, 자세한 것은
 * 이 화면이 받는다. 접히는 것은 시각과 누가 챙겼는지뿐이고, **"몇 번 챙겼나" 는 합계
 * 줄에 남아 접혀도 보인다.**
 *
 * ## 기록 버튼을 여기에도 둔다
 *
 * [CareLogSection] 을 통째로 쓰므로 밥·약·간식 버튼이 같이 온다. 일부러 그대로 뒀다 —
 * 전체 기록을 보다가 "아, 약 안 줬네" 를 여기서 바로 누를 수 있어야 한다. 되돌아가서
 * 누르게 하면 그게 더 이상하다.
 *
 * ⚠️ **어제 이전은 아직 못 본다.** `CareLogGateway` 에 `today()` 밖에 없어서 이 화면도
 * 오늘 하루치다 (`care/CareLogCoordinator.kt`). 날짜를 거슬러 보려면 서버에 기간 조회가
 * 먼저 있어야 한다.
 */
@Composable
fun CareLogScreen(
    state: CareLogState,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    onBack: () -> Unit = {},
    onRecord: (CareKind) -> Unit = {},
    onRetryLoad: () -> Unit = {},
    onConfirmDelete: (CareEvent) -> Unit = {},
    onDismissError: () -> Unit = {},
    canDelete: (CareEvent) -> Boolean = { true },
) {
    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize().background(CreamBg)) {
        // **상태 표시줄 밑으로 기어들지 않게.** 이 저장소의 전체 화면은 전부 이것을 붙인다
        // (`VetVisitsScreen` · `ChatSummariesScreen` · `WalkRecordsRoute`).
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // 날짜는 상단바가 받는다 — 섹션 머리를 접으므로 여기서 잃지 않게.
            CareLogTopBar(onBack, (state.today as? ChatLoadState.Ready)?.value?.day?.let(::dayLabel))
            // 하루치라 항목이 많아도 수십 건이다. `LazyColumn` 을 쓸 만큼은 아니고,
            // 섹션이 스크롤하지 않는 `Column` 이라 여기서 스크롤을 준다.
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                CareLogSection(
                    state = state,
                    onRecord = onRecord,
                    onRetryLoad = onRetryLoad,
                    onConfirmDelete = onConfirmDelete,
                    onDismissError = onDismissError,
                    canDelete = canDelete,
                    zone = zone,
                    // 여기서는 다 그린다. 전체보기 줄도 안 그린다 — 이미 전체보기다.
                    limit = null,
                    onOpenAll = null,
                    // 상단바가 이미 같은 말을 한다. 안 접으면 제목이 두 번 뜬다.
                    showHeader = false,
                )
            }
        }
    }
}

@Composable
private fun CareLogTopBar(onBack: () -> Unit, day: String?) {
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
        Text(
            "오늘의 케어 기록",
            color = TextDark,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        day?.let { Text(it, color = TextMuted, fontSize = 13.sp) }
    }
}
