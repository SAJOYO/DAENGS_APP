package com.daengs.app.ui.gait

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.gait.GaitComparison
import com.daengs.app.gait.GaitDelta
import com.daengs.app.gait.GaitMetric
import com.daengs.app.gait.GaitRecord
import com.daengs.app.gait.GaitSampleRecords
import com.daengs.app.gait.GaitVerdict
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDate

/**
 * 두 기록을 나란히 보는 화면.
 *
 * **이 화면에는 좋아졌다·나빠졌다가 없다.** `CONTEXT.md` 8절이 보행을 "기록·비교용"
 * 으로 못박아 뒀고, 그 원칙이 실제로 지켜지는지는 여기서 갈린다 — 두 시점을 나란히
 * 놓으면 화살표 하나만 그려도 곧바로 호전/악화 화면이 되기 때문이다. 그래서
 *
 *  - 지표는 [GaitDelta] 세 갈래뿐이고 셋 다 **차이의 유무**만 말한다
 *  - 어느 쪽이 낫다는 순서를 안 준다. 최근이 왼쪽인 것은 **시간 순서**일 뿐이다
 *  - 문장은 [GaitComparison.verdict] 가 지표에서 끌어낸 것을 그대로 쓴다
 *
 * @param onOpenDetail 최근 기록의 상세로 간다
 * @param onSaveToChat 이 비교를 대화에 남긴다. 서버에 저장하는 것이 아니다 —
 *   저장할 곳이 아직 없어서, "남긴다" 가 실제로 뜻하는 자리는 대화뿐이다
 */
@Composable
fun GaitCompareScreen(
    comparison: GaitComparison,
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onSaveToChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    // **두 편을 한 단추로 같이 돌린다.** 따로 돌리게 두면 한쪽을 보는 동안 다른
    // 쪽이 멈춰 있어서, 나란히 놓은 뜻이 없어진다 — 이 화면이 하려는 일이
    // "같은 순간의 두 시점" 이라 둘이 같이 움직여야 눈이 비교를 한다.
    var playing by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        GaitTopBar("보행 기록 비교", onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 두 기둥과 그 사이의 VS. 폭을 반씩 나눠서 크기로 순서를 안 준다 —
            // 한쪽을 크게 그리면 그쪽이 기준처럼 보인다.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ComparePillar(
                    comparison.recent,
                    "최근 기록",
                    accent = true,
                    playing = playing,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.width(46.dp).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(color = PinkSoft, shape = RoundedCornerShape(50)) {
                        Text(
                            "VS",
                            color = DaengPinkDeep,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        )
                    }
                }
                ComparePillar(
                    comparison.past,
                    "비교 기록",
                    accent = false,
                    playing = playing,
                    modifier = Modifier.weight(1f),
                )
            }

            // 둘 다 재생. 한쪽이라도 파일이 있어야 의미가 있다 — 표본끼리
            // 비교하면 돌릴 것이 없으므로 단추를 감춘다.
            if (comparison.recent.video != null || comparison.past.video != null) {
                GaitActionButton(
                    if (playing) DaengsIcon.Close else DaengsIcon.Play,
                    if (playing) "둘 다 멈춤" else "둘 다 재생",
                    { playing = !playing },
                    Modifier.fillMaxWidth(),
                    accent = playing,
                )
            }

            VerdictBanner(comparison.verdict)

            Surface(
                color = CardWhite,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, PinkSoft),
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    comparison.metrics.forEach { MetricRow(it) }
                    if (comparison.metrics.isEmpty()) {
                        Text("잴 수 있는 지표가 없어요.", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }

            // 이 화면이 판정이 아니라는 말은 **화면 안에** 있어야 한다. 문서에만
            // 적어 두면 화면을 보는 사람에게는 없는 말이다.
            Text(
                "이 비교는 같은 아이의 두 시점을 나란히 놓아 본 것이에요.\n" +
                    "건강 상태를 판단하거나 진단하지 않아요.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GaitActionButton(DaengsIcon.Chart, "결과 자세히 보기", onOpenDetail, Modifier.weight(1f))
            GaitActionButton(DaengsIcon.Chat, "대화에 남기기", onSaveToChat, Modifier.weight(1f), accent = true)
        }
    }
}

/**
 * 한쪽 기둥. 라벨 · 날짜 · 표지 · 길이.
 *
 * @param accent 최근 쪽만 분홍 테를 두른다. **더 중요하다는 뜻이 아니라** 둘이
 *   같은 모양이면 어느 쪽이 언제 것인지 날짜를 매번 읽어야 하기 때문이다
 */
@Composable
private fun ComparePillar(
    record: GaitRecord,
    label: String,
    accent: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (accent) PinkSoft.copy(alpha = 0.5f) else PinkFaint,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (accent) DaengPink.copy(alpha = 0.45f) else DaengsColors.BorderNeutral),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                label,
                color = if (accent) DaengPinkDeep else TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(record.dateLabel, color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            // 칸이 좁아 컨트롤을 끈다. 재생은 위의 단추가 둘을 같이 몬다.
            GaitVideoPlayer(
                record,
                Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                playing = playing,
                controls = false,
            )
            Text(record.lengthLabel, color = TextMuted, fontSize = 12.sp)
        }
    }
}

/**
 * 판정 문장 한 줄.
 *
 * **세 갈래 다 같은 색이다.** 차이가 관찰됐다고 노랑·빨강으로 칠하면 그 순간
 * 중립이던 문장이 경고가 된다 — 색은 글자보다 먼저 읽힌다.
 */
@Composable
private fun VerdictBanner(verdict: GaitVerdict) {
    Surface(color = PinkFaint, shape = RoundedCornerShape(15.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaengsIconView(DaengsIcon.Joint, Modifier.size(19.dp), tint = DaengPink)
            Spacer(Modifier.width(11.dp))
            Text(
                verdict.sentence,
                color = TextDark,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * 지표 한 줄.
 *
 * 오른쪽 점의 색만 갈린다 — "약간의 차이" 는 [DaengsColors.Warning] 이지만 **글자는
 * 검다.** 문장까지 주황으로 물들이면 주의보로 읽힌다.
 */
@Composable
private fun MetricRow(metric: GaitMetric) {
    val dot = when (metric.delta) {
        GaitDelta.Similar -> DaengsColors.Success
        GaitDelta.Slight -> DaengsColors.Warning
        GaitDelta.Unknown -> DaengsColors.BorderNeutral
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        DaengsIconView(DaengsIcon.Joint, Modifier.size(16.dp), tint = TextMuted)
        Spacer(Modifier.width(10.dp))
        Text(metric.name, color = TextDark, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            metric.delta.label,
            color = if (metric.delta == GaitDelta.Unknown) TextMuted else TextDark,
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dot))
    }
}

/** 보행 화면들이 같이 쓰는 상단바. 대화 헤더와 높이·되돌아가기 자리가 같다. */
@Composable
fun GaitTopBar(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = TextDark, fontSize = 34.sp, lineHeight = 30.sp) }
        Text(
            title,
            color = TextDark,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        trailing()
    }
}

@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitCompareScreenPreview() {
    val records = GaitSampleRecords.of(LocalDate.of(2026, 8, 31))
    val recent = GaitRecord("now", LocalDate.of(2026, 8, 31), seconds = 12)
    DaengsTheme {
        GaitCompareScreen(
            comparison = GaitComparison.of(recent, records[0], GaitSampleRecords.metricsFor(recent, records[0])),
            onBack = {},
            onOpenDetail = {},
            onSaveToChat = {},
        )
    }
}

/** 지표가 모자란 경우. 이쪽이 화면에서 제일 조용해야 한다. */
@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitCompareNotEnoughPreview() {
    val recent = GaitRecord("now", LocalDate.of(2026, 8, 31), seconds = 6, comparable = false)
    val past = GaitRecord("old", LocalDate.of(2026, 7, 15), seconds = 9, comparable = false)
    DaengsTheme {
        GaitCompareScreen(
            comparison = GaitComparison.of(recent, past, GaitSampleRecords.metricsFor(recent, past)),
            onBack = {},
            onOpenDetail = {},
            onSaveToChat = {},
        )
    }
}
