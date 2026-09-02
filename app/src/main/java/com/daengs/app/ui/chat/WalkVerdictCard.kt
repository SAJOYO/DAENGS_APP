package com.daengs.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
                    if (verdict.timeline.isNotEmpty()) TimelineStrip(verdict.timeline)
                    if (verdict.capped) {
                        Text(
                            "일부 값을 몰라 한 단계 낮춰 봤어요.",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    verdict.axes.forEach { AxisRow(it) }
                    if (verdict.locationLabel.isNotBlank()) {
                        Text(verdict.locationLabel, color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * 축 한 줄 — `기온   주의   체감온도 33.3℃ (여름식)`.
 *
 * **등급이 숫자보다 앞이다.** `PM10 42㎍/㎥` 로는 대부분 판단을 못 하는데, 그 해석은
 * 저쪽이 축마다 등급으로 이미 붙여 놨다. 숫자는 뒤에 흐리게 남겨 아는 사람만 본다.
 */
@Composable
private fun AxisRow(axis: WalkVerdict.Axis) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            axis.label,
            color = TextDark,
            fontSize = 13.sp,
            modifier = Modifier.width(AXIS_LABEL_WIDTH),
        )
        Text(
            gradeShort(axis.grade),
            color = gradeColor(axis.grade),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(AXIS_GRADE_WIDTH),
        )
        if (axis.note.isNotBlank()) {
            Text(axis.note, color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

/** 축 이름 칸. 세 줄의 등급이 세로로 맞아야 훑을 수 있다. */
private val AXIS_LABEL_WIDTH = 60.dp

/** 등급 칸. */
private val AXIS_GRADE_WIDTH = 44.dp

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
            windowSpan(window),
            color = TextDark,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * `12시` 또는 `12시~14시`.
 *
 * **끝이 시작과 같으면 범위로 안 쓴다.** 저쪽 `to` 는 구간의 끝이 아니라 "마지막으로
 * 좋은 시각" 이라, 좋은 시점이 하나뿐이면 둘이 같아진다. 그걸 그대로 그리면
 * `12시~12시` 라는 없는 범위가 된다.
 */
internal fun windowSpan(window: WalkVerdict.Window): String =
    if (window.isPoint) hour(window.from) else "${hour(window.from)}~${hour(window.to)}"

/** `10시`. 분은 안 쓴다 — 저쪽 시간대는 정시 단위라 `10시 00분` 은 자리만 먹는다. */
private fun hour(at: LocalDateTime): String = "${at.hour}시"

/**
 * 시간별 등급 띠.
 *
 * **"3시간 후에는?" 을 되묻지 않게 하는 자리다.** v1 오케스트레이션은 무상태라 그
 * 질문이 새 질문으로 가서 "지금" 을 다시 판정해 준다. 값은 이미 왔으니 펴 둔다.
 *
 * 가로로 민다 — 24시간을 한 줄에 욱여넣으면 칸이 글자보다 좁아진다.
 */
@Composable
private fun TimelineStrip(points: List<WalkVerdict.Point>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("시간대별", color = TextMuted, fontSize = 12.sp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            points.forEach { point ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = gradeTint(point.grade),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            gradeShort(point.grade),
                            color = gradeColor(point.grade),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                    Text("${point.at.hour}", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

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
                    axes = listOf(
                        WalkVerdict.Axis("heat", "기온", WalkVerdict.Grade.GOOD, "체감온도 21.4℃ (여름식)"),
                        WalkVerdict.Axis("air", "미세먼지", WalkVerdict.Grade.GOOD, "PM10 18㎍/㎥"),
                        WalkVerdict.Axis("rain", "비", WalkVerdict.Grade.GOOD, "강수 없음"),
                    ),
                    capped = false,
                    windows = listOf(
                        WalkVerdict.Window(base, base, WalkVerdict.Grade.GOOD),
                        WalkVerdict.Window(
                            base.plusHours(7),
                            base.plusHours(9),
                            WalkVerdict.Grade.GOOD,
                        ),
                    ),
                    timeline = (0..11).map {
                        WalkVerdict.Point(
                            base.plusHours(it.toLong()),
                            if (it in 3..5) WalkVerdict.Grade.CAUTION else WalkVerdict.Grade.GOOD,
                        )
                    },
                    locationLabel = "역삼1동 (측정소: 강남구) 기준",
                ),
            )
            WalkVerdictCard(
                WalkVerdict(
                    grade = WalkVerdict.Grade.UNSAFE,
                    axes = listOf(
                        WalkVerdict.Axis("heat", "기온", WalkVerdict.Grade.UNSAFE, "체감온도 33.3℃ (여름식)"),
                        WalkVerdict.Axis("air", "미세먼지", WalkVerdict.Grade.CAUTION, "PM10 82㎍/㎥"),
                        WalkVerdict.Axis("rain", "비", WalkVerdict.Grade.GOOD, "강수 없음"),
                    ),
                    capped = true,
                    windows = listOf(
                        WalkVerdict.Window(
                            base.plusHours(11),
                            base.plusHours(13),
                            WalkVerdict.Grade.CAUTION,
                        ),
                    ),
                    timeline = (0..11).map {
                        WalkVerdict.Point(
                            base.plusHours(it.toLong()),
                            if (it < 4) WalkVerdict.Grade.UNSAFE else WalkVerdict.Grade.UNKNOWN,
                        )
                    },
                    locationLabel = "역삼1동 (측정소: 강남구) 기준",
                ),
            )
        }
    }
}
