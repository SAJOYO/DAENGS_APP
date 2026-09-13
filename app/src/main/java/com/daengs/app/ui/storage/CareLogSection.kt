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
import com.daengs.app.care.CareDaySummary
import com.daengs.app.care.CareEvent
import com.daengs.app.care.CareKind
import com.daengs.app.care.CareLogState
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 저장소 탭 "오늘의 케어 기록". **밥·약·간식은 탭 한 번으로 기록**하고, 산책은 서버 `walks` 가
 * 진실이라 수만 읽어 같이 보여 준다 (SAJOYO/DAENGS_APP#201).
 *
 * 스크롤하지 않는 `Column` 이다 — 저장소 탭의 `LazyColumn` 머리에 꽂혀 탭 전체가 한 번에
 * 스크롤된다. 스크롤 안에 스크롤을 두지 않는다.
 *
 * 날짜는 응답의 `day`(서울 자정 경계) 를 그대로 보여 준다. "오늘" 을 앱이 따로 계산하지 않는다.
 */
@Composable
fun CareLogSection(
    state: CareLogState,
    modifier: Modifier = Modifier,
    onRecord: (CareKind) -> Unit = {},
    onRetryLoad: () -> Unit = {},
    onConfirmDelete: (CareEvent) -> Unit = {},
    onDismissError: () -> Unit = {},
    canDelete: (CareEvent) -> Boolean = { true },
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var pendingDeletion by remember { mutableStateOf<CareEvent?>(null) }
    val summary = (state.today as? ChatLoadState.Ready)?.value

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 케어 기록", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            summary?.let { Text(dayLabel(it.day), color = TextMuted, fontSize = 13.sp) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CareKind.entries.forEach { kind ->
                DaengsWideButton(
                    label = kind.label,
                    onClick = { onRecord(kind) },
                    modifier = Modifier.weight(1f),
                    enabled = state.recording == null,
                    busy = state.recording == kind,
                    accent = true,
                )
            }
        }

        state.recordError?.let { error ->
            val retryKind = state.lastRecordKind
            CareActionError(error.message ?: "기록하지 못했어요.", label = if (retryKind == null) "닫기" else "다시 시도") {
                if (retryKind == null) onDismissError() else onRecord(retryKind)
            }
        }
        state.deleteError?.let { error ->
            CareActionError(error.message ?: "기록을 지우지 못했어요.", label = "닫기", onDismissError)
        }

        when (val today = state.today) {
            ChatLoadState.Idle, ChatLoadState.Loading ->
                Text("오늘 기록을 불러오고 있어요", color = TextMuted, fontSize = 13.sp)
            is ChatLoadState.Failed ->
                CareActionError(today.error.message ?: "오늘 기록을 불러오지 못했어요.", label = "다시 시도", onRetryLoad)
            is ChatLoadState.Ready -> CareDayContent(
                summary = today.value,
                deletingEventId = state.deletingEventId,
                zone = zone,
                onRequestDelete = { pendingDeletion = it },
                canDelete = canDelete,
            )
        }
    }

    pendingDeletion?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("이 기록을 지울까요?", color = TextDark) },
            text = { Text("${eventLabel(event, zone)} 기록이 사라져요. 되돌릴 수 없어요.") },
            confirmButton = {
                DaengsTextAction("지우기", {
                    pendingDeletion = null
                    onConfirmDelete(event)
                }, tint = DaengsColors.Error)
            },
            dismissButton = { DaengsTextAction("취소", { pendingDeletion = null }) },
            containerColor = CardWhite,
        )
    }
}

@Composable
private fun CareDayContent(
    summary: CareDaySummary,
    deletingEventId: String?,
    zone: ZoneId,
    onRequestDelete: (CareEvent) -> Unit,
    canDelete: (CareEvent) -> Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "밥 ${summary.meal} · 약 ${summary.medication} · 간식 ${summary.snack} · 산책 ${summary.walk}",
            color = TextDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (summary.events.isEmpty()) {
            Text("아직 오늘 챙긴 기록이 없어요", color = TextMuted, fontSize = 13.sp)
        } else {
            summary.events.forEach { event ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(eventLabel(event, zone), color = TextDark, fontSize = 14.sp)
                        event.actor?.let {
                            Text(it.displayName, color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    if (event.id == deletingEventId) {
                        Text("지우는 중", color = TextMuted, fontSize = 13.sp)
                    } else if (canDelete(event)) {
                        DaengsTextAction("삭제", { onRequestDelete(event) }, tint = DaengsColors.Error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CareActionError(message: String, label: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(message, color = DaengsColors.Error, fontSize = 13.sp)
        DaengsTextAction(label, onAction)
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

private fun eventLabel(event: CareEvent, zone: ZoneId): String =
    "${event.kind.label} · ${Instant.ofEpochMilli(event.occurredAtMs).atZone(zone).format(TIME)}"

/**
 * 이 줄에 삭제를 띄울까. 서버 규칙을 그대로 옮긴 것이다 —
 * **「내가 쓴 것」 이거나 「그 기록이 달린 행이 내 것」.**
 *
 * ⚠️ **「내 대표 강아지가 내 것인가」로 재면 안 된다.** 공동 돌봄이 붙으면서 하루 요약이
 * **그룹 전체의 기록**을 합쳐 준다 — 남의 행에 달린 남의 기록이 같은 목록에 섞여 온다.
 * 그런데 옛 판정은 계정의 대표 강아지 하나만 보고 "대표면 전부" 로 열었다. 그러면
 * 그룹 주보호자에게 남의 기록의 삭제가 뜨고, 눌러야 404 를 안다.
 *
 * **[ownsPetRow] 는 `event.petId` 로 묻는다.** 화면에 보이는 표시용 id 가 아니라
 * **기록이 실제로 달린 행**이다 — 연결된 그룹에서 둘은 다른 값이고, 서버도 그 행의
 * 소유자를 본다.
 *
 * **작성자를 모르는 기록은 내 것이 아니다.** 옛 기록·탈퇴자의 `actor` 는 id 가 비어
 * 오는데(`CareActor` 머리말), 그때 "모르니까 나겠지" 로 기울면 지우려다 막힌다.
 */
internal fun canDeleteCareEvent(
    event: CareEvent,
    currentUserId: String?,
    ownsPetRow: (String) -> Boolean,
): Boolean {
    if (ownsPetRow(event.petId)) return true
    val authorId = event.actor?.appUserId ?: return false
    return currentUserId != null && authorId == currentUserId
}

/** `2025-09-01` → `9월 1일`. 서버가 준 날짜를 못 읽으면 그대로 보여 준다. */
private fun dayLabel(day: String): String =
    runCatching { LocalDate.parse(day) }.map { "${it.monthValue}월 ${it.dayOfMonth}일" }.getOrDefault(day)

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun CareLogSectionPreview() = DaengsTheme {
    CareLogSection(
        state = CareLogState(
            selectedPetId = "pet",
            today = ChatLoadState.Ready(
                CareDaySummary(
                    "pet", "2026-09-08", "Asia/Seoul", meal = 2, medication = 1, snack = 1, walk = 1,
                    events = listOf(
                        CareEvent("1", "pet", CareKind.SNACK, 1_788_400_800_000L, null, "c1"),
                        CareEvent("2", "pet", CareKind.MEDICATION, 1_788_397_200_000L, null, "c2"),
                        CareEvent("3", "pet", CareKind.MEAL, 1_788_393_600_000L, null, "c3"),
                    ),
                ),
            ),
        ),
        modifier = Modifier.padding(16.dp),
        zone = ZoneId.of("Asia/Seoul"),
    )
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun CareLogSectionBusyPreview() = DaengsTheme {
    CareLogSection(
        state = CareLogState(
            selectedPetId = "pet",
            today = ChatLoadState.Ready(CareDaySummary("pet", "2026-09-08", "Asia/Seoul", 0, 0, 0, 0, emptyList())),
            recording = CareKind.MEAL,
        ),
        modifier = Modifier.padding(16.dp),
    )
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun CareLogSectionErrorPreview() = DaengsTheme {
    CareLogSection(
        state = CareLogState(
            selectedPetId = "pet",
            today = ChatLoadState.Failed(ChatApiError(0, null, "서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.")),
            recordError = ChatApiError(0, null, "서버에 닿지 못했어요."),
            lastRecordKind = CareKind.SNACK,
        ),
        modifier = Modifier.padding(16.dp),
    )
}
