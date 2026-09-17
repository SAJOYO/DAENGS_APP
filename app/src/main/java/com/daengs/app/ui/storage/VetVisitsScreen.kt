package com.daengs.app.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.care.VetRange
import com.daengs.app.care.VetVisit
import com.daengs.app.care.VetVisitPage
import com.daengs.app.care.VetVisitState
import com.daengs.app.care.label
import com.daengs.app.care.vetRangePresets
import com.daengs.app.care.window
import com.daengs.app.chat.ChatLoadState
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.common.DaengsChip
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.common.DateWheel
import com.daengs.app.ui.common.KeepScrollInside
import com.daengs.app.ui.common.SheetFrame
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDate

/**
 * 진료비 전체보기 (PR #416). 저장소의 요약 카드에서 **화면으로 밀어 올린다.**
 *
 * ⚠️ **모달이 아니라 화면인 이유.** 이 안에 목록·기간 필터·삭제가 다 들어가는데, 모달로
 *    만들면 삭제 되묻기와 기간 시트가 **모달 위에 모달**로 겹치고 안드로이드 뒤로가기가
 *    금방 꼬인다 (PR #416 「왜 팝업이 아니라 풀스크린인가」).
 *
 * ⚠️ **안내는 조건부다.** `older_count` 가 0 이면 안내 자리가 통째로 없다. 늘 띄우면
 *    기록이 1년 안에만 있는 대부분의 화면에서 소음이고, **소음이 되면 정작 감춰진 게
 *    있을 때도 안 읽힌다.**
 *
 * ⚠️ **날짜는 서버가 준 것을 그대로 쓴다.** 저쪽이 `Asia/Seoul` 로 정해 보낸 값이라
 *    기기 시간대로 다시 환산하면 하루 어긋난다.
 */
@Composable
fun VetVisitsScreen(
    state: VetVisitState,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSelectRange: (VetRange) -> Unit = {},
    onRetryLoad: () -> Unit = {},
    onConfirmDelete: (VetVisit) -> Unit = {},
    onCallHospital: (String) -> Unit = {},
    onDismissError: () -> Unit = {},
) {
    var pendingDeletion by remember { mutableStateOf<VetVisit?>(null) }
    var pickingRange by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize().background(CreamBg)) {
        // **상태 표시줄 밑으로 기어들지 않게.** 이 저장소의 전체 화면은 전부 이것을 붙인다
        // (`WalkRecordsRoute` · `ScreeningHistoryScreen` · `PetMembersScreen`).
        // 시트는 이 패딩 밖에 있어야 바닥까지 붙으므로 [Box] 가 아니라 여기에 건다.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            VetVisitsTopBar(onBack)
            VetRangeChips(
                today = today,
                selected = state.range,
                onSelectRange = onSelectRange,
                onPickCustom = { pickingRange = true },
            )
            state.deleteError?.let { error ->
                VetActionError(
                    error.message ?: "기록을 지우지 못했어요.",
                    label = "닫기",
                    onAction = onDismissError,
                )
            }

            when (val visits = state.visits) {
                ChatLoadState.Idle, ChatLoadState.Loading -> Text(
                    "진료비 기록을 불러오고 있어요",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp),
                )
                is ChatLoadState.Failed -> Box(Modifier.padding(16.dp)) {
                    VetActionError(
                        visits.error.message ?: "진료비 기록을 불러오지 못했어요.",
                        "다시 시도",
                        onRetryLoad,
                    )
                }
                is ChatLoadState.Ready -> VetVisitLongList(
                    page = visits.value,
                    labels = state.reasonLabels,
                    deletingVisitId = state.deletingVisitId,
                    onRequestDelete = { pendingDeletion = it },
                    onCallHospital = onCallHospital,
                    onShowOlder = { pickingRange = true },
                )
            }
        }

        if (pickingRange) {
            VetRangeSheet(
                initial = state.range.window(today),
                today = today,
                onDismiss = { pickingRange = false },
                onConfirm = { from, to ->
                    pickingRange = false
                    onSelectRange(VetRange.Custom(from, to))
                },
            )
        }
    }

    pendingDeletion?.let { visit ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("이 기록을 지울까요?", color = TextDark) },
            // **되돌릴 수 없다** — 기록과 영수증 사진이 같이 사라진다 (저쪽 docs).
            text = { Text("${dayLabelOf(visit.visitedOn)} ${wonOf(visit.totalKrw)} 기록이 사라져요. 되돌릴 수 없어요.") },
            confirmButton = {
                DaengsTextAction("지우기", {
                    pendingDeletion = null
                    onConfirmDelete(visit)
                }, tint = DaengsColors.Error)
            },
            dismissButton = { DaengsTextAction("취소", { pendingDeletion = null }) },
            containerColor = CardWhite,
        )
    }
}

@Composable
private fun VetVisitsTopBar(onBack: () -> Unit) {
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
            // 뒤로 아이콘은 따로 없다. 오른쪽 꺾쇠를 돌려 쓴다 (`WalkRecordsHeader` 와 같다).
        ) { DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(18.dp).rotate(180f), tint = TextDark) }
        Spacer(Modifier.size(4.dp))
        Text("진료비", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 기간 프리셋. **연도는 오늘에서 센다** — 적어 두면 해가 바뀔 때 재작년이 뜬다
 * (`vetRangePresets`).
 *
 * 가로로 넘친다. `LazyColumn` 밖에 두어 목록과 같이 안 스크롤되게 한다 — 기간을 바꾸려고
 * 목록을 맨 위까지 올릴 일이 없어야 한다.
 */
@Composable
private fun VetRangeChips(
    today: LocalDate,
    selected: VetRange,
    onSelectRange: (VetRange) -> Unit,
    onPickCustom: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        vetRangePresets(today).forEach { preset ->
            DaengsChip(preset.label(), selected = preset == selected, onClick = { onSelectRange(preset) })
        }
        DaengsChip(
            "직접 고르기",
            selected = selected is VetRange.Custom,
            onClick = onPickCustom,
        )
    }
}

@Composable
private fun VetVisitLongList(
    page: VetVisitPage,
    labels: Map<String, String>,
    deletingVisitId: String?,
    onRequestDelete: (VetVisit) -> Unit,
    onCallHospital: (String) -> Unit,
    onShowOlder: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // **0 이면 이 자리가 아예 없다.** 창을 모르면 날짜를 못 적으므로 그때도 없다.
        if (page.olderCount > 0 && page.start != null) {
            item(key = "older-notice") {
                OlderNotice(start = page.start, olderCount = page.olderCount, onClick = onShowOlder)
            }
        }
        if (page.visits.isEmpty()) {
            item(key = "empty") {
                Text("이 기간에는 기록이 없어요", color = TextMuted, fontSize = 13.sp)
            }
        }
        items(page.visits, key = { it.id }) { visit ->
            VetVisitRow(
                visit = visit,
                labels = labels,
                deleting = visit.id == deletingVisitId,
                onRequestDelete = onRequestDelete,
                onCallHospital = onCallHospital,
            )
        }
    }
}

/** 감춰진 것이 있을 때만 뜬다. 탭하면 기간 시트가 열린다. */
@Composable
private fun OlderNotice(start: LocalDate, olderCount: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DaengsColors.WarningSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // 서버가 준 날짜 그대로. 기기 시간대로 환산하지 않는다.
        Text("$start 이후만 보입니다", color = DaengsColors.Warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("이전 기록 ${olderCount}건 보기", color = DaengsColors.Warning, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("›", color = DaengsColors.Warning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VetVisitRow(
    visit: VetVisit,
    labels: Map<String, String>,
    deleting: Boolean,
    onRequestDelete: (VetVisit) -> Unit,
    onCallHospital: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                headlineOf(visit, labels),
                color = TextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (deleting) {
                Text("지우는 중", color = TextMuted, fontSize = 13.sp)
            } else {
                DaengsTextAction("삭제", { onRequestDelete(visit) }, tint = DaengsColors.Error)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            visit.hospitalName?.let {
                Text(it, color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            // 눌러서 거는 자리. 이 번호가 틀리면 모르는 사람에게 전화가 걸리므로
            // 확인 화면이 서버와 같은 모양으로 한 번 걸러 둔다.
            visit.hospitalPhone?.let { phone ->
                DaengsTextAction(phone, { onCallHospital(phone) })
            }
        }
    }
}

/**
 * 기간을 직접 고르는 시트. **달력은 여기 숨어 있다** — 프리셋이 먼저다.
 *
 * ⚠️ **휠은 [WheelCard] 안에서 잠들어 있다** (APP#277). [SheetFrame] 은 끌어서 닫는
 *    시트가 아니라 세로 제스처를 다툴 상대가 없지만, 잠금은 그대로 둔다 — 시트 안이
 *    길어져 스크롤이 생기는 날 이 전제가 조용히 깨지는 것보다 탭 한 번이 싸다.
 */
@Composable
private fun VetRangeSheet(
    initial: com.daengs.app.care.VetWindow,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val years = (today.year - WHEEL_YEARS)..today.year
    // **휠이 표현할 수 있는 날로 당겨서 연다.** 「전체」의 창은 `0001-01-01` 인데 휠은
    // 스무 해까지만 열려 있다 — 그대로 넣으면 휠이 제 목록에 없는 값을 걸고 있게 되고,
    // 유저가 아무것도 안 돌리고 확인하면 **화면에 보이던 것과 다른 날짜**가 나간다.
    var from by remember { mutableStateOf(initial.from.intoWheel(years)) }
    var to by remember { mutableStateOf(initial.to.intoWheel(years)) }

    SheetFrame(title = "기간 고르기", onDismiss = onDismiss) {
        // ⚠️ **휠 둘은 시트 높이에 안 들어간다.** 그냥 쌓으면 [SheetFrame] 의 560dp 에서
        //    잘려 나가는데, 잘린 버튼은 **사라지지 않고 눌리지 않을 뿐**이다 — 탭이 뒤의
        //    어둠에 떨어져 시트가 닫히기만 하고, 유저에게는 "눌러도 아무 일이 없다" 로
        //    보인다. 휠은 안에서 스크롤하고 버튼은 아래 고정이다.
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .nestedScroll(KeepScrollInside),
        ) {
            Text("부터", color = TextMuted, fontSize = 12.sp)
            DateWheel(from, { from = it }, years = years)
            Spacer(Modifier.height(10.dp))
            Text("까지", color = TextMuted, fontSize = 12.sp)
            DateWheel(to, { to = it }, years = years)
        }
        if (to.isBefore(from)) {
            Text("끝이 시작보다 앞서요.", color = DaengsColors.Error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        DaengsWideButton(
            label = "이 기간으로 보기",
            onClick = { onConfirm(from, to) },
            enabled = !to.isBefore(from),
            accent = true,
        )
        Spacer(Modifier.height(14.dp))
    }
}

/**
 * 휠이 고를 수 있는 해의 수. 영수증은 보험 청구·연말정산 때문에 연 단위로 기억되는데
 * 스무 해면 그 쓰임을 다 덮는다. 더 옛날은 「전체」가 있다.
 */
private const val WHEEL_YEARS = 20

/** 휠의 해 범위 안으로 당긴다. 범위 밖이면 가장 가까운 해의 1월 1일 / 12월 31일이다. */
private fun LocalDate.intoWheel(years: IntRange): LocalDate = when {
    year < years.first -> LocalDate.of(years.first, 1, 1)
    year > years.last -> LocalDate.of(years.last, 12, 31)
    else -> this
}

/** `9월 10일 · 피부 · 61,700원`. 표시명을 못 찾으면 코드를 그대로 쓴다. */
private fun headlineOf(visit: VetVisit, labels: Map<String, String>): String =
    "${dayLabelOf(visit.visitedOn)} · ${labels[visit.reasonCode] ?: visit.reasonCode} · ${wonOf(visit.totalKrw)}"

// -- 미리보기 ---------------------------------------------------------------

private fun previewState(olderCount: Int, range: VetRange = VetRange.RecentYear) = VetVisitState(
    selectedPetId = "pet",
    range = range,
    visits = ChatLoadState.Ready(
        VetVisitPage(
            start = LocalDate.of(2025, 9, 15),
            end = LocalDate.of(2026, 9, 16),
            olderCount = olderCount,
            visits = listOf(
                previewVisit("1", LocalDate.of(2026, 9, 10), "skin", 61_700, "02-543-0075"),
                previewVisit("2", LocalDate.of(2026, 9, 2), "vaccination", 80_000, null),
                previewVisit("3", LocalDate.of(2026, 7, 18), "neutering", 340_000, null),
            ),
        ),
    ),
    reasonLabels = mapOf("skin" to "피부", "vaccination" to "예방접종", "neutering" to "중성화"),
)

@Preview(widthDp = 411, heightDp = 780, showBackground = true)
@Composable
private fun VetVisitsScreenPreview() = DaengsTheme {
    VetVisitsScreen(state = previewState(olderCount = 0), today = LocalDate.of(2026, 9, 16))
}

/** 안내가 뜨는 유일한 모양. 0 짜리와 나란히 두고 보라고 둘 다 둔다. */
@Preview(widthDp = 411, heightDp = 780, showBackground = true)
@Composable
private fun VetVisitsScreenOlderPreview() = DaengsTheme {
    VetVisitsScreen(state = previewState(olderCount = 3), today = LocalDate.of(2026, 9, 16))
}

@Preview(widthDp = 411, heightDp = 780, showBackground = true)
@Composable
private fun VetVisitsScreenEmptyPreview() = DaengsTheme {
    VetVisitsScreen(
        state = VetVisitState(
            selectedPetId = "pet",
            range = VetRange.Year(2024),
            visits = ChatLoadState.Ready(
                VetVisitPage(start = LocalDate.of(2024, 1, 1), end = LocalDate.of(2024, 12, 31), olderCount = 0, visits = emptyList()),
            ),
        ),
        today = LocalDate.of(2026, 9, 16),
    )
}
