package com.daengs.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 시트의 틀 — 어둠 · 손잡이 · 제목 · 닫기.
 *
 * ⚠️ **끌어서 닫는 시트가 아니다.** 여기에는 드래그 제스처가 하나도 없다 — 어둠을 누르거나
 *    ✕ 를 누르거나 뒤로 가면 닫힌다. 손잡이는 **끌 수 있다는 표시가 아니라 여기가 시트의
 *    위쪽이라는 표시**다. 이 성질 때문에 시트 안에 세로 제스처를 먹는 부품([DateWheel])을
 *    놓아도 "끌어 내리려는 손" 과 다투지 않는다. `material3` 의 `ModalBottomSheet` 로
 *    바꾸면 그 전제가 깨진다.
 *
 * `material3` 의 `ModalBottomSheet` 를 안 쓰는 이유는 따로 있다: 이 저장소는 M3 부품 대신
 * `Surface` 에 `.clickable` 을 붙여 직접 짠다 (`DaengsControls.kt` 첫 주석) — M3 시트는
 * 자기 모서리·자기 손잡이 색을 들고 와서 크림·핑크 사이에서 혼자 튄다.
 *
 * `GaitPickSheet` 가 먼저 쓰던 것을 **두 번째 쓰임(진료비 기간 고르기)이 생기면서** 여기로
 * 올렸다 — [KeepScrollInside] 와 같은 경위다.
 *
 * @param subtitle 제목 오른쪽 작은 글자. 몇 개 골랐는지 같은 것
 */
@Composable
fun SheetFrame(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(modifier.fillMaxSize()) {
        // 바깥을 눌러도 닫힌다. 물결(ripple)은 끈다 — 시트 뒤 어둠이 눌린 것처럼
        // 번쩍이면 그쪽에 뭔가 있는 줄 안다.
        Box(
            Modifier
                .fillMaxSize()
                .background(TextDark.copy(alpha = 0.34f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Surface(
            color = CardWhite,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 시트가 화면을 다 먹지 않게 막는다. 뒤에 무엇이 조금이라도
                // 보여야 "위에 얹힌 것" 으로 읽힌다.
                .heightIn(max = 560.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            Column(Modifier.padding(horizontal = 18.dp)) {
                // 손잡이. 끌어 내릴 수 있어 보이라고 두는 것이 아니라, 여기가
                // 시트의 위쪽이라는 표시다.
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DaengsColors.BorderNeutral),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        color = TextDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (subtitle != null) {
                        Text(subtitle, color = DaengPinkDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(50)).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { DaengsIconView(DaengsIcon.Close, Modifier.size(16.dp), tint = TextMuted) }
                }
                content()
            }
        }
    }
}
