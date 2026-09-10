package com.daengs.app.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.care.VetVisit
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
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * 저장소 탭 "진료비". 영수증을 찍으면 금액·병원·사유가 남는다 (SAJOYO/DAENGS_APP#258).
 *
 * `CareLogSection` 옆에 서고 같은 규칙을 지킨다 — **스크롤하지 않는 `Column`** 이다.
 * 저장소 탭의 `LazyColumn` 에 꽂혀 탭 전체가 한 번에 스크롤된다. 스크롤 안에 스크롤을
 * 두지 않는다.
 *
 * ⚠️ **사유 표시명을 앱이 갖고 있지 않다.** 확정 응답(`VetVisitResponse`)에는
 *    `reason_code` 만 실려 오고 표시명이 없다 — 저쪽이 17개 한글을 앱에 하드코딩하지
 *    말라고 `reason-options` 를 따로 낸 것이라([VetVisitState.reasonLabels] 가 그것을
 *    담는다), 여기서는 그 지도로만 라벨을 찾는다. **모르는 코드가 오면 코드를 그대로
 *    보여 준다** — 저쪽이 사유를 하나 더 만드는 날 크래시가 아니라 못생긴 라벨이어야 한다.
 */
@Composable
fun VetVisitSection(
    state: VetVisitState,
    modifier: Modifier = Modifier,
    onPickReceipt: () -> Unit = {},
    onRetryLoad: () -> Unit = {},
    onConfirmDelete: (VetVisit) -> Unit = {},
    onDismissError: () -> Unit = {},
    onCallHospital: (String) -> Unit = {},
) {
    var pendingDeletion by remember { mutableStateOf<VetVisit?>(null) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("진료비", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Text("영수증을 찍으면 금액과 병원이 남아요.", color = TextMuted, fontSize = 13.sp)

        DaengsWideButton(label = "영수증 찍기", onClick = onPickReceipt, accent = true)

        state.deleteError?.let { error ->
            VetActionError(error.message ?: "기록을 지우지 못했어요.", label = "닫기", onDismissError)
        }

        when (val visits = state.visits) {
            ChatLoadState.Idle, ChatLoadState.Loading ->
                Text("진료비 기록을 불러오고 있어요", color = TextMuted, fontSize = 13.sp)
            is ChatLoadState.Failed ->
                VetActionError(visits.error.message ?: "진료비 기록을 불러오지 못했어요.", "다시 시도", onRetryLoad)
            is ChatLoadState.Ready -> VetVisitList(
                visits = visits.value,
                labels = state.reasonLabels,
                deletingVisitId = state.deletingVisitId,
                onRequestDelete = { pendingDeletion = it },
                onCallHospital = onCallHospital,
            )
        }
    }

    pendingDeletion?.let { visit ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("이 기록을 지울까요?", color = TextDark) },
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
private fun VetVisitList(
    visits: List<VetVisit>,
    labels: Map<String, String>,
    deletingVisitId: String?,
    onRequestDelete: (VetVisit) -> Unit,
    onCallHospital: (String) -> Unit,
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
        if (visits.isEmpty()) {
            Text("아직 남긴 진료비 기록이 없어요", color = TextMuted, fontSize = 13.sp)
            return@Column
        }
        visits.forEach { visit ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        headlineOf(visit, labels),
                        color = TextDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (visit.id == deletingVisitId) {
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
    }
}

@Composable
private fun VetActionError(message: String, label: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(message, color = DaengsColors.Error, fontSize = 13.sp)
        DaengsTextAction(label, onAction)
    }
}

/** `9월 10일 · 피부 · 61,700원`. 표시명을 못 찾으면 코드를 그대로 쓴다. */
private fun headlineOf(visit: VetVisit, labels: Map<String, String>): String =
    "${dayLabelOf(visit.visitedOn)} · ${labels[visit.reasonCode] ?: visit.reasonCode} · ${wonOf(visit.totalKrw)}"

private fun dayLabelOf(day: LocalDate): String = "${day.monthValue}월 ${day.dayOfMonth}일"

private val WON = NumberFormat.getIntegerInstance(Locale.KOREA)

private fun wonOf(amount: Int): String = "${WON.format(amount)}원"

// -- 미리보기 ---------------------------------------------------------------

private fun previewVisit(id: String, day: LocalDate, reason: String, amount: Int, phone: String?) = VetVisit(
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
                listOf(
                    previewVisit("1", LocalDate.of(2026, 9, 10), "skin", 61_700, "02-543-0075"),
                    previewVisit("2", LocalDate.of(2026, 9, 2), "vaccination", 80_000, null),
                ),
            ),
            reasonLabels = mapOf("skin" to "피부", "vaccination" to "예방접종"),
        ),
        modifier = Modifier.padding(16.dp),
    )
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun VetVisitSectionEmptyPreview() = DaengsTheme {
    VetVisitSection(
        state = VetVisitState(selectedPetId = "pet", visits = ChatLoadState.Ready(emptyList())),
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
        modifier = Modifier.padding(16.dp),
    )
}
