package com.daengs.app.ui.gait

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.daengs.app.gait.GaitTitleStore
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 기록 제목을 정하거나 고치는 다이얼로그. **촬영·업로드·수정 셋이 같은 것을 쓴다.**
 *
 * 촬영 완료와 파일 업로드가 둘 다 `runGait` 하나로 모이므로, 거기서 한 번 띄우면
 * 두 경로가 자동으로 같은 UI 를 탄다. 상세 화면의 ✎ 도 이걸 연다 — 초기값과 왼쪽
 * 단추 문구만 다르다 ([initial] 이 있으면 "취소", 없으면 "건너뛰기").
 *
 * **비면 기본값이다.** 빈칸으로 저장하든 건너뛰든 [onDone] 에 `null` 이 간다 — 그러면
 * 화면이 "보행 기록" 을 그린다 ([com.daengs.app.gait.GaitRecord.displayTitle]). 기본값
 * 문자열을 저장해 두지 않는 이유는 나중에 문구를 바꿀 수 있어야 해서다.
 *
 * 길이 제한([GaitTitleStore.MAX_LENGTH])은 **입력 단계에서** 막는다. 저장할 때 잘라
 * 버리면 사용자가 무엇이 잘렸는지 모른다.
 *
 * @param initial 고치는 경우의 현재 제목. 새 기록이면 null
 * @param onDone 정리된 제목(빈 값이면 null). 건너뛰기·취소도 이걸 부른다 — 취소는
 *   [initial] 을 그대로 돌려준다
 */
@Composable
fun GaitTitleDialog(
    initial: String?,
    /**
     * 고치는 중인가. 제목이 아직 없는 기록의 ✎ 도 "수정" 이다 — 초기값만 보고 정하면
     * 그때 "기록 제목을 정해주세요 / 건너뛰기" 가 떠서 새 기록을 만드는 것처럼 읽힌다.
     */
    editing: Boolean = initial != null,
    // 마지막 인자라야 부르는 쪽이 후행 람다로 넘길 수 있다.
    onDone: (String?) -> Unit,
) {
    var text by remember { mutableStateOf(initial.orEmpty()) }

    // 취소는 원래 값 그대로. 다이얼로그 밖을 눌러도 같다 — 밖을 눌렀는데 제목이
    // 지워지면 실수 하나로 기록 이름이 날아간다.
    val cancel = { onDone(initial) }

    Dialog(onDismissRequest = cancel) {
        Surface(color = CardWhite, shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (editing) "기록 제목 수정" else "기록 제목을 정해주세요",
                    color = TextDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                BasicTextField(
                    value = text,
                    // 길이는 여기서 막는다. `take` 로 조용히 자르면 붙여넣은 긴 제목이
                    // 어디서 끊겼는지 사용자가 모른다 — 대신 더 안 들어간다.
                    onValueChange = { new -> if (new.length <= GaitTitleStore.MAX_LENGTH) text = new },
                    singleLine = true,
                    textStyle = TextStyle(color = TextDark, fontSize = 15.sp),
                    cursorBrush = SolidColor(DaengPink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(PinkFaint)
                        .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(13.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text("예: 저녁 산책", color = TextMuted, fontSize = 15.sp)
                            }
                            inner()
                        }
                    },
                )

                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "비워 두면 \"${com.daengs.app.gait.GaitRecord.DEFAULT_TITLE}\" 으로 저장돼요.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${text.length} / ${GaitTitleStore.MAX_LENGTH}",
                        color = if (text.length >= GaitTitleStore.MAX_LENGTH) DaengPinkDeep else TextMuted,
                        fontSize = 12.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DialogButton(
                        if (editing) "취소" else "건너뛰기",
                        accent = false,
                        onClick = cancel,
                        modifier = Modifier.weight(1f),
                    )
                    DialogButton(
                        "저장",
                        accent = true,
                        onClick = { onDone(GaitTitleStore.normalize(text)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, accent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (accent) DaengPinkDeep else PinkFaint)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (accent) CardWhite else TextDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun GaitTitleDialogPreview() {
    // Dialog 는 @Preview 에 안 그려져서 속만 그대로 띄운다.
    DaengsTheme {
        Surface(color = CardWhite, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("기록 제목을 정해주세요", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(PinkFaint)
                        .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(13.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) { Text("저녁 산책", color = TextDark, fontSize = 15.sp) }
                Row(Modifier.fillMaxWidth()) {
                    Text("비워 두면 \"보행 기록\" 으로 저장돼요.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("5 / 20", color = TextMuted, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DialogButton("건너뛰기", accent = false, onClick = {}, modifier = Modifier.weight(1f))
                    DialogButton("저장", accent = true, onClick = {}, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
