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
import kotlinx.coroutines.delay

/** Home is a compact entry point; the full overview owns score details and game rules. */
@Composable
fun HomeGameRoute(repository: ActivityRepository, ownerId: String?, petId: String?, petName: String?, onOpenGame: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    key(ownerId, petId) {
        var overview by remember { mutableStateOf(com.daengs.app.ui.game.TerritoryGameOverview()) }
        LaunchedEffect(repository, ownerId, petId, lifecycle) {
            if (ownerId != null && petId != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    overview = com.daengs.app.ui.game.loadGameOverview(repository, petId)
                    delay(30_000)
                }
            }
        }
        val message = when {
            ownerId == null -> "로그인하고 시즌 성적 보기"
            petId == null -> "우리 강아지와 점령을 시작해요"
            overview.status == com.daengs.app.ui.game.GameOverviewStatus.READY ->
                "${overview.points ?: "—"} 점 · 점령 ${overview.owned ?: "—"}곳" + if (overview.aggregating) " · 집계 중" else ""
            else -> overview.message
        }
        HomeGameCard(petName?.takeIf { ownerId != null }?.let { "${it}의 이번 시즌" } ?: "점령 게임", message, onOpenGame)
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
