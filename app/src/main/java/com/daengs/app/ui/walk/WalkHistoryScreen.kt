package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.walk.RecordedWeather
import com.daengs.app.walk.WalkHistory
import com.daengs.app.walk.WalkSummary

/**
 * 지난 산책.
 *
 * **끝난 산책만 나온다.** 강제 종료로 열린 채 남은 세션은 목록에 안 온다 — 그건
 * 기록이 아니라 사고의 흔적이라, 이어 기록할지 버릴지 정하기 전에는 보여 주지 않는다.
 *
 * 거리·시간은 저장된 숫자가 아니라 **원본 좌표로 그때그때 계산한 값**이다
 * (`summarize`). 그래서 산책 중에 보던 숫자와 목록의 숫자가 같다.
 */
@Composable
fun WalkHistoryScreen(
    history: WalkHistory,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 서버와 맞추라는 신호. 화면이 직접 하지 않는 이유는 토큰이 여기 없어서다.
     *
     * **끝나기를 기다리지 않는다.** 목록은 로컬을 읽어 바로 뜨고, 동기화로 뭔가
     * 들어오면 아래 `LaunchedEffect` 가 한 번 더 읽어 채운다.
     */
    onSync: (() -> Unit)? = null,
) {
    var walks by remember { mutableStateOf<List<WalkSummary>?>(null) }

    LaunchedEffect(history) {
        walks = history.finished()
        onSync?.invoke()
        // 동기화가 로컬을 채울 시간을 준 뒤 한 번 더 읽는다. 새 폰에서 처음 열면
        // 이 두 번째 읽기에 지난 산책이 들어온다.
        kotlinx.coroutines.delay(SYNC_SETTLE_MS)
        walks = history.finished()
    }

    BackHandler(onBack = onBack)

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 18.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaengsTextAction("← 뒤로", onBack)
            Spacer(Modifier.width(4.dp))
            Text("지난 산책", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        val list = walks
        when {
            // null 은 **아직 못 읽은 것**이다. 빈 목록과 같은 말을 하면 안 된다 —
            // 기록이 있는데도 "없어요" 가 잠깐 스친다.
            list == null -> Unit

            list.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "아직 산책 기록이 없어요.\n방문을 열고 산책을 시작해 보세요.",
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.sessionId }) { walk ->
                    WalkRow(walk) { onOpen(walk.sessionId) }
                }
            }
        }
    }
}

@Composable
private fun WalkRow(walk: WalkSummary, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardWhite,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                formatWalkDay(walk.startedAtMillis),
                color = TextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                walkRowSubtitle(walk),
                color = TextMuted,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * 아는 것만 적는다.
 *
 * 날씨를 못 받은 산책은 날씨 조각이 통째로 빠진다 — 빈 자리를 "맑음"이나 "-" 로
 * 채우지 않는다 (`PlaceFacts` 와 같은 원칙).
 */
private fun walkRowSubtitle(walk: WalkSummary): String = buildList {
    add(formatWalkClock(walk.startedAtMillis))
    add(formatWalkDuration(walk.activeDurationMillis))
    add(formatWalkDistance(walk.distanceMeters))
    walk.weather?.let { add(weatherLabel(it)) }
}.joinToString(" · ")

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun WalkHistoryRowPreview() {
    DaengsTheme {
        Column(Modifier.background(CreamBg).padding(18.dp)) {
            WalkRow(
                WalkSummary(
                    sessionId = "s1",
                    dogIds = listOf("dog-1"),
                    startedAtMillis = 1_756_600_000_000L,
                    endedAtMillis = 1_756_602_000_000L,
                    weather = RecordedWeather(weatherCode = 61, isDay = true, temperatureC = 18.5f),
                    distanceMeters = 1_842.0,
                    activeDurationMillis = 1_920_000L,
                    segments = emptyList(),
                    anchor = null,
                ),
            ) {}
            Spacer(Modifier.height(10.dp))
            // 날씨를 못 받은 산책. 줄에서 날씨만 빠진다.
            WalkRow(
                WalkSummary(
                    sessionId = "s2",
                    dogIds = emptyList(),
                    startedAtMillis = 1_756_500_000_000L,
                    endedAtMillis = 1_756_500_600_000L,
                    weather = null,
                    distanceMeters = 420.0,
                    activeDurationMillis = 540_000L,
                    segments = emptyList(),
                    anchor = null,
                ),
            ) {}
        }
    }
}

/**
 * 동기화가 로컬을 채울 때까지 기다리는 시간.
 *
 * 목록을 붙잡아 두는 시간이 아니다 — 먼저 뜨고, 이만큼 뒤에 **한 번 더 읽을 뿐**이다.
 * 서버 왕복이 이보다 오래 걸리면 다음에 열 때 보인다.
 */
private const val SYNC_SETTLE_MS = 1_500L
