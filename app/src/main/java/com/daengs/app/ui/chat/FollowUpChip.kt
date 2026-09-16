package com.daengs.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextMuted

/**
 * 결과 말풍선 아래 "이어 묻기" 칩의 생김새.
 *
 * 피부 판정([ReportFollowUpChip])과 보행 비교([GaitFollowUpChip])가 같은 것을 쓴다 —
 * 같은 자리에 같은 일을 하는 버튼이라, 둘이 달라 보이면 하나는 다른 종류의 버튼처럼
 * 읽힌다. 다른 것은 **글자뿐**이다.
 *
 * 답을 기다리는 동안([enabled] = false)에는 눌리지 않는다 — 입력칸과 같은 규칙이다.
 */
@Composable
internal fun FollowUpChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        color = PinkFaint,
        shape = shape,
        modifier = Modifier.clip(shape).clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            color = if (enabled) DaengPinkDeep else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FollowUpChipPreview() {
    FollowUpChip("이 결과 물어보기", enabled = true) {}
}

@Preview(showBackground = true)
@Composable
private fun FollowUpChipWaitingPreview() {
    FollowUpChip("이 결과 물어보기", enabled = false) {}
}
