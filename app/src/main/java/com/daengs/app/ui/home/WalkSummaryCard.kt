package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.*
import com.daengs.app.ui.walk.formatWalkDistance
import com.daengs.app.walk.WalkDayTotals
import java.util.Locale

/** The existing home card, with an optional compact territory header and four walking metrics. */
@Composable
fun WalkSummaryCard(
    modifier: Modifier = Modifier,
    totals: WalkDayTotals? = null,
    onOpenHistory: (() -> Unit)? = null,
    territoryHeader: (@Composable () -> Unit)? = null,
) {
    var speedExplanation by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = CardWhite, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (territoryHeader != null) {
                territoryHeader()
                HorizontalDivider(color = PinkSoft, thickness = 1.dp)
            }
            Row(Modifier.padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("오늘의 산책", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.width(6.dp))
                DaengsIconView(DaengsIcon.Paw, Modifier.size(15.dp), DaengPink)
                Spacer(Modifier.weight(1f))
                onOpenHistory?.let { open ->
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = open)
                        .padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("지난 산책", color = DaengPinkDeep, fontSize = 13.sp)
                        DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(13.dp), DaengPinkDeep)
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 12.dp)
                .background(PinkFaint, RoundedCornerShape(18.dp)).padding(horizontal = 4.dp, vertical = 10.dp)) {
                // Keep four columns on ordinary phones; large accessibility text gets a readable 2x2 grid.
                val largeText = LocalDensity.current.fontScale > 1.3f
                val compact = maxWidth < 330.dp && !largeText
                val metrics = listOf(
                    HomeWalkMetric(DaengsIcon.Paws, totals?.let { "${it.count}회" } ?: "—", "횟수"),
                    HomeWalkMetric(DaengsIcon.Clock, totals?.let { formatHomeWalkMinutes(it.activeDurationMillis) } ?: "—", "시간"),
                    HomeWalkMetric(DaengsIcon.Pin, totals?.let { formatWalkDistance(it.distanceMeters) } ?: "—", "거리"),
                    HomeWalkMetric(DaengsIcon.Speedometer, formatHomeWalkSpeed(totals), "평균 속도"),
                )
                val columns = if (largeText) 2 else 4
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    metrics.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { metric ->
                                HomeWalkStat(metric, Modifier.weight(1f), compact = compact,
                                    onClick = if (metric.icon == DaengsIcon.Speedometer) ({ speedExplanation = true }) else null)
                            }
                        }
                    }
                }
            }
        }
    }
    if (speedExplanation) AlertDialog(onDismissRequest = { speedExplanation = false },
        title = { Text("평균 속도") },
        text = { Text("오늘 완료한 산책의 총 거리 ÷ 총 활동 시간이에요. 일시정지와 기록이 끊긴 시간은 제외하고, 기록 중 잠깐 멈춘 시간은 포함해요. 기록이 없으면 —로 표시해요.") },
        confirmButton = { TextButton(onClick = { speedExplanation = false }) { Text("확인") } })
}

private data class HomeWalkMetric(val icon: DaengsIcon, val value: String, val label: String)

@Composable
private fun HomeWalkStat(metric: HomeWalkMetric, modifier: Modifier, compact: Boolean, onClick: (() -> Unit)?) {
    val action = if (onClick == null) Modifier else Modifier.clip(RoundedCornerShape(10.dp))
        .clickable(role = Role.Button, onClickLabel = "평균 속도 계산 기준", onClick = onClick)
    Column(modifier.then(action).padding(vertical = 2.dp)
        .semantics(mergeDescendants = true) { contentDescription = "${metric.label} ${metric.value}" },
        horizontalAlignment = Alignment.CenterHorizontally) {
        DaengsIconView(metric.icon, Modifier.size(24.dp), DaengPink)
        Spacer(Modifier.height(6.dp))
        Text(metric.value, color = TextDark, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 16.sp)
        Text(metric.label, color = TextMuted, fontSize = 11.sp)
    }
}

internal fun formatHomeWalkMinutes(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000L
    return if (minutes >= 60L) "${minutes / 60L}시간 ${minutes % 60L}분" else "${minutes}분"
}

/** Weighted by total activity time, never an unweighted average of per-walk speeds. */
internal fun formatHomeWalkSpeed(totals: WalkDayTotals?): String {
    if (totals == null || totals.count <= 0 || totals.activeDurationMillis <= 0 ||
        !totals.distanceMeters.isFinite() || totals.distanceMeters < 0) return "—"
    return String.format(Locale.ROOT, "%.1fkm/h", totals.distanceMeters * 3_600.0 / totals.activeDurationMillis)
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
internal fun HomeSummaryPreview() {
    DaengsTheme {
        WalkSummaryCard(Modifier.padding(14.dp), WalkDayTotals(2, 1_920_000, 2300.0), {},
            territoryHeader = { HomeGameCard("보리의 점령 현황", "3곳 · 320점 · 순위 —", {}) })
    }
}

@Preview(widthDp = 320, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun HomeSummaryEmptyPreview() {
    DaengsTheme { WalkSummaryCard(Modifier.padding(14.dp), WalkDayTotals.EMPTY, {},
        territoryHeader = { HomeGameCard("점령 현황", "진행 중인 시즌이 없어요", {}) }) }
}

@Preview(widthDp = 320, fontScale = 1.5f, showBackground = true)
@Composable
private fun HomeSummaryLargeTextPreview() {
    DaengsTheme { WalkSummaryCard(Modifier.padding(14.dp), territoryHeader = {
        HomeGameCard("점령 현황", "시즌 현황을 불러오고 있어요", {})
    }) }
}
