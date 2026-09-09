package com.daengs.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.daengs.app.activity.*
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.delay

/** Recreated on account/dog changes. Failed or unprocessed reads never become a zero score. */
@Composable
fun HomeGameRoute(repository: ActivityRepository, ownerId: String?, petId: String?, petName: String?, onOpenGame: () -> Unit) {
    if (ownerId == null || petId == null) {
        HomeGameCard("점령 현황", if (ownerId == null) "로그인하고 현황 보기" else "강아지를 선택해 주세요", onOpenGame)
        return
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    key(ownerId, petId) {
        var message by remember { mutableStateOf("시즌 현황을 불러오고 있어요") }
        var retry by remember { mutableIntStateOf(0) }
        var expanded by remember { mutableStateOf(false) }
        var detail by remember { mutableStateOf("") }
        LaunchedEffect(ownerId, petId, retry, lifecycle) {
          lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val seasonResult = repository.currentSeason()
                val error = seasonResult.exceptionOrNull()
                val season = seasonResult.getOrNull()
                if (error != null) {
                    message = if (error is ActivityHttpException && error.code == "activity_disabled") "시즌 게임을 준비하고 있어요" else "시즌 현황을 불러오지 못했어요"
                    detail = ""
                    break
                }
                if (season == null) {
                    message = "진행 중인 시즌이 없어요"
                    detail = ""
                } else {
                    val result = repository.territorySummary(season.id, petId)
                    val summary = result.getOrNull()
                    if (summary == null) {
                        message = "점령 현황을 불러오지 못했어요"
                        detail = ""
                    } else {
                        val score = summary.score
                        val points = score?.let {
                            BigDecimal(it.holdingUnits).divide(BigDecimal("36000000000"), 1, RoundingMode.DOWN)
                                .add(BigDecimal.valueOf(it.bonus)).stripTrailingZeros().toPlainString()
                        } ?: if (summary.status == ActivityTerritoryStatus.READY && summary.statistics?.acquisitionCount == 0L) "0" else "—"
                        val owned = score?.currentCount ?: summary.statistics?.ownedSiteCount
                        message = "${owned?.toString() ?: "—"}곳 · ${points}점 · " +
                            if (summary.status != ActivityTerritoryStatus.READY) "집계 중" else "순위 —"
                        val minutes = ((season.endsMs - season.serverNowMs).coerceAtLeast(0) + 59_999) / 60_000
                        val end = if (minutes >= 1440) "${minutes / 1440}일" else if (minutes >= 60) "${minutes / 60}시간" else "${minutes}분"
                        detail = "시즌 종료까지 $end · ${summary.statistics?.takeoverCount ?: "—"}회 탈취\n" +
                            "보유 시간 점수는 서버 정산 기준이며 실시간 점수와 차이가 있을 수 있어요.\n현재 순위 정보는 제공되지 않아요."
                    }
                }
                delay(30_000)
            }
          }
        }
        HomeGameCard("${petName ?: "우리 강아지"}의 이번 시즌", message, { expanded = true })
        if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("이번 시즌 점령 현황") },
            text = { Text(message + if (detail.isNotBlank()) "\n\n$detail" else "") },
            confirmButton = { TextButton(onClick = { expanded = false; onOpenGame() }) { Text("산책 지도 보기") } },
            dismissButton = { TextButton(onClick = { retry++ }) { Text("새로고침") } })
    }
}

@Composable
fun HomeGameCard(title: String, message: String, onOpen: () -> Unit) {
    // Match the existing bell's 44dp height so the header cannot take space from the miniroom.
    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
        .background(PinkFaint)
        .clickable(role = Role.Button, onClickLabel = "점령 현황 보기", onClick = onOpen)
        .semantics { contentDescription = "$title · $message" }.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DaengsIconView(DaengsIcon.TerritoryPole, Modifier.size(24.dp), DaengPinkDeep)
        Spacer(Modifier.width(4.dp))
        Text(message, color = TextDark, fontWeight = FontWeight.Medium, fontSize = 11.sp,
            lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Preview(widthDp = 360, showBackground = true)
@Composable
private fun HomeGamePreview() { DaengsTheme { HomeGameCard("보리의 이번 시즌", "3곳 · 320점 · 순위 —") {} } }
