package com.daengs.app.ui.nickname

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 이름을 확인하고 고치는 자리. **두 곳에서 들어온다.**
 *
 * - **로그인 직후** — 인사말과 함께 서버가 지어 준 이름을 보여 준다
 * - **「마이」의 "고치기"** — [startEditing] 으로 인사말을 건너뛰고 곧장 고친다
 *
 * ## 왜 묻지 않고 보여 주나
 *
 * 랜딩이 "카카오로 시작하기" 라고 약속한다. 그 다음 화면이 빈 칸을 내밀고 "이미 사용
 * 중입니다" 로 거절할 수 있으면 그 약속이 깨진다. 그래서 **서버가 하나 지어 주고**
 * 여기서는 보여 주기만 한다 — 바꾸고 싶은 사람만 한 번 더 누른다.
 *
 * ## 왜 "본 적 있음" 을 저장하지 않나
 *
 * 인사말은 **로그인한 그 순간에만** 뜬다 (`MainActivity` 가 `signIn` 성공 갈래에서만
 * 이 화면으로 보낸다). 저장된 토큰으로 켤 때는 안 뜬다. 방 둘러보기처럼 기기에 표를
 * 남길 필요가 없고, 남기면 계정마다 지워 줘야 하는 값이 하나 는다.
 *
 * @param issued 서버가 지어 준 이름. **null 이면 이 화면에 오지 않는다** —
 *   부르는 쪽이 홈으로 보낸다 (옛 서버이거나 `me` 가 실패한 경우)
 * @param busy 저장하는 중. 두 번 눌리는 것을 막는다
 * @param error 저장이 실패한 이유. 남이 채간 경우가 여기로 온다
 * @param ask 이름을 물어본다. null 이면 (프리뷰) 안 묻는다
 * @param startEditing 인사말을 건너뛰고 곧장 고치기로 연다 (「마이」에서 들어올 때)
 */
@Composable
fun NicknameScreen(
    issued: String,
    onStart: () -> Unit,
    onSave: (String) -> Unit,
    busy: Boolean = false,
    error: String? = null,
    ask: (suspend (String) -> Result<Boolean>)? = null,
    startEditing: Boolean = false,
) {
    // 「마이」의 "고치기" 로 들어오면 인사말을 건너뛴다. 이미 아는 이름에 대고
    // "이름을 하나 지어 뒀어요" 라고 하면 무엇을 하는 화면인지 안 읽힌다.
    var editing by remember { mutableStateOf(startEditing) }
    var typed by remember { mutableStateOf(issued) }
    val check = rememberNicknameCheck(typed, if (editing) issued else null, ask.takeIf { editing })

    Box(Modifier.fillMaxSize().background(CreamBg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (editing) {
                Text("뭐라고 부를까요?", color = TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "나중에 「마이」에서 언제든 바꿀 수 있어요.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(22.dp))
                NicknameField(
                    value = typed,
                    onChange = { typed = it },
                    check = check,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // **문장을 이름으로 자르지 않는다.** 예전에는 "네 이름은 / <이름> / 야"
                // 세 줄이었다. 조사 "야" 만 남은 줄이 생기고, 앱에서 여기만 반말이었다
                // (버튼은 "이 이름으로 시작", 다음 화면은 "뭐라고 부를까요?" 다).
                Text("반가워요!", color = TextMuted, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "이름을 하나 지어 뒀어요",
                    color = TextDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    issued,
                    color = DaengPink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            // 저장이 실패한 이유. **화면에 그대로 둔다** — 남이 채간 경우가 여기로 오고,
            // 그때 사용자는 다른 이름을 골라야 한다.
            error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = DaengsColors.Error, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))
            if (editing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WideButton(
                        // 「마이」에서 들어왔으면 되돌아갈 인사말이 없다. 그때는 나간다.
                        label = if (startEditing) "그만두기" else "그냥 쓸래요",
                        fill = PinkFaint,
                        tint = TextMuted,
                        enabled = !busy,
                        onClick = {
                            if (startEditing) onStart() else { editing = false; typed = issued }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    WideButton(
                        label = if (busy) "저장하는 중" else "이 이름으로",
                        fill = DaengPink,
                        tint = CardWhite,
                        enabled = !busy && nicknameSavable(typed, check),
                        onClick = { onSave(typed.trim()) },
                        modifier = Modifier.weight(1.3f),
                    )
                }
            } else {
                WideButton(
                    label = "이 이름으로 시작",
                    fill = DaengPink,
                    tint = CardWhite,
                    enabled = !busy,
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "바꿀래요",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !busy) { editing = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun WideButton(
    label: String,
    fill: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        label,
        color = tint,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            // 못 누르는 버튼은 **색으로도** 말해 준다. 눌리기만 안 되면 고장으로 읽힌다.
            .background(if (enabled) fill else fill.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
    )
}

// -- 프리뷰 ------------------------------------------------------------------
//
// **실기기에서는 못 본다.** 이 폰의 디버그 빌드로 카카오 로그인이 안 되고, 이 화면은
// 로그인 직후에만 뜬다. 여기가 유일하게 보는 자리다.

@Preview(name = "이름 확인", widthDp = 360, heightDp = 640)
@Composable
private fun NicknameScreenPreview() {
    DaengsTheme { NicknameScreen(issued = "댕댕이7K2Q", onStart = {}, onSave = {}) }
}

@Preview(name = "이름 확인 · 긴 이름", widthDp = 360, heightDp = 640)
@Composable
private fun NicknameScreenLongPreview() {
    DaengsTheme { NicknameScreen(issued = "가".repeat(MAX_NICKNAME), onStart = {}, onSave = {}) }
}

@Preview(name = "이름 확인 · 남이 채갔을 때", widthDp = 360, heightDp = 640)
@Composable
private fun NicknameScreenTakenPreview() {
    DaengsTheme {
        NicknameScreen(
            issued = "댕댕이7K2Q",
            onStart = {},
            onSave = {},
            error = "방금 다른 분이 가져갔어요. 다른 이름을 골라 주세요.",
        )
    }
}

/** 「마이」에서 들어온 모습. 인사말 없이 곧장 고치는 자리다. */
@Preview(name = "이름 고치기 (마이)", widthDp = 360, heightDp = 640)
@Composable
private fun NicknameScreenEditPreview() {
    DaengsTheme {
        NicknameScreen(issued = "네옹집사", onStart = {}, onSave = {}, startEditing = true)
    }
}
