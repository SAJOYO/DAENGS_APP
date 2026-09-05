package com.daengs.app.ui.nickname

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.delay

/**
 * 입력이 멈추고 이만큼 뒤에 물어본다.
 *
 * 글자마다 부르면 "네옹이" 를 치는 동안 요청이 셋 나간다. 사람이 한 글자를 더 칠지
 * 기다리는 시간이라 **너무 길면 답이 늦게 오고, 너무 짧으면 낭비**다.
 */
const val NICKNAME_DEBOUNCE_MS = 350L

/**
 * 지금 이 글자의 상태. **묻는 일정까지 여기서 든다.**
 *
 * `LaunchedEffect(value)` 가 키라, 글자가 바뀌면 **앞선 대기와 요청이 취소된다** —
 * "앞선 요청은 버린다" 가 따로 코드를 쓰지 않고 성립하는 자리다.
 *
 * @param current 지금 저장돼 있는 이름. 같은 값이면 안 물어본다
 * @param ask 서버에 물어본다. null 이면 (프리뷰·테스트) 묻지 않고
 *   [NicknameCheck.Idle] 에 머문다
 */
@Composable
fun rememberNicknameCheck(
    value: String,
    current: String?,
    ask: (suspend (String) -> Result<Boolean>)?,
): NicknameCheck {
    var check by remember { mutableStateOf<NicknameCheck>(NicknameCheck.Idle) }

    LaunchedEffect(value, current, ask) {
        val shapeError = nicknameErrorOf(value)
        // **모양은 즉시 말한다.** 빈 칸에 350ms 를 기다렸다 말하면 늦게 반응하는
        // 것으로 읽힌다. 아무것도 안 친 상태는 잔소리를 안 한다.
        if (shapeError != null) {
            check = if (value.isEmpty()) NicknameCheck.Idle else NicknameCheck.Shape(shapeError)
            return@LaunchedEffect
        }
        if (ask == null || !shouldAskAvailability(value, current)) {
            check = NicknameCheck.Idle
            return@LaunchedEffect
        }
        check = NicknameCheck.Checking
        delay(NICKNAME_DEBOUNCE_MS)
        check = ask(value.trim()).fold(
            onSuccess = { if (it) NicknameCheck.Free else NicknameCheck.Taken },
            // **초록불을 켜지 않는다.** 못 물어본 것과 비어 있는 것은 다르다.
            onFailure = { NicknameCheck.Unknown },
        )
    }
    return check
}

/**
 * 이름을 치는 칸. **첫 이름 확인 화면과 「마이」가 같은 것을 쓴다** — 두 벌이 되면
 * 문구와 기다리는 시간이 갈린다.
 */
@Composable
fun NicknameField(
    value: String,
    onChange: (String) -> Unit,
    check: NicknameCheck,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(CardWhite, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text("이름", color = TextMuted, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(color = TextDark, fontSize = 14.sp),
                cursorBrush = SolidColor(DaengPink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                // 자리표시자 글자가 아니라 **이 칸**을 가리킬 이름. 스크린리더가 읽고
                // 테스트가 찾는다 (`PetFormScreen` 의 `TextInput` 과 같은 이유).
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "닉네임" },
            )
        }
        nicknameCheckMessage(check)?.let { line ->
            Spacer(Modifier.height(6.dp))
            Text(line, color = checkTint(check), fontSize = 12.sp)
        }
    }
}

/**
 * 상태의 색. **[NicknameCheck.Unknown] 을 초록으로 칠하지 않는다** — 그게 이
 * 화면에서 제일 하기 쉬운 거짓말이다.
 */
private fun checkTint(check: NicknameCheck): Color = when (check) {
    NicknameCheck.Free -> DaengsColors.Success
    NicknameCheck.Taken -> DaengsColors.Error
    is NicknameCheck.Shape -> DaengsColors.Error
    else -> TextMuted
}

// -- 프리뷰 ------------------------------------------------------------------
//
// 여섯 상태를 한 번에 본다. 실기기에서는 로그인해야 볼 수 있는 화면이라
// (이 폰의 디버그 빌드로는 카카오 로그인이 안 된다) 여기가 유일하게 다 보는 자리다.

@Preview(name = "닉네임 칸 · 여섯 상태", widthDp = 320)
@Composable
private fun NicknameFieldStatesPreview() {
    DaengsTheme {
        Column(
            Modifier.background(CreamBg).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        ) {
            NicknameField("", {}, NicknameCheck.Idle)
            NicknameField("가".repeat(31), {}, NicknameCheck.Shape("30자까지 쓸 수 있어요."))
            NicknameField("네옹", {}, NicknameCheck.Checking)
            NicknameField("네옹", {}, NicknameCheck.Free)
            NicknameField("네옹", {}, NicknameCheck.Taken)
            NicknameField("네옹", {}, NicknameCheck.Unknown)
        }
    }
}
