package com.daengs.app.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.care.VetVisit
import com.daengs.app.care.VetVisitPage
import com.daengs.app.care.VetVisitState
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDate
import java.time.ZoneId

/**
 * 저장소 탭 "진료비". 영수증을 찍으면 금액·병원·사유가 남는다 (SAJOYO/DAENGS_APP#258).
 *
 * **PR #416 부터 목록이 아니라 요약 카드 하나다.** 저장소는 진료비 전용 화면이 아니라
 * 다른 기록도 같이 있는 자리여서, 기록이 늘수록 이 칸만 길어졌다. 목록·기간 필터·삭제는
 * [VetVisitsScreen] 으로 옮겼고 여기 남은 것은 **이번 달 얼마 / 마지막 언제 / 들어가는 길**
 * 셋이다.
 *
 * `CareLogSection` 옆에 서고 같은 규칙을 지킨다 — **스크롤하지 않는 `Column`** 이다.
 * 저장소 탭의 `LazyColumn` 에 꽂혀 탭 전체가 한 번에 스크롤된다. 스크롤 안에 스크롤을
 * 두지 않는다.
 *
 * ⚠️ **[today] 는 `Asia/Seoul` 의 오늘이다.** "이번 달" 의 경계가 서버와 같아야 한다 —
 *    기기 시간대로 재면 달이 바뀌는 순간이 어긋나 지난달 영수증이 이번 달 합계에 섞인다.
 *
 * ⚠️ **영수증 찍기는 여기 남는다.** 전체보기는 보는 화면이고, 찍으려고 한 단계 더 들어갈
 *    이유가 없다.
 */
@Composable
fun VetVisitSection(
    state: VetVisitState,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
    /**
     * 사진은 찍었는데 흐름을 못 연 경우의 한 줄. **침묵하면 안 된다** — 세션이 만료된
     * 폰에서는 찍고 화면이 닫히고 아무 일도 안 일어나는 것으로 보인다.
     */
    startError: String? = null,
    onPickReceipt: () -> Unit = {},
    onRetryLoad: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onOpenAll: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("진료비", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Text("영수증을 찍으면 금액과 병원이 남아요.", color = TextMuted, fontSize = 13.sp)

        DaengsWideButton(label = "영수증 찍기", onClick = onPickReceipt, accent = true)

        startError?.let { VetActionError(it, label = "닫기", onDismissError) }
        state.deleteError?.let { error ->
            VetActionError(error.message ?: "기록을 지우지 못했어요.", label = "닫기", onDismissError)
        }

        when (val visits = state.visits) {
            ChatLoadState.Idle, ChatLoadState.Loading ->
                Text("진료비 기록을 불러오고 있어요", color = TextMuted, fontSize = 13.sp)
            is ChatLoadState.Failed ->
                VetActionError(visits.error.message ?: "진료비 기록을 불러오지 못했어요.", "다시 시도", onRetryLoad)
            is ChatLoadState.Ready -> VetVisitSummary(
                page = visits.value,
                labels = state.reasonLabels,
                today = today,
                onOpenAll = onOpenAll,
            )
        }
    }
}

/**
 * 이번 달 합계와 마지막 방문.
 *
 * ⚠️ **합계는 [page] 안의 기록만 센다.** 기본 창이 최근 1년이라 이번 달은 언제나 그 안에
 *    있다 — 합계를 위해 따로 부르지 않는다. 다만 유저가 전체보기에서 기간을 「2024년」
 *    으로 바꿔 두고 돌아오면 이 카드도 그 창을 보게 되므로, **돌아올 때 기본 창으로 다시
 *    읽는다** (`ChatSummaryRoute` 의 배선).
 */
@Composable
private fun VetVisitSummary(
    page: VetVisitPage,
    labels: Map<String, String>,
    today: LocalDate,
    onOpenAll: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (page.visits.isEmpty()) {
            if (page.olderCount > 0) {
                // ⚠️ **여기서 길을 막으면 이 PR 이 고치려는 버그가 그대로 재현된다.**
                //    기록이 전부 1년보다 오래됐으면 기본 창은 빈 목록을 준다. 그때
                //    "아직 없어요" 라고 말하고 전체보기까지 감추면, 유저는 기록이 감춰져
                //    있다고 알려 줄 화면에 **영영 못 들어간다.** `older_count` 가 그것을
                //    아는 유일한 근거이므로, 비어 있어도 0 보다 크면 길을 준다.
                Text("이 기간에 남긴 기록이 없어요", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenAll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "이전 기록 ${page.olderCount}건 보기",
                        color = DaengsColors.BrandPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text("›", color = DaengsColors.BrandPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // 정말로 한 건도 없다. 전체보기를 안 준다 — 눌러도 빈 목록이다.
                Text("아직 남긴 진료비 기록이 없어요", color = TextMuted, fontSize = 13.sp)
            }
            return@Column
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("이번 달", color = TextMuted, fontSize = 12.sp)
            Text(
                wonOf(page.visits.thisMonthTotal(today)),
                color = TextDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        page.visits.maxByOrNull { it.visitedOn }?.let { last ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("마지막 방문", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(
                    "${dayLabelOf(last.visitedOn)} · ${labels[last.reasonCode] ?: last.reasonCode}",
                    color = TextDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenAll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("전체보기", color = DaengsColors.BrandPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("›", color = DaengsColors.BrandPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 이번 달에 쓴 돈. **경계는 KST 의 달** 이다 — 지난달 마지막 날은 여기 안 들어간다.
 * 이 한 줄이 틀리면 유저는 자기가 쓴 적 없는 금액을 본다.
 */
private fun List<VetVisit>.thisMonthTotal(today: LocalDate): Int =
    filter { it.visitedOn.year == today.year && it.visitedOn.month == today.month }
        .sumOf { it.totalKrw }

@Composable
internal fun VetActionError(message: String, label: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(message, color = DaengsColors.Error, fontSize = 13.sp)
        DaengsTextAction(label, onAction)
    }
}

/** `9월 10일`. 목록 화면도 같은 모양을 쓴다. */
internal fun dayLabelOf(day: LocalDate): String = "${day.monthValue}월 ${day.dayOfMonth}일"

// -- 미리보기 ---------------------------------------------------------------

private fun previewPage(visits: List<VetVisit>, olderCount: Int = 0) =
    VetVisitPage(start = null, end = null, olderCount = olderCount, visits = visits)

internal fun previewVisit(id: String, day: LocalDate, reason: String, amount: Int, phone: String?) = VetVisit(
    id = id, petId = "pet", visitedOn = day, totalKrw = amount,
    hospitalName = "압구정동물병원", hospitalAddress = null, hospitalPhone = phone,
    reasonCode = reason, reasonDetail = null, isEmergency = false, isOncology = false,
    clientEventId = "c-$id",
)

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun VetVisitSectionPreview() = DaengsTheme {
    VetVisitSection(
        state = VetVisitState(
            selectedPetId = "pet",
            visits = ChatLoadState.Ready(
                previewPage(
                    listOf(
                        previewVisit("1", LocalDate.of(2026, 9, 10), "skin", 61_700, "02-543-0075"),
                        previewVisit("2", LocalDate.of(2026, 9, 2), "vaccination", 80_000, null),
                        previewVisit("3", LocalDate.of(2026, 7, 18), "neutering", 340_000, null),
                    ),
                ),
            ),
            reasonLabels = mapOf("skin" to "피부", "vaccination" to "예방접종", "neutering" to "중성화"),
        ),
        today = LocalDate.of(2026, 9, 16),
        modifier = Modifier.padding(16.dp),
    )
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun VetVisitSectionEmptyPreview() = DaengsTheme {
    VetVisitSection(
        state = VetVisitState(selectedPetId = "pet", visits = ChatLoadState.Ready(previewPage(emptyList()))),
        today = LocalDate.of(2026, 9, 16),
        modifier = Modifier.padding(16.dp),
    )
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun VetVisitSectionFailedPreview() = DaengsTheme {
    VetVisitSection(
        state = VetVisitState(
            selectedPetId = "pet",
            visits = ChatLoadState.Failed(ChatApiError(0, null, "서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.")),
        ),
        today = LocalDate.of(2026, 9, 16),
        modifier = Modifier.padding(16.dp),
    )
}
