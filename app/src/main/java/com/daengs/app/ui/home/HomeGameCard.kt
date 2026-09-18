package com.daengs.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.daengs.app.ui.theme.TextMuted
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
 * 시즌 한 줄의 높이.
 *
 * **재서 정했다.** 글자는 17.4dp 인데 이 줄이 차지하는 띠가 **58dp** 였다
 * (Pixel 3 XL 실측). `TextButton` 안쪽 `Row` 에 걸린 `ButtonDefaults.MinHeight` 때문이다 —
 * 아래에서 `LocalMinimumInteractiveComponentSize` 를 풀어 둔 것만으로는 안 줄었다.
 * 그건 48dp **터치 타깃**을 푸는 것이고 버튼 **최소 높이**는 별개다.
 * 그래서 높이를 직접 준다.
 *
 * 잠금 테스트: `HomeBottomBarLockTest`.
 */
internal val HomeGameRowHeight = 28.dp

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
    // **터치 타깃 최소 높이를 푼다.** Material3 는 버튼에 48dp 를 강제하는데, 이 줄은
    // 글자 한 줄(약 20dp)에 위아래 6dp 라 32dp 다. 모자란 16dp 가 보이지 않는 여백으로
    // 위아래에 붙어서, 한 줄짜리 줄 밑이 유난히 떠 보였다. 홈은 미니룸이 주인공인
    // 화면이라 그 자리를 방에 돌려준다.
    //
    // 접근성 손실이 작은 이유: 이 줄은 **화면 폭 전체**가 눌리는 자리라, 높이가 32dp 로
    // 줄어도 손가락이 빗나가기 어렵다. 폭이 좁은 아이콘 버튼이었다면 안 했을 것이다.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    TextButton(
        onClick = onOpen,
        // 높이를 **직접 준다.** 겉에서 고정 제약을 걸면 안쪽 `Row` 의
        // `defaultMinSize(minHeight = 40.dp)` 가 최대 제약에 막혀 못 부푼다.
        modifier = Modifier
            .fillMaxWidth()
            .height(HomeGameRowHeight)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
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
}

@Preview(widthDp = 360, showBackground = true)
@Composable
private fun HomeGamePreview() { HomeGameCard("보리의 이번 시즌", "320 점 · 점령 3곳") {} }
