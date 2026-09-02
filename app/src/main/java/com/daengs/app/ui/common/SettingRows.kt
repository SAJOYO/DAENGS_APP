package com.daengs.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 설정 줄. **되돌릴 수 없는 일은 전부 이 모양이다.**
 *
 * 로그아웃·회원 탈퇴가 쓰던 것을 꺼내 왔다. 배웅을 되돌리거나 아이를 기록에서 지우는
 * 것도 같은 무게라, 화면 한가운데의 버튼이 아니라 **맨 아래 줄**로 내려야 한다 —
 * 손이 먼저 가는 자리에 두면 잘못 누른다.
 *
 * 위험한 줄은 [DaengsColors.Error] 로 색만 다르게 한다. 크기를 키우거나 굵게 하면
 * 오히려 눈에 먼저 들어온다.
 */
@Composable
fun SettingSection(content: @Composable () -> Unit) {
    Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(content = { content() })
    }
}

@Composable
fun SettingRow(
    label: String,
    onClick: () -> Unit,
    tint: Color = TextDark,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = tint, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(16.dp), tint = TextMuted)
    }
}

@Composable
fun SettingDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(DaengsColors.BorderNeutral),
    )
}
