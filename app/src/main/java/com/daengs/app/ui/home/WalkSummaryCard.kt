package com.daengs.app.ui.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.dashEffect
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.miniroom.OutsideView
import com.daengs.app.ui.walk.formatWalkDistance
import com.daengs.app.walk.WalkDayTotals

private val StatAccent = listOf(
    Color(0xFFF2B441),
    Color(0xFFEE8F5A),
    Color(0xFFEE7FA0),
)

/**
 * 오늘 걸은 것.
 *
 * **숫자는 진짜다.** 예전엔 `HomeDemoData.WALK_STATS` 에 박힌 `1회 · 32분 · 2.3km` 가
 * 그대로 떴는데, 진짜로 걸어도 안 변하니 카드가 거짓말을 하고 있었다.
 *
 * @param totals null 이면 아직 못 읽은 것이다. 0 과 다르다 — 0 으로 그리면 기록이
 *   있는데도 "0회" 가 잠깐 스친다.
 */
@Composable
fun WalkSummaryCard(
    modifier: Modifier = Modifier,
    totals: WalkDayTotals? = null,
    /**
     * 오늘의 한 마디 두 줄. **기본값을 안 준다** — 잊으면 컴파일러가 짚어 준다.
     * 기본값을 두면 비 오는 날에도 "산책하기 딱 좋은 날" 이 조용히 남는다.
     */
    dailyWord: List<String>,
    onOpenHistory: (() -> Unit)? = null,
    territoryHeader: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CardWhite,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
            if (territoryHeader != null) {
                territoryHeader()
                androidx.compose.material3.HorizontalDivider(color = PinkSoft)
                Spacer(Modifier.height(10.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    HomeDemoData.WALK_TITLE,
                    color = TextDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.width(6.dp))
                DaengsIconView(DaengsIcon.Paw, Modifier.size(15.dp), tint = DaengPink)
                Spacer(Modifier.weight(1f))
                // 오늘 것만 여기 뜬다. 지난 산책은 목록에서 본다.
                onOpenHistory?.let { open ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = open)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("지난 산책", color = DaengPinkDeep, fontSize = 13.sp)
                        Spacer(Modifier.width(2.dp))
                        DaengsIconView(
                            DaengsIcon.ChevronRight,
                            Modifier.size(13.dp),
                            tint = DaengPinkDeep,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // **좁으면 한 마디를 아래로 내린다.**
            //
            // 넓은 화면의 두 칸 배치에서 이 카드가 화면의 절반만 받는데, 그때도 나란히
            // 두면 한 마디가 세 줄로 쪼개지고 하트와 글자가 겹친다. 나란히 둘 만한
            // 폭인지를 재서 정한다 — 화면 크기가 아니라 이 카드가 실제로 받은 폭이다.
            BoxWithConstraints {
                val sideBySide = maxWidth >= SUMMARY_SIDE_BY_SIDE_MIN
                val stats: @Composable (Modifier) -> Unit = { m ->
                    Row(
                        m
                            .background(PinkFaint, RoundedCornerShape(18.dp))
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        todayStats(totals).forEachIndexed { i, stat ->
                            StatItem(stat, StatAccent[i % StatAccent.size])
                        }
                    }
                }
                if (sideBySide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        stats(Modifier.weight(1f))
                        Spacer(Modifier.width(11.dp))
                        DailyWordNote(dailyWord, Modifier.weight(1f))
                    }
                } else {
                    Column {
                        stats(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(11.dp))
                        DailyWordNote(dailyWord, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/**
 * 카드에 올릴 세 칸.
 *
 * 아직 못 읽었으면 `-` 다. **0 으로 그리지 않는다** — 기록이 있는데도 "0회" 가
 * 스치면 사용자는 기록이 날아간 줄 안다.
 */
private fun todayStats(totals: WalkDayTotals?): List<HomeDemoData.WalkStat> = listOf(
    HomeDemoData.WalkStat(DaengsIcon.Paws, totals?.let { "${it.count}회" } ?: "-", "횟수"),
    HomeDemoData.WalkStat(
        DaengsIcon.Clock,
        totals?.let { formatWalkMinutes(it.activeDurationMillis) } ?: "-",
        "시간",
    ),
    HomeDemoData.WalkStat(
        DaengsIcon.Pin,
        totals?.let { formatWalkDistance(it.distanceMeters) } ?: "-",
        "거리",
    ),
)

/**
 * 홈 카드의 시간 표기.
 *
 * 목록·상세는 `00:32` 처럼 초까지 쓰지만(`formatWalkDuration`), 여기는 하루치 합이라
 * **분이면 충분하다.** 카드가 좁아서 자릿수가 늘면 세 칸이 서로 밀린다.
 */
private fun formatWalkMinutes(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000L
    return if (minutes >= 60L) "${minutes / 60L}시간 ${minutes % 60L}분" else "${minutes}분"
}

@Composable
private fun StatItem(stat: HomeDemoData.WalkStat, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DaengsIconView(stat.icon, Modifier.size(24.dp), tint = accent)
        Spacer(Modifier.height(6.dp))
        Text(stat.value, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(1.dp))
        Text(stat.label, color = TextMuted, fontSize = 11.sp)
    }
}

/** 시안의 압정으로 꽂은 메모지. */
@Composable
private fun DailyWordNote(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 15.dp, height = 13.dp)
                .background(DaengPink, RoundedCornerShape(4.dp)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(CardWhite, RoundedCornerShape(14.dp))
                .drawBehind {
                    drawRoundRect(
                        color = PinkSoft,
                        topLeft = Offset(2f, 2f),
                        size = Size(size.width - 4f, size.height - 4f),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        style = Stroke(width = 2.5f, pathEffect = dashEffect()),
                    )
                }
                .padding(horizontal = 13.dp, vertical = 12.dp),
        ) {
            Column {
                Text(
                    HomeDemoData.DAILY_WORD_TITLE,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                lines.forEach {
                    Text(it, color = DaengPinkDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            DaengsIconView(
                DaengsIcon.Heart,
                Modifier.size(17.dp).align(Alignment.BottomEnd),
                tint = DaengPink,
            )
        }
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun WalkSummaryCardPreview() {
    DaengsTheme {
        WalkSummaryCard(
            Modifier.padding(14.dp),
            totals = WalkDayTotals(
                count = 2,
                activeDurationMillis = 1_920_000L,
                distanceMeters = 2_310.0,
            ),
            dailyWord = homeWeatherWords(OutsideView.DAY_CLEAR, 21f).daily,
            onOpenHistory = {},
            territoryHeader = { HomeGameCard("보리의 이번 시즌", "3곳 · 320점 · 순위 —", {}) },
        )
    }
}

/** 아직 못 읽은 상태. 세 칸이 `-` 여야 한다 — 0 으로 그리면 거짓말이 스친다. */
@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun WalkSummaryCardUnreadPreview() {
    DaengsTheme {
        WalkSummaryCard(
            Modifier.padding(14.dp),
            totals = null,
            // 비 오는 날의 한 마디. 창밖이 비면 이 카드도 같이 바뀐다.
            dailyWord = homeWeatherWords(OutsideView.DAY_RAIN, 18f).daily,
            onOpenHistory = {},
        )
    }
}

/** 오늘 아직 안 걸었을 때. `0회 · 0분 · 0m` 다. */
@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun WalkSummaryCardEmptyPreview() {
    DaengsTheme {
        WalkSummaryCard(
            Modifier.padding(14.dp),
            totals = WalkDayTotals.EMPTY,
            dailyWord = homeWeatherWords(OutsideView.NIGHT_CLEAR, 8f).daily,
            onOpenHistory = {},
        )
    }
}

/**
 * 통계 세 칸과 「오늘의 한 마디」를 나란히 둘 수 있는 최소 폭.
 *
 * 넓은 화면의 두 칸 배치에서 이 카드가 절반만 받는데, 그 폭(≈300dp)에 나란히 두면
 * 한 마디가 세 줄로 쪼개지고 하트와 글자가 겹친다. 폰(≈383dp)은 이 선 위라 지금
 * 모습이 그대로다.
 */
private val SUMMARY_SIDE_BY_SIDE_MIN = 340.dp
