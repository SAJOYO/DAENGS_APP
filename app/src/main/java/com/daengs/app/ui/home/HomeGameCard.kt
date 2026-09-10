package com.daengs.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.daengs.app.ui.theme.TextMuted
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
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
    if (ownerId == null || petId == null) return
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
                        message = "$points 점 · 점령 ${owned?.toString() ?: "—"}곳" +
                            if (summary.status != ActivityTerritoryStatus.READY) " · 집계 중" else ""
                        val minutes = ((season.endsMs - season.serverNowMs).coerceAtLeast(0) + 59_999) / 60_000
                        val end = if (minutes >= 1440) "${minutes / 1440}일" else if (minutes >= 60) "${minutes / 60}시간" else "${minutes}분"
                        detail = "시즌 종료까지 $end · ${summary.statistics?.takeoverCount ?: "—"}회 탈취\n" +
                            (score?.let(::activityScoreBreakdown)?.let { "$it\n" } ?: "") +
                            (summary.finalRank?.let { "최종 ${it}위\n" } ?: "") +
                            "보유 시간 점수는 서버 정산 기준이에요."
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

/**
 * 홈의 시즌 한 줄.
 *
 * **한 줄이다.** 전에는 제목과 본문을 쌓아서, `시즌 현황을 불러오고 있어요` 나
 * `준비 중` 한 마디만 띄우는 동안에도 두 줄 높이를 먹었다. 홈은 미니룸이 주인공인
 * 화면이라 그 자리를 돌려준다.
 *
 * 제목은 안 지운다 — 누구의 시즌인지가 사라지면 숫자만 남는다. 대신 좁으면 제목이
 * 먼저 줄고(`weight`), 본문은 자기 길이만큼만 차지한다.
 */
@Composable
fun HomeGameCard(title: String, message: String, onOpen: () -> Unit) {
    TextButton(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(message, style = MaterialTheme.typography.labelMedium,
                color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("현황 ›", style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(widthDp = 360, showBackground = true)
@Composable
private fun HomeGamePreview() { HomeGameCard("보리의 이번 시즌", "320 점 · 점령 3곳") {} }
