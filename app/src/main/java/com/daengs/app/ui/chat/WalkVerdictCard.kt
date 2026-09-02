package com.daengs.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.assistant.WalkVerdict
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDateTime

/**
 * 산책 판정 카드.
 *
 * **여기서 판단하지 않는다.** 등급도, 어느 축이 깎았는지도, 추천 시간대도 저쪽이
 * 정해서 [WalkVerdict] 에 담아 준 것이고 이 파일은 자리를 정할 뿐이다. 이유 줄
 * ("체감온도 33.3℃ (여름식)") 도 저쪽이 지은 문장을 그대로 쓴다 — 앱에서 다시
 * 쓰면 같은 값을 두 군데서 말하게 된다.
 *
 * 말풍선이 아니라 카드인 이유는 담을 것이 산문이 아니라 **측정값과 시간 범위**여서다.
 * 숫자를 문장에 녹이면 오히려 읽기 나쁘고, 시간대가 여럿이면 늘어지고, 무엇보다
 * **위험한 날에 색을 줄 수가 없다** (저쪽은 `UNSAFE` 도 `ANSWERED` 로 보낸다).
 *
 * **등급 문장은 여기 없다.** 그건 위 말풍선이 맡는다 ([walkSentence]) — 여긴 챗봇이라
 * 판정은 말로 오는 편이 자연스럽고, 카드가 같은 말을 또 하면 두 번 읽게 된다.
 *
 * **접힌 채로 온다.** 대화는 흘러가는 곳이라 카드가 매번 다 펼쳐져 있으면 위에 있던
 * 이야기가 밀려난다 (`GaitComparedBubble` 이 표를 접어 두는 것과 같은 이유). 접혔을
 * 때도 **추천 시간대는 보인다** — 이 응답에서 바로 쓸 수 있는 건 그것뿐이라서다.
 */
@Composable
fun WalkVerdictCard(verdict: WalkVerdict, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PinkSoft),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Text("오늘 산책", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                GradeChip(verdict.grade)
                Spacer(Modifier.weight(1f))
                Text(
                    if (expanded) "접기" else "자세히",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(4.dp))
                DaengsIconView(
                    DaengsIcon.CaretDown,
                    tint = TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
            }

            if (verdict.windows.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "나가기 좋은 시간",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    verdict.windows.take(MAX_WINDOWS).forEach { WindowChip(it) }
                }
            }

            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (verdict.capped) {
                        Text(
                            "일부 값을 몰라 한 단계 낮춰 봤어요.",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    verdict.reasons.forEach { reason ->
                        Row {
                            Text("· ", color = TextMuted, fontSize = 13.sp)
                            Text(reason, color = TextDark, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                    verdict.notes.forEach { note ->
                        Text(note, color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    if (verdict.locationLabel.isNotBlank()) {
                        Text(verdict.locationLabel, color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** 등급 뱃지. 카드에서 색을 지닌 유일한 자리라 위험한 날이 여기서 눈에 띈다. */
@Composable
private fun GradeChip(grade: WalkVerdict.Grade) {
    Surface(color = gradeTint(grade), shape = RoundedCornerShape(8.dp)) {
        Text(
            gradeShort(grade),
            color = gradeColor(grade),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun WindowChip(window: WalkVerdict.Window) {
    Surface(color = PinkFaint, shape = RoundedCornerShape(10.dp)) {
        Text(
            "${hour(window.from)}~${hour(window.to)}",
            color = TextDark,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** `10시`. 분은 안 쓴다 — 저쪽 시간대는 정시 단위라 `10시 00분` 은 자리만 먹는다. */
private fun hour(at: LocalDateTime): String = "${at.hour}시"

private fun gradeShort(grade: WalkVerdict.Grade): String = when (grade) {
    WalkVerdict.Grade.GOOD -> "좋음"
    WalkVerdict.Grade.CAUTION -> "주의"
    WalkVerdict.Grade.UNSAFE -> "위험"
    WalkVerdict.Grade.UNKNOWN -> "모름"
}

private fun gradeColor(grade: WalkVerdict.Grade): Color = when (grade) {
    WalkVerdict.Grade.GOOD -> DaengsColors.Success
    WalkVerdict.Grade.CAUTION -> DaengsColors.Warning
    WalkVerdict.Grade.UNSAFE -> DaengsColors.Error
    WalkVerdict.Grade.UNKNOWN -> TextMuted
}

private fun gradeTint(grade: WalkVerdict.Grade): Color = gradeColor(grade).copy(alpha = 0.12f)

/** 칩을 몇 개까지 늘어놓을지. 한 줄을 넘기면 접히지 않고 잘려 나가서 끊는다. */
private const val MAX_WINDOWS = 3

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun WalkVerdictCardPreview() {
    val base = LocalDateTime.of(2026, 9, 2, 10, 0)
    DaengsTheme {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WalkVerdictCard(
                WalkVerdict(
                    grade = WalkVerdict.Grade.GOOD,
                    reasons = listOf("체감온도 21.4℃ (여름식)", "PM10 18㎍/㎥", "강수 없음"),
                    capped = false,
                    windows = listOf(
                        WalkVerdict.Window(base, base.plusHours(2), WalkVerdict.Grade.GOOD),
                        WalkVerdict.Window(
                            base.plusHours(7),
                            base.plusHours(9),
                            WalkVerdict.Grade.GOOD,
                        ),
                    ),
                    locationLabel = "역삼1동 (측정소: 강남구) 기준",
                    notes = emptyList(),
                ),
            )
            WalkVerdictCard(
                WalkVerdict(
                    grade = WalkVerdict.Grade.UNSAFE,
                    reasons = listOf("체감온도 33.3℃ (여름식)", "폭염주의보"),
                    capped = true,
                    windows = listOf(
                        WalkVerdict.Window(
                            base.plusHours(11),
                            base.plusHours(13),
                            WalkVerdict.Grade.CAUTION,
                        ),
                    ),
                    locationLabel = "역삼1동 (측정소: 강남구) 기준",
                    notes = listOf("한낮에는 아스팔트가 뜨거워 발바닥이 델 수 있어요."),
                ),
            )
        }
    }
}
